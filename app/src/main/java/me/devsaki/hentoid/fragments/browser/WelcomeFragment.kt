package me.devsaki.hentoid.fragments.browser

import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.settings.SettingsSourceSelectActivity
import me.devsaki.hentoid.activities.sources.CustomWebViewClient
import me.devsaki.hentoid.database.domains.SiteHistory
import me.devsaki.hentoid.databinding.FragmentWebWelcomeBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.launchBrowserFor
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.viewholders.DrawerItem
import me.devsaki.hentoid.viewholders.IconItem
import me.devsaki.hentoid.viewmodels.BrowserViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

class WelcomeFragment : Fragment(R.layout.fragment_web_welcome) {

    // == COMMUNICATION
    // Viewmodel
    private lateinit var viewModel: BrowserViewModel

    private val prefsListener =
        OnSharedPreferenceChangeListener { _, k -> onSharedPreferenceChanged(k) }


    // === UI
    private var binding: FragmentWebWelcomeBinding? = null

    private val sitesItemAdapter = ItemAdapter<IconItem<Site>>()
    private val sitesFastAdapter = FastAdapter.with(sitesItemAdapter)

    private val historyItemAdapter = ItemAdapter<DrawerItem<SiteHistory>>()
    private val historyFastAdapter = FastAdapter.with(historyItemAdapter)

    // === VARIABLES
    private var parent: CustomWebViewClient.BrowserActivity? = null
    private var sitePerTimestamp: List<Site> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.registerPrefsChangedListener(prefsListener)
    }

    override fun onDestroy() {
        parent = null
        Settings.unregisterPrefsChangedListener(prefsListener)
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWebWelcomeBinding.inflate(inflater, container, false)

        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(requireActivity().application)
        )[BrowserViewModel::class.java]

        viewModel.siteHistory().observe(viewLifecycleOwner) { onHistoryChanged(it) }

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parent = activity as CustomWebViewClient.BrowserActivity?

        binding?.apply {
            activeSites.adapter = sitesFastAdapter
            sitesFastAdapter.onClickListener = { _, _, i, _ -> onSiteClick(i) }

            editSites.setOnClickListener {
                startActivity(Intent(requireActivity(), SettingsSourceSelectActivity::class.java))
            }

            recentHistory.adapter = historyFastAdapter
            historyFastAdapter.onClickListener = { _, _, i, _ -> onHistoryClick(i) }
        }

        viewModel.loadHistory()
    }

    private fun loadActiveSites() {
        val activeSites = Settings.activeSites.filter { it.isVisible }
        val items = mutableSetOf<Site>()
        items.addAll(sitePerTimestamp.filter { activeSites.contains(it) })
        items.addAll(activeSites)
        sitesItemAdapter.set(items.map { IconItem(it.ico, it) })
    }

    private fun onHistoryChanged(history: List<SiteHistory>) {
        historyItemAdapter.set(
            history
                .sortedBy { it.timestamp * -1 }
                .filter { it.site.isVisible }
                .map {
                    val parts = UriParts(it.url)
                    val shortUrl = parts.pathFull.substring(parts.host.length)
                    DrawerItem(shortUrl, it.site.ico, it.id, mTag = it)
                }.filter { it.label.length > 1 } // Don't include site roots or '/'
        )
        sitePerTimestamp = history.sortedBy { it.timestamp * -1 }
            .map { it.site }.distinct()
            .filter { it.isVisible }
        loadActiveSites()
    }

    private fun onSiteClick(item: IconItem<Site>): Boolean {
        context?.let {
            item.getObject()?.let { o ->
                launchBrowserFor(requireContext(), Site.searchByCode(o.code))
                return true
            }
        }
        return false
    }

    private fun onHistoryClick(item: DrawerItem<SiteHistory>): Boolean {
        context?.let {
            item.getObject()?.let { o ->
                launchBrowserFor(it, o.url)
                return true
            }
        }
        return false
    }

    /**
     * Callback for any change in Settings
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return
        if (Settings.Key.ACTIVE_SITES == key) loadActiveSites()
    }
}