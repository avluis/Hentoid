package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.util.Locale;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.StatusContent;

/**
 * Created by DevSaki on 10/05/2015.
 * Image File builder
 */
@Entity
public class ImageFile {

    @Id
    private long id;
    @Expose
    private Integer order;
    @Expose
    private String url;
    @Expose
    private String name;
    @Expose
    @Convert(converter = StatusContent.StatusContentConverter.class, dbType = Integer.class)
    private StatusContent status;
    // Temporary during SAVED state only; no need to expose them for JSON persistence
    private String downloadParams;
    public ToOne<Content> content;

    public ImageFile() {
    }

    public ImageFile(int order, String url, StatusContent status) {
        this.order = order;
        this.name = String.format(Locale.US, "%03d", order);
        this.url = url;
        this.status = status;
    }

/*
    public Integer getId() {
        return url.hashCode();
    }
*/

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
}
