package me.devsaki.hentoid.util;

import android.util.Pair;

import com.google.gson.Gson;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

public class HttpHelper {

    private static final int TIMEOUT = 30000; // 30 seconds

    @Nullable
    public static Document getOnlineDocument(String url) throws IOException {
        return getOnlineDocument(url, null, true);
    }

    @Nullable
    public static Document getOnlineDocument(String url, List<Pair<String, String>> headers, boolean useHentoidAgent) throws IOException {
        ResponseBody resource = getOnlineResource(url, headers, useHentoidAgent);
        if (resource != null) {
            return Jsoup.parse(resource.string());
        }
        return null;
    }

    @Nullable
    public static <T> T getOnlineJson(String url, Class<T> type) throws IOException {
        return getOnlineJson(url, null, true, type);
    }

    @Nullable
    public static <T> T getOnlineJson(String url, List<Pair<String, String>> headers, boolean useHentoidAgent, Class<T> type) throws IOException {
        ResponseBody resource = getOnlineResource(url, headers, useHentoidAgent);
        if (resource != null) {
            String s = resource.string();
            if (s.startsWith("{")) return new Gson().fromJson(s, type);
        }
        return null;
    }

    @Nullable
    private static ResponseBody getOnlineResource(String url, List<Pair<String, String>> headers, boolean useHentoidAgent) throws IOException {
        OkHttpClient okHttp = OkHttpClientSingleton.getInstance(TIMEOUT);
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null) for (Pair<String, String> header : headers)
            requestBuilder.addHeader(header.first, header.second);
        requestBuilder.header("User-Agent", useHentoidAgent ? Consts.USER_AGENT : Consts.USER_AGENT_NEUTRAL);
        Request request = requestBuilder.get().build();
        return okHttp.newCall(request).execute().body();
    }
}
