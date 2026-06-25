package com.fieldsync.plugin.plugin;

import android.content.Context;
import com.atak.plugins.impl.AbstractPluginTool;
import com.fieldsync.plugin.R;

public class FieldSyncTool extends AbstractPluginTool {
    public FieldSyncTool(Context ctx) {
        super(ctx,
            ctx.getString(R.string.app_name),
            ctx.getString(R.string.app_name),
            ctx.getResources().getDrawable(R.drawable.ic_fieldsync),
            Actions.SHOW_MAIN);
    }
}
