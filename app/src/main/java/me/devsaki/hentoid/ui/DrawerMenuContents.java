package me.devsaki.hentoid.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.enums.DrawerItem;
import me.devsaki.hentoid.util.Preferences;

public class DrawerMenuContents {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ICON = "icon";

    private final List<Class> activities = new ArrayList<>();
    private final ArrayList<Map<String, ?>> items = new ArrayList<>();

    public DrawerMenuContents() {
        for (DrawerItem drawerItem : DrawerItem.values()) {
            // Hide panda if not explicitely enabled
            if (drawerItem.label.equals("PANDA") && !Preferences.isUseSfw()) continue;

            activities.add(drawerItem.activityClass);
            items.add(populateDrawerItem(drawerItem.label, drawerItem.icon));
        }
    }

    private Map<String, ?> populateDrawerItem(String title, int icon) {
        HashMap<String, Object> item = new HashMap<>();
        item.put(FIELD_TITLE, title);
        item.put(FIELD_ICON, icon);
        return item;
    }

    public Class getActivity(int position) {
        return activities.get(position);
    }

    public List<Map<String, ?>> getItems() {
        return items;
    }

    public int getPosition(Class activityClass) {
        for (int i = 0; i < activities.size(); i++) {
            if (activities.get(i).equals(activityClass)) {
                return i;
            }
        }
        return -1;
    }
}
