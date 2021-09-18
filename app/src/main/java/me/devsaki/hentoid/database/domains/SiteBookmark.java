package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import me.devsaki.hentoid.enums.Site;

/**
 * Created by Robb on 2020/10
 * Site bookmarks
 */
@Entity
public class SiteBookmark {

    @Id
    public long id;
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    private Site site;
    private String title;
    private String url;
    private int order = -1;

    public SiteBookmark() { // Required by ObjectBox when an alternate constructor exists
    }

    public SiteBookmark(@NonNull final Site site, @NonNull final String title, @NonNull final String url) {
        this.site = site;
        this.title = title;
        this.url = url;
    }

    public Site getSite() {
        return site;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SiteBookmark that = (SiteBookmark) o;
        return urlsAreSame(getUrl(), that.getUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(neutralizeUrl(getUrl()));
    }

    public static String neutralizeUrl(String url) {
        if (null == url) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // Quick comparator to avoid host/someurl and host/someurl/ to be considered as different by the bookmarks managaer
    public static boolean urlsAreSame(String url1, String url2) {
        return neutralizeUrl(url1).equalsIgnoreCase(neutralizeUrl(url2));
    }
}
