package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import me.devsaki.hentoid.database.contants.ImageFileTable;
import me.devsaki.hentoid.database.enums.StatusContent;

/**
 * Created by DevSaki on 10/05/2015.
 */
public class ImageFile extends ImageFileTable {

    @Expose
    private Integer order;
    @Expose
    private String url;
    @Expose
    private String name;
    @Expose
    private StatusContent status;

    public Integer getId() {
        return url.hashCode();
    }

    public Integer getOrder() {
        return order;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public StatusContent getStatus() {
        return status;
    }

    public ImageFile setOrder(Integer order) {
        this.order = order;
        return this;
    }

    public ImageFile setUrl(String url) {
        this.url = url;
        return this;
    }

    public ImageFile setName(String name) {
        this.name = name;
        return this;
    }

    public ImageFile setStatus(StatusContent status) {
        this.status = status;
        return this;
    }
}
