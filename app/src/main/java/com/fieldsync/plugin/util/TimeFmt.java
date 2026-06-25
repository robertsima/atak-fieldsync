package com.fieldsync.plugin.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small helper for human-friendly relative timestamps ("2 min ago"). */
public final class TimeFmt {

    private TimeFmt() {}

    public static String relative(long timestamp) {
        long delta = System.currentTimeMillis() - timestamp;
        if (delta < 0) delta = 0;

        long sec = delta / 1000;
        if (sec < 60) return "just now";
        long min = sec / 60;
        if (min < 60) return min + " min ago";
        long hr = min / 60;
        if (hr < 24) return hr + (hr == 1 ? " hr ago" : " hrs ago");
        long day = hr / 24;
        if (day < 7) return day + (day == 1 ? " day ago" : " days ago");

        return new SimpleDateFormat("MM/dd HH:mm", Locale.US).format(new Date(timestamp));
    }
}
