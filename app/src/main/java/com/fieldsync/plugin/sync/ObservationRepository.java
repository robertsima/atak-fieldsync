package com.fieldsync.plugin.sync;

import android.util.Log;

import com.ditto.kotlin.Ditto;
import com.ditto.kotlin.DittoQueryResultItem;
import com.ditto.kotlin.DittoStoreObserver;
import com.fieldsync.plugin.model.Observation;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObservationRepository {
    private static final String TAG = "ObservationRepo";
    private static ObservationRepository instance;

    public interface ObservationListener {
        void onObservationsChanged(List<Observation> observations);
    }

    public static synchronized ObservationRepository getInstance() {
        if (instance == null) instance = new ObservationRepository();
        return instance;
    }

    private Ditto ditto() {
        return DittoManager.getInstance().getDitto();
    }

    public void insert(Observation obs) {
        Ditto d = ditto();
        if (d == null) { Log.w(TAG, "insert skipped — Ditto not initialized"); return; }

        Map<String, Object> doc = new HashMap<>();
        doc.put("_id", obs.id);
        doc.put("operatorUID", obs.operatorUID);
        doc.put("callsign", obs.callsign);
        doc.put("text", obs.text);
        doc.put("category", obs.category);
        doc.put("latitude", obs.latitude);
        doc.put("longitude", obs.longitude);
        doc.put("timestamp", obs.timestamp);
        doc.put("archived", obs.archived);
        doc.put("important", obs.important);

        try {
            // Upsert: editing an existing observation reuses its _id, so an INSERT would
            // otherwise conflict. ON ID CONFLICT DO UPDATE makes create and edit share this path.
            DittoHelper.execute(d,
                "INSERT INTO observations DOCUMENTS (:doc) ON ID CONFLICT DO UPDATE",
                Collections.singletonMap("doc", doc));
        } catch (Exception e) {
            Log.e(TAG, "insert failed", e);
        }
    }

    public void archive(String id) {
        Ditto d = ditto();
        if (d == null) { Log.w(TAG, "archive skipped — Ditto not initialized"); return; }

        try {
            DittoHelper.execute(d,
                "UPDATE observations SET archived=true WHERE _id=:id",
                Collections.singletonMap("id", id));
        } catch (Exception e) {
            Log.e(TAG, "archive failed", e);
        }
    }

    /** One-shot lookup of a single observation by id. Returns null if not found / Ditto down. */
    public Observation getById(String id) {
        Ditto d = ditto();
        if (d == null) return null;

        final Observation[] holder = new Observation[1];
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        DittoStoreObserver obs = DittoHelper.registerObserverWithArgs(
            d,
            "SELECT * FROM observations WHERE _id=:id",
            Collections.singletonMap("id", id),
            result -> {
                if (!result.getItems().isEmpty()) {
                    holder[0] = fromQueryItem(result.getItems().get(0));
                }
                latch.countDown();
            });
        try {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            if (obs != null) obs.close();
        }
        return holder[0];
    }

    public DittoStoreObserver observeActive(ObservationListener listener) {
        Ditto d = ditto();
        if (d == null) {
            Log.w(TAG, "observeActive skipped — Ditto not initialized");
            return null;
        }
        return DittoHelper.registerObserver(
            d,
            "SELECT * FROM observations WHERE archived=false ORDER BY timestamp DESC",
            result -> {
                List<Observation> obs = new ArrayList<>();
                for (DittoQueryResultItem item : result.getItems()) {
                    obs.add(fromQueryItem(item));
                }
                listener.onObservationsChanged(obs);
            }
        );
    }

    private static Observation fromQueryItem(DittoQueryResultItem item) {
        try {
            JSONObject json = new JSONObject(item.jsonString());
            Observation obs = new Observation();
            obs.id = json.optString("_id");
            obs.operatorUID = json.optString("operatorUID");
            obs.callsign = json.optString("callsign");
            obs.text = json.optString("text");
            obs.category = json.optString("category");
            obs.latitude = json.optDouble("latitude", 0.0);
            obs.longitude = json.optDouble("longitude", 0.0);
            obs.timestamp = json.optLong("timestamp");
            obs.archived = json.optBoolean("archived", false);
            obs.important = json.optBoolean("important", false);
            return obs;
        } catch (JSONException e) {
            throw new RuntimeException("Failed to parse observation", e);
        }
    }
}
