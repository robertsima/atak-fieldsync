package com.fieldsync.plugin.sync;

import android.util.Log;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.ditto.kotlin.DittoStoreObserver;
import com.fieldsync.plugin.model.AIQuery;
import com.fieldsync.plugin.model.Observation;
import com.fieldsync.plugin.model.ObservationPriority;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Watches the observation feed and, with no operator involvement, publishes an enriched AI query
 * whenever a NEW significant observation arrives (important / high-priority / keyword — see
 * {@link ObservationPriority}). The AI node reads the query, decides whether it's useful, and (if
 * so) writes a response that surfaces in the AI Updates feed.
 *
 * Seed-on-first-callback avoids a burst of queries for observations that already existed at
 * startup: the first callback only records ids; later callbacks trigger on genuinely new ones.
 *
 * Owned by FieldSyncMapComponent.
 */
public class AIAutoTrigger {
    private static final String TAG = "AIAutoTrigger";

    private final MapView mapView;
    private final Set<String> seenIds = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean seeded = false;
    private DittoStoreObserver observer;

    public AIAutoTrigger(MapView mapView) {
        this.mapView = mapView;
    }

    public void init() {
        observer = ObservationRepository.getInstance().observeActive(this::onObservations);
        Log.i(TAG, "AIAutoTrigger initialized");
    }

    private void onObservations(List<Observation> observations) {
        if (!seeded) {
            for (Observation o : observations) seenIds.add(o.id);
            seeded = true;
            return;
        }
        for (Observation o : observations) {
            if (!seenIds.add(o.id)) continue; // already processed this id
            if (ObservationPriority.isSignificant(o)) {
                trigger(o);
            }
        }
    }

    private void trigger(Observation o) {
        try {
            String callsign = mapView.getSelfMarker().getMetaString("callsign", "UNKNOWN");
            String obsLoc = (o.latitude != 0.0 || o.longitude != 0.0)
                ? String.format(Locale.US, "%.5f, %.5f", o.latitude, o.longitude)
                : "unknown";

            GeoPoint self = mapView.getSelfMarker().getPoint();
            String selfLoc = self != null
                ? String.format(Locale.US, "%.5f, %.5f", self.getLatitude(), self.getLongitude())
                : "unknown";

            String queryText = "New field observation from " + o.callsign
                + " [" + o.category + "] at " + obsLoc + ": \"" + o.text + "\". "
                + "Requesting operator " + callsign + " is at " + selfLoc + ". "
                + "Assess relevance to this operator's position and provide a brief update only if useful.";

            AIQuery query = new AIQuery();
            query.id = UUID.randomUUID().toString();
            query.queryText = queryText;
            query.requestedBy = mapView.getSelfMarker().getUID();
            query.requestedByCallsign = callsign;
            query.timestamp = System.currentTimeMillis();
            query.status = "pending";

            AIQueryRepository.getInstance().submitQuery(query);
            Log.i(TAG, "Auto-triggered AI query for observation " + o.id);
        } catch (Exception e) {
            Log.e(TAG, "trigger failed for observation " + o.id, e);
        }
    }

    public void dispose() {
        if (observer != null) {
            observer.close();
            observer = null;
        }
    }
}
