package com.fieldsync.plugin.model;

public enum ObservationCategory {
    HAZARD,
    INFRASTRUCTURE,
    LOGISTICS,
    MEDICAL,
    SECURITY,
    GENERAL;

    /** Categories that are considered urgent enough to draw on the map and notify the AI. */
    public boolean isHighPriority() {
        return this == HAZARD || this == MEDICAL || this == SECURITY;
    }

    /** Null-safe lookup by enum name; falls back to GENERAL for unknown/blank values. */
    public static ObservationCategory fromName(String name) {
        if (name != null) {
            for (ObservationCategory c : values()) {
                if (c.name().equalsIgnoreCase(name)) return c;
            }
        }
        return GENERAL;
    }
}
