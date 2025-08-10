package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.databinding.FragmentNavigationDrawer2Binding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.GroupSearchManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference


class NavigationDrawerFragment2 : Fragment(R.layout.fragment_navigation_drawer2) {

    // === COMMUNICATION
    private lateinit var viewModel: LibraryViewModel
    private lateinit var activity: WeakReference<LibraryActivity>

    // === UI
    private var binding: FragmentNavigationDrawer2Binding? = null

    lateinit var libraryMenu: MenuItem
    lateinit var browserMenu: MenuItem
    lateinit var queueMenu: MenuItem
    lateinit var settingsMenu: MenuItem
    lateinit var toolsMenu: MenuItem
    lateinit var aboutMenu: MenuItem

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
        Settings.unregisterPrefsChangedListener(prefsListener)
        binding = null
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentNavigationDrawer2Binding.inflate(inflater, container, false)

        // More listeners

        Settings.registerPrefsChangedListener(prefsListener)

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

    private fun onTotalQueueChanged(totalQueue: Int) {
        if (totalQueue > 0) {
            var text = if (totalQueue > 99) "99+" else totalQueue.toString()
            if (1 == text.length) text = " $text "
            // TODO
        } else {
            // TODO
        }
    }

    private fun onFavPagesChanged(favPages: Int) {
        // TODO
    }

    private fun onGroupingChanged(targetGroupingId: Int) {
        val targetGrouping = Grouping.searchById(targetGroupingId)
        // if (Grouping.FLAT == targetGrouping) binding?.backBooksBtn?.isVisible = false
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        //updateInfo = event TODO
        applyFlagsAndAlerts()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDrawerClosed(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.DRAWER) return
        // if (CommunicationEvent.Type.CLOSED == event.type) binding?.drawerList?.scrollToPosition(0) TODO
    }

    private fun updateItems() {
        binding?.apply {
            val submenu1 = navigator.menu.addSubMenu(0, 0, 0, R.string.title_submenu_library)
            val libAllBooksMenu = submenu1.add(R.string.groups_flat)
            libAllBooksMenu.setIcon(R.drawable.ic_menu_home)
            val libArtistsMenu = submenu1.add(R.string.groups_by_artist)
            libArtistsMenu.setIcon(R.drawable.ic_attribute_artist)
            val libDateMenu = submenu1.add(R.string.groups_by_dl_date)
            libDateMenu.setIcon(R.drawable.ic_calendar)
            val libCustomMenu = submenu1.add(R.string.groups_custom)
            libCustomMenu.setIcon(R.drawable.ic_custom_group)
            val libFoldersMenu = submenu1.add(R.string.groups_folders)
            libFoldersMenu.setIcon(R.drawable.ic_folder)

            val submenu2 = navigator.menu.addSubMenu(1, 1, 1, R.string.title_submenu_content)
            val browserMenu = submenu2.add(R.string.title_activity_browser)
            browserMenu.setIcon(R.drawable.ic_browser)
            val queueMenu = submenu2.add(R.string.title_activity_queue)
            queueMenu.setIcon(R.drawable.ic_action_download)

            val submenu3 = navigator.menu.addSubMenu(2, 2, 2, R.string.title_submenu_settings)
            val settingsMenu = submenu3.add(R.string.title_activity_settings)
            settingsMenu.setIcon(R.drawable.ic_settings)
            val toolsMenu = submenu3.add(R.string.tools_title)
            toolsMenu.setIcon(R.drawable.ic_tools)
            val aboutMenu = submenu3.add(R.string.title_activity_about)
            aboutMenu.setIcon(R.drawable.ic_info)
            /*
                        libraryMenu = navigator.menu.add(R.string.title_activity_downloads)
                        libraryMenu.setIcon(R.drawable.ic_menu_home)

                        browserMenu = navigator.menu.add(R.string.title_activity_browser)
                        browserMenu.setIcon(R.drawable.ic_browser)
                        queueMenu = navigator.menu.add(R.string.title_activity_queue)
                        queueMenu.setIcon(R.drawable.ic_action_download)

                        // Divider
                        navigator.menu.add("")

                        settingsMenu = navigator.menu.add(R.string.title_activity_settings)
                        settingsMenu.setIcon(R.drawable.ic_settings)
                        toolsMenu = navigator.menu.add(R.string.tools_title)
                        toolsMenu.setIcon(R.drawable.ic_tools)
                        aboutMenu = navigator.menu.add(R.string.title_activity_about)
                        aboutMenu.setIcon(R.drawable.ic_info)
             */
        }
        applyFlagsAndAlerts()
    }

    private fun applyFlagsAndAlerts() {
        /*
        updateInfo?.apply {
            // Display the "new update available" flag
            if (hasNewVersion) showFlagAboutItem()
        }
         */
    }

    /**
     * Callback for any change in Preferences
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return
        if (Settings.Key.ACTIVE_SITES == key) updateItems()
    }
}