package me.devsaki.hentoid.util;

import android.util.SparseArray;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import me.devsaki.hentoid.HentoidApp;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Manages a single instance of OkHttpClient per timeout delay
 */
@SuppressWarnings("squid:S3077") // https://stackoverflow.com/questions/11639746/what-is-the-point-of-making-the-singleton-instance-volatile-while-using-double-l
public class OkHttpClientSingleton {

    private static volatile SparseArray<OkHttpClient> instance = new SparseArray<>();


    private OkHttpClientSingleton() {
    }

    public static OkHttpClient getInstance() {
        int DEFAULT_TIMEOUT = 20 * 1000;
        return getInstance(DEFAULT_TIMEOUT);
    }

    public static OkHttpClient getInstance(int timeoutMs) {
        if (null == OkHttpClientSingleton.instance.get(timeoutMs)) {
            synchronized (OkHttpClientSingleton.class) {
                if (null == OkHttpClientSingleton.instance.get(timeoutMs)) {

                    int CACHE_SIZE = 2 * 1024 * 1024; // 2 MB

                    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                            .addInterceptor(OkHttpClientSingleton::onIntercept)
                            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .cache(new Cache(HentoidApp.getAppContext().getCacheDir(), CACHE_SIZE));


                    OkHttpClientSingleton.instance.put(timeoutMs, clientBuilder.build());
                }
            }
        }
        return OkHttpClientSingleton.instance.get(timeoutMs);
    }

    private static okhttp3.Response onIntercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request()
                .newBuilder()
                .header("User-Agent", Consts.USER_AGENT_NEUTRAL)
                .build();
        return chain.proceed(request);
    }
}
