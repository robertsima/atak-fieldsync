package com.fieldsync.plugin.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.model.Observation;
import com.fieldsync.plugin.model.ObservationCategory;
import com.fieldsync.plugin.plugin.Actions;
import com.fieldsync.plugin.sync.ObservationRepository;

import java.util.UUID;

public class ObservationFormDropDown extends DropDownReceiver implements DropDown.OnStateListener {

    private final View view;
    private final EditText textInput;
    private final Spinner categorySpinner;
    private final CheckBox locationCheckbox;
    private final Button saveButton;
    private final Button archiveButton;

    private String editingId = null;

    public ObservationFormDropDown(MapView mapView, Context ctx) {
        super(mapView);
        view = PluginLayoutInflater.inflate(ctx, R.layout.dropdown_observation_form, null);

        textInput = view.findViewById(R.id.obs_text);
        categorySpinner = view.findViewById(R.id.obs_category);
        locationCheckbox = view.findViewById(R.id.obs_use_location);
        saveButton = view.findViewById(R.id.btn_save);
        archiveButton = view.findViewById(R.id.btn_archive);

        ObservationCategory[] categories = ObservationCategory.values();
        String[] categoryNames = new String[categories.length];
        for (int i = 0; i < categories.length; i++) categoryNames[i] = categories[i].name();
        categorySpinner.setAdapter(new ArrayAdapter<>(ctx,
            android.R.layout.simple_spinner_dropdown_item, categoryNames));

        saveButton.setOnClickListener(v -> save());
        archiveButton.setOnClickListener(v -> archive());
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Actions.SHOW_OBS_FORM.equals(intent.getAction())) return;

        editingId = intent.getStringExtra("obs_id");
        boolean isEdit = editingId != null;

        textInput.setText("");
        categorySpinner.setSelection(0);
        locationCheckbox.setChecked(false);
        archiveButton.setVisibility(isEdit ? View.VISIBLE : View.GONE);

        showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
    }

    private void save() {
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(getMapView().getContext(),
                "Observation text cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Observation obs = new Observation();
        obs.id = editingId != null ? editingId : UUID.randomUUID().toString();
        obs.text = text;
        obs.category = categorySpinner.getSelectedItem().toString();
        obs.timestamp = System.currentTimeMillis();
        obs.archived = false;

        MapView mv = getMapView();
        obs.operatorUID = mv.getSelfMarker().getUID();
        obs.callsign = mv.getSelfMarker().getMetaString("callsign", "UNKNOWN");

        if (locationCheckbox.isChecked()) {
            GeoPoint pt = mv.getSelfMarker().getPoint();
            obs.latitude = pt.getLatitude();
            obs.longitude = pt.getLongitude();
        }

        ObservationRepository.getInstance().insert(obs);
        closeDropDown();
    }

    private void archive() {
        if (editingId != null) {
            ObservationRepository.getInstance().archive(editingId);
            closeDropDown();
        }
    }

    @Override public void onDropDownSelectionRemoved() {}
    @Override public void onDropDownVisible(boolean v) {}
    @Override public void onDropDownSizeChanged(double width, double height) {}
    @Override public void onDropDownClose() {}

    @Override
    protected void disposeImpl() {}
}
