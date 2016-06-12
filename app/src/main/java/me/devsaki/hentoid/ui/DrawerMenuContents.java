package me.devsaki.hentoid.ui;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/11/2016.
 * Populates Drawer Menu Contents from a resource string array.
 * This includes generation the required class names, menu item title
 * and menu item icon.
 * This class is expecting a list of activities named like so:
 * '[activity_name]' - it then builds the actual Activity class as so:
 * '[activity_name]Activity.class'.
 * From this list, it will also build the activity title and corresponding
 * activity drawable with the following resource id:
 * 'R.drawable.ic_menu_[activity_name]' - make sure this drawable actually exists.
 */
public class DrawerMenuContents {
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ICON = "icon";
    private static final String TAG = LogHelper.makeLogTag(DrawerMenuContents.class);
    private final String[] mActivityList;
    private ArrayList<Map<String, ?>> items;
    private Class[] activities;

    public DrawerMenuContents(Context context) {
        mActivityList = context.getResources().getStringArray(R.array.nav_drawer_entries);
        populateActivities();
    }

    private void populateActivities() {
        activities = new Class[mActivityList.length];
        items = new ArrayList<>(mActivityList.length);

        String activity;
        String title;
        int resource;
        String resourcePrefix = "ic_menu_";
        Class<?> cls = null;
        for (int i = 0; i < mActivityList.length; i++) {
            activity = mActivityList[i];
            title = mActivityList[i].toUpperCase(Locale.US);
            resource = Helper.getId(resourcePrefix + mActivityList[i].toLowerCase(Locale.US),
                    R.drawable.class);
            try {
                cls = Class.forName("me.devsaki.hentoid.activities." + activity + "Activity");
            } catch (ClassNotFoundException e) {
                HentoidApp.getInstance().trackException(e);
                LogHelper.e(TAG, "Class not found: ", e);
            }

            activities[i] = cls;
            items.add(populateDrawerItem(title, resource));
        }
    }

    public List<Map<String, ?>> getItems() {
        return items;
    }

    public Class getActivity(int position) {
        return activities[position];
    }

    public int getPosition(Class activityClass) {
        for (int i = 0; i < activities.length; i++) {
            if (activities[i].equals(activityClass)) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, ?> populateDrawerItem(String title, int icon) {
        HashMap<String, Object> item = new HashMap<>();
        item.put(FIELD_TITLE, title);
        item.put(FIELD_ICON, icon);
        return item;
    }
}
