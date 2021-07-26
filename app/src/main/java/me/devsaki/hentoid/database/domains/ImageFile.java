package me.devsaki.hentoid.database.domains;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImageHelper;
import timber.log.Timber;

/**
 * Created by DevSaki on 10/05/2015.
 * Image File builder
 */
@Entity
public class ImageFile {

    @Id
    private long id;
    private Integer order = -1;
    private String url = "";
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

    // Temporary attributes during SAVED state only; no need to expose them for JSON persistence
    private String downloadParams = "";


    // Runtime attributes; no need to expose them nor to persist them

    // Display order of the image in the image viewer
    @Transient
    private int displayOrder;
    // Has the image been read from a backup URL ?
    @Transient
    private boolean isBackup = false;


    public ImageFile() {
    }

    public ImageFile(int order, String url, StatusContent status, int maxPages) {
        this.order = order;

        int nbMaxDigits = (int) (Math.floor(Math.log10(maxPages)) + 1);
        this.name = String.format(Locale.ENGLISH, "%0" + nbMaxDigits + "d", order);

        this.url = url;
        this.status = status;
    }

    public static ImageFile newCover(String url, StatusContent status) {
        ImageFile result = new ImageFile().setOrder(0).setUrl(url).setStatus(status);
        result.setName(Consts.THUMB_FILE_NAME).setIsCover(true);
        return result;
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
        return this;
    }

    public String getUrl() {
        return url;
    }

    public ImageFile setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getName() {
        return name;
    }

    public ImageFile setName(String name) {
        this.name = name;
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
        return this;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
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

    public String getMimeType() {
        return (null == mimeType) ? ImageHelper.MIME_IMAGE_GENERIC : mimeType;
    }

    public ImageFile setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public void setContentId(long contentId) {
        this.content.setTargetId(contentId);
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
    public ToOne<Chapter> getChapter() {
        return chapter;
    }

    public void setChapter(Chapter chapter) {
        if (null == this.chapter) {
            Timber.d(">> INIT ToONE");
            this.chapter = new ToOne<>(this, ImageFile_.chapter);
        }
        this.chapter.setTarget(chapter);
    }

    public boolean isReadable() {
        return !name.equals(Consts.THUMB_FILE_NAME);
    }

    public String getUsableUri() {
        String result = "";
        if (ContentHelper.isInLibrary(getStatus())) result = getFileUri();
        if (result.isEmpty()) result = getUrl();
        if (result.isEmpty() && !getContent().isNull())
            result = getContent().getTarget().getCoverImageUrl();

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageFile imageFile = (ImageFile) o;
        return getId() == imageFile.getId() &&
                Objects.equals(getUrl(), imageFile.getUrl());
    }

    @Override
    public int hashCode() {
        // Must be an int32, so we're bound to use Objects.hash
        return Objects.hash(getId(), getUrl());
    }

    public long uniqueHash() {
        return Helper.hash64((id + "." + url).getBytes());
    }
}
