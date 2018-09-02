package me.devsaki.hentoid.model;

import com.google.gson.annotations.SerializedName;

public class UpdateInfoJson {

    @SerializedName("updateURL")
    private String updateUrl;

    @SerializedName("versionCode")
    private int versionCode;

    public String getUpdateUrl() {
        return updateUrl;
    }

    public void setUpdateUrl(String updateUrl) {
        this.updateUrl = updateUrl;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }
}
