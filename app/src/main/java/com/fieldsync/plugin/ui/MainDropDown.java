package com.fieldsync.plugin.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.plugin.Actions;

public class MainDropDown extends DropDownReceiver implements DropDown.OnStateListener {

    private final View view;

    public MainDropDown(MapView mapView, Context ctx) {
        super(mapView);
        view = PluginLayoutInflater.inflate(ctx, R.layout.dropdown_main, null);

        view.<Button>findViewById(R.id.btn_observations).setOnClickListener(v ->
            AtakBroadcast.getInstance().sendBroadcast(new Intent(Actions.SHOW_OBS_LIST)));

        view.<Button>findViewById(R.id.btn_ask_ai).setOnClickListener(v ->
            AtakBroadcast.getInstance().sendBroadcast(new Intent(Actions.SHOW_AI_QUERY)));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Actions.SHOW_MAIN.equals(intent.getAction())) {
            showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
        }
    }

    @Override public void onDropDownSelectionRemoved() {}
    @Override public void onDropDownVisible(boolean v) {}
    @Override public void onDropDownSizeChanged(double width, double height) {}
    @Override public void onDropDownClose() {}

    @Override
    protected void disposeImpl() {}
}
