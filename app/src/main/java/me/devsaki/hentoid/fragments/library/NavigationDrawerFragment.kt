package me.devsaki.hentoid.fragments.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.AboutActivity
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.prefs.PreferencesActivity
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.activities.ToolsActivity
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.activities.bundles.ToolsBundle
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.FragmentNavigationDrawerBinding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.json.core.UpdateInfo
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.viewholders.DrawerItem
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.GroupSearchManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference


class NavigationDrawerFragment : Fragment(R.layout.fragment_navigation_drawer) {

    private val favBookId = Long.MAX_VALUE

    // === COMMUNICATION
    private lateinit var viewModel: LibraryViewModel
    private lateinit var activity: WeakReference<LibraryActivity>

    // === UI
    private var binding: FragmentNavigationDrawerBinding? = null

    private val itemAdapter = ItemAdapter<DrawerItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    private var updateInfo: UpdateEvent? = null

    // === VARS
    // Content search and filtering criteria in the form of a Bundle (see ContentSearchManager.ContentSearchBundle)
    private var contentSearchBundle: Bundle? = null

    // Settings listener
    private val prefsListener =
        OnSharedPreferenceChangeListener { _, key -> onSharedPreferenceChanged(key) }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is LibraryActivity) { "Parent activity has to be a LibraryActivity" }
        activity = WeakReference(requireActivity() as LibraryActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        Preferences.unregisterPrefsChangedListener(prefsListener)
        binding = null
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentNavigationDrawerBinding.inflate(inflater, container, false)

        fastAdapter.onClickListener = { _, _, _, pos -> onItemClick(pos) }

        binding?.apply {
            drawerAboutBtn.setOnClickListener { onAboutClick() }
            drawerAppPrefsBtn.setOnClickListener { onPrefsClick() }
            drawerToolsBtn.setOnClickListener { onToolsClick() }
            drawerAppQueueBtn.setOnClickListener { onQueueClick() }
            backBooksBtn.setOnClickListener { onBackClick() }
            drawerList.adapter = fastAdapter
        }

        Preferences.registerPrefsChangedListener(prefsListener)

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        viewModel.totalQueue.observe(viewLifecycleOwner) { onTotalQueueChanged(it) }
        viewModel.favPages.observe(viewLifecycleOwner) { onFavPagesChanged(it) }
        viewModel.contentSearchBundle.observe(viewLifecycleOwner) { contentSearchBundle = it }
        viewModel.groupSearchBundle.observe(viewLifecycleOwner) {
            val searchBundle = GroupSearchManager.GroupSearchBundle(it)
            onGroupingChanged(searchBundle.groupingId)
        }
        updateItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.type != CommunicationEvent.Type.DISABLE) return
        binding?.backBooksBtn?.isVisible = (CommunicationEvent.Recipient.GROUPS == event.recipient)
    }

    private fun onGroupingChanged(targetGroupingId: Int) {
        val targetGrouping = Grouping.searchById(targetGroupingId)
        if (Grouping.FLAT == targetGrouping) binding?.backBooksBtn?.isVisible = false
    }

    private fun updateItems() {
        val drawerItems: MutableList<DrawerItem> = ArrayList()
        val activeSites = Settings.activeSites
        for (s in activeSites) if (s.isVisible) drawerItems.add(DrawerItem.fromSite(s))
        itemAdapter.clear()
        itemAdapter.add(0, drawerItems)
        applyFlagsAndAlerts()
    }

    private fun onItemClick(position: Int): Boolean {
        val item: DrawerItem = itemAdapter.getAdapterItem(position)
        if (item.identifier == favBookId) launchFavBook()
        else launchActivity(item.activityClass)
        return true
    }

    private fun launchFavBook() {
        val builder = ReaderActivityBundle()
        builder.isOpenFavPages = true

        val viewer = Intent(context, ReaderActivity::class.java)
        viewer.putExtras(builder.bundle)

        requireContext().startActivity(viewer)
    }

    @Suppress("DEPRECATION")
    private fun launchActivity(activityClass: Class<*>, bundle: Bundle? = null) {
        val intent = Intent(activity.get(), activityClass)
        if (bundle != null) intent.putExtras(bundle)
        ContextCompat.startActivity(requireContext(), intent, null)
        activity.get()?.apply {
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                overridePendingTransition(0, 0)
            }
            closeNavigationDrawer()
        }
    }

    private fun showFlagAboutItem() {
        binding?.drawerAboutBtnBadge?.visibility = View.VISIBLE
    }

    private fun onTotalQueueChanged(totalQueue: Int) {
        if (totalQueue > 0) {
            var text = if (totalQueue > 99) "99+" else totalQueue.toString()
            if (1 == text.length) text = " $text "
            binding?.drawerQueueBtnBadge?.text = text
            binding?.drawerQueueBtnBadge?.visibility = View.VISIBLE
        } else {
            binding?.drawerQueueBtnBadge?.visibility = View.GONE
        }
    }

    private fun onFavPagesChanged(favPages: Int) {
        val favItem = itemAdapter.adapterItems.firstOrNull { i -> (favBookId == i.identifier) }
        if (favPages > 0) {
            if (null == favItem) {
                itemAdapter.add(
                    DrawerItem(
                        resources.getString(R.string.fav_pages).uppercase(),
                        R.drawable.ic_page_fav,
                        Content.getWebActivityClass(Site.NONE),
                        favBookId
                    )
                )
            }
        } else {
            if (favItem != null) itemAdapter.removeByIdentifier(favItem.identifier)
        }
    }

    private fun showFlagAlerts(alerts: Map<Site, UpdateInfo.SourceAlert>) {
        for ((index, menuItem) in itemAdapter.adapterItems.withIndex()) {
            if (alerts.containsKey(menuItem.site)) {
                val alert = alerts[menuItem.site]
                if (alert != null) {
                    menuItem.alertStatus = alert.getStatus()
                    fastAdapter.notifyItemChanged(index)
                }
            }
        }
    }

    private fun applyFlagsAndAlerts() {
        updateInfo?.apply {
            // Display the "new update available" flag
            if (hasNewVersion) showFlagAboutItem()

            // Display the site alert flags, if any
            if (sourceAlerts.isNotEmpty()) showFlagAlerts(sourceAlerts)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        updateInfo = event
        applyFlagsAndAlerts()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDrawerClosed(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.DRAWER) return
        if (CommunicationEvent.Type.CLOSED == event.type) binding?.drawerList?.scrollToPosition(0)
    }

    private fun onAboutClick() {
        launchActivity(AboutActivity::class.java)
    }

    private fun onPrefsClick() {
        launchActivity(PreferencesActivity::class.java)
    }

    private fun onToolsClick() {
        val toolsBuilder = ToolsBundle()
        toolsBuilder.contentSearchBundle = contentSearchBundle
        launchActivity(ToolsActivity::class.java, toolsBuilder.bundle)
    }

    private fun onQueueClick() {
        launchActivity(QueueActivity::class.java)
    }

    private fun onBackClick() {
        activity.get()?.let {
            it.goBackToGroups()
            it.closeNavigationDrawer()
        }
    }

    /**
     * Callback for any change in Preferences
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return
        if (Preferences.Key.ACTIVE_SITES == key) updateItems()
    }
}