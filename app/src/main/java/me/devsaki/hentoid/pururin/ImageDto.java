package me.devsaki.hentoid.pururin;

import com.google.gson.annotations.Expose;

/**
 * Created by neko on 29/06/2015.
 */
public class ImageDto {
    @Expose
    private String i;
    @Expose
    private String f;
    @Expose
    private String w;
    @Expose
    private String h;

    public String getI() {
        return i;
    }

    public void setI(String i) {
        this.i = i;
    }

    public String getF() {
        return f;
    }

    public void setF(String f) {
        this.f = f;
    }

    public String getW() {
        return w;
    }

    public void setW(String w) {
        this.w = w;
    }

    public String getH() {
        return h;
    }

    public void setH(String h) {
        this.h = h;
    }
}
