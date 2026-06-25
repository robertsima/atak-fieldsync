package com.fieldsync.plugin.model;

import java.util.Locale;

/**
 * Single source of truth for deciding when an observation is "significant" — i.e. worth
 * drawing on the map (Phase 2) and worth auto-notifying the AI node about (Phase 4).
 *
 * An observation is significant when ANY of:
 *   - the operator flagged it Important, or
 *   - its category is high priority (HAZARD / MEDICAL / SECURITY), or
 *   - its text contains one of the urgent keywords below.
 */
public final class ObservationPriority {

    /** Lowercase keywords that mark free-text as urgent regardless of category. */
    public static final String[] KEYWORDS = {
        "casualty", "wounded", "ied", "contact", "hostile", "fire",
        "evac", "medevac", "ambush", "explosion", "mass cas", "urgent"
    };

    private ObservationPriority() {}

    public static boolean isSignificant(Observation obs) {
        if (obs == null) return false;
        if (obs.important) return true;
        if (ObservationCategory.fromName(obs.category).isHighPriority()) return true;
        return matchesKeyword(obs.text);
    }

    public static boolean matchesKeyword(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.US);
        for (String kw : KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}
