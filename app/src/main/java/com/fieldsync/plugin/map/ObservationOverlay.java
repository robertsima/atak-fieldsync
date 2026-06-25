package com.fieldsync.plugin.map;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.ditto.kotlin.DittoStoreObserver;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.model.Observation;
import com.fieldsync.plugin.model.ObservationCategory;
import com.fieldsync.plugin.model.ObservationPriority;
import com.fieldsync.plugin.plugin.Actions;
import com.fieldsync.plugin.sync.ObservationRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders significant (important / high-priority / keyword) observations that carry a location
 * as markers in a dedicated "FieldSync" map group. Driven entirely by the Ditto live query, so a
 * marker appears on every peer's map as soon as the observation syncs.
 *
 * Owned by FieldSyncMapComponent — not a DropDownReceiver.
 */
public class ObservationOverlay {
    private static final String TAG = "ObservationOverlay";
    private static final String GROUP_NAME = "FieldSync";
    /** Meta key stamped on our markers so we can recognise taps and avoid touching other items. */
    public static final String META_OBS_ID = "fieldsync_obs_id";

    private final MapView mapView;
    private final Context pluginContext;

    private MapGroup group;
    private DefaultMapGroupOverlay overlay;
    private DittoStoreObserver observer;
    private MapEventDispatchListener clickListener;

    /** obs.id -> live marker, only ever touched on the UI thread. */
    private final Map<String, Marker> markers = new HashMap<>();

    public ObservationOverlay(MapView mapView, Context pluginContext) {
        this.mapView = mapView;
        this.pluginContext = pluginContext;
    }

    public void init() {
        group = new DefaultMapGroup(GROUP_NAME);
        String iconUri = "android.resource://" + pluginContext.getPackageName()
                + "/" + R.drawable.ic_fieldsync;
        overlay = new DefaultMapGroupOverlay(mapView, group, iconUri);
        mapView.getRootGroup().addGroup(group);
        mapView.getMapOverlayManager().addOverlay(overlay);

        clickListener = event -> {
            MapItem item = event.getItem();
            if (item == null) return;
            String obsId = item.getMetaString(META_OBS_ID, null);
            if (obsId == null) return;
            Intent intent = new Intent(Actions.SHOW_OBS_FORM);
            intent.putExtra("obs_id", obsId);
            AtakBroadcast.getInstance().sendBroadcast(intent);
        };
        mapView.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_CLICK, clickListener);

        observer = ObservationRepository.getInstance().observeActive(observations ->
            mapView.post(() -> syncMarkers(observations)));
        Log.i(TAG, "ObservationOverlay initialized");
    }

    /** Reconcile the marker set against the latest active observations (UI thread only). */
    private void syncMarkers(List<Observation> observations) {
        Set<String> wanted = new HashSet<>();
        for (Observation obs : observations) {
            if (!ObservationPriority.isSignificant(obs) || !hasLocation(obs)) continue;
            wanted.add(obs.id);
            Marker m = markers.get(obs.id);
            if (m == null) {
                m = createMarker(obs);
                markers.put(obs.id, m);
                group.addItem(m);
            } else {
                updateMarker(m, obs);
            }
        }
        // Drop markers whose observation is gone, archived, or no longer significant/located.
        Iterator<Map.Entry<String, Marker>> it = markers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Marker> e = it.next();
            if (!wanted.contains(e.getKey())) {
                group.removeItem(e.getValue());
                it.remove();
            }
        }
    }

    private Marker createMarker(Observation obs) {
        Marker m = new Marker(new GeoPoint(obs.latitude, obs.longitude), obs.id);
        m.setType(cotType(obs));
        m.setMetaString(META_OBS_ID, obs.id);
        m.setMetaBoolean("removable", false);
        m.setMetaBoolean("movable", false);
        m.setMetaBoolean("editable", false);
        m.setMetaBoolean("archive", false);
        m.setShowLabel(true);
        updateMarker(m, obs);
        return m;
    }

    private void updateMarker(Marker m, Observation obs) {
        m.setPoint(new GeoPoint(obs.latitude, obs.longitude));
        m.setType(cotType(obs));
        String title = obs.callsign + " · " + obs.category;
        m.setTitle(title);
        m.setMetaString("callsign", obs.callsign);
        m.setMetaString("remarks", obs.text);
    }

    private static boolean hasLocation(Observation obs) {
        return obs.latitude != 0.0 || obs.longitude != 0.0;
    }

    private static String cotType(Observation obs) {
        switch (ObservationCategory.fromName(obs.category)) {
            case HAZARD:   return "a-h-G";        // hostile ground — red
            case SECURITY: return "a-h-G";
            case MEDICAL:  return "a-f-G-U-C-I";  // friendly medical
            default:       return "a-n-G";        // neutral ground — yellow
        }
    }

    public void dispose() {
        try {
            if (observer != null) { observer.close(); observer = null; }
            if (clickListener != null) {
                mapView.getMapEventDispatcher().removeMapEventListener(MapEvent.ITEM_CLICK, clickListener);
                clickListener = null;
            }
            mapView.post(() -> {
                for (Marker m : markers.values()) group.removeItem(m);
                markers.clear();
                if (overlay != null) mapView.getMapOverlayManager().removeOverlay(overlay);
                if (group != null) mapView.getRootGroup().removeGroup(group);
            });
        } catch (Exception e) {
            Log.e(TAG, "dispose failed", e);
        }
    }
}
