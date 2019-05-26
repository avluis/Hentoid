package me.devsaki.hentoid.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Date;
import java.util.List;

import eu.davidea.flexibleadapter.items.IFlexible;

public class GitHubReleases {

    @Expose
    public List<GitHubRelease> releases;

    public static class GitHubRelease {

        @SerializedName("tag_name")
        private String tagName;

        @SerializedName("name")
        private String name;

        @SerializedName("body")
        private String body;

        @SerializedName("assets")
        private List<GitHubAsset> assets;

        public String getTagName() {
            return tagName;
        }

        public String getName() {
            return name;
        }

        public String getBody() {
            return body;
        }

        public List<GitHubAsset> getAssets() {
            return assets;
        }
    }

    public static class GitHubAsset {
        @SerializedName("body")
        private String body;

        @SerializedName("content-type")
        private String contentType;

        @SerializedName("browser_download_url")
        private String downloadUrl;

        @SerializedName("size")
        private long size;

        @SerializedName("created_at")
        private Date creationDate;


        public String getBody() {
            return body;
        }

        public String getContentType() {
            return contentType;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public long getSize() {
            return size;
        }

        public Date getCreationDate() {
            return creationDate;
        }
    }
}
