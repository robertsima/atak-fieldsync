package com.fieldsync.plugin.sync;

import android.content.Context;
import android.util.Log;

import com.ditto.kotlin.Ditto;
import com.ditto.kotlin.DittoSyncSubscription;
import com.fieldsync.plugin.BuildConfig;

public class DittoManager {
    private static final String TAG = "DittoManager";
    private static DittoManager instance;

    private Ditto ditto;
    private DittoSyncSubscription observationsSubscription;
    private DittoSyncSubscription queriesSubscription;
    private DittoSyncSubscription responsesSubscription;

    public static synchronized DittoManager getInstance() {
        if (instance == null) instance = new DittoManager();
        return instance;
    }

    public void init(Context ctx) {
        try {
            DittoHelper.initialize(ctx);
            ditto = DittoHelper.createDitto(
                BuildConfig.DITTO_APP_ID,
                BuildConfig.DITTO_AUTH_URL
            );
            DittoHelper.setupAuth(ditto, BuildConfig.DITTO_PLAYGROUND_TOKEN);

            observationsSubscription = DittoHelper.registerSubscription(
                ditto, "SELECT * FROM observations");
            queriesSubscription = DittoHelper.registerSubscription(
                ditto, "SELECT * FROM ai_queries");
            responsesSubscription = DittoHelper.registerSubscription(
                ditto, "SELECT * FROM ai_responses");

            DittoHelper.startSync(ditto);
            Log.i(TAG, "Ditto initialized, sync started");
        } catch (Throwable e) {
            Log.e(TAG, "Ditto initialization failed — sync disabled", e);
            ditto = null;
        }
    }

    public Ditto getDitto() {
        return ditto;
    }

    public void close() {
        try {
            if (observationsSubscription != null) observationsSubscription.close();
            if (queriesSubscription != null) queriesSubscription.close();
            if (responsesSubscription != null) responsesSubscription.close();
            if (ditto != null) {
                DittoHelper.stopSync(ditto);
                ditto.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing Ditto", e);
        } finally {
            ditto = null;
            observationsSubscription = null;
            queriesSubscription = null;
            responsesSubscription = null;
        }
    }
}
