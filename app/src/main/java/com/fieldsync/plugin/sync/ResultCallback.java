package com.fieldsync.plugin.sync;

import com.ditto.kotlin.DittoQueryResult;

public interface ResultCallback {
    void onResult(DittoQueryResult result);
}
