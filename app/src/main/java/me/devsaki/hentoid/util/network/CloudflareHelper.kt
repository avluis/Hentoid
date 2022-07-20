package me.devsaki.hentoid.util.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.StringHelper
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

const val RELOAD_LIMIT = 3

class CloudflareHelper {

    private val compositeDisposable = CompositeDisposable()
    private var webView: CloudflareWebView? = null

    init {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            this.webView = try {
                CloudflareWebView(HentoidApp.getInstance())
            } catch (e: Resources.NotFoundException) {
                // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
                // Creating with the application Context fixes this, but is not generally recommended for view creation
                CloudflareWebView(Helper.getFixedContext(HentoidApp.getInstance()))
            }
        }
    }


    // TODO doc
    fun tryPassCloudflare(
        revivedSite: Site,
        oldCookie: String?,
        onProgress: Runnable?,
        onPassed: Runnable,
        onFailed: Runnable
    ) {
        val oldCookieInternal: String = oldCookie ?: StringHelper.protect(
            HttpHelper.parseCookies(
                HttpHelper.getCookies(revivedSite.url)
            )[Consts.CLOUDFLARE_COOKIE]
        )

        // Nuke the cookie to force its refresh
        val domain = "." + HttpHelper.getDomainFromUri(revivedSite.url)
        HttpHelper.setCookies(domain, Consts.CLOUDFLARE_COOKIE + "=;Max-Age=0; secure; HttpOnly")

        webView?.setUserAgent(revivedSite.userAgent);
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            webView?.loadUrl(revivedSite.url)
        }
        val reloadTimer = AtomicInteger(0)
        val reloadCounter = AtomicInteger(0)
        // Wait for cookies to refresh
        compositeDisposable.add(
            Observable.timer(1500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .repeat()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val cfcookie =
                        HttpHelper.parseCookies(HttpHelper.getCookies(revivedSite.url))[Consts.CLOUDFLARE_COOKIE]
                    if (cfcookie != null && cfcookie.isNotEmpty() && cfcookie != oldCookieInternal) {
                        Timber.d("CF-COOKIE : refreshed !")
                        onPassed.run()
                        compositeDisposable.clear()
                    } else {
                        Timber.v("CF-COOKIE : not refreshed")
                        onProgress?.run()
                        // Reload if nothing for 7.5s
                        if (reloadTimer.incrementAndGet() > 5 && reloadCounter.incrementAndGet() < RELOAD_LIMIT) {
                            reloadTimer.set(0)
                            Timber.v("CF-COOKIE : RELOAD %d/%d", reloadCounter.get(), RELOAD_LIMIT)
                            handler.post {
                                webView?.reload()
                            }
                        }
                        if (reloadCounter.get() >= RELOAD_LIMIT) {
                            compositeDisposable.clear()
                            onFailed.run()
                        }
                    }
                }
        )
    }

    fun clear() {
        compositeDisposable.clear()
        webView?.removeAllViews()
        webView?.destroy()
        webView = null
    }

    internal class CloudflareWebView @SuppressLint("SetJavaScriptEnabled") constructor(
        context: Context
    ) :
        WebView(context) {
        init {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptThirdPartyCookies(this, true)
            settings.let {
                it.builtInZoomControls = true
                it.displayZoomControls = false
                it.domStorageEnabled = true
                it.useWideViewPort = true
                it.javaScriptEnabled = true
                it.loadWithOverviewMode = true
            }
            if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
        }

        fun setUserAgent(agent: String) {
            settings.userAgentString = agent
        }
    }
}