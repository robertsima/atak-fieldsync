package com.fieldsync.plugin.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.ditto.kotlin.DittoStoreObserver;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.model.Observation;
import com.fieldsync.plugin.plugin.Actions;
import com.fieldsync.plugin.sync.ObservationRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ObservationListDropDown extends DropDownReceiver
        implements DropDown.OnStateListener {

    private final View view;
    private final ArrayAdapter<String> adapter;
    private final List<Observation> currentObs = new ArrayList<>();
    private DittoStoreObserver dittoObserver;

    public ObservationListDropDown(MapView mapView, Context ctx) {
        super(mapView);
        view = PluginLayoutInflater.inflate(ctx, R.layout.dropdown_observation_list, null);

        adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, new ArrayList<>());
        ListView listView = view.findViewById(R.id.obs_list);
        listView.setAdapter(adapter);

        view.<Button>findViewById(R.id.btn_new_observation).setOnClickListener(v ->
            AtakBroadcast.getInstance().sendBroadcast(new Intent(Actions.SHOW_OBS_FORM)));

        listView.setOnItemClickListener((parent, v, position, id) -> {
            if (position < currentObs.size()) {
                Intent intent = new Intent(Actions.SHOW_OBS_FORM);
                intent.putExtra("obs_id", currentObs.get(position).id);
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        });
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Actions.SHOW_OBS_LIST.equals(intent.getAction())) {
            startObserving();
            showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
        }
    }

    private void startObserving() {
        if (dittoObserver != null) return;
        dittoObserver = ObservationRepository.getInstance().observeActive(observations -> {
            currentObs.clear();
            currentObs.addAll(observations);
            getMapView().post(() -> {
                adapter.clear();
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.US);
                for (Observation obs : observations) {
                    String label = "[" + obs.category + "] " + obs.callsign
                        + "  " + sdf.format(new Date(obs.timestamp))
                        + "\n" + truncate(obs.text, 80);
                    adapter.add(label);
                }
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void stopObserving() {
        if (dittoObserver != null) {
            dittoObserver.close();
            dittoObserver = null;
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {}

    @Override
    public void onDropDownVisible(boolean v) {}

    @Override
    public void onDropDownSizeChanged(double width, double height) {}

    @Override
    public void onDropDownClose() {
        stopObserving();
    }

    @Override
    protected void disposeImpl() {
        stopObserving();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
