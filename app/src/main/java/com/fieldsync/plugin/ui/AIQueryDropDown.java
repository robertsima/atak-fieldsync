package com.fieldsync.plugin.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.ditto.kotlin.DittoStoreObserver;
import com.fieldsync.plugin.R;
import com.fieldsync.plugin.model.AIQuery;
import com.fieldsync.plugin.plugin.Actions;
import com.fieldsync.plugin.sync.AIQueryRepository;

import java.util.UUID;

public class AIQueryDropDown extends DropDownReceiver implements DropDown.OnStateListener {

    private static final long TIMEOUT_MS = 60_000;

    private final View view;
    private final EditText queryInput;
    private final Button sendButton;
    private final TextView statusText;
    private final TextView responseText;

    private DittoStoreObserver responseObserver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    public AIQueryDropDown(MapView mapView, Context ctx) {
        super(mapView);
        view = PluginLayoutInflater.inflate(ctx, R.layout.dropdown_ai_query, null);

        queryInput = view.findViewById(R.id.query_input);
        sendButton = view.findViewById(R.id.btn_send);
        statusText = view.findViewById(R.id.query_status);
        responseText = view.findViewById(R.id.query_response);

        sendButton.setOnClickListener(v -> submitQuery());
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Actions.SHOW_AI_QUERY.equals(intent.getAction())) {
            queryInput.setText("");
            statusText.setText("");
            responseText.setText("");
            statusText.setVisibility(View.GONE);
            showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
        }
    }

    private void submitQuery() {
        String text = queryInput.getText().toString().trim();
        if (text.isEmpty()) return;

        clearPreviousObserver();

        AIQuery query = new AIQuery();
        query.id = UUID.randomUUID().toString();
        query.queryText = text;
        query.requestedBy = getMapView().getSelfMarker().getUID();
        query.requestedByCallsign = getMapView().getSelfMarker().getMetaString("callsign", "UNKNOWN");
        query.timestamp = System.currentTimeMillis();
        query.status = "pending";

        AIQueryRepository.getInstance().submitQuery(query);

        sendButton.setEnabled(false);
        statusText.setText("Waiting for AI node…");
        statusText.setVisibility(View.VISIBLE);
        responseText.setText("");

        responseObserver = AIQueryRepository.getInstance().observeResponse(query.id, response -> {
            handler.post(() -> {
                cancelTimeout();
                statusText.setVisibility(View.GONE);
                responseText.setText(response.responseText);
                sendButton.setEnabled(true);
            });
        });

        timeoutRunnable = () -> {
            clearPreviousObserver();
            statusText.setText("No response from AI node. Is it running on the network?");
            sendButton.setEnabled(true);
        };
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    private void clearPreviousObserver() {
        cancelTimeout();
        if (responseObserver != null) {
            responseObserver.close();
            responseObserver = null;
        }
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    @Override public void onDropDownSelectionRemoved() {}
    @Override public void onDropDownVisible(boolean v) {}
    @Override public void onDropDownSizeChanged(double width, double height) {}
    @Override public void onDropDownClose() { clearPreviousObserver(); }

    @Override
    protected void disposeImpl() {
        clearPreviousObserver();
    }
}
