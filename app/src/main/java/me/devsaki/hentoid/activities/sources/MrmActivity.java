package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.enums.Site;

public class MrmActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "myreadingmanga.info";
    private static final String[] GALLERY_FILTER = {"myreadingmanga.info/[%\\w\\-]+/$"};
    private static final String[] REMOVABLE_ELEMENTS = {"center.imgtop", "a[rel^='nofollow noopener']"};

    Site getStartSite() {
        return Site.MRM;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new MrmWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addRemovableElements(REMOVABLE_ELEMENTS);
        return client;
    }

    private static class MrmWebClient extends CustomWebViewClient {

        MrmWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        @Override
        boolean isGalleryPage(@NonNull String url) {
            if (url.endsWith("/upload/")) return false;
            if (url.endsWith("/whats-that-book/")) return false;
            if (url.endsWith("/video-movie/")) return false;
            if (url.endsWith("/yaoi-manga/")) return false;
            if (url.endsWith("/contact/")) return false;
            if (url.endsWith("/about/")) return false;
            if (url.endsWith("/terms-service/")) return false;
            if (url.endsWith("/my-bookmark/")) return false;
            if (url.endsWith("/privacy-policy/")) return false;
            if (url.endsWith("/dmca-notice/")) return false;
            if (url.contains("?relatedposts")) return false;
            return super.isGalleryPage(url);
        }
    }
}
