package me.devsaki.hentoid.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.analytics.FirebaseAnalytics
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.databinding.ActivityQueueBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.events.DownloadReviveEvent
import me.devsaki.hentoid.fragments.ProgressDialogFragment
import me.devsaki.hentoid.fragments.queue.ErrorsFragment
import me.devsaki.hentoid.fragments.queue.QueueFragment
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.network.CloudflareHelper
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.util.notification.NotificationManager
import me.devsaki.hentoid.viewmodels.QueueViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.AddQueueMenu.Companion.show
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.util.Timer
import kotlin.concurrent.timer
import kotlin.math.roundToInt


class QueueActivity : BaseActivity() {

    // == Communication
    private lateinit var viewModel: QueueViewModel

    // == UI
    private var binding: ActivityQueueBinding? = null
    private lateinit var queueTab: TabLayout.Tab
    private lateinit var errorsTab: TabLayout.Tab

    // == Vars
    private var cloudflareHelper: CloudflareHelper? = null
    private var reviveTimer: Timer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        binding?.let {
            Helper.tryShowMenuIcons(this, it.queueToolbar.menu)
            it.queueToolbar.setNavigationOnClickListener { finish() }
            it.downloadReviveCancel.setOnClickListener { clearReviveDownload() }

            // Instantiate a ViewPager and a PagerAdapter.
            val pagerAdapter: FragmentStateAdapter = ScreenSlidePagerAdapter(this)
            it.queuePager.isUserInputEnabled = false // Disable swipe to change tabs

            it.queuePager.adapter = pagerAdapter
            TabLayoutMediator(
                it.queueTabs, it.queuePager
            ) { tab: TabLayout.Tab, position: Int ->
                if (0 == position) {
                    queueTab = tab
                    tab.setText(R.string.queue_queue_tab)
                    tab.setIcon(R.drawable.ic_action_download)
                } else {
                    errorsTab = tab
                    tab.setText(R.string.queue_errors_tab)
                    tab.setIcon(R.drawable.ic_error)
                }
            }.attach()
            it.queuePager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    onTabSelected(it, position)
                }
            })
        }

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[QueueViewModel::class.java]
        viewModel.getQueue().observe(this) { onQueueChanged(it) }
        viewModel.getErrors().observe(this) { onErrorsChanged(it) }

        if (!Preferences.getRecentVisibility()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        val intent = intent
        if (intent?.extras != null) processIntent(intent.extras!!)

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        cloudflareHelper?.clear()
        reviveTimer?.cancel()
        binding = null
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.extras != null) processIntent(intent.extras!!)
    }

    private fun processIntent(extras: Bundle) {
        val parser = QueueActivityBundle(extras)
        val contentHash = parser.contentHash
        if (contentHash != 0L) {
            if (parser.isErrorsTab) binding?.queuePager?.currentItem = 1
            viewModel.setContentToShowFirst(contentHash)
        }
        val revivedSite = Site.searchByCode(parser.reviveDownloadForSiteCode.toLong())
        val oldCookie = parser.reviveOldCookie
        if (revivedSite != Site.NONE && oldCookie.isNotEmpty()) reviveDownload(
            revivedSite,
            oldCookie
        )
    }

    private fun onTabSelected(binding: ActivityQueueBinding, position: Int) {
        // Update permanent toolbar
        binding.queueToolbar.menu.let {
            it.findItem(R.id.action_invert_queue).isVisible = (0 == position)
            it.findItem(R.id.action_import_downloads).isVisible = (0 == position)
            it.findItem(R.id.action_cancel_all).isVisible = (0 == position)
            it.findItem(R.id.action_cancel_all_errors).isVisible = (1 == position)
            it.findItem(R.id.action_redownload_all).isVisible = (1 == position)
            // NB : That doesn't mean it should be visible at all times on tab 0 !
            if (1 == position) it.findItem(R.id.action_error_stats).isVisible = false
        }

        // Update selection toolbar
        binding.queueSelectionToolbar.visibility = View.GONE
        binding.queueSelectionToolbar.menu.clear()
        if (0 == position) binding.queueSelectionToolbar.inflateMenu(R.menu.queue_queue_selection_menu)
        else binding.queueSelectionToolbar.inflateMenu(R.menu.queue_error_selection_menu)
        Helper.tryShowMenuIcons(this, binding.queueSelectionToolbar.menu)

        // Log the view of the tab
        val bundle = Bundle()
        bundle.putInt("tag", position)
        FirebaseAnalytics.getInstance(this).logEvent("view_queue_tab", bundle)
    }

    fun getToolbar(): Toolbar? {
        return binding?.queueToolbar
    }

    fun getSelectionToolbar(): Toolbar? {
        return binding?.queueSelectionToolbar
    }

    private fun onQueueChanged(result: List<QueueRecord>) {
        // Update queue tab
        if (result.isEmpty()) queueTab.removeBadge() else {
            val badge: BadgeDrawable = queueTab.orCreateBadge
            badge.isVisible = true
            badge.number = result.size
        }
    }

    private fun onErrorsChanged(result: List<Content>) {
        // Update errors tab
        if (result.isEmpty()) errorsTab.removeBadge() else {
            val badge: BadgeDrawable = errorsTab.orCreateBadge
            badge.isVisible = true
            badge.number = result.size
        }
    }

    private class ScreenSlidePagerAdapter(fa: FragmentActivity?) :
        FragmentStateAdapter(fa!!) {
        override fun createFragment(position: Int): Fragment {
            return if (0 == position) QueueFragment() else ErrorsFragment()
        }

        override fun getItemCount(): Int {
            return 2
        }
    }

    /**
     * Redownload the given list of Content according to the given parameters
     * NB : Used by both the regular redownload and redownload from scratch
     *
     * @param contentList    List of content to be redownloaded
     * @param reparseContent True if the content (general metadata) has to be re-parsed from the site; false to keep
     * @param reparseImages  True if the images have to be re-detected and redownloaded from the site; false to keep
     */
    fun redownloadContent(
        contentList: List<Content>,
        reparseContent: Boolean,
        reparseImages: Boolean
    ) {
        binding?.let {
            if (Preferences.getQueueNewDownloadPosition() == Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
                show(
                    this, it.queueTabs, this
                ) { position: Int, _: PowerMenuItem? ->
                    redownloadContent(
                        contentList,
                        reparseContent,
                        reparseImages,
                        if (0 == position) Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_TOP else Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM
                    )
                }
            } else redownloadContent(
                contentList,
                reparseContent,
                reparseImages,
                Preferences.getQueueNewDownloadPosition()
            )
        }
    }

    /**
     * Redownload the given list of Content according to the given parameters
     * NB : Used by both the regular redownload and redownload from scratch
     *
     * @param contentList    List of content to be redownloaded
     * @param reparseContent True if the content (general metadata) has to be re-parsed from the site; false to keep
     * @param reparseImages  True if the images have to be re-detected and redownloaded from the site; false to keep
     * @param position       Position of the new item to redownload, either QUEUE_NEW_DOWNLOADS_POSITION_TOP or QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM
     */
    private fun redownloadContent(
        contentList: List<Content>,
        reparseContent: Boolean,
        reparseImages: Boolean,
        position: Int
    ) {
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            if (WebkitPackageHelper.getWebViewUpdating())
                ToastHelper.toast(R.string.redownloaded_updating_webview)
            else ToastHelper.toast(R.string.redownloaded_missing_webview)
            return
        }
        if (reparseContent || reparseImages) ProgressDialogFragment.invoke(
            supportFragmentManager,
            resources.getString(R.string.redownload_queue_progress),
            R.plurals.book
        )
        viewModel.redownloadContent(contentList, reparseContent, reparseImages, position,
            { nbSuccess: Int ->
                val message = resources.getQuantityString(
                    R.plurals.redownloaded_scratch,
                    nbSuccess, nbSuccess, contentList.size
                )
                binding?.let {
                    Snackbar.make(it.root, message, BaseTransientBottomBar.LENGTH_LONG)
                        .setAnchorView(it.snackbarLocation).show()
                }
            }, { t: Throwable -> Timber.i(t) }
        )
    }

    private fun changeReviveUIVisibility(visible: Boolean) {
        binding?.let {
            val visibility = if (visible) View.VISIBLE else View.GONE
            it.downloadReviveTxt.visibility = visibility
            it.downloadReviveProgress.visibility = visibility
            it.downloadReviveCancel.visibility = visibility
        }
    }

    /**
     * Revive event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onReviveDownload(event: DownloadReviveEvent) {
        reviveDownload(event.site, event.url)
    }

    // TODO doc
    private fun reviveDownload(revivedSite: Site, oldCookie: String) {
        Timber.d(">> REVIVAL ASKED @ %s", revivedSite.url)
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            if (WebkitPackageHelper.getWebViewUpdating()) ToastHelper.toast(R.string.revive_updating_webview) else ToastHelper.toast(
                R.string.revive_missing_webview
            )
            return
        }

        binding?.let {
            // Remove any notification
            val userActionNotificationManager =
                NotificationManager(this, R.id.user_action_notification)
            userActionNotificationManager.cancel()
            // How many ticks in 1.5 minutes, which is the maximum time for revival
            it.downloadReviveProgress.max = (90 / 1.5).roundToInt()
            it.downloadReviveProgress.progress = it.downloadReviveProgress.max
            changeReviveUIVisibility(true)

            // Start progress UI
            reviveTimer?.cancel()
            reviveTimer = timer("revive-timer", false, 1500, 1500) {
                val currentProgress: Int = it.downloadReviveProgress.progress
                if (currentProgress > 0) it.downloadReviveProgress.progress =
                    currentProgress - 1
            }

            // Try passing CF
            if (null == cloudflareHelper) cloudflareHelper = CloudflareHelper()
            if (cloudflareHelper!!.tryPassCloudflare(revivedSite, oldCookie)) {
                EventBus.getDefault()
                    .post(DownloadCommandEvent(DownloadCommandEvent.Type.EV_UNPAUSE))
            }
        }
        clearReviveDownload()
    }

    private fun clearReviveDownload() {
        changeReviveUIVisibility(false)
        reviveTimer?.cancel()
        cloudflareHelper?.clear()
    }
}