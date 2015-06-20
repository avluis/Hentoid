package me.devsaki.hentoid.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Created by neko on 14/06/2015.
 */
public class LastVersionDto {

    @SerializedName("last_version_name")
    private String lastVersionName;
    @SerializedName("last_version_code")
    private Integer lastVersionCode;
    @SerializedName("download_link")
    private String downloadLink;
    @SerializedName("documentation_link")
    private String documentationLink;

    public String getLastVersionName() {
        return lastVersionName;
    }

    public void setLastVersionName(String lastVersionName) {
        this.lastVersionName = lastVersionName;
    }

    public Integer getLastVersionCode() {
        return lastVersionCode;
    }

    public void setLastVersionCode(Integer lastVersionCode) {
        this.lastVersionCode = lastVersionCode;
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
