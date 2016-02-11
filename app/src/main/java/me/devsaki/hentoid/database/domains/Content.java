package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.util.List;

import me.devsaki.hentoid.activities.HitomiActivity;
import me.devsaki.hentoid.activities.NhentaiActivity;
import me.devsaki.hentoid.activities.TsuminoActivity;
import me.devsaki.hentoid.abstracts.BaseWebActivity;
import me.devsaki.hentoid.database.contants.ContentTable;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by DevSaki on 09/05/2015.
 */
public class Content extends ContentTable {

    @Expose
    private String url;
    @Expose
    private String title;
    @Expose
    private AttributeMap attributes;
    @Expose
    private String coverImageUrl;
    @Expose
    private Integer qtyPages;
    @Expose
    private long uploadDate;
    @Expose
    private long downloadDate;
    @Expose
    private StatusContent status;
    @Expose
    private List<ImageFile> imageFiles;
    @Expose(serialize = false, deserialize = false)
    private double percent;
    @Expose
    private Site site;

    public Content() {
        //Default Constructor
    }

    public Content(String title,
                   String url,
                   String coverImageUrl,
                   AttributeMap attributes,
                   Integer qtyPages,
                   Site site) {
        this.title = title;
        this.url = url;
        this.coverImageUrl = coverImageUrl;
        this.attributes = attributes;
        this.qtyPages = qtyPages;
        this.site = site;
        status = StatusContent.SAVED;
    }

    public AttributeMap getAttributes() {
        return attributes;
    }

    public void setAttributes(AttributeMap attributes) {
        this.attributes = attributes;
    }

    public int getId() {
        return url.hashCode();
    }

    public String getUniqueSiteId() {
        String[] paths;
        switch (site) {
            case FAKKU:
                return url.substring(url.lastIndexOf("/") + 1);
            case PURURIN:
                paths = url.split("/");
                return paths[2].replace(".html", "") + "-" + paths[1];
            case HITOMI:
                paths = url.split("/");
                return paths[1].replace(".html", "") + "-" + title.replaceAll("[^a-zA-Z0-9.-]", "_");
            case NHENTAI:
            case TSUMINO:
                return url.replace("/", "") + "-" + site.getDescription();
            default:
                return null;
        }
    }

    public Class<?> getWebActivityClass() {
        switch (site) {
            case HITOMI:
                return HitomiActivity.class;
            case NHENTAI:
                return NhentaiActivity.class;
            case TSUMINO:
                return TsuminoActivity.class;
            default:
                return BaseWebActivity.class; //Fallback for Pururin and Fakku
        }
    }

    public String getCategory() {
        if (getSite() == Site.FAKKU)
            return url.substring(1, url.lastIndexOf("/"));
        else {
            List<Attribute> attributesList = attributes.get(AttributeType.CATEGORY);
            if (attributesList != null && attributesList.size() > 0)
                return attributesList.get(0).getName();
        }
        return null;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getGalleryUrl() {
        String galleryConst;
        switch (site) {
            case PURURIN:
                galleryConst = "/gallery";
                break;
            case HITOMI:
                galleryConst = "/galleries";
                break;
            case NHENTAI:
                galleryConst = "/g";
                break;
            case TSUMINO:
                galleryConst = "/Book/Info";
                break;
            default:
                galleryConst = "";
                break; //Includes Fakku
        }
        return site.getUrl() + galleryConst + url;
    }

    public String getReaderUrl() {
        switch (site) {
            case HITOMI:
                return site.getUrl() + "/reader" + url;
            case NHENTAI:
                return getGalleryUrl() + "1/";
            case TSUMINO:
                return site.getUrl() + "/Read/View" + url;
            default:
                return null;
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public StatusContent getStatus() {
        return status;
    }

    public void setStatus(StatusContent status) {
        this.status = status;
    }

    public List<ImageFile> getImageFiles() {
        return imageFiles;
    }

    public void setImageFiles(List<ImageFile> imageFiles) {
        this.imageFiles = imageFiles;
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