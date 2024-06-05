package me.devsaki.hentoid.database.domains;

import static me.devsaki.hentoid.util.ContentHelperKKt.isInLibrary;
import static me.devsaki.hentoid.util.image.ImageHelperKt.MIME_IMAGE_GENERIC;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.DBHelper;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.file.ArchiveHelperKt;

/**
 * Image File builder
 */
@Entity
public class ImageFile {

    @Id
    private long id;
    private Integer order = -1;
    private String url = "";
    private String pageUrl = "";
    private String name = "";
    private String fileUri = "";
    private boolean read = false;
    private boolean favourite = false;
    private boolean isCover = false;
    @Convert(converter = StatusContent.StatusContentConverter.class, dbType = Integer.class)
    private StatusContent status = StatusContent.UNHANDLED_ERROR;
    private ToOne<Content> content;
    private ToOne<Chapter> chapter;
    private String mimeType;
    private long size = 0;
    private long imageHash = 0;
    private boolean isTransformed = false;

    // Temporary attributes during SAVED state only; no need to expose them for JSON persistence
    private String downloadParams = "";

    // WARNING : Update copy constructor when adding attributes


    // == Runtime attributes; no need to expose them nor to persist them

    // cached value of uniqueHash
    @Transient
    private long uniqueHash = 0;
    // Display order of the image in the image viewer (read-time only; 0-indexed)
    @Transient
    private int displayOrder;
    // Backup URL for that picture (download-time only)
    @Transient
    private String backupUrl = "";
    // Has the image been read from a backup URL ? (download-time only)
    @Transient
    private boolean isBackup = false;
    // Force refresh (read-time only)
    @Transient
    public boolean isForceRefresh = false;

    // WARNING : Update copy constructor when adding attributes


    public ImageFile() { // Required by ObjectBox when an alternate constructor exists
    }

    public ImageFile(ImageFile img) {
        this.id = img.id;
        this.order = img.order;
        this.url = img.url;
        this.pageUrl = img.pageUrl;
        this.name = img.name;
        this.fileUri = img.fileUri;
        this.read = img.read;
        this.favourite = img.favourite;
        this.isCover = img.isCover;
        this.status = img.status;
        if (DBHelper.isReachable(img, img.content)) {
            this.content.setTarget(img.content.getTarget());
        } else {
            this.content.setTargetId(img.content.getTargetId());
        }
        if (DBHelper.isReachable(img, img.chapter)) {
            this.chapter.setTarget(img.chapter.getTarget());
        } else {
            this.chapter.setTargetId(img.chapter.getTargetId());
        }
        this.mimeType = img.mimeType;
        this.size = img.size;
        this.imageHash = img.imageHash;
        this.downloadParams = img.downloadParams;
        this.isTransformed = img.isTransformed;

        this.uniqueHash = img.uniqueHash;
        this.displayOrder = img.displayOrder;
        this.backupUrl = img.backupUrl;
        this.isBackup = img.isBackup;
        this.isForceRefresh = img.isForceRefresh;
    }

    public static ImageFile fromImageUrl(int order, String url, StatusContent status, int maxPages) {
        ImageFile result = new ImageFile();
        init(result, order, status, maxPages, null);
        result.url = url;
        return result;
    }

    public static ImageFile fromImageUrl(int order, String url, StatusContent status, String name) {
        ImageFile result = new ImageFile();
        init(result, order, status, -1, name);
        result.url = url;
        return result;
    }

    public static ImageFile fromPageUrl(int order, String url, StatusContent status, int maxPages) {
        ImageFile result = new ImageFile();
        init(result, order, status, maxPages, null);
        result.pageUrl = url;
        return result;
    }

    public static ImageFile fromPageUrl(int order, String url, StatusContent status, String name) {
        ImageFile result = new ImageFile();
        init(result, order, status, -1, name);
        result.pageUrl = url;
        return result;
    }

    public static ImageFile newCover(String url, StatusContent status) {
        ImageFile result = new ImageFile().setOrder(0).setUrl(url).setStatus(status);
        result.setName(Consts.THUMB_FILE_NAME).setIsCover(true);
        return result;
    }

    private static void init(ImageFile imgFile, int order, StatusContent status, int maxPages, String name) {
        imgFile.order = order;
        imgFile.status = status;
        if (null == name || name.isEmpty()) {
            int nbMaxDigits = (int) (Math.floor(Math.log10(maxPages)) + 1);
            imgFile.computeName(nbMaxDigits);
        } else {
            imgFile.name = name;
        }
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Integer getOrder() {
        return order;
    }

    public ImageFile setOrder(Integer order) {
        this.order = order;
        uniqueHash = 0;
        return this;
    }

    public String getUrl() {
        return StringHelper.protect(url);
    }

    public ImageFile setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getName() {
        return name;
    }

    public ImageFile setName(String name) {
        this.name = name;
        return this;
    }

    public ImageFile computeName(int nbMaxDigits) {
        name = String.format(Locale.ENGLISH, "%0" + nbMaxDigits + "d", order);
        return this;
    }

    public StatusContent getStatus() {
        return status;
    }

    public ImageFile setStatus(StatusContent status) {
        this.status = status;
        return this;
    }

    public String getDownloadParams() {
        return (null == downloadParams) ? "" : downloadParams;
    }

    public ImageFile setDownloadParams(String params) {
        downloadParams = params;
        return this;
    }

    public boolean isCover() {
        return isCover;
    }

    public ImageFile setIsCover(boolean isCover) {
        this.isCover = isCover;
        if (isCover) this.read = true;
        uniqueHash = 0;
        return this;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
        uniqueHash = 0;
    }

    public String getFileUri() {
        return (null == fileUri) ? "" : fileUri;
    }

    public ImageFile setFileUri(String fileUri) {
        this.fileUri = fileUri;
        return this;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isBackup() {
        return isBackup;
    }

    public void setBackup(boolean backup) {
        isBackup = backup;
    }

    public String getBackupUrl() {
        return backupUrl;
    }

    public void setBackupUrl(String backupUrl) {
        this.backupUrl = backupUrl;
    }

    public String getMimeType() {
        return (null == mimeType) ? MIME_IMAGE_GENERIC : mimeType;
    }

    public ImageFile setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public long getContentId() {
        return this.content.getTargetId();
    }

    public ImageFile setContentId(long id) {
        this.content.setTargetId(id);
        return this;
    }

    public long getSize() {
        return size;
    }

    public ImageFile setSize(long size) {
        this.size = size;
        return this;
    }

    public long getImageHash() {
        return imageHash;
    }

    public void setImageHash(long hash) {
        this.imageHash = hash;
    }

    public boolean isTransformed() {
        return isTransformed;
    }

    public void setTransformed(boolean transformed) {
        isTransformed = transformed;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public ToOne<Content> getContent() {
        return content;
    }

    public void setContent(ToOne<Content> content) {
        this.content = content;
    }

    @Nullable
    public Chapter getLinkedChapter() {
        return DBHelper.reach(this, chapter);
    }

    @Nullable
    public ToOne<Chapter> getChapter() {
        return chapter;
    }

    public long getChapterId() {
        return this.chapter.getTargetId();
    }

    public ImageFile setChapterId(long id) {
        this.chapter.setTargetId(id);
        return this;
    }

    public void setChapter(Chapter chapter) {
        if (null == this.chapter)
            this.chapter = new ToOne<>(this, ImageFile_.chapter);
        this.chapter.setTarget(chapter);
        uniqueHash = 0;
    }

    public boolean isReadable() {
        return !name.startsWith(Consts.THUMB_FILE_NAME) && !name.startsWith(Consts.EXT_THUMB_FILE_PREFIX);
    }

    public String getUsableUri() {
        String result = "";
        if (isInLibrary(getStatus())) result = getFileUri();
        if (result.isEmpty()) result = getUrl();
        if (result.isEmpty() && !getContent().isNull())
            result = getContent().getTarget().getCoverImageUrl();

        return result;
    }

    public boolean isArchived() {
        String lowerUri = url.toLowerCase();
        for (String ext : ArchiveHelperKt.getSupportedExtensions()) {
            if (lowerUri.contains("." + ext + File.separator)) return true;
        }
        return false;
    }

    public boolean needsPageParsing() {
        return (pageUrl != null && !pageUrl.isEmpty() && (null == url || url.isEmpty()));
    }

    // Hashcode (and by consequence equals) has to take into account fields that get visually updated on the app UI
    // If not done, FastAdapter's PagedItemListImpl cache won't detect changes to the object
    // and items won't be visually updated on screen
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageFile imageFile = (ImageFile) o;
        if (imageFile.isForceRefresh || isForceRefresh) return false;

        return getId() == imageFile.getId() &&
                Objects.equals(getUrl(), imageFile.getUrl())
                && Objects.equals(getPageUrl(), imageFile.getPageUrl())
                && Objects.equals(getFileUri(), imageFile.getFileUri())
                && Objects.equals(getOrder(), imageFile.getOrder())
                && Objects.equals(isCover(), imageFile.isCover()) // Sometimes the thumb picture has the same URL as the 1st page
                && isFavourite() == imageFile.isFavourite()
                && chapter.getTargetId() == imageFile.chapter.getTargetId();
    }

    @Override
    public int hashCode() {
        // Must be an int32, so we're bound to use Objects.hash
        return Objects.hash(getId(), getPageUrl(), getUrl(), getFileUri(), getOrder(), isCover(), isFavourite(), chapter.getTargetId(), isForceRefresh);
    }

    public long uniqueHash() {
        if (0 == uniqueHash)
            uniqueHash = Helper.hash64((id + "." + pageUrl + "." + url + "." + order + "." + isCover + "." + chapter.getTargetId()).getBytes());
        return uniqueHash;
    }
}
