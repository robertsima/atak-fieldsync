package com.fieldsync.plugin.view;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.ditto.kotlin.DittoStoreObserver;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.model.Observation;
import com.fieldsync.plugin.model.ObservationCategory;
import com.fieldsync.plugin.plugin.Actions;
import com.fieldsync.plugin.sync.AIQueryRepository;
import com.fieldsync.plugin.sync.ObservationRepository;

import java.util.UUID;

/**
 * The single FieldSync hub opened from the toolbar icon. Three tabs — Quick Add, Observations
 * (live list) and AI Updates (live feed) — so everything is in one place. Create/edit still opens
 * the separate {@link ObservationFormDropDown} (also used by map long-press and marker taps).
 */
public class MainDropDown extends DropDownReceiver implements DropDown.OnStateListener {

    private static final int AI_FEED_LIMIT = 20;

    private final View view;
    private final EditText quickText;
    private final CheckBox quickImportant;

    private final View tabQuick, tabObs, tabAi;
    private final Button tabBtnQuick, tabBtnObs, tabBtnAi;

    private final ObservationAdapter obsAdapter;
    private final AIUpdateAdapter aiAdapter;

    private DittoStoreObserver obsObserver;
    private DittoStoreObserver aiObserver;

    public MainDropDown(MapView mapView, Context ctx) {
        super(mapView);
        view = PluginLayoutInflater.inflate(ctx, R.layout.dropdown_main, null);

        // Quick Add
        quickText = view.findViewById(R.id.quick_text);
        quickImportant = view.findViewById(R.id.quick_important);
        view.<Button>findViewById(R.id.btn_quick_drop).setOnClickListener(v -> quickDrop());

        // Tabs
        tabQuick = view.findViewById(R.id.tab_quick);
        tabObs = view.findViewById(R.id.tab_obs);
        tabAi = view.findViewById(R.id.tab_ai);
        tabBtnQuick = view.findViewById(R.id.tab_btn_quick);
        tabBtnObs = view.findViewById(R.id.tab_btn_obs);
        tabBtnAi = view.findViewById(R.id.tab_btn_ai);
        tabBtnQuick.setOnClickListener(v -> selectTab(0));
        tabBtnObs.setOnClickListener(v -> selectTab(1));
        tabBtnAi.setOnClickListener(v -> selectTab(2));

        // Observations tab
        obsAdapter = new ObservationAdapter(ctx);
        ListView obsList = view.findViewById(R.id.obs_list);
        obsList.setEmptyView(view.findViewById(R.id.obs_empty));
        obsList.setAdapter(obsAdapter);
        obsList.setOnItemClickListener((parent, v, position, id) -> {
            Observation obs = obsAdapter.getObservation(position);
            if (obs != null) {
                Intent intent = new Intent(Actions.SHOW_OBS_FORM);
                intent.putExtra("obs_id", obs.id);
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        });
        view.<Button>findViewById(R.id.btn_new_observation).setOnClickListener(v ->
            AtakBroadcast.getInstance().sendBroadcast(new Intent(Actions.SHOW_OBS_FORM)));

        // AI Updates tab
        aiAdapter = new AIUpdateAdapter(ctx);
        ListView aiList = view.findViewById(R.id.ai_list);
        aiList.setEmptyView(view.findViewById(R.id.ai_empty));
        aiList.setAdapter(aiAdapter);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Actions.SHOW_MAIN.equals(intent.getAction())) {
            startObserving();
            selectTab(1); // open to the live Observations picture
            showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
        }
    }

    private void selectTab(int index) {
        tabQuick.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabObs.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabAi.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        tabBtnQuick.setAlpha(index == 0 ? 1f : 0.5f);
        tabBtnObs.setAlpha(index == 1 ? 1f : 0.5f);
        tabBtnAi.setAlpha(index == 2 ? 1f : 0.5f);
    }

    /** One-tap capture at the operator's own location, no form. */
    private void quickDrop() {
        String text = quickText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(getMapView().getContext(),
                "Type something to add", Toast.LENGTH_SHORT).show();
            return;
        }

        MapView mv = getMapView();
        Observation obs = new Observation();
        obs.id = UUID.randomUUID().toString();
        obs.text = text;
        obs.category = ObservationCategory.GENERAL.name();
        obs.important = quickImportant.isChecked();
        obs.timestamp = System.currentTimeMillis();
        obs.archived = false;
        obs.operatorUID = mv.getSelfMarker().getUID();
        obs.callsign = mv.getSelfMarker().getMetaString("callsign", "UNKNOWN");

        GeoPoint pt = mv.getSelfMarker().getPoint();
        if (pt != null) {
            obs.latitude = pt.getLatitude();
            obs.longitude = pt.getLongitude();
        }

        ObservationRepository.getInstance().insert(obs);
        quickText.setText("");
        quickImportant.setChecked(false);
        Toast.makeText(mv.getContext(), "Observation added", Toast.LENGTH_SHORT).show();
        selectTab(1); // jump to the list so they see it land
    }

    private void startObserving() {
        if (obsObserver == null) {
            obsObserver = ObservationRepository.getInstance().observeActive(observations ->
                getMapView().post(() -> obsAdapter.setItems(observations)));
        }
        if (aiObserver == null) {
            aiObserver = AIQueryRepository.getInstance().observeRecentResponses(
                AI_FEED_LIMIT, responses -> getMapView().post(() -> aiAdapter.setItems(responses)));
        }
    }

    private void stopObserving() {
        if (obsObserver != null) { obsObserver.close(); obsObserver = null; }
        if (aiObserver != null) { aiObserver.close(); aiObserver = null; }
    }

    @Override public void onDropDownSelectionRemoved() {}
    @Override public void onDropDownVisible(boolean v) {}
    @Override public void onDropDownSizeChanged(double width, double height) {}
    @Override public void onDropDownClose() { stopObserving(); }

    @Override
    protected void disposeImpl() {
        stopObserving();
    }
}
