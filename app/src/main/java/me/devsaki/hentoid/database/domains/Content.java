package me.devsaki.hentoid.database.domains;

import me.devsaki.hentoid.database.contants.ContentTable;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.database.enums.Status;
import com.google.gson.annotations.Expose;

import java.util.HashMap;
import java.util.List;

/**
 * Created by DevSaki on 09/05/2015.
 */
public class Content extends ContentTable{

    @Expose
    private String url;
    @Expose
    private String title;
    @Expose
    private String htmlDescription;
    @Expose
    private HashMap<AttributeType, List<Attribute>> attributes;
    @Expose
    private String coverImageUrl;
    @Expose
    private Integer qtyPages;
    @Expose(serialize = false, deserialize = false)
    private Integer qtyFavorites;
    @Expose
    private long uploadDate;
    @Expose
    private long downloadDate;
    @Expose
    private Status status;
    @Expose
    private List<ImageFile> imageFiles;
    @Expose(serialize = false, deserialize = false)
    private boolean downloadable;
    @Expose(serialize = false, deserialize = false)
    private double percent;
    @Expose
    private Site site;

    public HashMap<AttributeType, List<Attribute>> getAttributes() {
        return attributes;
    }

    public void setAttributes(HashMap<AttributeType, List<Attribute>> attributes) {
        this.attributes = attributes;
    }

    public int getId() {
        return url.hashCode();
    }

    public String getUniqueSiteId() {
        if(getSite()==Site.FAKKU)
            return url.substring(url.lastIndexOf("/")+1);
        else if(getSite()==Site.PURURIN){
            return url.substring(url.lastIndexOf("/")+1).replace(".html", "");
        }
        return null;
    }

    public String getCategory() {
        return url.substring(1, url.lastIndexOf("/"));
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHtmlDescription() {
        return htmlDescription;
    }

    public void setHtmlDescription(String htmlDescription) {
        this.htmlDescription = htmlDescription;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public Integer getQtyPages() {
        return qtyPages;
    }

    public void setQtyPages(Integer qtyPages) {
        this.qtyPages = qtyPages;
    }

    public Integer getQtyFavorites() {
        return qtyFavorites;
    }

    public void setQtyFavorites(Integer qtyFavorites) {
        this.qtyFavorites = qtyFavorites;
    }

    public long getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(long uploadDate) {
        this.uploadDate = uploadDate;
    }

    public long getDownloadDate() {
        return downloadDate;
    }

    public void setDownloadDate(long downloadDate) {
        this.downloadDate = downloadDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<ImageFile> getImageFiles() {
        return imageFiles;
    }

    public void setImageFiles(List<ImageFile> imageFiles) {
        this.imageFiles = imageFiles;
    }

    public boolean isDownloadable() {
        return downloadable;
    }

    public void setDownloadable(boolean downloadable) {
        this.downloadable = downloadable;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

}
