package me.devsaki.hentoid.util;

import android.util.SparseArray;

import java.util.concurrent.TimeUnit;

import me.devsaki.hentoid.HentoidApp;
import okhttp3.Cache;
import okhttp3.OkHttpClient;

/**
 * Manages a single instance of OkHttpClient per timeout delay
 */
public class OkHttpClientSingleton {

    private static volatile SparseArray<OkHttpClient> instance = new SparseArray<>();

    private OkHttpClientSingleton() {}

    public static OkHttpClient getInstance(int timeoutMs)
    {
        if (null == OkHttpClientSingleton.instance.get(timeoutMs)) {
            synchronized(OkHttpClientSingleton.class) {
                if (null == OkHttpClientSingleton.instance.get(timeoutMs)) {

                    int CACHE_SIZE = 2 * 1024 * 1024; // 2 MB

                    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .cache(new Cache(HentoidApp.getAppContext().getCacheDir(), CACHE_SIZE));


                    OkHttpClientSingleton.instance.put(timeoutMs,clientBuilder.build());
                }
            }
        }
        return OkHttpClientSingleton.instance.get(timeoutMs);
    }
}
