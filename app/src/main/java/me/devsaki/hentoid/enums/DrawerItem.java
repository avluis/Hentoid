package me.devsaki.hentoid.enums;

import android.support.v7.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.sources.ASMHentaiActivity;
import me.devsaki.hentoid.activities.sources.EHentaiActivity;
import me.devsaki.hentoid.activities.sources.FakkuActivity;
import me.devsaki.hentoid.activities.sources.HentaiCafeActivity;
import me.devsaki.hentoid.activities.sources.HitomiActivity;
import me.devsaki.hentoid.activities.sources.NexusActivity;
import me.devsaki.hentoid.activities.sources.NhentaiActivity;
import me.devsaki.hentoid.activities.sources.PandaActivity;
import me.devsaki.hentoid.activities.sources.PururinActivity;
import me.devsaki.hentoid.activities.sources.TsuminoActivity;
import me.devsaki.hentoid.util.Preferences;

public enum DrawerItem {

    NHENTAI("NHENTAI", R.drawable.ic_menu_nhentai, NhentaiActivity.class),
    HCAFE("HENTAI CAFE", R.drawable.ic_menu_hentaicafe, HentaiCafeActivity.class),
    HITOMI("HITOMI", R.drawable.ic_menu_hitomi, HitomiActivity.class),
    ASM("ASMHENTAI", R.drawable.ic_menu_asmhentai, ASMHentaiActivity.class),
    TSUMINO("TSUMINO", R.drawable.ic_menu_tsumino, TsuminoActivity.class),
    PURURIN("PURURIN", R.drawable.ic_menu_pururin, PururinActivity.class),
    PANDA("PANDA", R.drawable.ic_menu_panda, PandaActivity.class),
    EHENTAI("E-HENTAI", R.drawable.ic_menu_ehentai, EHentaiActivity.class),
    FAKKU("FAKKU", R.drawable.ic_menu_fakku, FakkuActivity.class),
    NEXUS("NEXUS", R.drawable.ic_menu_nexus, NexusActivity.class),
    //    MIKAN("MIKAN SEARCH", R.drawable.ic_menu_mikan, MikanSearchActivity.class),
    HOME("HOME", R.drawable.ic_menu_home, DownloadsActivity.class),
    QUEUE("QUEUE", R.drawable.ic_menu_queue, QueueActivity.class),
    PREFS("PREFERENCES", R.drawable.ic_menu_prefs, PrefsActivity.class),
    ABOUT("ABOUT", R.drawable.ic_menu_about, AboutActivity.class);

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ICON = "icon";

    public static List<Class<? extends AppCompatActivity>> activityClasses;

    public final String label;
    public final int icon;
    public final Class<? extends AppCompatActivity> activityClass;

    DrawerItem(String label, int icon, Class<? extends AppCompatActivity> activityClass) {
        this.label = label;
        this.icon = icon;
        this.activityClass = activityClass;
    }

    private Map<String, ?> makeAdapterItem() {
        HashMap<String, Object> item = new HashMap<>();
        item.put(FIELD_TITLE, label);
        item.put(FIELD_ICON, icon);
        return item;
    }

    public static Class<? extends AppCompatActivity> getActivity(int position) {
        return activityClasses.get(position);
    }

    public static int getPosition(Class<? extends AppCompatActivity> activityClass) {
        return activityClasses.indexOf(activityClass);
    }

    public static List<Map<String, ?>> makeAdapterItems() {
        ArrayList<Map<String, ?>> items = new ArrayList<>(values().length);
        activityClasses = new ArrayList<>(values().length);

        for (DrawerItem value : values()) {
            // Hide panda if not explicitely enabled
            if (value.label.equals("PANDA") && !Preferences.isUseSfw()) continue;

            items.add(value.makeAdapterItem());
            activityClasses.add(value.activityClass);
        }

        return items;
    }
}
