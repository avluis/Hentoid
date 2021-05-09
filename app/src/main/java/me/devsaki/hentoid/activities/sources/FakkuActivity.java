package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

public class FakkuActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "fakku.net";
    private static final String[] GALLERY_FILTER = {"fakku.net/hentai/[\\w\\-]+$"};

    Site getStartSite() {
        return Site.FAKKU2;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new FakkuWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);

        showTooltip(R.string.help_web_fakku_account, false); // Kinda hacky, but it's better than creating a whole new class just for that

        return client;
    }

    private class FakkuWebClient extends CustomWebViewClient {

        FakkuWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        // Show a tooltip everytime a content is ignored (premium Fakku content)
        @Override
        protected void processContent(@Nonnull Content content, @NonNull String url, boolean quickDownload) {
            if (content.getStatus() != null && content.getStatus().equals(StatusContent.IGNORED)) {
                showTooltip(R.string.help_web_fakku_premium, true);
                return;
            }
            super.processContent(content, url, quickDownload);
        }
    }
}
