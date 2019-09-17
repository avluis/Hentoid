package me.devsaki.hentoid.activities.sources;

import android.view.WindowManager;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Preferences;

public class DoujinsActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "doujins.com";
    private static final String GALLERY_FILTER = "//doujins.com/[A-Za-z0-9\\-]+/[A-Za-z0-9\\-]+-[0-9]+";

    Site getStartSite() {
        return Site.DOUJINS;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
}
