package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.AboutActivity
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.PrefsActivity
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.activities.ToolsActivity
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.FragmentNavigationDrawerBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.json.core.UpdateInfo.SourceAlert
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.viewholders.DrawerItem
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference


class NavigationDrawerFragment : Fragment(R.layout.fragment_navigation_drawer) {

    private val favBookId = Long.MAX_VALUE

    // ======== COMMUNICATION
    // Viewmodel
    private lateinit var viewModel: LibraryViewModel

    // Activity
    private lateinit var activity: WeakReference<LibraryActivity>

    // === UI
    private var binding: FragmentNavigationDrawerBinding? = null

    private val itemAdapter = ItemAdapter<DrawerItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    private var updateInfo: UpdateEvent? = null

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
    ): View {
        binding = FragmentNavigationDrawerBinding.inflate(inflater, container, false)

        fastAdapter.onClickListener = { _, _, _, pos -> onItemClick(pos) }

        binding?.apply {
            drawerAboutBtn.setOnClickListener { onAboutClick() }
            drawerAppPrefsBtn.setOnClickListener { onPrefsClick() }
            drawerToolsBtn.setOnClickListener { onToolsClick() }
            drawerAppQueueBtn.setOnClickListener { onQueueClick() }
            drawerList.adapter = fastAdapter
        }

        Preferences.registerPrefsChangedListener(prefsListener)

        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        viewModel.totalQueue.observe(viewLifecycleOwner)
        { totalQueue: Int -> onTotalQueueChanged(totalQueue) }
        viewModel.favPages.observe(viewLifecycleOwner)
        { favPages: Int -> onFavPagesChanged(favPages) }

        updateItems()
    }

    private fun updateItems() {
        val drawerItems: MutableList<DrawerItem> = ArrayList()
        val activeSites = Preferences.getActiveSites()
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

    private fun launchActivity(activityClass: Class<*>) {
        val intent = Intent(activity.get(), activityClass)
        ContextCompat.startActivity(requireContext(), intent, null)
        activity.get()?.overridePendingTransition(0, 0)
        activity.get()?.closeNavigationDrawer()
    }

    private fun showFlagAboutItem() {
        binding!!.drawerAboutBtnBadge.visibility = View.VISIBLE
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

    private fun showFlagAlerts(alerts: Map<Site, SourceAlert>) {
        for ((index, menuItem) in itemAdapter.adapterItems.withIndex()) {
            if (alerts.containsKey(menuItem.site)) {
                val alert = alerts[menuItem.site]
                if (alert != null) {
                    menuItem.alertStatus = alert.status
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
        if (event.recipient != CommunicationEvent.RC_DRAWER) return
        if (CommunicationEvent.EV_CLOSED == event.type) binding?.drawerList?.scrollToPosition(0)
    }

    private fun onAboutClick() {
        launchActivity(AboutActivity::class.java)
    }

    private fun onPrefsClick() {
        launchActivity(PrefsActivity::class.java)
    }

    private fun onToolsClick() {
        launchActivity(ToolsActivity::class.java)
    }

    private fun onQueueClick() {
        launchActivity(QueueActivity::class.java)
    }

    /**
     * Callback for any change in Preferences
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return
        if (Preferences.Key.ACTIVE_SITES == key) updateItems()
    }
}