package me.devsaki.hentoid;

import android.content.Context;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by avluis on 2/11/16.
 * A collection of Google Analytics trackers. Fetch the tracker you need using
 * {@code AnalyticsTrackers.get(...)}
 */
final class AnalyticsTrackers {

    private static final Map<Target, Tracker> mTrackers = new HashMap<>();

    private AnalyticsTrackers() {
        throw new AssertionError("Do not instantiate");
    }

    static synchronized Tracker get(Context context, Target target) {
        if (!mTrackers.containsKey(target)) {
            Tracker tracker;
            switch (target) {
                case APP:
                    tracker = GoogleAnalytics.getInstance(context).newTracker(R.xml.app_tracker);
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled analytics target " + target);
            }
            mTrackers.put(target, tracker);
        }

        return mTrackers.get(target);
    }

    enum Target {APP}
}
