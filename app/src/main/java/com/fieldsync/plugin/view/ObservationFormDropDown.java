package com.fieldsync.plugin.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
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

import java.util.Locale;
import java.util.UUID;

public class ObservationFormDropDown extends DropDownReceiver implements DropDown.OnStateListener {

    private final View view;
    private final EditText textInput;
    private final Spinner categorySpinner;
    private final CheckBox locationCheckbox;
    private final CheckBox importantCheckbox;
    private final TextView coordText;
    private final Button saveButton;
    private final Button archiveButton;
    private final String[] categoryNames;

    private String editingId = null;
    private double pendingLat, pendingLon;
    private boolean hasPendingLoc;

    public ObservationFormDropDown(MapView mapView, Context ctx) {
        super(mapView);
        view = PluginLayoutInflater.inflate(ctx, R.layout.dropdown_observation_form, null);

        textInput = view.findViewById(R.id.obs_text);
        categorySpinner = view.findViewById(R.id.obs_category);
        locationCheckbox = view.findViewById(R.id.obs_use_location);
        importantCheckbox = view.findViewById(R.id.obs_important);
        coordText = view.findViewById(R.id.obs_coord);
        saveButton = view.findViewById(R.id.btn_save);
        archiveButton = view.findViewById(R.id.btn_archive);

        ObservationCategory[] categories = ObservationCategory.values();
        categoryNames = new String[categories.length];
        for (int i = 0; i < categories.length; i++) categoryNames[i] = categories[i].name();

        // The plugin dark theme leaves dropdown rows white-on-white. Force readable colors:
        // collapsed value white (sits on the dark panel), popup rows black on white.
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<String>(ctx,
                android.R.layout.simple_spinner_item, categoryNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.WHITE);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(Color.BLACK);
                tv.setBackgroundColor(Color.WHITE);
                int pad = (int) (12 * parent.getResources().getDisplayMetrics().density);
                tv.setPadding(pad, pad, pad, pad);
                return tv;
            }
        };
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        saveButton.setOnClickListener(v -> save());
        archiveButton.setOnClickListener(v -> archive());
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Actions.SHOW_OBS_FORM.equals(intent.getAction())) return;

        editingId = intent.getStringExtra("obs_id");
        boolean isEdit = editingId != null;

        // Reset to a clean form.
        textInput.setText("");
        categorySpinner.setSelection(0);
        locationCheckbox.setChecked(false);
        importantCheckbox.setChecked(false);
        coordText.setVisibility(View.GONE);
        hasPendingLoc = false;
        archiveButton.setVisibility(isEdit ? View.VISIBLE : View.GONE);

        if (isEdit) {
            Observation obs = ObservationRepository.getInstance().getById(editingId);
            if (obs != null) populateFrom(obs);
        } else {
            double lat = intent.getDoubleExtra("lat", Double.NaN);
            double lon = intent.getDoubleExtra("lon", Double.NaN);
            if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                setLocation(lat, lon);
            }
            if (intent.getBooleanExtra("quick", false)) {
                textInput.requestFocus();
            }
        }

        showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
    }

    private void populateFrom(Observation obs) {
        textInput.setText(obs.text);
        importantCheckbox.setChecked(obs.important);
        for (int i = 0; i < categoryNames.length; i++) {
            if (categoryNames[i].equalsIgnoreCase(obs.category)) {
                categorySpinner.setSelection(i);
                break;
            }
        }
        if (obs.latitude != 0.0 || obs.longitude != 0.0) {
            setLocation(obs.latitude, obs.longitude);
        }
    }

    private void setLocation(double lat, double lon) {
        pendingLat = lat;
        pendingLon = lon;
        hasPendingLoc = true;
        locationCheckbox.setChecked(true);
        coordText.setText(String.format(Locale.US, "%.5f, %.5f", lat, lon));
        coordText.setVisibility(View.VISIBLE);
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
        obs.important = importantCheckbox.isChecked();
        obs.timestamp = System.currentTimeMillis();
        obs.archived = false;

        MapView mv = getMapView();
        obs.operatorUID = mv.getSelfMarker().getUID();
        obs.callsign = mv.getSelfMarker().getMetaString("callsign", "UNKNOWN");

        if (locationCheckbox.isChecked()) {
            if (hasPendingLoc) {
                obs.latitude = pendingLat;
                obs.longitude = pendingLon;
            } else {
                GeoPoint pt = mv.getSelfMarker().getPoint();
                obs.latitude = pt.getLatitude();
                obs.longitude = pt.getLongitude();
            }
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
