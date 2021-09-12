package me.devsaki.hentoid.database.domains;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import me.devsaki.hentoid.enums.Site;

/**
 * Created by Robb on 2019/11
 * Site browsing history
 */
@Entity
public class SiteHistory {

    @Id
    public long id;
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    private Site site;
    private String url; // Last

    public SiteHistory() { // Required by ObjectBox when an alternate constructor exists
    }

    public SiteHistory(Site site, String url) {
        this.site = site;
        this.url = url;
    }

    public Site getSite() {
        return site;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
