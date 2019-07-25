package me.devsaki.hentoid.enums;

import androidx.appcompat.app.AppCompatActivity;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.sources.ASMHentaiActivity;
import me.devsaki.hentoid.activities.sources.EHentaiActivity;
import me.devsaki.hentoid.activities.sources.FakkuActivity;
import me.devsaki.hentoid.activities.sources.HentaiCafeActivity;
import me.devsaki.hentoid.activities.sources.HitomiActivity;
import me.devsaki.hentoid.activities.sources.MusesActivity;
import me.devsaki.hentoid.activities.sources.NexusActivity;
import me.devsaki.hentoid.activities.sources.NhentaiActivity;
import me.devsaki.hentoid.activities.sources.PururinActivity;
import me.devsaki.hentoid.activities.sources.TsuminoActivity;

public enum DrawerItem {

    NHENTAI("NHENTAI", R.drawable.ic_menu_nhentai, NhentaiActivity.class),
    HCAFE("HENTAI CAFE", R.drawable.ic_menu_hentaicafe, HentaiCafeActivity.class),
    HITOMI("HITOMI", R.drawable.ic_menu_hitomi, HitomiActivity.class),
    ASM("ASMHENTAI", R.drawable.ic_menu_asmhentai, ASMHentaiActivity.class),
    TSUMINO("TSUMINO", R.drawable.ic_menu_tsumino, TsuminoActivity.class),
    PURURIN("PURURIN", R.drawable.ic_menu_pururin, PururinActivity.class),
    EHENTAI("E-HENTAI", R.drawable.ic_menu_ehentai, EHentaiActivity.class),
    FAKKU("FAKKU", R.drawable.ic_menu_fakku, FakkuActivity.class),
    NEXUS("HENTAI NEXUS", R.drawable.ic_menu_nexus, NexusActivity.class),
    MUSES("8MUSES", R.drawable.ic_menu_8muses, MusesActivity.class),
    //    MIKAN("MIKAN SEARCH", R.drawable.ic_menu_mikan, MikanSearchActivity.class),
    //    HOME("HOME", R.drawable.ic_menu_home, DownloadsActivity.class),
    QUEUE("QUEUE", R.drawable.ic_menu_queue, QueueActivity.class),
    PREFS("PREFERENCES", R.drawable.ic_menu_prefs, PrefsActivity.class),
    ABOUT("ABOUT", R.drawable.ic_menu_about, AboutActivity.class);

    public final String label;
    public final int icon;
    public final Class<? extends AppCompatActivity> activityClass;

    DrawerItem(String label, int icon, Class<? extends AppCompatActivity> activityClass) {
        this.label = label;
        this.icon = icon;
        this.activityClass = activityClass;
    }
}
