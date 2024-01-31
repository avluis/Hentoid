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
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.shortSnack
import me.devsaki.hentoid.databinding.DialogPrefsCookiesBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.network.WebkitPackageHelper

class CookiesDialogFragment : DialogFragment(R.layout.dialog_prefs_cookies) {

    // == UI
    private var binding: DialogPrefsCookiesBinding? = null

    // === VARIABLES
//    private var parent: Parent? = null
    private lateinit var sites: List<Site>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        parent = parentFragment as Parent
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogPrefsCookiesBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        //      parent = null
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        sites = Preferences.getActiveSites().filter { s -> s.isVisible }

        binding?.apply {
            val siteLbls: MutableList<String> = sites.map { s -> s.description }.toMutableList()
            siteLbls.add(0, resources.getString(R.string.web_all_sites))
            sitePicker.entries = siteLbls
            sitePicker.setOnIndexChangeListener { refreshCookieList() }
            actionButton.setOnClickListener { onActionClick() }
        }
        refreshCookieList()
    }

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
            cookiesList.text = TextUtils.join("\n", cookies.keys)
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
        val siteCookies = HttpHelper.parseCookies(HttpHelper.getCookies(site.url))
        val domain = "." + HttpHelper.getDomainFromUri(site.url)
        siteCookies.forEach {
            val cookieName = it.key
            HttpHelper.setCookies(
                domain,
                "$cookieName=;Max-Age=0"
            ) // TODO check if one needs to explicitely set COOKIES_STANDARD_ATTRS
        }
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