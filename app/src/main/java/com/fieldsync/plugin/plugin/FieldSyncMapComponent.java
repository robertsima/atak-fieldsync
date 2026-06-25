package com.fieldsync.plugin.plugin;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.map.ObservationOverlay;
import com.fieldsync.plugin.sync.AIAutoTrigger;
import com.fieldsync.plugin.sync.DittoManager;
import com.fieldsync.plugin.view.MainDropDown;
import com.fieldsync.plugin.view.ObservationFormDropDown;

public class FieldSyncMapComponent extends DropDownMapComponent {

    private MainDropDown mainDropDown;
    private ObservationFormDropDown observationFormDropDown;

    private ObservationOverlay observationOverlay;
    private AIAutoTrigger aiAutoTrigger;
    private MapEventDispatchListener longPressListener;

    @Override
    public void onCreate(Context ctx, Intent intent, MapView mapView) {
        ctx.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(ctx, intent, mapView);

        DittoManager.getInstance().init(ctx);

        mainDropDown = new MainDropDown(mapView, ctx);
        observationFormDropDown = new ObservationFormDropDown(mapView, ctx);

        registerDropDownReceiver(mainDropDown,
            new AtakBroadcast.DocumentedIntentFilter(Actions.SHOW_MAIN));
        registerDropDownReceiver(observationFormDropDown,
            new AtakBroadcast.DocumentedIntentFilter(Actions.SHOW_OBS_FORM));

        // Map markers for significant observations.
        observationOverlay = new ObservationOverlay(mapView, ctx);
        observationOverlay.init();

        // Autonomous AI updates.
        aiAutoTrigger = new AIAutoTrigger(mapView);
        aiAutoTrigger.init();

        // Long-press the map to drop an objective/update at that point.
        longPressListener = event -> {
            PointF p = event.getPointF();
            if (p == null) return;
            GeoPointMetaData g = mapView.inverseWithElevation(p.x, p.y);
            if (g == null || g.get() == null) return;
            Intent i = new Intent(Actions.SHOW_OBS_FORM);
            i.putExtra("lat", g.get().getLatitude());
            i.putExtra("lon", g.get().getLongitude());
            i.putExtra("quick", true);
            AtakBroadcast.getInstance().sendBroadcast(i);
        };
        mapView.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_LONG_PRESS, longPressListener);
    }

    @Override
    protected void onDestroyImpl(Context ctx, MapView mapView) {
        if (longPressListener != null) {
            mapView.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_LONG_PRESS, longPressListener);
            longPressListener = null;
        }
        if (aiAutoTrigger != null) aiAutoTrigger.dispose();
        if (observationOverlay != null) observationOverlay.dispose();

        DittoManager.getInstance().close();
        if (mainDropDown != null) mainDropDown.dispose();
        if (observationFormDropDown != null) observationFormDropDown.dispose();
        super.onDestroyImpl(ctx, mapView);
    }
}
