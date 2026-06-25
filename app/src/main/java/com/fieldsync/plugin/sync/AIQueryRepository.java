package com.fieldsync.plugin.sync;

import android.util.Log;

import com.ditto.kotlin.Ditto;
import com.ditto.kotlin.DittoQueryResultItem;
import com.ditto.kotlin.DittoStoreObserver;
import com.fieldsync.plugin.model.AIQuery;
import com.fieldsync.plugin.model.AIResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AIQueryRepository {
    private static final String TAG = "AIQueryRepo";
    private static AIQueryRepository instance;

    public interface ResponseListener {
        void onResponseReceived(AIResponse response);
    }

    public interface RecentResponsesListener {
        void onResponsesChanged(List<AIResponse> responses);
    }

    public static synchronized AIQueryRepository getInstance() {
        if (instance == null) instance = new AIQueryRepository();
        return instance;
    }

    private Ditto ditto() {
        return DittoManager.getInstance().getDitto();
    }

    public void submitQuery(AIQuery query) {
        Ditto d = ditto();
        if (d == null) { Log.w(TAG, "submitQuery skipped — Ditto not initialized"); return; }

        Map<String, Object> doc = new HashMap<>();
        doc.put("_id", query.id);
        doc.put("queryText", query.queryText);
        doc.put("requestedBy", query.requestedBy);
        doc.put("requestedByCallsign", query.requestedByCallsign);
        doc.put("timestamp", query.timestamp);
        doc.put("status", "pending");

        try {
            DittoHelper.execute(d,
                "INSERT INTO ai_queries DOCUMENTS (:doc)",
                Collections.singletonMap("doc", doc));
        } catch (Exception e) {
            Log.e(TAG, "submitQuery failed", e);
        }
    }

    public DittoStoreObserver observeResponse(String queryId, ResponseListener listener) {
        Ditto d = ditto();
        if (d == null) {
            Log.w(TAG, "observeResponse skipped — Ditto not initialized");
            return null;
        }
        Map<String, Object> args = Collections.singletonMap("queryId", queryId);
        return DittoHelper.registerObserverWithArgs(
            d,
            "SELECT * FROM ai_responses WHERE queryId=:queryId",
            args,
            result -> {
                if (!result.getItems().isEmpty()) {
                    listener.onResponseReceived(responseFromQueryItem(result.getItems().get(0)));
                }
            }
        );
    }

    /** Live feed of the most recent AI responses, newest first. Drives the AI Updates screen. */
    public DittoStoreObserver observeRecentResponses(int limit, RecentResponsesListener listener) {
        Ditto d = ditto();
        if (d == null) {
            Log.w(TAG, "observeRecentResponses skipped — Ditto not initialized");
            return null;
        }
        // Inline the limit — it is a small controlled integer, not user input.
        String query = "SELECT * FROM ai_responses ORDER BY timestamp DESC LIMIT " + limit;
        return DittoHelper.registerObserver(d, query, result -> {
            List<AIResponse> list = new ArrayList<>();
            for (DittoQueryResultItem item : result.getItems()) {
                list.add(responseFromQueryItem(item));
            }
            listener.onResponsesChanged(list);
        });
    }

    // One-shot read using an observer + latch: observer fires immediately with current store state.
    public List<AIQuery> getRecentQueries(int limit) {
        Ditto d = ditto();
        if (d == null) return Collections.emptyList();

        List<AIQuery> result = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        // Inline the limit value — it is always a small controlled integer, not user input.
        String query = "SELECT * FROM ai_queries ORDER BY timestamp DESC LIMIT " + limit;

        DittoStoreObserver observer = DittoHelper.registerObserver(d, query, queryResult -> {
            for (DittoQueryResultItem item : queryResult.getItems()) {
                result.add(queryFromQueryItem(item));
            }
            latch.countDown();
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            observer.close();
        }
        return result;
    }

    private static AIResponse responseFromQueryItem(DittoQueryResultItem item) {
        try {
            JSONObject json = new JSONObject(item.jsonString());
            AIResponse resp = new AIResponse();
            resp.id = json.optString("_id");
            resp.queryId = json.optString("queryId");
            resp.responseText = json.optString("responseText");
            resp.confidence = json.optString("confidence");
            resp.timestamp = json.optLong("timestamp");
            return resp;
        } catch (JSONException e) {
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    private static AIQuery queryFromQueryItem(DittoQueryResultItem item) {
        try {
            JSONObject json = new JSONObject(item.jsonString());
            AIQuery query = new AIQuery();
            query.id = json.optString("_id");
            query.queryText = json.optString("queryText");
            query.requestedBy = json.optString("requestedBy");
            query.requestedByCallsign = json.optString("requestedByCallsign");
            query.timestamp = json.optLong("timestamp");
            query.status = json.optString("status");
            return query;
        } catch (JSONException e) {
            throw new RuntimeException("Failed to parse AI query", e);
        }
    }
}
