package com.fieldsync.plugin.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.model.Observation;
import com.fieldsync.plugin.model.ObservationCategory;
import com.fieldsync.plugin.util.TimeFmt;

import java.util.ArrayList;
import java.util.List;

/** Renders observation rows with a category color badge, callsign, relative time and a star. */
public class ObservationAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Observation> items = new ArrayList<>();

    public ObservationAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setItems(List<Observation> observations) {
        items.clear();
        items.addAll(observations);
        notifyDataSetChanged();
    }

    public Observation getObservation(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return getObservation(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = PluginLayoutInflater.inflate(ctx, R.layout.row_observation, null);
        }
        Observation obs = items.get(position);

        TextView category = row.findViewById(R.id.row_category);
        category.setText(obs.category);
        category.setBackgroundColor(categoryColor(obs.category));

        row.findViewById(R.id.row_important)
            .setVisibility(obs.important ? View.VISIBLE : View.GONE);

        ((TextView) row.findViewById(R.id.row_callsign)).setText(obs.callsign);
        ((TextView) row.findViewById(R.id.row_time)).setText(TimeFmt.relative(obs.timestamp));
        ((TextView) row.findViewById(R.id.row_text)).setText(obs.text);

        return row;
    }

    private int categoryColor(String categoryName) {
        int res;
        switch (ObservationCategory.fromName(categoryName)) {
            case HAZARD:         res = R.color.cat_hazard; break;
            case INFRASTRUCTURE: res = R.color.cat_infrastructure; break;
            case LOGISTICS:      res = R.color.cat_logistics; break;
            case MEDICAL:        res = R.color.cat_medical; break;
            case SECURITY:       res = R.color.cat_security; break;
            default:             res = R.color.cat_general; break;
        }
        return ctx.getResources().getColor(res);
    }
}
