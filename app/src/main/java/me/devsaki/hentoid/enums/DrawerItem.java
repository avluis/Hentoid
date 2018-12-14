package me.devsaki.hentoid.enums;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.activities.MikanSearchActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.websites.ASMHentaiActivity;
import me.devsaki.hentoid.activities.websites.EHentaiActivity;
import me.devsaki.hentoid.activities.websites.HentaiCafeActivity;
import me.devsaki.hentoid.activities.websites.HitomiActivity;
import me.devsaki.hentoid.activities.websites.NhentaiActivity;
import me.devsaki.hentoid.activities.websites.PandaActivity;
import me.devsaki.hentoid.activities.websites.PururinActivity;
import me.devsaki.hentoid.activities.websites.TsuminoActivity;

public enum DrawerItem {

    NHENTAI("NHENTAI", R.drawable.ic_menu_nhentai, NhentaiActivity.class),
    HCAFE("HENTAI CAFE", R.drawable.ic_menu_hentaicafe, HentaiCafeActivity.class),
    HITOMI("HITOMI", R.drawable.ic_menu_hitomi, HitomiActivity.class),
    ASM("ASMHENTAI", R.drawable.ic_menu_asmhentai, ASMHentaiActivity.class),
    TSUMINO("TSUMINO", R.drawable.ic_menu_tsumino, TsuminoActivity.class),
    PURURIN("PURURIN", R.drawable.ic_menu_pururin, PururinActivity.class),
    PANDA("PANDA", R.drawable.ic_menu_panda, PandaActivity.class),
    EHENTAI("E-HENTAI", R.drawable.ic_menu_ehentai, EHentaiActivity.class),
//    MIKAN("MIKAN SEARCH", R.drawable.ic_menu_mikan, MikanSearchActivity.class),
    HOME("HOME", R.drawable.ic_menu_downloads, DownloadsActivity.class),
    QUEUE("QUEUE", R.drawable.ic_menu_queue, QueueActivity.class),
    PREFS("PREFERENCES", R.drawable.ic_menu_prefs, PrefsActivity.class),
    ABOUT("ABOUT", R.drawable.ic_menu_about, AboutActivity.class);

    public final String label;
    public final int icon;
    public final Class activityClass;

    DrawerItem(String label, int icon, Class activityClass) {
        this.label = label;
        this.icon = icon;
        this.activityClass = activityClass;
    }
}
