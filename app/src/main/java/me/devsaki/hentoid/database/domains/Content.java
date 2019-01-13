package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.devsaki.hentoid.activities.websites.ASMHentaiActivity;
import me.devsaki.hentoid.activities.websites.BaseWebActivity;
import me.devsaki.hentoid.activities.websites.EHentaiActivity;
import me.devsaki.hentoid.activities.websites.HentaiCafeActivity;
import me.devsaki.hentoid.activities.websites.HitomiActivity;
import me.devsaki.hentoid.activities.websites.NhentaiActivity;
import me.devsaki.hentoid.activities.websites.PandaActivity;
import me.devsaki.hentoid.activities.websites.PururinActivity;
import me.devsaki.hentoid.activities.websites.TsuminoActivity;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by DevSaki on 09/05/2015.
 * Content builder
 */
public class Content implements Serializable {

    @Expose
    private String url;
    @Expose
    private String title;
    @Expose
    private String author;
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
    @Expose
    private Site site;
    private String storageFolder; // Not exposed because it will vary according to book location -> valued at import
    @Expose
    private boolean favourite;
    @Expose
    private long reads = 0;
    @Expose
    private long lastReadDate;
    // Runtime attributes; no need to expose them
    private double percent;
    private int queryOrder;
    private boolean selected = false;


    public AttributeMap getAttributes() {
        if (null == attributes) attributes = new AttributeMap();
        return attributes;
    }

    public Content setAttributes(AttributeMap attributes) {
        this.attributes = attributes;
        return this;
    }

    public int getId() {
        return url.hashCode();
    }

    public String getUniqueSiteId() {
        String[] paths;

        switch (site) {
            case FAKKU:
                return url.substring(url.lastIndexOf("/") + 1);
            case EHENTAI:
            case PURURIN:
                paths = url.split("/");
                return paths[1];
            case HITOMI:
                paths = url.split("/");
                return paths[1].replace(".html", "");
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
            case NHENTAI:
            case PANDA:
            case TSUMINO:
                return url.replace("/", "");
            case HENTAICAFE:
                return url.replace("/?p=", "");
            default:
                return "";
        }
    }

    // Used for upgrade purposes
    @Deprecated
    public String getOldUniqueSiteId() {
        String[] paths;
        switch (site) {
            case FAKKU:
                return url.substring(url.lastIndexOf("/") + 1);
            case PURURIN:
                paths = url.split("/");
                return paths[2].replace(".html", "") + "-" + paths[1];
            case HITOMI:
                paths = url.split("/");
                return paths[1].replace(".html", "") + "-" +
                        title.replaceAll("[^a-zA-Z0-9.-]", "_");
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
            case NHENTAI:
            case PANDA:
            case EHENTAI:
            case TSUMINO:
                return url.replace("/", "") + "-" + site.getDescription();
            case HENTAICAFE:
                return url.replace("/?p=", "") + "-" + site.getDescription();
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
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
                return ASMHentaiActivity.class;
            case HENTAICAFE:
                return HentaiCafeActivity.class;
            case TSUMINO:
                return TsuminoActivity.class;
            case PURURIN:
                return PururinActivity.class;
            case EHENTAI:
                return EHentaiActivity.class;
            case PANDA:
                return PandaActivity.class;
            default:
                return BaseWebActivity.class; // Fallback for FAKKU
        }
    }

    public String getCategory() {
        if (site == Site.FAKKU) {
            return url.substring(1, url.lastIndexOf("/"));
        } else {
            if (attributes != null) {
                List<Attribute> attributesList = attributes.get(AttributeType.CATEGORY);
                if (attributesList != null && attributesList.size() > 0) {
                    return attributesList.get(0).getName();
                }
            }
        }

        return null;
    }

    public String getUrl() {
        return url;
    }

    public Content setUrl(String url) {
        this.url = url;
        return this;
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
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
            case EHENTAI:           // Won't work because of the temporary key
            case NHENTAI:
                galleryConst = "/g";
                break;
            case TSUMINO:
                galleryConst = "/Book/Info";
                break;
            case HENTAICAFE:
            case PANDA:
            default:
                galleryConst = "";
                break; // Includes FAKKU & Hentai Cafe
        }

        return site.getUrl() + galleryConst + url;
    }

    public String getReaderUrl() {
        switch (site) {
            case HITOMI:
                return site.getUrl() + "/reader" + url;
//            case NHENTAI:
//                return getGalleryUrl() + "1/";
            case TSUMINO:
                return site.getUrl() + "/Read/View" + url;
            case ASMHENTAI:
                return site.getUrl() + "/gallery" + url + "1/";
            case ASMHENTAI_COMICS:
                return site.getUrl() + "/gallery" + url;
            case EHENTAI:               // Won't work anyway because of the temporary key
            case HENTAICAFE:
            case NHENTAI:
            case PANDA:
                return getGalleryUrl();
            case PURURIN:
                return site.getUrl() + "/read/" + url.substring(1).replace("/", "/01/");
            default:
                return null;
        }
    }

    public Content populateAuthor() {
        String author = "";
        if (getAttributes().containsKey(AttributeType.ARTIST) && attributes.get(AttributeType.ARTIST).size() > 0)
            author = attributes.get(AttributeType.ARTIST).get(0).getName();
        if (null == author || author.equals("")) // Try and get Circle
        {
            if (attributes.containsKey(AttributeType.CIRCLE) && attributes.get(AttributeType.CIRCLE).size() > 0)
                author = attributes.get(AttributeType.CIRCLE).get(0).getName();
        }
        if (null == author) author = "";
        setAuthor(author);
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Content setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getAuthor() {
        if (null == author) populateAuthor();
        return author;
    }

    public Content setAuthor(String author) {
        this.author = author;
        return this;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public Content setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
        return this;
    }

    public Integer getQtyPages() {
        return qtyPages;
    }

    public Content setQtyPages(Integer qtyPages) {
        this.qtyPages = qtyPages;
        return this;
    }

    public long getUploadDate() {
        return uploadDate;
    }

    public Content setUploadDate(long uploadDate) {
        this.uploadDate = uploadDate;
        return this;
    }

    public long getDownloadDate() {
        return downloadDate;
    }

    public Content setDownloadDate(long downloadDate) {
        this.downloadDate = downloadDate;
        return this;
    }

    public StatusContent getStatus() {
        return status;
    }

    public Content setStatus(StatusContent status) {
        this.status = status;
        return this;
    }

    public List<ImageFile> getImageFiles() {
        if (null == imageFiles) imageFiles = Collections.emptyList();
        return imageFiles;
    }

    public Content setImageFiles(List<ImageFile> imageFiles) {
        this.imageFiles = imageFiles;
        return this;
    }

    public double getPercent() {
        return percent;
    }

    public Content setPercent(double percent) {
        this.percent = percent;
        return this;
    }

    public Site getSite() {
        return site;
    }

    public Content setSite(Site site) {
        this.site = site;
        return this;
    }

    public String getStorageFolder() {
        return storageFolder == null ? "" : storageFolder;
    }

    public Content setStorageFolder(String storageFolder) {
        this.storageFolder = storageFolder;
        return this;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public Content setFavourite(boolean favourite) {
        this.favourite = favourite;
        return this;
    }

    private int getQueryOrder() {
        return queryOrder;
    }

    public Content setQueryOrder(int order) {
        queryOrder = order;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }


    public long getReads() {
        return reads;
    }

    public Content increaseReads() {
        this.reads++;
        return this;
    }

    public Content setReads(long reads) {
        this.reads = reads;
        return this;
    }

    public long getLastReadDate() {
        return (0 == lastReadDate) ? downloadDate : lastReadDate;
    }

    public Content setLastReadDate(long lastReadDate) {
        this.lastReadDate = lastReadDate;
        return this;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Content content = (Content) o;

        return (url != null ? url.equals(content.url) : content.url == null) && site == content.site;
    }

    @Override
    public int hashCode() {
        int result = url != null ? url.hashCode() : 0;
        result = 31 * result + (site != null ? site.hashCode() : 0);
        return result;
    }

    public static final Comparator<Content> TITLE_ALPHA_COMPARATOR = (a, b) -> a.getTitle().compareTo(b.getTitle());

    public static final Comparator<Content> DLDATE_COMPARATOR = (a, b) -> Long.compare(a.getDownloadDate(), b.getDownloadDate()) * -1; // Inverted - last download date first

    public static final Comparator<Content> ULDATE_COMPARATOR = (a, b) -> Long.compare(a.getUploadDate(), b.getUploadDate()) * -1; // Inverted - last upload date first

    public static final Comparator<Content> TITLE_ALPHA_INV_COMPARATOR = (a, b) -> a.getTitle().compareTo(b.getTitle()) * -1;

    public static final Comparator<Content> DLDATE_INV_COMPARATOR = (a, b) -> Long.compare(a.getDownloadDate(), b.getDownloadDate());

    public static final Comparator<Content> READS_ORDER_COMPARATOR = (a, b) -> {
        int comp = Long.compare(a.getReads(), b.getReads());
        return (0 == comp) ? Long.compare(a.getLastReadDate(), b.getLastReadDate()) : comp;
    };

    public static final Comparator<Content> READS_ORDER_INV_COMPARATOR = (a, b) -> {
        int comp = Long.compare(a.getReads(), b.getReads()) * -1;
        return (0 == comp) ? Long.compare(a.getLastReadDate(), b.getLastReadDate()) * -1 : comp;
    };

    public static final Comparator<Content> READ_DATE_INV_COMPARATOR = (a, b) -> Long.compare(a.getLastReadDate(), b.getLastReadDate()) * -1;

    public static final Comparator<Content> QUERY_ORDER_COMPARATOR = (a, b) -> Integer.compare(a.getQueryOrder(), b.getQueryOrder());
}
