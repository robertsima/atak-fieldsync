package com.fieldsync.plugin.plugin;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.sync.DittoManager;
import com.fieldsync.plugin.ui.AIQueryDropDown;
import com.fieldsync.plugin.ui.MainDropDown;
import com.fieldsync.plugin.ui.ObservationFormDropDown;
import com.fieldsync.plugin.ui.ObservationListDropDown;

public class FieldSyncMapComponent extends DropDownMapComponent {

    private MainDropDown mainDropDown;
    private ObservationListDropDown observationListDropDown;
    private ObservationFormDropDown observationFormDropDown;
    private AIQueryDropDown aiQueryDropDown;

    @Override
    public void onCreate(Context ctx, Intent intent, MapView mapView) {
        ctx.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(ctx, intent, mapView);

        DittoManager.getInstance().init(ctx);

        mainDropDown = new MainDropDown(mapView, ctx);
        observationListDropDown = new ObservationListDropDown(mapView, ctx);
        observationFormDropDown = new ObservationFormDropDown(mapView, ctx);
        aiQueryDropDown = new AIQueryDropDown(mapView, ctx);

        registerDropDownReceiver(mainDropDown,
            new AtakBroadcast.DocumentedIntentFilter(Actions.SHOW_MAIN));
        registerDropDownReceiver(observationListDropDown,
            new AtakBroadcast.DocumentedIntentFilter(Actions.SHOW_OBS_LIST));
        registerDropDownReceiver(observationFormDropDown,
            new AtakBroadcast.DocumentedIntentFilter(Actions.SHOW_OBS_FORM));
        registerDropDownReceiver(aiQueryDropDown,
            new AtakBroadcast.DocumentedIntentFilter(Actions.SHOW_AI_QUERY));
    }

    @Override
    protected void onDestroyImpl(Context ctx, MapView mapView) {
        DittoManager.getInstance().close();
        if (mainDropDown != null) mainDropDown.dispose();
        if (observationListDropDown != null) observationListDropDown.dispose();
        if (observationFormDropDown != null) observationFormDropDown.dispose();
        if (aiQueryDropDown != null) aiQueryDropDown.dispose();
        super.onDestroyImpl(ctx, mapView);
    }
}
