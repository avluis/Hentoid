package me.devsaki.hentoid.events;

import me.devsaki.hentoid.enums.Site;

public class DownloadReviveEvent {
    public final String url;
    public final Site site;

    public DownloadReviveEvent(final Site site, final String url) {
        this.url = url;
        this.site = site;
    }
}
