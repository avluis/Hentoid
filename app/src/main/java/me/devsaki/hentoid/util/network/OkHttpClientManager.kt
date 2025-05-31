package me.devsaki.hentoid.util.network

import android.util.SparseArray
import androidx.core.util.containsKey
import androidx.core.util.valueIterator
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.util.Settings.dnsOverHttps
import me.devsaki.hentoid.util.assertNonUiThread
import me.devsaki.hentoid.util.network.Source.Companion.fromValue
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.dnsoverhttps.DnsOverHttps
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Manages a single instance of OkHttpClient per timeout delay
 */
object OkHttpClientManager {
    // Key = timeout delay (ms); Value = client
    private val instances = SparseArray<OkHttpClient>()

    fun getInstance(): OkHttpClient {
        return getInstance(DEFAULT_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT, true)
    }

    fun getInstance(connectTimeout: Int, ioTimeout: Int, followRedirects: Boolean): OkHttpClient {
        val key = (connectTimeout * 100) + ioTimeout + (if (followRedirects) 1 else 0)
        synchronized(instances) {
            if (!instances.containsKey(key))
                instances[key] = buildClient(connectTimeout, ioTimeout, followRedirects)
            return instances[key]
        }
    }

    fun reset() {
        assertNonUiThread() // Closing network operations shouldn't happen on the UI thread
        synchronized(instances) {
            instances.valueIterator().forEach {
                it.dispatcher.executorService.shutdown()
                it.connectionPool.evictAll()
                try {
                    it.cache?.close()
                } catch (e: Exception) {
                    Timber.i(e)
                }
            }
            instances.clear()
        }
    }

    private fun buildBootstrapClient(): OkHttpClient {
        val cacheSize = 5L * 1024 * 1024 // 5 MB

        return OkHttpClient.Builder()
            .addInterceptor(this::rewriteUserAgentInterceptor)
            .addInterceptor(BrotliInterceptor)
            .cache(Cache(HentoidApp.getInstance().cacheDir, cacheSize))
            .build()
    }

    private fun buildClient(
        connectTimeout: Int,
        ioTimeout: Int,
        followRedirects: Boolean
    ): OkHttpClient {
        synchronized(instances) {
            if (!instances.containsKey(0)) instances.put(0, buildBootstrapClient())
        }
        val primaryClient = instances.get(0)

        // Set custom delays
        val result = primaryClient.newBuilder()
            .followRedirects(followRedirects)
            .connectTimeout(connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(ioTimeout.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(ioTimeout.toLong(), TimeUnit.MILLISECONDS)

        // Add DNS over HTTPS if needed
        val doHSource = fromValue(dnsOverHttps)
        if (doHSource != Source.NONE) {
            val dns = DnsOverHttps.Builder()
                .client(primaryClient)
                .url(doHSource.getPrimaryUrl())
                .bootstrapDnsHosts(doHSource.getHosts())
                .build()
            result.dns(dns)
        }

        return result.build()
    }

    @Throws(IOException::class)
    private fun rewriteUserAgentInterceptor(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        // If not specified, all requests are done with the device's mobile user-agent, without the Hentoid string
        if (null == chain.request().header("User-Agent")
            && null == chain.request().header("user-agent")
        ) builder.header(HEADER_USER_AGENT, getMobileUserAgent(false, true))
        return chain.proceed(builder.build())
    }
}