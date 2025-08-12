package me.devsaki.hentoid.fragments.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.toSpannable
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.AboutActivity
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.activities.ToolsActivity
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.activities.bundles.ToolsBundle
import me.devsaki.hentoid.activities.prefs.PreferencesActivity
import me.devsaki.hentoid.activities.sources.WelcomeActivity
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.FragmentNavigationDrawer2Binding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.getRandomInt
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.GroupSearchManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference
import kotlin.math.floor

private const val MENU_FACTOR = 1000

class NavigationDrawerFragment2 : Fragment(R.layout.fragment_navigation_drawer2) {

    enum class NavItem {
        LIBRARY, BROWSER, QUEUE, SETTINGS, TOOLS, ABOUT
    }

    // === COMMUNICATION
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var activity: WeakReference<Activity>

    // === UI
    private var binding: FragmentNavigationDrawer2Binding? = null

    // === VARS
    // Content search and filtering criteria in the form of a Bundle (see ContentSearchManager.ContentSearchBundle)
    private var contentSearchBundle: Bundle? = null
    private var updateInfo: UpdateEvent? = null
    private lateinit var origin: NavItem

    private lateinit var menu: Menu

    // Settings listener
    private val prefsListener =
        OnSharedPreferenceChangeListener { _, key -> onSharedPreferenceChanged(key) }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity = WeakReference(requireActivity())
        tag?.let { origin = NavItem.valueOf(it) }
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
        binding?.navigator?.apply {
            setNavigationItemSelectedListener { item ->
                when (floor(item.itemId * 1f / MENU_FACTOR).toInt()) {
                    NavItem.LIBRARY.ordinal -> {
                        val grouping = Grouping.searchById(item.itemId % MENU_FACTOR)
                        if (Grouping.NONE == grouping) launchActivity(LibraryActivity::class.java)
                        libraryViewModel.setGrouping(grouping.id)
                    }

                    NavItem.BROWSER.ordinal -> {
                        val code = item.itemId % MENU_FACTOR
                        val site = Site.searchByCode(code)
                        if (!site.isVisible) {
                            launchActivity(WelcomeActivity::class.java)
                        } else {
                            launchActivity(Content.getWebActivityClass(site))
                        }
                    }

                    NavItem.QUEUE.ordinal -> launchActivity(QueueActivity::class.java)
                    NavItem.SETTINGS.ordinal -> launchActivity(PreferencesActivity::class.java)
                    NavItem.TOOLS.ordinal -> {
                        val toolsBuilder = ToolsBundle()
                        toolsBuilder.contentSearchBundle = contentSearchBundle
                        launchActivity(ToolsActivity::class.java, toolsBuilder.bundle)
                    }

                    NavItem.ABOUT.ordinal -> launchActivity(AboutActivity::class.java)
                }

                true
            }
        }

        Settings.registerPrefsChangedListener(prefsListener)

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vmFactory = ViewModelFactory(requireActivity().application)
        libraryViewModel =
            ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        libraryViewModel.totalQueue.observe(viewLifecycleOwner) { onTotalQueueChanged(it) }
        libraryViewModel.favPages.observe(viewLifecycleOwner) { onFavPagesChanged(it) }
        libraryViewModel.contentSearchBundle.observe(viewLifecycleOwner) {
            contentSearchBundle = it
        }
        libraryViewModel.groupSearchBundle.observe(viewLifecycleOwner) {
            val searchBundle = GroupSearchManager.GroupSearchBundle(it)
            onGroupingChanged(searchBundle.groupingId)
        }
        updateItems()
    }

    override fun onResume() {
        super.onResume()
        updateItems()
    }

    private fun onTotalQueueChanged(totalQueue: Int) {
        val txt = SpannableStringBuilder.valueOf(resources.getText(R.string.title_activity_queue))
        if (totalQueue > 0) txt.append("  ").append(formatCountBadge(requireContext(), totalQueue))
        getMenu(menu, NavItem.QUEUE).title = txt.toSpannable()
    }

    private fun showFlagAboutItem() {
        val txt = SpannableStringBuilder.valueOf(resources.getText(R.string.title_activity_about))
        txt.append("  ").append(formatCountBadge(requireContext(), 1))
        getMenu(menu, NavItem.ABOUT).title = txt.toSpannable()
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
        updateInfo = event
        applyFlagsAndAlerts()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDrawerClosed(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.DRAWER) return
        if (CommunicationEvent.Type.CLOSED == event.type) binding?.navigator?.scrollTo(0, 0)
    }

    private fun updateItems() {
        binding?.apply {
            menu = navigator.menu
            menu.clear()
            val submenu1 = navigator.menu.addSubMenu(0, 0, 0, R.string.title_submenu_library)
            if (origin == NavItem.LIBRARY) {
                addMenu(
                    submenu1,
                    R.string.groups_flat,
                    R.drawable.ic_menu_home,
                    NavItem.LIBRARY,
                    Grouping.FLAT.id,
                    Settings.groupingDisplay == Grouping.FLAT.id || Settings.groupingDisplay == Grouping.NONE.id
                )
                addMenu(
                    submenu1,
                    R.string.groups_by_artist,
                    R.drawable.ic_attribute_artist,
                    NavItem.LIBRARY,
                    Grouping.ARTIST.id,
                    Settings.groupingDisplay == Grouping.ARTIST.id
                )
                addMenu(
                    submenu1,
                    R.string.groups_by_dl_date,
                    R.drawable.ic_calendar,
                    NavItem.LIBRARY,
                    Grouping.DL_DATE.id,
                    Settings.groupingDisplay == Grouping.DL_DATE.id
                )
                addMenu(
                    submenu1,
                    R.string.groups_custom,
                    R.drawable.ic_custom_group,
                    NavItem.LIBRARY,
                    Grouping.CUSTOM.id,
                    Settings.groupingDisplay == Grouping.CUSTOM.id
                )
                addMenu(
                    submenu1,
                    R.string.groups_folders,
                    R.drawable.ic_folder,
                    NavItem.LIBRARY,
                    Grouping.FOLDERS.id,
                    Settings.groupingDisplay == Grouping.FOLDERS.id
                )
                // TODO Dynamic book
            } else {
                addMenu(
                    submenu1,
                    R.string.title_activity_downloads,
                    R.drawable.ic_menu_home,
                    NavItem.LIBRARY,
                    Grouping.NONE.id
                )
            }


            val submenu2 =
                navigator.menu.addSubMenu(1, getRandomInt(), 1, R.string.title_submenu_content)
            if (origin == NavItem.BROWSER) {
                addMenu(
                    submenu2,
                    R.string.title_activity_queue,
                    R.drawable.ic_action_download,
                    NavItem.QUEUE
                )
                // All sites
                // TODO mark current site as selected
                Settings.activeSites.forEach { site ->
                    addMenu(submenu2, site.name, site.ico, NavItem.BROWSER, site.code)
                }
                // TODO edit sites
            } else {
                // Generic browser
                addMenu(
                    submenu2,
                    R.string.title_activity_browser,
                    R.drawable.ic_browser,
                    NavItem.BROWSER
                )
                addMenu(
                    submenu2,
                    R.string.title_activity_queue,
                    R.drawable.ic_action_download,
                    NavItem.QUEUE,
                    isSelected = origin == NavItem.QUEUE
                )
            }


            val submenu3 = navigator.menu.addSubMenu(2, 2, 2, R.string.title_submenu_settings)
            addMenu(
                submenu3,
                R.string.title_activity_settings,
                R.drawable.ic_settings,
                NavItem.SETTINGS
            )
            addMenu(
                submenu3,
                R.string.tools_title,
                R.drawable.ic_tools,
                NavItem.TOOLS
            )
            addMenu(
                submenu3,
                R.string.title_activity_about,
                R.drawable.ic_info,
                NavItem.ABOUT
            )
        }
        applyFlagsAndAlerts()
    }

    private fun applyFlagsAndAlerts() {
        updateInfo?.apply {
            // Display the "new update available" flag
            if (hasNewVersion) showFlagAboutItem()

            // Display the site alert flags, if any
            //if (sourceAlerts.isNotEmpty()) showFlagAlerts(sourceAlerts) TODO
        }
    }

    /**
     * Callback for any change in Preferences
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return
        if (Settings.Key.ACTIVE_SITES == key) updateItems()
    }

    fun formatCountBadge(context: Context, count: Int): SpannableString {
        val badgePaddingV = context.resources.getDimension(R.dimen.nav_badge_padding_vertical)
        val badgePaddingH = context.resources.getDimension(R.dimen.nav_badge_padding_horizontal)
        val badgeDrawable = cn.nekocode.badge.BadgeDrawable.Builder()
            .number(count)
            .type(cn.nekocode.badge.BadgeDrawable.TYPE_NUMBER)
            .badgeColor(context.getThemedColor(R.color.secondary_light))
            .textColor(context.getThemedColor(R.color.on_secondary_light))
            .padding(badgePaddingH, badgePaddingV, badgePaddingH, badgePaddingV, badgePaddingH)
            .build()
        return badgeDrawable.toSpannable()
    }

    @Suppress("DEPRECATION")
    private fun launchActivity(activityClass: Class<*>, bundle: Bundle? = null) {
        val intent = Intent(activity.get(), activityClass)
        // If FLAG_ACTIVITY_CLEAR_TOP is not set,
        // it can interfere with actions mapped to the "back" command
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (bundle != null) intent.putExtras(bundle)
        ContextCompat.startActivity(requireContext(), intent, null)
        activity.get()?.apply {
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                overridePendingTransition(0, 0)
            }
            EventBus.getDefault().post(CommunicationEvent(CommunicationEvent.Type.CLOSE_DRAWER))
        }
    }

    private fun launchFavBook() {
        val builder = ReaderActivityBundle()
        builder.isOpenFavPages = true

        val viewer = Intent(context, ReaderActivity::class.java)
        viewer.putExtras(builder.bundle)

        requireContext().startActivity(viewer)
    }

    private fun menuId(navItem: NavItem, subItem: Int = 0): Int {
        return (navItem.ordinal * MENU_FACTOR) + subItem
    }

    private fun addMenu(
        submenu: SubMenu,
        text: Int,
        icon: Int,
        navItem: NavItem,
        subItem: Int = 0,
        isSelected: Boolean = false
    ): MenuItem {
        return addMenu(submenu, getString(text), icon, navItem, subItem, isSelected)
    }

    private fun addMenu(
        submenu: SubMenu,
        text: String,
        icon: Int,
        navItem: NavItem,
        subItem: Int = 0,
        isSelected: Boolean = false
    ): MenuItem {
        val order = submenu.item.order + submenu.children.count() + 1
        val result = submenu.add(
            submenu.item.groupId,
            menuId(navItem, subItem),
            order,
            text
        )
        result.isCheckable = true
        result.isChecked = isSelected
        result.setIcon(icon)
        return result
    }

    private fun getMenu(m: Menu, navItem: NavItem, subItem: Int = 0): MenuItem {
        return m.findItem(menuId(navItem, subItem))
    }
}