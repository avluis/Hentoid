package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.util.Locale;

import me.devsaki.hentoid.enums.StatusContent;

/**
 * Created by DevSaki on 10/05/2015.
 * Image File builder
 */
public class ImageFile {

    @Expose
    private Integer order;
    @Expose
    private String url;
    @Expose
    private String name;
    @Expose
    private StatusContent status;


    public ImageFile() {};

    public ImageFile(int order, String url, StatusContent status)
    {
        this.order = order;
        this.name = String.format(Locale.US, "%03d", order);
        this.url = url;
        this.status = status;
    }


    public Integer getId() {
        return url.hashCode();
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
}
