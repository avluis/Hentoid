package me.devsaki.hentoid.json;

import java.util.List;

import me.devsaki.hentoid.workers.PrimaryImportWorker;

/**
 * General builder for works.
 *
 * @deprecated Replaced by {@link PrimaryImportWorker} methods; class is kept for retrocompatibilty
 */
@SuppressWarnings("DeprecatedIsStillUsed") // Shouldn't be used for new devs but still used to ensure retrocompatiblity with old files
@Deprecated
public class DoujinBuilder {

    private String title;
    private URLBuilder serie;
    private URLBuilder artist;
    private String description;
    private String urlImageTitle;
    private String url;
    private int qtyPages;
    private URLBuilder language;
    private URLBuilder translator;
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
