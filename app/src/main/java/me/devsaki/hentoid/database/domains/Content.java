package me.devsaki.hentoid.database.domains;

import static me.devsaki.hentoid.util.JsonHelper.MAP_STRINGS;

import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Transient;
import io.objectbox.converter.PropertyConverter;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.activities.sources.ASMHentaiActivity;
import me.devsaki.hentoid.activities.sources.AllPornComicActivity;
import me.devsaki.hentoid.activities.sources.BaseWebActivity;
import me.devsaki.hentoid.activities.sources.DoujinsActivity;
import me.devsaki.hentoid.activities.sources.EHentaiActivity;
import me.devsaki.hentoid.activities.sources.ExHentaiActivity;
import me.devsaki.hentoid.activities.sources.HbrowseActivity;
import me.devsaki.hentoid.activities.sources.Hentai2ReadActivity;
import me.devsaki.hentoid.activities.sources.HentaifoxActivity;
import me.devsaki.hentoid.activities.sources.HitomiActivity;
import me.devsaki.hentoid.activities.sources.ImhentaiActivity;
import me.devsaki.hentoid.activities.sources.LusciousActivity;
import me.devsaki.hentoid.activities.sources.Manhwa18Activity;
import me.devsaki.hentoid.activities.sources.ManhwaActivity;
import me.devsaki.hentoid.activities.sources.MrmActivity;
import me.devsaki.hentoid.activities.sources.MultpornActivity;
import me.devsaki.hentoid.activities.sources.MusesActivity;
import me.devsaki.hentoid.activities.sources.NhentaiActivity;
import me.devsaki.hentoid.activities.sources.PixivActivity;
import me.devsaki.hentoid.activities.sources.PorncomixActivity;
import me.devsaki.hentoid.activities.sources.PururinActivity;
import me.devsaki.hentoid.activities.sources.ToonilyActivity;
import me.devsaki.hentoid.activities.sources.TsuminoActivity;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Content builder
 */
@SuppressWarnings("UnusedReturnValue")
@Entity
public class Content implements Serializable {

    @IntDef({DownloadMode.DOWNLOAD, DownloadMode.STREAM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DownloadMode {
        int DOWNLOAD = Preferences.Constant.DL_ACTION_DL_PAGES; // Download images
        int STREAM = Preferences.Constant.DL_ACTION_STREAM; // Saves the book for on-demande viewing
    }

    @Id
    private long id;
    @Index
    private String url;
    private String uniqueSiteId; // Has to be queryable in DB, hence has to be a field
    private String title;
    private String author;
    private ToMany<Attribute> attributes;
    private String coverImageUrl;
    private Integer qtyPages; // Integer is actually unnecessary, but changing this to plain int requires a small DB model migration...
    private long uploadDate;
    private long downloadDate = 0;
    private long downloadCompletionDate = 0;
    @Index
    @Convert(converter = StatusContent.StatusContentConverter.class, dbType = Integer.class)
    private StatusContent status;
    @Backlink(to = "content")
    private ToMany<ImageFile> imageFiles;
    @Backlink(to = "content")
    public ToMany<GroupItem> groupItems;
    @Backlink(to = "content")
    private ToMany<Chapter> chapters;
    @Index
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    private Site site;
    /**
     * @deprecated Replaced by {@link me.devsaki.hentoid.workers.ImportWorker} methods; class is kept for retrocompatibilty
     */
    @Deprecated
    private String storageFolder; // Used as pivot for API29 migration; no use after that (replaced by storageUri)
    private String storageUri; // Not exposed because it will vary according to book location -> valued at import
    private boolean favourite = false;
    private boolean completed = false;
    private long reads = 0;
    private long lastReadDate;
    private int lastReadPageIndex = 0;
    private boolean manuallyMerged = false;
    @Convert(converter = Content.StringMapConverter.class, dbType = String.class)
    private Map<String, String> bookPreferences = new HashMap<>();

    private @DownloadMode
    int downloadMode;
    private ToOne<Content> contentToReplace;

    // Aggregated data redundant with the sum of individual data contained in ImageFile
    // ObjectBox can't do the sum in a single Query, so here it is !
    private long size = 0;
    private float readProgress = 0f;

    // Temporary during SAVED state only
    private String downloadParams;
    // Temporary during ERROR state only
    @Backlink(to = "content")
    private ToMany<ErrorRecord> errorLog;
    // Needs to be in the DB to keep the information when deletion takes a long time
    // and user navigates away; no need to save that into JSON
    private boolean isBeingDeleted = false;
    // Needs to be in the DB to optimize I/O
    // No need to save that into the JSON file itself, obviously
    private String jsonUri;
    // Useful only during cleanup operations; no need to get it into the JSON
    private boolean isFlaggedForDeletion = false;

    // Runtime attributes; no need to expose them for JSON persistence nor to persist them to DB
    @Transient
    private long progress;          // number of downloaded pages; used to display the progress bar on the queue screen
    @Transient
    private long downloadedBytes = 0;// Number of downloaded bytes; used to display the size estimate on the queue screen
    @Transient
    private boolean isFirst;        // True if current content is the first of its set in the DB query
    @Transient
    private boolean isLast;         // True if current content is the last of its set in the DB query
    @Transient
    private int numberDownloadRetries = 0;  // Current number of download retries current content has gone through
    @Transient
    private int readPagesCount = -1;  // Read pages count fed by payload; only useful to update list display
    @Transient
    private String archiveLocationUri;  // Only used when importing external archives
    @Transient
    private boolean updatedProperties = false;  // Only used when using ImageListParsers to indicate the passed Content has been updated

    public Content() { // Required by ObjectBox when an alternate constructor exists
    }


    public ToMany<Attribute> getAttributes() {
        return this.attributes;
    }

    public void setAttributes(ToMany<Attribute> attributes) {
        this.attributes = attributes;
    }

    public void clearAttributes() {
        this.attributes.clear();
    }

    public void putAttributes(Collection<Attribute> attributes) {
        // We do want to compare array references, not content
        if (attributes != null && attributes != this.attributes) {
            this.attributes.clear();
            this.attributes.addAll(attributes);
        }
    }

    public AttributeMap getAttributeMap() {
        AttributeMap result = new AttributeMap();
        if (attributes != null)
            for (Attribute a : attributes) result.add(a);
        return result;
    }

    public void putAttributes(@NonNull AttributeMap attrs) {
        if (attributes != null) {
            attributes.clear();
            addAttributes(attrs);
        }
    }

    public Content addAttributes(@NonNull AttributeMap attrs) {
        if (attributes != null) {
            for (Map.Entry<AttributeType, List<Attribute>> entry : attrs.entrySet()) {
                List<Attribute> attrList = entry.getValue();
                if (attrList != null)
                    addAttributes(attrList);
            }
        }
        return this;
    }

    public Content addAttributes(@NonNull List<Attribute> attrs) {
        if (attributes != null) attributes.addAll(attrs);
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

    public void populateUniqueSiteId() {
        this.uniqueSiteId = computeUniqueSiteId();
    }

    public void setUniqueSiteId(@NonNull String uniqueSiteId) {
        this.uniqueSiteId = uniqueSiteId;
    }

    private String computeUniqueSiteId() {
        String[] paths;

        if (null == url) return "";

        switch (site) {
            case FAKKU:
                return url.substring(url.lastIndexOf('/') + 1);
            case EHENTAI:
            case EXHENTAI:
            case PURURIN:
                paths = url.split("/");
                return (paths.length > 1) ? paths[1] : paths[0];
            case MRM:
            case HBROWSE:
                return url.split("/")[0];
            case HITOMI:
                paths = url.split("/");
                String expression = (paths.length > 1) ? paths[1] : paths[0];
                return expression.replace(".html", "");
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
            case NHENTAI:
            case PANDA:
            case TSUMINO:
                return url.replace("/", "");
            case MUSES:
                return url.replace("/comics/album/", "").replace("/", ".");
            case FAKKU2:
            case HENTAIFOX:
            case PORNCOMIX:
            case MANHWA:
            case TOONILY:
            case IMHENTAI:
            case ALLPORNCOMIC:
            case MULTPORN:
                paths = url.split("/");
                return paths[paths.length - 1];
            case DOUJINS:
                // ID is the last numeric part of the URL
                // e.g. lewd-title-ch-1-3-42116 -> 42116 is the ID
                int lastIndex = url.lastIndexOf('-');
                return url.substring(lastIndex + 1);
            case LUSCIOUS:
                // ID is the last numeric part of the URL
                // e.g. /albums/lewd_title_ch_1_3_42116/ -> 42116 is the ID
                lastIndex = url.lastIndexOf('_');
                return url.substring(lastIndex + 1, url.length() - 1);
            case PIXIV:
                // - If artworks, ID is the artwork ID
                // - If not, ID is the whole URL
                if (url.contains("artworks")) return url.substring(url.lastIndexOf('/') + 1);
                else return url;
            default:
                return "";
        }
    }

    /**
     * @deprecated Used for upgrade purposes from old versions
     */
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
            case EXHENTAI:
            case TSUMINO:
                return url.replace("/", "") + "-" + site.getDescription();
            default:
                return null;
        }
    }

    public static Class<? extends AppCompatActivity> getWebActivityClass(Site site) {
        switch (site) {
            case HITOMI:
                return HitomiActivity.class;
            case NHENTAI:
                return NhentaiActivity.class;
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
                return ASMHentaiActivity.class;
            case TSUMINO:
                return TsuminoActivity.class;
            case PURURIN:
                return PururinActivity.class;
            case EHENTAI:
                return EHentaiActivity.class;
            case EXHENTAI:
                return ExHentaiActivity.class;
            case MUSES:
                return MusesActivity.class;
            case DOUJINS:
                return DoujinsActivity.class;
            case LUSCIOUS:
                return LusciousActivity.class;
            case PORNCOMIX:
                return PorncomixActivity.class;
            case HBROWSE:
                return HbrowseActivity.class;
            case HENTAI2READ:
                return Hentai2ReadActivity.class;
            case HENTAIFOX:
                return HentaifoxActivity.class;
            case MRM:
                return MrmActivity.class;
            case MANHWA:
                return ManhwaActivity.class;
            case IMHENTAI:
                return ImhentaiActivity.class;
            case TOONILY:
                return ToonilyActivity.class;
            case ALLPORNCOMIC:
                return AllPornComicActivity.class;
            case PIXIV:
                return PixivActivity.class;
            case MANHWA18:
                return Manhwa18Activity.class;
            case MULTPORN:
                return MultpornActivity.class;
            default:
                return BaseWebActivity.class;
        }
    }

    public String getGalleryUrl() {
        String galleryConst;
        switch (site) {
            case HENTAIFOX:
            case PURURIN:
            case IMHENTAI:
                galleryConst = "/gallery";
                break;
            case HITOMI:
                galleryConst = "/galleries";
                break;
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
            case EHENTAI:           // Won't work because of the temporary key
            case EXHENTAI:          // Won't work because of the temporary key
            case NHENTAI:
                galleryConst = "/g";
                break;
            case TSUMINO:
                galleryConst = "/entry";
                break;
            case FAKKU2:
                galleryConst = "/hentai/";
                break;
            case LUSCIOUS:
                return site.getUrl().replace("/manga/", "") + url;
            case PORNCOMIX:
                return url;
            default:
                galleryConst = "";
        }

        return site.getUrl() + (galleryConst + url).replace("//", "/");
    }

    public String getReaderUrl() {
        switch (site) {
            case HITOMI:
                return site.getUrl() + "/reader" + url;
            case TSUMINO:
                return site.getUrl() + "/Read/Index" + url;
            case ASMHENTAI:
                return site.getUrl() + "/gallery" + url + "1/";
            case ASMHENTAI_COMICS:
                return site.getUrl() + "/gallery" + url;
            case PURURIN:
                return site.getUrl() + "/read/" + url.substring(1).replace("/", "/01/");
            case FAKKU2:
                return getGalleryUrl() + "/read/page/1";
            case MUSES:
                return site.getUrl().replace("album", "picture") + "/1";
            case LUSCIOUS:
                return getGalleryUrl() + "read/";
            case PORNCOMIX:
                if (getGalleryUrl().contains("/manga")) return getGalleryUrl() + "/p/1/";
                else return getGalleryUrl() + "#&gid=1&pid=1";
            case HENTAIFOX:
                return site.getUrl() + "g" + url;
            default:
                return getGalleryUrl();
        }
    }

    /**
     * Neutralizes the given cover URL to detect duplicate books
     *
     * @param url  Cover URL to neutralize
     * @param site Site the URL is taken from
     * @return Neutralized cover URL
     */
    public static String getNeutralCoverUrlRoot(@NonNull final String url, @NonNull final Site site) {
        if (url.isEmpty()) return url;

        if (site == Site.MANHWA) {
            HttpHelper.UriParts parts = new HttpHelper.UriParts(url);
            // Remove the last part of the filename if it is formatted as "numberxnumber"
            String[] nameParts = parts.getFileNameNoExt().split("-");
            String[] lastPartParts = nameParts[nameParts.length - 1].split("x");
            for (String s : lastPartParts)
                if (!StringHelper.isNumeric(s)) return url;

            nameParts = Arrays.copyOf(nameParts, nameParts.length - 1);
            return parts.getPath() + TextUtils.join("-", nameParts);
        } else {
            return url;
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
        return (null == url) ? "" : url;
    }

    public Content setUrl(String url) {
        this.url = url;
        populateUniqueSiteId();
        return this;
    }

    public String getTitle() {
        return StringHelper.protect(title);
    }

    public Content setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getAuthor() {
        if (null == author) author = ContentHelper.formatBookAuthor(this);
        return author;
    }

    public Content setAuthor(String author) {
        this.author = author;
        return this;
    }

    public int getQtyPages() {
        return (null == qtyPages) ? 0 : qtyPages;
    }

    public Content setQtyPages(int qtyPages) {
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

    public long getDownloadCompletionDate() {
        return downloadCompletionDate;
    }

    public Content setDownloadCompletionDate(long value) {
        downloadCompletionDate = value;
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

    public Content setImageFiles(List<ImageFile> imageFiles) {
        // We do want to compare array references, not content
        if (imageFiles != null && imageFiles != this.imageFiles) {
            this.imageFiles.clear();
            this.imageFiles.addAll(imageFiles);
        }
        return this;
    }

    public ImageFile getCover() {
        List<ImageFile> images = getImageFiles();
        if (images != null && !images.isEmpty()) {
            for (ImageFile img : images)
                if (img.isCover()) return img;
        }
        ImageFile makeupCover = ImageFile.fromImageUrl(0, getCoverImageUrl(), StatusContent.ONLINE, 1);
        makeupCover.setImageHash(Long.MIN_VALUE); // Makeup cover is unhashable
        return makeupCover;
    }

    public String getCoverImageUrl() {
        return (null == coverImageUrl) ? "" : coverImageUrl;
    }

    public Content setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
        return this;
    }

    @Nullable
    public ToMany<ErrorRecord> getErrorLog() {
        return errorLog;
    }

    public void setErrorLog(List<ErrorRecord> errorLog) {
        if (errorLog != null && !errorLog.equals(this.errorLog)) {
            this.errorLog.clear();
            this.errorLog.addAll(errorLog);
        }
    }

    public double getPercent() {
        if (getQtyPages() > 0)
            return progress * 1.0 / getQtyPages();
        else
            return 0;
    }

    public void setProgress(long progress) {
        this.progress = progress;
    }

    public void computeProgress() {
        if (0 == progress && imageFiles != null)
            progress = Stream.of(imageFiles).filter(i -> i.getStatus() == StatusContent.DOWNLOADED || i.getStatus() == StatusContent.ERROR).count();
    }

    public double getBookSizeEstimate() {
        if (downloadedBytes > 0) {
            computeProgress();
            if (progress > 3) return (long) (downloadedBytes / getPercent());
        }
        return 0;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    public void computeDownloadedBytes() {
        if (0 == downloadedBytes)
            downloadedBytes = Stream.of(imageFiles).mapToLong(ImageFile::getSize).sum();
    }

    public long getNbDownloadedPages() {
        if (imageFiles != null)
            return Stream.of(imageFiles).filter(i -> (i.getStatus() == StatusContent.DOWNLOADED || i.getStatus() == StatusContent.EXTERNAL || i.getStatus() == StatusContent.ONLINE) && i.isReadable()).count();
        else return 0;
    }

    private long getDownloadedPagesSize() {
        if (imageFiles != null) {
            Long result = Stream.of(imageFiles).filter(i -> (i.getStatus() == StatusContent.DOWNLOADED || i.getStatus() == StatusContent.EXTERNAL || i.getStatus() == StatusContent.ONLINE)).collect(Collectors.summingLong(ImageFile::getSize));
            if (result != null) return result;
        }
        return 0;
    }

    public long getSize() {
        return size;
    }

    public void forceSize(long size) {
        this.size = size;
    }

    public void computeSize() {
        size = getDownloadedPagesSize();
    }

    public void computeReadProgress() {
        if (null == getImageFiles()) {
            readProgress = 0;
            return;
        }
        long denominator = Stream.of(getImageFiles()).withoutNulls().filter(ImageFile::isReadable).count();
        if (0 == denominator) {
            readProgress = 0;
            return;
        }
        readProgress = getReadPagesCount() * 1f / denominator;
    }

    public float getReadProgress() {
        return readProgress;
    }

    public Site getSite() {
        return site;
    }

    public Content setSite(Site site) {
        this.site = site;
        return this;
    }


    /**
     * @deprecated Replaced by getStorageUri; accessor is kept for API29 migration
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public String getStorageFolder() {
        return storageFolder == null ? "" : storageFolder;
    }

    /**
     * @deprecated Replaced by getStorageUri; accessor is kept for API29 migration
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public void resetStorageFolder() {
        storageFolder = "";
    }

    public String getStorageUri() {
        return storageUri == null ? "" : storageUri;
    }

    public Content setStorageUri(String storageUri) {
        this.storageUri = storageUri;
        return this;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Content setCompleted(boolean completed) {
        this.completed = completed;
        return this;
    }

    public Content setFavourite(boolean favourite) {
        this.favourite = favourite;
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
        return lastReadDate;
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

    public Map<String, String> getBookPreferences() {
        return bookPreferences;
    }

    public void setBookPreferences(Map<String, String> bookPreferences) {
        this.bookPreferences = bookPreferences;
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

    public boolean isFlaggedForDeletion() {
        return isFlaggedForDeletion;
    }

    public void setFlaggedForDeletion(boolean flaggedForDeletion) {
        isFlaggedForDeletion = flaggedForDeletion;
    }

    public int getNumberDownloadRetries() {
        return numberDownloadRetries;
    }

    public void increaseNumberDownloadRetries() {
        this.numberDownloadRetries++;
    }

    public boolean isArchive() {
        return ArchiveHelper.isSupportedArchive(getStorageUri()); // Warning : this shortcut assumes the URI contains the file name, which is not guaranteed (not in any spec) !
    }

    public String getArchiveLocationUri() {
        return archiveLocationUri;
    }

    public void setArchiveLocationUri(String archiveLocationUri) {
        this.archiveLocationUri = archiveLocationUri;
    }

    public List<GroupItem> getGroupItems(Grouping grouping) {
        List<GroupItem> result = new ArrayList<>();
        for (GroupItem gi : groupItems)
            if (gi.group.getTarget().grouping.equals(grouping)) result.add(gi);

        return result;
    }

    public int getReadPagesCount() {
        if (readPagesCount > -1) return readPagesCount;

        if (null == imageFiles) return 0;
        int countReadPages = (int) Stream.of(imageFiles).filter(ImageFile::isRead).filter(ImageFile::isReadable).count();
        if (0 == countReadPages && lastReadPageIndex > 0)
            return lastReadPageIndex; // pre-v1.13 content
        else return countReadPages; // post v1.13 content
    }

    public void setReadPagesCount(int count) {
        readPagesCount = count;
    }

    @Nullable
    public ToMany<Chapter> getChapters() {
        return chapters;
    }

    public void setChapters(List<Chapter> chapters) {
        // We do want to compare array references, not content
        if (chapters != null && chapters != this.chapters) {
            this.chapters.clear();
            this.chapters.addAll(chapters);
        }
    }

    public void clearChapters() {
        this.chapters.clear();
    }

    public int getDownloadMode() {
        return downloadMode;
    }

    public Content setDownloadMode(int downloadMode) {
        this.downloadMode = downloadMode;
        return this;
    }

    public boolean isManuallyMerged() {
        return manuallyMerged;
    }

    public void setManuallyMerged(boolean manuallyMerged) {
        this.manuallyMerged = manuallyMerged;
    }

    public boolean isUpdatedProperties() {
        return updatedProperties;
    }

    public void setUpdatedProperties(boolean updatedProperties) {
        this.updatedProperties = updatedProperties;
    }

    public ToOne<Content> getContentToReplace() {
        return contentToReplace;
    }

    public void setContentIdToReplace(long contentIdToReplace) {
        this.contentToReplace.setTargetId(contentIdToReplace);
    }

    public static class StringMapConverter implements PropertyConverter<Map<String, String>, String> {
        @Override
        public Map<String, String> convertToEntityProperty(String databaseValue) {
            if (null == databaseValue) return new HashMap<>();

            try {
                return JsonHelper.jsonToObject(databaseValue, MAP_STRINGS);
            } catch (IOException e) {
                Timber.w(e);
                return new HashMap<>();
            }
        }

        @Override
        public String convertToDatabaseValue(Map<String, String> entityProperty) {
            return JsonHelper.serializeToJson(entityProperty, MAP_STRINGS);
        }
    }

    // Hashcode (and by consequence equals) has to take into account fields that get visually updated on the app UI
    // If not done, FastAdapter's PagedItemListImpl cache won't detect changes to the object
    // and items won't be visually updated on screen
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content content = (Content) o;
        return isFavourite() == content.isFavourite() &&
                isCompleted() == content.isCompleted() &&
                getDownloadDate() == content.getDownloadDate() && // To differentiate external books that have no URL
                getSize() == content.getSize() && // To differentiate external books that have no URL
                getLastReadDate() == content.getLastReadDate() &&
                isBeingDeleted() == content.isBeingDeleted() &&
                Objects.equals(getUrl(), content.getUrl()) &&
                Objects.equals(getCoverImageUrl(), content.getCoverImageUrl()) &&
                getSite() == content.getSite() &&
                getTitle().equals(content.getTitle());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUrl(), getCoverImageUrl(), getDownloadDate(), getSize(), getSite(), isFavourite(), isCompleted(), getLastReadDate(), isBeingDeleted(), getTitle());
    }

    public long uniqueHash() {
        return Helper.hash64((id + "." + uniqueSiteId).getBytes());
    }
}
