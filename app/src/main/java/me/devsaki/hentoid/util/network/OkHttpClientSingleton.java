package me.devsaki.hentoid.util.network;

import android.util.SparseArray;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import me.devsaki.hentoid.core.HentoidApp;
import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.dnsoverhttps.DnsOverHttps;
import timber.log.Timber;

/**
 * Manages a single instance of OkHttpClient per timeout delay
 */
@SuppressWarnings("squid:S3077")
// https://stackoverflow.com/questions/11639746/what-is-the-point-of-making-the-singleton-instance-volatile-while-using-double-l
public class OkHttpClientSingleton {

    private static volatile SparseArray<OkHttpClient> instance = new SparseArray<>();


    private OkHttpClientSingleton() {
    }

    public static OkHttpClient getInstance() {
        return getInstance(HttpHelper.DEFAULT_REQUEST_TIMEOUT, HttpHelper.DEFAULT_REQUEST_TIMEOUT);
    }

    public static OkHttpClient getInstance(int connectTimeout, int ioTimeout) {
        int key = (connectTimeout * 100) + ioTimeout;
        if (null == instance.get(key)) {
            synchronized (OkHttpClientSingleton.class) {
                if (null == instance.get(key)) {
                    instance.put(key, buildClient(connectTimeout, ioTimeout));
                }
            }
        }
        return OkHttpClientSingleton.instance.get(key);
    }

    public static void reset() {
        int size = instance.size();
        for (int i = 0; i < size; i++) {
            instance.valueAt(i).dispatcher().executorService().shutdown();
            instance.valueAt(i).connectionPool().evictAll();
            try {
                Cache cache = instance.valueAt(i).cache();
                if (cache != null) cache.close();
            } catch (IOException e) {
                Timber.i(e);
            }
        }
        instance.clear();
    }

    private static OkHttpClient buildBootstrapClient() {
        long CACHE_SIZE = 5L * 1024 * 1024; // 5 MB

        return new OkHttpClient.Builder()
                .addInterceptor(OkHttpClientSingleton::rewriteUserAgentInterceptor)
                .cache(new Cache(HentoidApp.getInstance().getCacheDir(), CACHE_SIZE))
                .build();
    }

    private static OkHttpClient buildClient(int connectTimeout, int ioTimeout) {
        if (null == instance.get(0)) {
            synchronized (OkHttpClientSingleton.class) {
                if (null == instance.get(0)) {
                    instance.put(0, buildBootstrapClient());
                }
            }
        }
        OkHttpClient primaryClient = instance.get(0);

        DnsOverHttps dns = new DnsOverHttps.Builder() // TODO make dynamic
                .client(primaryClient)
                .url(HttpUrl.get("https://cloudflare-dns.com/dns-query")) // TODO make dynamic
                .bootstrapDnsHosts(DnsOverHttpsProviders.getCloudflareHosts()) // TODO make dynamic
                .build();

        return primaryClient.newBuilder()
                .dns(dns)
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(ioTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(ioTimeout, TimeUnit.MILLISECONDS)
                .build();
    }

    private static okhttp3.Response rewriteUserAgentInterceptor(Interceptor.Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();
        // If not specified, all requests are done with the device's mobile user-agent, without the Hentoid string
        if (null == chain.request().header("User-Agent") && null == chain.request().header("user-agent"))
            builder.header(HttpHelper.HEADER_USER_AGENT, HttpHelper.getMobileUserAgent(false, true));
        return chain.proceed(builder.build());
    }
}
