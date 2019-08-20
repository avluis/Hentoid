package me.devsaki.hentoid.model;

import com.google.gson.annotations.SerializedName;

public class UpdateInfoJson {

    @SerializedName("updateURL")
    private String updateUrl;

    @SerializedName("versionCode")
    private int versionCode;

    @SerializedName("updateURL.debug")
    private String updateUrlDebug;

    @SerializedName("versionCode.debug")
    private int versionCodeDebug;


    public String getUpdateUrl(boolean isDebug) {
        return isDebug ? updateUrlDebug : updateUrl;
    }

    public int getVersionCode(boolean isDebug) {
        return isDebug ? versionCodeDebug : versionCode;
    }
}
