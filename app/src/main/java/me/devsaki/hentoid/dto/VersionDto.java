package me.devsaki.hentoid.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Created by neko on 14/06/2015.
 */
public class VersionDto {

    @SerializedName("version_name")
    private String versionName;
    @SerializedName("version_code")
    private Integer versionCode;
    @SerializedName("download_link")
    private String downloadLink;
    @SerializedName("documentation_link")
    private String documentationLink;

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public Integer getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(Integer versionCode) {
        this.versionCode = versionCode;
    }

    public String getDownloadLink() {
        return downloadLink;
    }

    public void setDownloadLink(String downloadLink) {
        this.downloadLink = downloadLink;
    }

    public String getDocumentationLink() {
        return documentationLink;
    }

    public void setDocumentationLink(String documentationLink) {
        this.documentationLink = documentationLink;
    }
}
