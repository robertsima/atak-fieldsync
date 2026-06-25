package com.fieldsync.plugin.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.model.AIResponse;
import com.fieldsync.plugin.util.TimeFmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders AI update cards: confidence chip, relative time, response text. */
public class AIUpdateAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<AIResponse> items = new ArrayList<>();

    public AIUpdateAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setItems(List<AIResponse> responses) {
        items.clear();
        items.addAll(responses);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = PluginLayoutInflater.inflate(ctx, R.layout.row_ai_update, null);
        }
        AIResponse resp = items.get(position);

        TextView conf = row.findViewById(R.id.ai_confidence);
        String confidence = resp.confidence == null || resp.confidence.isEmpty()
            ? "—" : resp.confidence.toUpperCase(Locale.US);
        conf.setText(confidence);
        conf.setBackgroundColor(confidenceColor(resp.confidence));

        ((TextView) row.findViewById(R.id.ai_time)).setText(TimeFmt.relative(resp.timestamp));
        ((TextView) row.findViewById(R.id.ai_text)).setText(resp.responseText);

        return row;
    }

    private int confidenceColor(String confidence) {
        int res = R.color.cat_general;
        if (confidence != null) {
            switch (confidence.toLowerCase(Locale.US)) {
                case "low":      res = R.color.conf_low; break;
                case "moderate": res = R.color.conf_moderate; break;
                case "high":     res = R.color.conf_high; break;
                default:         res = R.color.cat_general; break;
            }
        }
        return ctx.getResources().getColor(res);
    }
}
