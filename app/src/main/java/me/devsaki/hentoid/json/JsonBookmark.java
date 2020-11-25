package me.devsaki.hentoid.json;

import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.enums.Site;

class JsonBookmark {

    private Site site;
    private String title;
    private String url;
    private int order;

    private JsonBookmark() {
    }

    static JsonBookmark fromEntity(SiteBookmark b) {
        JsonBookmark result = new JsonBookmark();
        result.site = b.getSite();
        result.title = b.getTitle();
        result.url = b.getUrl();
        result.order = b.getOrder();
        return result;
    }

    SiteBookmark toEntity() {
        SiteBookmark result = new SiteBookmark(site, title, url);
        result.setOrder(order);
        return result;
    }
}
