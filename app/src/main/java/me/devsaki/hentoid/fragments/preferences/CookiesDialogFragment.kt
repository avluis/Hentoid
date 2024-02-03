package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.shortSnack
import me.devsaki.hentoid.databinding.DialogPrefsCookiesBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import timber.log.Timber

class CookiesDialogFragment : DialogFragment(R.layout.dialog_prefs_cookies) {

    // == UI
    private var binding: DialogPrefsCookiesBinding? = null

    // === VARIABLES
    private lateinit var sites: List<Site>


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogPrefsCookiesBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        sites = Preferences.getActiveSites().filter { s -> s.isVisible }.sortedBy { s -> s.name }

        binding?.apply {
            val siteLbls: MutableList<String> = sites.map { s -> s.description }.toMutableList()
            siteLbls.add(0, resources.getString(R.string.web_all_sites))
            sitePicker.entries = siteLbls
            sitePicker.setOnIndexChangeListener { refreshCookieList() }
            actionButton.setOnClickListener { onActionClick() }
        }
        refreshCookieList()
    }

    /**
     * Display the cookie list for the selected site
     * NB : Cookies set with an empty value do not count
     */
    private fun refreshCookieList() {
        binding?.apply {
            val cookies: Map<String, String>
            if (0 == sitePicker.index) {
                cookies = HashMap()
                sites.forEach { s ->
                    val siteCookies = HttpHelper.parseCookies(HttpHelper.getCookies(s.url))
                    siteCookies.forEach {
                        cookies[it.key] = it.value
                    }
                }
            } else {
                val site = sites[sitePicker.index - 1]
                cookies = HttpHelper.parseCookies(HttpHelper.getCookies(site.url))
            }
            if (cookies.keys.isNotEmpty())
                cookiesList.text = TextUtils.join("\n", cookies.keys)
            else
                cookiesList.text = resources.getString(R.string.pref_browser_clear_cookies_ko)
        }
    }

    private fun deleteAllCookies() {
        CookieManager.getInstance().removeAllCookies {
            val caption =
                if (it) R.string.pref_browser_clear_cookies_ok else R.string.pref_browser_clear_cookies_ko
            (activity as AppCompatActivity).shortSnack(caption)
        }
    }

    private fun deleteCookiesFrom(site: Site) {
        val mgr = CookieManager.getInstance()
        val siteCookies: List<String> =
            if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
                CookieManagerCompat.getCookieInfo(mgr, site.url)
            } else {
                mgr.getCookie(site.url).split("; ")
            }
        siteCookies.forEach {
            HttpHelper.Cookie.parse(it).let { ck ->
                // For some reason, we have to use an empty domain when the cookie's domain doesn't start with .
                val domain = if (ck.domain.startsWith(".")) ck.domain else ""
                val cookieParts: MutableList<String> = ArrayList()
                cookieParts.add("${ck.name}=")
                cookieParts.add("domain=$domain")
                cookieParts.add("path=${ck.path}")
                cookieParts.add("Max-Age=-999")
                if (ck.isSecure) cookieParts.add("secure")
                if (ck.isHttpOnly) cookieParts.add("httponly")
                val cookieString = TextUtils.join("; ", cookieParts)

                val protocol = if (ck.isSecure) "https" else "http"
                val domain2 = if (ck.domain.startsWith(".")) ck.domain.substring(1) else ck.domain
                mgr.setCookie(
                    "$protocol://$domain2/",
                    cookieString
                ) { b -> Timber.v("$protocol://$domain2/ $cookieString $b") }
            }
        }
        (activity as AppCompatActivity).shortSnack(R.string.pref_browser_clear_cookies_ok)
    }

    private fun onActionClick() {
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            (activity as AppCompatActivity).shortSnack(R.string.pref_browser_clear_cookies_missing_webview)
            return
        } else if (WebkitPackageHelper.getWebViewUpdating()) {
            (activity as AppCompatActivity).shortSnack(R.string.pref_browser_clear_cookies_updating_webview)
            return
        } else {
            binding?.apply {
                if (0 == sitePicker.index) deleteAllCookies()
                else deleteCookiesFrom(sites[sitePicker.index - 1])
            }
        }
        dismissAllowingStateLoss()
    }

    companion object {
        fun invoke(parentFragment: Fragment) {
            val fragment = CookiesDialogFragment()
            fragment.show(parentFragment.childFragmentManager, null)
        }
    }
}