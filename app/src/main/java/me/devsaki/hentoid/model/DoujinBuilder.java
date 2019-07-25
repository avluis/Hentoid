package me.devsaki.hentoid.model;

import com.google.gson.annotations.Expose;

import java.util.List;

/**
 * General builder for works.
 *
 * @deprecated Replaced by {@link me.devsaki.hentoid.services.ImportService} methods; class is kept for retrocompatibilty
 */
@Deprecated
public class DoujinBuilder {

    @Expose
    private String title;
    @Expose
    private URLBuilder serie;
    @Expose
    private URLBuilder artist;
    @Expose
    private String description;
    @Expose
    private String urlImageTitle;
    @Expose
    private String url;
    @Expose
    private int qtyPages;
    @Expose
    private URLBuilder language;
    @Expose
    private URLBuilder translator;
    @Expose
    private List<URLBuilder> lstTags;

    public String getId() {
        int idxStart = url.lastIndexOf('/');
        String id = url.substring(idxStart);
        String category = url.replace(id, "");
        category = category.substring(category.lastIndexOf('/'));

        return category + id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public URLBuilder getSeries() {
        return serie;
    }

    public void setSerie(URLBuilder serie) {
        this.serie = serie;
    }

    public URLBuilder getArtist() {
        return artist;
    }

    public void setArtist(URLBuilder artist) {
        this.artist = artist;
    }

    public String getUrlImageTitle() {
        return urlImageTitle;
    }

    public void setUrlImageTitle(String urlImageTitle) {
        this.urlImageTitle = urlImageTitle;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getQtyPages() {
        return qtyPages;
    }

    public void setQtyPages(int qtyPages) {
        this.qtyPages = qtyPages;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public URLBuilder getLanguage() {
        return language;
    }

    public void setLanguage(URLBuilder language) {
        this.language = language;
    }

    public URLBuilder getTranslator() {
        return translator;
    }

    public void setTranslator(URLBuilder translator) {
        this.translator = translator;
    }

    public List<URLBuilder> getLstTags() {
        return lstTags;
    }

    public void setLstTags(List<URLBuilder> lstTags) {
        this.lstTags = lstTags;
    }
}
