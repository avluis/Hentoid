package me.devsaki.hentoid.database.domains;

import java.util.Locale;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Consts;

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
    private boolean favourite = false;
    private boolean isCover = false;
    @Convert(converter = StatusContent.StatusContentConverter.class, dbType = Integer.class)
    private StatusContent status = StatusContent.UNHANDLED_ERROR;
    public ToOne<Content> content;
    private String mimeType;
    private long size = 0;

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
        this.name = String.format(Locale.US, "%0" + nbMaxDigits + "d", order);

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

    public void setIsCover(boolean isCover) {
        this.isCover = isCover;
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
        return (null == mimeType) ? "image/*" : mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setContentId(long contentId) {
        this.content.setTargetId(contentId);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
