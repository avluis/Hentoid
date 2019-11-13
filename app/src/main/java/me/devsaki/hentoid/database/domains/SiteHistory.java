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
    public Site site;
    public String url; // Last


    public SiteHistory() {
    }

    public SiteHistory(Site site, String url) {
        this.site = site;
        this.url = url;
    }
}
