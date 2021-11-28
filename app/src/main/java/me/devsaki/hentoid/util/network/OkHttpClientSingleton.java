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
        return getInstance(HttpHelper.DEFAULT_REQUEST_TIMEOUT);
    }

    public static OkHttpClient getInstance(int connectTimeout, int ioTimeout) {
        int key = (connectTimeout * 100) + ioTimeout;
        if (null == OkHttpClientSingleton.instance.get(key)) {
            synchronized (OkHttpClientSingleton.class) {
                if (null == OkHttpClientSingleton.instance.get(key)) {
                    OkHttpClientSingleton.instance.put(key, buildClient(connectTimeout, ioTimeout));
                }
            }
        }
        return OkHttpClientSingleton.instance.get(key);
    }

    public static OkHttpClient getInstance(int timeoutMs) {
        if (null == OkHttpClientSingleton.instance.get(timeoutMs)) {
            synchronized (OkHttpClientSingleton.class) {
                if (null == OkHttpClientSingleton.instance.get(timeoutMs)) {
                    OkHttpClientSingleton.instance.put(timeoutMs, buildClient(timeoutMs, timeoutMs));
                }
            }
        }
        return OkHttpClientSingleton.instance.get(timeoutMs);
    }

    private static OkHttpClient buildClient(int connectTimeout, int ioTimeout) {
        long CACHE_SIZE = 5L * 1024 * 1024; // 5 MB

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .addInterceptor(OkHttpClientSingleton::rewriteUserAgentInterceptor)
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(ioTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(ioTimeout, TimeUnit.MILLISECONDS)
                .cache(new Cache(HentoidApp.getInstance().getCacheDir(), CACHE_SIZE));

        OkHttpClient bootstrapClient = clientBuilder.build();

        DnsOverHttps dns = new DnsOverHttps.Builder() // TODO make dynamic
                .client(bootstrapClient)
                .url(HttpUrl.get("https://cloudflare-dns.com/dns-query")) // TODO make dynamic
                .bootstrapDnsHosts(DnsOverHttpsProviders.getCloudflareHosts()) // TODO make dynamic
                .build();

        return bootstrapClient.newBuilder().dns(dns).build();
    }

    private static okhttp3.Response rewriteUserAgentInterceptor(Interceptor.Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();
        // If not specified, all requests are done with the device's mobile user-agent, without the Hentoid string
        if (null == chain.request().header("User-Agent") && null == chain.request().header("user-agent"))
            builder.header(HttpHelper.HEADER_USER_AGENT, HttpHelper.getMobileUserAgent(false, true));
        return chain.proceed(builder.build());
    }
}
