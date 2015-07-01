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

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StatusContent getStatus() {
        return status;
    }

    public void setStatus(StatusContent status) {
        this.status = status;
    }
}
