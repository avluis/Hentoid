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
 * '[activity_name]' - it then builds the actual Activity class:
 * '[activity_name]Activity.class'.
 * From this list, it will also build the activity title and corresponding
 * activity drawable with the following resource id:
 * 'R.drawable.ic_menu_[activity_name]' - make sure this drawable actually exists.
 */
public class DrawerMenuContents {
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ICON = "icon";

    private static final String TAG = LogHelper.makeLogTag(DrawerMenuContents.class);

    private String[] activityName;
    private String[] activityCode;
    private Class[] activities;
    private ArrayList<Map<String, ?>> items;

    public DrawerMenuContents(Context cxt) {
        setActivityList(
                cxt.getResources().getStringArray(R.array.nav_drawer_names),
                cxt.getResources().getStringArray(R.array.nav_drawer_codes));
        populateActivities();
    }

    private void setActivityList(String[] names, String[] codes) {
        this.activityName = names;
        this.activityCode = codes;
    }

    private void populateActivities() {
        activities = new Class[activityCode.length];
        items = new ArrayList<>(activityCode.length);

        String activity,
                title,
                resourcePrefix = "ic_menu_";
        int resource;
        Class<?> cls = null;
        for (int i = 0; i < activityCode.length; i++) {
            activity = activityCode[i];
            title = activityName[i].toUpperCase(Locale.US);
            resource = Helper.getId(resourcePrefix + activityCode[i].toLowerCase(Locale.US),
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

    private Map<String, ?> populateDrawerItem(String title, int icon) {
        HashMap<String, Object> item = new HashMap<>();
        item.put(FIELD_TITLE, title);
        item.put(FIELD_ICON, icon);
        return item;
    }

    public Class getActivity(int position) {
        return activities[position];
    }

    public List<Map<String, ?>> getItems() {
        return items;
    }

    public int getPosition(Class activityClass) {
        for (int i = 0; i < activities.length; i++) {
            if (activities[i].equals(activityClass)) {
                return i;
            }
        }
        return -1;
    }
}
