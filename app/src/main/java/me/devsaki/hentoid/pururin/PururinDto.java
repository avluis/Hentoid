package me.devsaki.hentoid.pururin;

import com.google.gson.annotations.Expose;

import java.util.List;

/**
 * Created by neko on 29/06/2015.
 */
public class PururinDto {

    @Expose
    private String gid;
    @Expose
    private String slug;
    @Expose
    private Integer mode;
    @Expose
    private Integer index;
    @Expose
    private String title;
    @Expose
    private List<ImageDto> images;

    public String getGid() {
        return gid;
    }

    public void setGid(String gid) {
        this.gid = gid;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Integer getMode() {
        return mode;
    }

    public void setMode(Integer mode) {
        this.mode = mode;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<ImageDto> getImages() {
        return images;
    }

    public void setImages(List<ImageDto> images) {
        this.images = images;
    }
}
