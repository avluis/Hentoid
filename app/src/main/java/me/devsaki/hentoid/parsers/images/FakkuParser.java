package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

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
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.FakkuGalleryMetadata;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.exception.AccountException;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Created by robb_w on 2019/02
 * Handles parsing of content from Fakku
 */
public class FakkuParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();


    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {

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

        // Add referer information to downloadParams
        downloadParams.put(HttpHelper.HEADER_REFERER_KEY, content.getReaderUrl());

        String cookieStr = downloadParams.get(HttpHelper.HEADER_COOKIE_KEY);
        if (null == cookieStr || !cookieStr.toLowerCase().contains("fakku"))
            throw new AccountException("Your have to be logged in with a Fakku account");

        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, Site.FAKKU2.getUrl() + "/"));
        String readUrl = content.getGalleryUrl().replace("www", "books").replace("/hentai", "//hentai") + "/read";

        // Get the raw content of the page to detect if it's JSON (no JSON = probably trying to get a premium book with a non-premium account)
        ResponseBody response = HttpHelper.getOnlineResource(readUrl, headers, Site.FAKKU2.useMobileAgent(), Site.FAKKU2.useHentoidAgent(), Site.FAKKU2.useWebviewAgent()).body();
        if (null == response) throw new IOException("Could not load response");

        String rawResource = response.string().trim();
        if (!rawResource.startsWith("{") || !rawResource.endsWith("}")) // No JSON file
            throw new AccountException("Your have to be logged in with a paid Fakku account to download non-free books");

        // Parse the content if JSON
        FakkuGalleryMetadata info = JsonHelper.jsonToObject(rawResource, FakkuGalleryMetadata.class);
        if (null == info) {
            Timber.e("Could not get info @%s", readUrl);
            return result;
        }

        progress.start(content.getUrl(), info.getPages().keySet().size() + 1);

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
            pageInfo = FakkuDecode.getBookPageData(info.getKeyHash(), new String(StringHelper.decode64(info.getKeyData())), pid, BuildConfig.FK_TOKEN);
        progress.advance();

        result = new ArrayList<>();
        result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));
        for (String p : info.getPages().keySet()) {
            int order = Integer.parseInt(p);
            if (pageInfo != null && order > pageInfo.size()) continue;

            FakkuGalleryMetadata.FakkuPage page = info.getPages().get(p);
            if (page != null) {
                ImageFile img = ParseHelper.urlToImageFile(page.getImage(), order, info.getPages().size(), StatusContent.SAVED);

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

    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages) {
        // This class does not use backup URLs
        return Optional.of(new ImageFile(order, url, StatusContent.SAVED, maxPages));
    }
}
