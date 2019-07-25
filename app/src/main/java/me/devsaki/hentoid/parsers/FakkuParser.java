package me.devsaki.hentoid.parsers;

import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.devsaki.fakku.FakkuDecode;
import me.devsaki.fakku.PageInfo;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.parsers.content.FakkuGalleryMetadata;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

/**
 * Created by robb_w on 2019/02
 * Handles parsing of content from Fakku
 */
public class FakkuParser implements ImageListParser {

    private int currentStep;
    private int maxSteps;

    public List<ImageFile> parseImageList(Content content) {

        List<ImageFile> result = Collections.emptyList();
        String downloadParamsStr = content.getDownloadParams();
        if (null == downloadParamsStr || downloadParamsStr.isEmpty()) {
            Timber.e("Download parameters not set");
            return result;
        }

        Type type = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> downloadParams = new Gson().fromJson(downloadParamsStr, type);

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

        progressStart(info.pages.keySet().size() + 1);

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
        if (info.key_data != null)
            pageInfo = FakkuDecode.getBookPageData(info.key_hash, Helper.decode64(info.key_data), pid, BuildConfig.FK_TOKEN);
        progressPlus();

        result = new ArrayList<>();
        for (String p : info.pages.keySet()) {
            int order = Integer.parseInt(p);
            FakkuGalleryMetadata.FakkuPage page = info.pages.get(p);
            ImageFile img = ParseHelper.urlToImageFile(page.image, order);

            String pageInfoValue;
            if (pageInfo != null) pageInfoValue = JsonHelper.serializeToJson(pageInfo.get(order - 1)); // String contains JSON data within a JSON...
            else pageInfoValue = "unprotected";

            downloadParams.put("pageInfo", pageInfoValue);
            downloadParamsStr = JsonHelper.serializeToJson(downloadParams);

            img.setDownloadParams(downloadParamsStr);
            result.add(img);
            progressPlus();
        }
        Timber.d("%s", result);

        progressComplete();

        return result;
    }

    private void progressStart(int maxSteps) {
        currentStep = 0;
        this.maxSteps = maxSteps;
        ParseHelper.signalProgress(currentStep, maxSteps);
    }

    private void progressPlus() {
        ParseHelper.signalProgress(++currentStep, maxSteps);
    }

    private void progressComplete() {
        ParseHelper.signalProgress(maxSteps, maxSteps);
    }
}
