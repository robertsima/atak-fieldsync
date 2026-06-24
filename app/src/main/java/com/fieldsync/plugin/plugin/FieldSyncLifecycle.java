package com.fieldsync.plugin.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import gov.tak.api.plugin.IServiceController;

public class FieldSyncLifecycle extends AbstractPlugin {
    public FieldSyncLifecycle(IServiceController svc) {
        super(svc,
            new FieldSyncTool(svc.getService(PluginContextProvider.class).getPluginContext()),
            new FieldSyncMapComponent());
    }
}
