package me.devsaki.hentoid.database.domains;

import androidx.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToMany;
import me.devsaki.hentoid.activities.sources.ASMHentaiActivity;
import me.devsaki.hentoid.activities.sources.BaseWebActivity;
import me.devsaki.hentoid.activities.sources.EHentaiActivity;
import me.devsaki.hentoid.activities.sources.FakkuActivity;
import me.devsaki.hentoid.activities.sources.HentaiCafeActivity;
import me.devsaki.hentoid.activities.sources.HitomiActivity;
import me.devsaki.hentoid.activities.sources.MusesActivity;
import me.devsaki.hentoid.activities.sources.NexusActivity;
import me.devsaki.hentoid.activities.sources.NhentaiActivity;
import me.devsaki.hentoid.activities.sources.PururinActivity;
import me.devsaki.hentoid.activities.sources.TsuminoActivity;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by DevSaki on 09/05/2015.
 * Content builder
 */
@Entity
public class Content implements Serializable {

    @Id
    private long id;
    @Expose
    private String url;
    @Expose(serialize = false, deserialize = false)
    private String uniqueSiteId; // Has to be queryable in DB, hence has to be a field
    @Expose
    private String title;
    @Expose
    private String author;
    @Expose(serialize = false, deserialize = false)
    private ToMany<Attribute> attributes;
    @Expose
    private String coverImageUrl;
    @Expose
    private Integer qtyPages;
    @Expose
    private long uploadDate;
    @Expose
    private long downloadDate;
    @Expose
    @Convert(converter = StatusContent.StatusContentConverter.class, dbType = Integer.class)
    private StatusContent status;
    @Expose(serialize = false, deserialize = false)
    @Backlink(to = "content")
    private ToMany<ImageFile> imageFiles;
    @Expose
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    private Site site;
    private String storageFolder; // Not exposed because it will vary according to book location -> valued at import
    @Expose
    private boolean favourite;
    @Expose
    private long reads = 0;
    @Expose
    private long lastReadDate;
    @Expose
    private int lastReadPageIndex = 0;
    // Temporary during SAVED state only; no need to expose them for JSON persistence
    @Expose(serialize = false, deserialize = false)
    private String downloadParams;
    // Temporary during ERROR state only; no need to expose them for JSON persistence
    @Expose(serialize = false, deserialize = false)
    @Backlink(to = "content")
    private ToMany<ErrorRecord> errorLog;
    // Needs to be in the DB to keep the information when deletion takes a long time and user navigates
    // No need to save that into JSON
    @Expose(serialize = false, deserialize = false)
    private boolean isBeingDeleted = false;
    // Needs to be in the DB to optimize I/O
    // No need to save that into the JSON file itself, obviously
    @Expose(serialize = false, deserialize = false)
    private String jsonUri;

    // Runtime attributes; no need to expose them for JSON persistence nor to persist them to DB
    @Transient
    private double percent;
    @Transient
    private int queryOrder;
    @Transient
    private boolean isFirst;
    @Transient
    private boolean isLast;
    @Transient
    private boolean selected = false;

    // Attributes kept for retro-compatibility with contentV2.json Hentoid files
    @Transient
    @Expose
    @SerializedName("attributes")
    private AttributeMap attributeMap;
    @Transient
    @Expose
    @SerializedName("imageFiles")
    private ArrayList<ImageFile> imageList;


    public ToMany<Attribute> getAttributes() {
        return this.attributes;
    }

    public void setAttributes(ToMany<Attribute> attributes) {
        this.attributes = attributes;
    }

    public AttributeMap getAttributeMap() {
        AttributeMap result = new AttributeMap();
        for (Attribute a : attributes) {
            a.computeUrl(this.getSite());
            result.add(a);
        }
        return result;
    }

    public Content addAttributes(AttributeMap attributes) {
        if (attributes != null) {
            for (AttributeType type : attributes.keySet()) {
                this.attributes.addAll(attributes.get(type));
            }
        }
        return this;
    }

    public long getId() {
        return this.id;
    }

    public Content setId(long id) {
        this.id = id;
        return this;
    }

    public String getUniqueSiteId() {
        return this.uniqueSiteId;
    }

    private String computeUniqueSiteId() {
        String[] paths;

        switch (site) {
            case FAKKU:
                return url.substring(url.lastIndexOf('/') + 1);
            case EHENTAI:
            case PURURIN:
                paths = url.split("/");
                return (paths.length > 1) ? paths[1] : paths[0];
            case HITOMI:
                paths = url.split("/");
                String expression = (paths.length > 1) ? paths[1] : paths[0];
                return expression.replace(".html", "");
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
            case NHENTAI:
            case PANDA:
            case TSUMINO:
            case NEXUS:
                return url.replace("/", "");
            case HENTAICAFE:
                return url.replace("/?p=", "");
            case FAKKU2:
                paths = url.split("/");
                return paths[paths.length - 1];
            case MUSES:
                return url.replace("/comics/album/", "").replace("/", ".");
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
                return url.substring(url.lastIndexOf('/') + 1);
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
        return getWebActivityClass(this.site);
    }

    public static Class<?> getWebActivityClass(Site site) {
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
            case FAKKU2:
                return FakkuActivity.class;
            case NEXUS:
                return NexusActivity.class;
            case MUSES:
                return MusesActivity.class;
            default:
                return BaseWebActivity.class;
        }
    }

    public String getCategory() {
        if (site == Site.FAKKU) {
            return url.substring(1, url.lastIndexOf('/'));
        } else {
            if (attributes != null) {
                List<Attribute> attributesList = getAttributeMap().get(AttributeType.CATEGORY);
                if (attributesList != null && !attributesList.isEmpty()) {
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
        this.uniqueSiteId = computeUniqueSiteId();
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
            case FAKKU2:
                galleryConst = "/hentai/";
                break;
            case NEXUS:
                galleryConst = "/view";
                break;
            case MUSES:
            case FAKKU:
            case HENTAICAFE:
            case PANDA:
            default:
                galleryConst = "";
        }

        return site.getUrl() + galleryConst + url;
    }

    public String getReaderUrl() {
        switch (site) {
            case HITOMI:
                return site.getUrl() + "/reader" + url;
            case TSUMINO:
                return site.getUrl() + "/Read/View" + url;
            case ASMHENTAI:
                return site.getUrl() + "/gallery" + url + "1/";
            case ASMHENTAI_COMICS:
                return site.getUrl() + "/gallery" + url;
            case EHENTAI:               // Won't work anyway because of the temporary key
            case NHENTAI:
            case PANDA:
                return getGalleryUrl();
            case HENTAICAFE:
                return site.getUrl() + "/manga/read/$1/en/0/1/"; // $1 has to be replaced by the textual unique site ID without the author name
            case PURURIN:
                return site.getUrl() + "/read/" + url.substring(1).replace("/", "/01/");
            case FAKKU2:
                return getGalleryUrl() + "/read/page/1";
            case NEXUS:
                return site.getUrl() + "/read" + url + "/001";
            case MUSES:
                return site.getUrl().replace("album", "picture") + "/1";
            default:
                return null;
        }
    }

    public Content populateAuthor() {
        String authorStr = "";
        AttributeMap attrMap = getAttributeMap();
        if (attrMap.containsKey(AttributeType.ARTIST) && !attrMap.get(AttributeType.ARTIST).isEmpty())
            authorStr = attrMap.get(AttributeType.ARTIST).get(0).getName();
        if ((null == authorStr || authorStr.equals(""))
             && attrMap.containsKey(AttributeType.CIRCLE)
             && !attrMap.get(AttributeType.CIRCLE).isEmpty()) // Try and get Circle
            authorStr = attrMap.get(AttributeType.CIRCLE).get(0).getName();

        if (null == authorStr) authorStr = "";
        setAuthor(authorStr);
        return this;
    }

    public Content preJSONExport() { // TODO - this is shabby
        this.attributeMap = getAttributeMap();
        this.imageList = new ArrayList<>(imageFiles);
        return this;
    }

    public Content postJSONImport() {   // TODO - this is shabby
        if (null == site) site = Site.NONE;

        if (this.attributeMap != null) {
            this.attributes.clear();
            for (AttributeType type : this.attributeMap.keySet()) {
                for (Attribute attr : this.attributeMap.get(type)) {
                    if (null == attr.getType())
                        attr.setType(AttributeType.SERIE); // Fix the issue with v1.6.5
                    this.attributes.add(attr.computeLocation(site));
                }
            }
        }
        if (this.imageList != null) {
            this.imageFiles.clear();
            this.imageFiles.addAll(this.imageList);
        }
        this.populateAuthor();
        this.uniqueSiteId = computeUniqueSiteId();
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

    long getUploadDate() {
        return uploadDate;
    }

    public Content setUploadDate(long uploadDate) {
        this.uploadDate = uploadDate;
        return this;
    }

    long getDownloadDate() {
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

    @Nullable
    public ToMany<ImageFile> getImageFiles() {
        return imageFiles;
    }

    public Content addImageFiles(List<ImageFile> imageFiles) {
        if (imageFiles != null) {
            this.imageFiles.clear();
            this.imageFiles.addAll(imageFiles);
        }
        return this;
    }

    @Nullable
    public ToMany<ErrorRecord> getErrorLog() {
        return errorLog;
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

    public boolean isLast() {
        return isLast;
    }

    public void setLast(boolean last) {
        this.isLast = last;
    }

    public boolean isFirst() {
        return isFirst;
    }

    public void setFirst(boolean first) {
        this.isFirst = first;
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

    public String getDownloadParams() {
        return (null == downloadParams) ? "" : downloadParams;
    }

    public Content setDownloadParams(String params) {
        downloadParams = params;
        return this;
    }

    public int getLastReadPageIndex() {
        return lastReadPageIndex;
    }

    public void setLastReadPageIndex(int index) {
        this.lastReadPageIndex = index;
    }

    public boolean isBeingDeleted() {
        return isBeingDeleted;
    }

    public void setIsBeingDeleted(boolean isBeingDeleted) {
        this.isBeingDeleted = isBeingDeleted;
    }

    public String getJsonUri() {
        return (null == jsonUri) ? "" : jsonUri;
    }

    public void setJsonUri(String jsonUri) {
        this.jsonUri = jsonUri;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Content)) {
            return false;
        }

        Content content = (Content) o;

        return this == o || (Objects.equals(content.url, url) && Objects.equals(content.site, site));
    }

    @Override
    public int hashCode() {
        int result = url != null ? url.hashCode() : 0;
        result = 31 * result + (site != null ? site.hashCode() : 0);
        return result;
    }

    public static Comparator<Content> getComparator() {
        return QUERY_ORDER_COMPARATOR;
    }

    private static final Comparator<Content> QUERY_ORDER_COMPARATOR = (a, b) -> Integer.compare(a.getQueryOrder(), b.getQueryOrder());
}
