package me.devsaki.hentoid.json;

import android.webkit.MimeTypeMap;

import com.squareup.moshi.Json;

import java.util.Date;
import java.util.List;

public class GithubRelease {
    @Json(name = "tag_name")
    public String tagName;
    public String name;
    public String body;
    @Json(name = "created_at")
    public Date creationDate;
    public List<GithubAsset> assets;
    public boolean prerelease;
    public boolean draft;

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public boolean isPublished() {
        return !prerelease && !draft;
    }

    public String getApkAssetUrl() {
        String apkMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk");
        for (GithubAsset asset : assets) {
            if (asset.content_type.equalsIgnoreCase(apkMimeType)) return asset.browser_download_url;
        }
        return "";
    }


    public static class GithubAsset {
        public String browser_download_url;
        public String content_type;
    }
}
