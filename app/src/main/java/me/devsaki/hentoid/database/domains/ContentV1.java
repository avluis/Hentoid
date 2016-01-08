package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.devsaki.hentoid.database.contants.ContentTable;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.database.enums.StatusContent;

/**
 * Created by DevSaki on 09/05/2015.
 */
@Deprecated
public class ContentV1 extends ContentTable {

    @Expose
    private String url;
    @Expose
    private String title;
    @Expose
    private String htmlDescription;
    @Expose
    private Attribute serie;
    @Expose
    private List<Attribute> artists;
    @Expose
    private List<Attribute> publishers;
    @Expose
    private Attribute language;
    @Expose
    private List<Attribute> tags;
    @Expose
    private List<Attribute> translators;
    @Expose
    private String coverImageUrl;
    @Expose(serialize = false, deserialize = false)
    private String sampleImageUrl;
    @Expose
    private Integer qtyPages;
    @Expose(serialize = false, deserialize = false)
    private Integer qtyFavorites;
    @Expose
    private long uploadDate;
    @Expose
    private Attribute user;
    @Expose
    private long downloadDate;
    @Expose
    private StatusContent status;
    @Expose
    private List<ImageFile> imageFiles;
    @Expose(serialize = false, deserialize = false)
    private boolean downloadable;
    @Expose(serialize = false, deserialize = false)
    private double percent;
    @Expose
    private Site site;

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setHtmlDescription(String htmlDescription) {
        this.htmlDescription = htmlDescription;
    }

    public Attribute getSerie() {
        return serie;
    }

    public void setSerie(Attribute serie) {
        this.serie = serie;
    }

    public List<Attribute> getArtists() {
        return artists;
    }

    public void setArtists(List<Attribute> artists) {
        this.artists = artists;
    }

    public List<Attribute> getPublishers() {
        return publishers;
    }

    public Attribute getLanguage() {
        return language;
    }

    public void setLanguage(Attribute language) {
        this.language = language;
    }

    public List<Attribute> getTags() {
        return tags;
    }

    public void setTags(List<Attribute> tags) {
        this.tags = tags;
    }

    public List<Attribute> getTranslators() {
        return translators;
    }

    public void setTranslators(List<Attribute> translators) {
        this.translators = translators;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public void setQtyPages(Integer qtyPages) {
        this.qtyPages = qtyPages;
    }

    public Attribute getUser() {
        return user;
    }

    public void setDownloadDate(long downloadDate) {
        this.downloadDate = downloadDate;
    }

    public StatusContent getStatus() {
        return status;
    }

    public void setMigratedStatus() {
        status = StatusContent.MIGRATED;
    }

    public Site getSite() {
        //to keep compatibility, if null return Fakku
        if (site == null)
            return Site.FAKKU;
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Content toContent() {
        Content content = new Content();
        content.setSite(getSite());
        content.setUrl(url);
        content.setUploadDate(uploadDate);
        content.setAttributes(new HashMap<AttributeType, List<Attribute>>());
        content.getAttributes().put(AttributeType.ARTIST, getArtists());
        List<Attribute> aux = new ArrayList<>();
        if (getSerie() != null)
            aux.add(getSerie());
        content.getAttributes().put(AttributeType.SERIE, aux);
        aux = new ArrayList<>();
        if (getLanguage() != null)
            aux.add(getLanguage());
        content.getAttributes().put(AttributeType.LANGUAGE, aux);
        content.getAttributes().put(AttributeType.PUBLISHER, getPublishers());
        content.getAttributes().put(AttributeType.TAG, getTags());
        content.getAttributes().put(AttributeType.TRANSLATOR, getTranslators());
        aux = new ArrayList<>();
        if (getUser() != null)
            aux.add(getUser());
        content.getAttributes().put(AttributeType.UPLOADER, aux);
        content.setImageFiles(imageFiles);
        content.setCoverImageUrl(coverImageUrl);
        content.setHtmlDescription(htmlDescription);
        content.setTitle(title);
        content.setQtyPages(qtyPages);
        content.setDownloadDate(downloadDate);
        content.setStatus(status);
        return content;
    }
}
