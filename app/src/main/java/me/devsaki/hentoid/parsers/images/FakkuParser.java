package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.devsaki.fakku.FakkuDecode;
import me.devsaki.fakku.PageInfo;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.json.sources.FakkuGalleryMetadata;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

/**
 * Created by robb_w on 2019/02
 * Handles parsing of content from Fakku
 */
public class FakkuParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();


    public List<ImageFile> parseImageList(Content content) {

        List<ImageFile> result = Collections.emptyList();
        String downloadParamsStr = content.getDownloadParams();
        if (null == downloadParamsStr || downloadParamsStr.isEmpty()) {
            Timber.e("Download parameters not set");
            return result;
        }

        Map<String, String> downloadParams;
        try {
            downloadParams = JsonHelper.jsonToObject(downloadParamsStr, JsonHelper.MAP_STRINGS);
        } catch (IOException e) {
            Timber.e(e);
            return result;
        }

        if (!downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY)) {
            Timber.e("Download parameters do not contain any cookie");
            return result;
        }

        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, downloadParams.get(HttpHelper.HEADER_COOKIE_KEY)));
        String readUrl = content.getGalleryUrl().replace("www", "books") + "/read";
        FakkuGalleryMetadata info;
        try {
            info = HttpHelper.getOnlineJson(readUrl, headers, false, FakkuGalleryMetadata.class);
        } catch (IOException e) {
            Timber.e(e, "I/O Error while attempting to connect to %s", readUrl);
            return result;
        }

        if (null == info) {
            Timber.e("Could not get info @%s", readUrl);
            return result;
        }

        progress.start(info.getPages().keySet().size() + 1);

        // Add referer information to downloadParams for future image download
        downloadParams.put(HttpHelper.HEADER_REFERER_KEY, content.getReaderUrl());

        // Process book info to get page detailed info
        String pid = null;
        String cookie = downloadParams.get(HttpHelper.HEADER_COOKIE_KEY);
        if (cookie != null) {
            String[] cookieContent = cookie.split(";");
            for (String s : cookieContent) {
                String[] cookieParts = s.split("=");
                if (cookieParts[0].toLowerCase().trim().equals("fakku_zid")) {
                    pid = cookieParts[1];
                    break;
                }
            }
        }
        if (null == pid) {
            Timber.e("Could not extract zid");
            return result;
        }

        List<PageInfo> pageInfo = null;
        if (info.getKeyData() != null)
            pageInfo = FakkuDecode.getBookPageData(info.getKeyHash(), Helper.decode64(info.getKeyData()), pid, BuildConfig.FK_TOKEN);
        progress.advance();

        result = new ArrayList<>();
        for (String p : info.getPages().keySet()) {
            int order = Integer.parseInt(p);
            FakkuGalleryMetadata.FakkuPage page = info.getPages().get(p);
            if (page != null) {
                ImageFile img = ParseHelper.urlToImageFile(page.getImage(), order, info.getPages().size());

                String pageInfoValue;
                if (pageInfo != null)
                    pageInfoValue = JsonHelper.serializeToJson(pageInfo.get(order - 1), PageInfo.class); // String contains JSON data within a JSON...
                else pageInfoValue = "unprotected";

                downloadParams.put("pageInfo", pageInfoValue);
                downloadParamsStr = JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS);

                img.setDownloadParams(downloadParamsStr);
                result.add(img);
                progress.advance();
            }
        }
        Timber.d("%s", result);

        progress.complete();

        return result;
    }

    public ImageFile parseBackupUrl(String url, int order, int maxPages) {
        // This class does not use backup URLs
        return null;
    }
}
