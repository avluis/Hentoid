package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;

public class FakkuActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "fakku.net";
    private static final String[] GALLERY_FILTER = {"fakku.net/hentai/[\\w\\-]+$"};

    Site getStartSite() {
        return Site.FAKKU2;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);

        showTooltip(R.string.help_web_fakku_account, false); // Kinda hacky, but it's better than creating a whole new class just for that

        return client;
    }
}
