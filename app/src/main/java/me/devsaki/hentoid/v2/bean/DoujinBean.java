package me.devsaki.hentoid.v2.bean;

import java.util.List;

/**
 * General builder for works.
 */
public class DoujinBean {

    private String title;
    private URLBean serie;
    private URLBean artist;
    private String description;
    private String urlImageTitle;
    private String url;
    private int qtyPages;
    private URLBean language;
    private URLBean translator;
    private List<URLBean> lstTags;

    public String getId() {
        int idxStart = url.lastIndexOf("/");

        String id = url.substring(idxStart);
        String category = url.replace(id, "");
        category = category.substring(category.lastIndexOf("/"));
        return category + id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public URLBean getSerie() {
        return serie;
    }

    public void setSerie(URLBean serie) {
        this.serie = serie;
    }

    public URLBean getArtist() {
        return artist;
    }

    public void setArtist(URLBean artist) {
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

    public URLBean getLanguage() {
        return language;
    }

    public void setLanguage(URLBean language) {
        this.language = language;
    }

    public URLBean getTranslator() {
        return translator;
    }

    public void setTranslator(URLBean translator) {
        this.translator = translator;
    }

    public List<URLBean> getLstTags() {
        return lstTags;
    }

    public void setLstTags(List<URLBean> lstTags) {
        this.lstTags = lstTags;
    }
}