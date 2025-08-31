package me.devsaki.hentoid.fragments

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
import android.widget.Toast
import androidx.core.text.toSpannable
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cn.nekocode.badge.BadgeDrawable
import com.google.android.material.button.MaterialButton
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.AboutActivity
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.activities.ToolsActivity
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.activities.bundles.ToolsBundle
import me.devsaki.hentoid.activities.settings.SettingsActivity
import me.devsaki.hentoid.activities.settings.SettingsSourceSelectActivity
import me.devsaki.hentoid.activities.sources.WelcomeActivity
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.core.runUpdateDownloadWorker
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.FragmentNavigationDrawerBinding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.getRandomInt
import me.devsaki.hentoid.util.getTextColorForBackground
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.launchBrowserFor
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.UpdateDownloadWorker
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import kotlin.math.floor

private const val MENU_FACTOR = 1000

class NavigationDrawerFragment : Fragment(R.layout.fragment_navigation_drawer),
    SelectSiteDialogFragment.Parent {

    enum class NavItem {
        LIBRARY, FAV_BOOK, BROWSER, EDIT_SOURCES, QUEUE, ABOUT
    }

    // === COMMUNICATION
    private lateinit var libraryViewModel: LibraryViewModel

    private val listener =
        OnSharedPreferenceChangeListener { _, key: String? ->
            onSharedPreferenceChanged(key ?: "")
        }

    // === UI
    private var binding: FragmentNavigationDrawerBinding? = null
    private var updateAppBtn: MaterialButton? = null

    // === VARS
    // Content search and filtering criteria in the form of a Bundle (see ContentSearchManager.ContentSearchBundle)
    private var contentSearchBundle: Bundle? = null
    private var updateInfo: UpdateEvent? = null

    private var site: Site = Site.NONE
    private lateinit var origin: NavItem
    private var isCustomGroupingAvailable = false
    private var isDynamicGroupingAvailable = false
    private var isFavBookAvailable = false

    private lateinit var menu: Menu


    override fun onAttach(context: Context) {
        super.onAttach(context)
        tag?.let { origin = NavItem.valueOf(it) }
    }

    override fun onDestroy() {
        binding = null
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentNavigationDrawerBinding.inflate(inflater, container, false)

        // More listeners
        binding?.navigator?.apply {
            this@NavigationDrawerFragment.menu = menu
            setNavigationItemSelectedListener { item ->
                when (floor(item.itemId * 1f / MENU_FACTOR).toInt()) {
                    NavItem.LIBRARY.ordinal -> {
                        val grouping = Grouping.searchById(item.itemId % MENU_FACTOR)
                        if (origin == NavItem.LIBRARY) libraryViewModel.setGrouping(grouping.id)
                        else launchActivity(LibraryActivity::class.java, clearTop = true)
                    }

                    NavItem.FAV_BOOK.ordinal -> launchFavBook()

                    NavItem.BROWSER.ordinal -> {
                        if (origin == NavItem.BROWSER) {
                            val code = item.itemId % MENU_FACTOR
                            val site = Site.searchByCode(code)
                            if (!site.isVisible) {
                                launchActivity(WelcomeActivity::class.java)
                            } else {
                                Timber.d("${this@NavigationDrawerFragment.site} ${this@NavigationDrawerFragment.site.isVisible}")
                                launchActivity(
                                    Content.getWebActivityClass(site),
                                    reorderToFront = this@NavigationDrawerFragment.site.isVisible
                                )
                            }
                        } else {
                            SelectSiteDialogFragment.invoke(
                                this@NavigationDrawerFragment, "",
                                Settings.activeSites.map { it.code })
                        }
                    }

                    NavItem.EDIT_SOURCES.ordinal -> launchActivity(SettingsSourceSelectActivity::class.java)
                    NavItem.QUEUE.ordinal -> launchActivity(QueueActivity::class.java)
                }

                true
            }
        }
        Settings.registerPrefsChangedListener(listener)

        return binding?.root
    }

    override fun onDestroyView() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        Settings.unregisterPrefsChangedListener(listener)
        super.onDestroyView()
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
        libraryViewModel.isCustomGroupingAvailable.observe(viewLifecycleOwner) {
            onCustomGroupingAvailable(it)
        }
        libraryViewModel.isDynamicGroupingAvailable.observe(viewLifecycleOwner) {
            onDynamicGroupingAvailable(it)
        }
        binding?.apply {
            val header = this.navigator.getHeaderView(0)
            val settingsBtn: MaterialButton = header.requireById(R.id.settings_btn)
            settingsBtn.setOnClickListener { launchActivity(SettingsActivity::class.java) }

            val toolsBtn: MaterialButton = header.requireById(R.id.tools_btn)
            toolsBtn.setOnClickListener {
                val toolsBuilder = ToolsBundle()
                toolsBuilder.contentSearchBundle = contentSearchBundle
                launchActivity(ToolsActivity::class.java, toolsBuilder.bundle)
            }

            val aboutBtn: MaterialButton = header.requireById(R.id.about_btn)
            aboutBtn.setOnClickListener { launchActivity(AboutActivity::class.java) }

            updateAppBtn = header.requireById(R.id.update_btn)
        }
        updateAppBtn?.setOnClickListener {
            // TODO make it a beautiful "update available" dialog with a download button
            updateInfo?.let { nfo ->
                context?.let { ctx ->
                    // Download the latest update (equivalent to tapping the "Update available" notification)
                    if (!UpdateDownloadWorker.isRunning(ctx) && nfo.apkUrl.isNotEmpty()) {
                        Toast.makeText(ctx, R.string.downloading_update, Toast.LENGTH_SHORT)
                            .show()
                        ctx.runUpdateDownloadWorker(nfo.apkUrl)
                    }
                }
            }
        }
        updateItems()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        updateItems()
    }

    private fun onTotalQueueChanged(totalQueue: Int) {
        val txt = SpannableStringBuilder.valueOf(resources.getText(R.string.title_activity_queue))
        if (totalQueue > 0) txt.append("  ").append(formatCountBadge(requireContext(), totalQueue))
        getMenu(menu, NavItem.QUEUE)?.title = txt.toSpannable()
    }

    private fun onFavPagesChanged(favPages: Int) {
        isFavBookAvailable = favPages > 0
        getMenu(menu, NavItem.FAV_BOOK)?.isVisible = isFavBookAvailable
    }

    private fun onCustomGroupingAvailable(isAvailable: Boolean) {
        isCustomGroupingAvailable = isAvailable
        getMenu(menu, NavItem.LIBRARY, Grouping.CUSTOM.id)?.isVisible = isAvailable
    }

    private fun onDynamicGroupingAvailable(isAvailable: Boolean) {
        isDynamicGroupingAvailable = isAvailable
        getMenu(menu, NavItem.LIBRARY, Grouping.DYNAMIC.id)?.isVisible = isAvailable
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        updateInfo = event
        updateItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.DRAWER) return
        if (CommunicationEvent.Type.CLOSED == event.type) binding?.navigator?.scrollTo(0, 0)
        if (CommunicationEvent.Type.SIGNAL_SITE == event.type) {
            site = Site.valueOf(event.message)
            updateItems()
        }
    }

    private fun updateItems() {
        binding?.apply {
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
                    R.string.groups_dynamic,
                    R.drawable.ic_search,
                    NavItem.LIBRARY,
                    Grouping.DYNAMIC.id,
                    Settings.groupingDisplay == Grouping.DYNAMIC.id
                ).isVisible = isDynamicGroupingAvailable
                addMenu(
                    submenu1,
                    R.string.groups_custom,
                    R.drawable.ic_custom_group,
                    NavItem.LIBRARY,
                    Grouping.CUSTOM.id,
                    Settings.groupingDisplay == Grouping.CUSTOM.id
                ).isVisible = isCustomGroupingAvailable
                addMenu(
                    submenu1,
                    R.string.groups_folders,
                    R.drawable.ic_folder,
                    NavItem.LIBRARY,
                    Grouping.FOLDERS.id,
                    Settings.groupingDisplay == Grouping.FOLDERS.id
                )
                addMenu(
                    submenu1,
                    R.string.fav_pages,
                    R.drawable.ic_page_fav,
                    NavItem.FAV_BOOK
                ).isVisible = isFavBookAvailable
            } else {
                addMenu(
                    submenu1,
                    R.string.title_activity_downloads,
                    R.drawable.ic_menu_home,
                    NavItem.LIBRARY,
                    Grouping.FLAT.id
                )
            }


            val submenu2 =
                navigator.menu.addSubMenu(1, getRandomInt(), 1, R.string.title_submenu_content)
            // Browser welcome screen
            addMenu(
                submenu2,
                R.string.title_activity_browser,
                R.drawable.ic_browser,
                NavItem.BROWSER,
                isSelected = origin == NavItem.BROWSER && this@NavigationDrawerFragment.site == Site.NONE
            )
            addMenu(
                submenu2,
                R.string.title_activity_queue,
                R.drawable.ic_action_download,
                NavItem.QUEUE,
                isSelected = origin == NavItem.QUEUE
            )

            if (origin == NavItem.BROWSER || Settings.navigationNostalgiaMode) {
                // All sites
                Settings.activeSites.forEach { site ->
                    val sb = SpannableStringBuilder.valueOf(site.name)
                    updateInfo?.sourceAlerts[site]?.let { siteAlert ->
                        sb.append("  ")
                            .append(
                                formatAlertBadge(
                                    requireContext(),
                                    siteAlert.getStatus().symbol,
                                    siteAlert.getStatus().color
                                )
                            )
                    }
                    val siteMenu =
                        addMenu(submenu2, sb.toSpannable(), site.ico, NavItem.BROWSER, site.code)
                    if (this@NavigationDrawerFragment.site == site) siteMenu.isChecked = true
                }
                addMenu(
                    submenu2,
                    R.string.pref_drawer_sources_title,
                    R.drawable.ic_edit_square,
                    NavItem.EDIT_SOURCES
                )
            }
        }
        updateAppBtn?.isVisible = (true == updateInfo?.hasNewVersion)
    }

    /**
     * Callback for any change in Settings
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return
        if (Settings.Key.ACTIVE_SITES == key) updateItems()
        if (Settings.Key.NOSTALGIA_MODE == key) updateItems()
    }

    fun formatAlertBadge(context: Context, text: String, color: Int): SpannableString {
        val badgePaddingV = context.resources.getDimension(R.dimen.nav_badge_padding_vertical)
        val badgePaddingH = context.resources.getDimension(R.dimen.nav_badge_padding_horizontal)
        val badgeColor = context.getThemedColor(color)
        val textColor = getTextColorForBackground(badgeColor)
        val badgeDrawable = BadgeDrawable.Builder()
            .type(BadgeDrawable.TYPE_ONLY_ONE_TEXT)
            .text1(text)
            .badgeColor(badgeColor)
            .textColor(textColor)
            .padding(badgePaddingH, badgePaddingV, badgePaddingH, badgePaddingV, badgePaddingH)
            .build()
        return badgeDrawable.toSpannable()
    }

    fun formatCountBadge(context: Context, count: Int): SpannableString {
        val badgePaddingV = context.resources.getDimension(R.dimen.nav_badge_padding_vertical)
        val badgePaddingH = context.resources.getDimension(R.dimen.nav_badge_padding_horizontal)
        val badgeDrawable = BadgeDrawable.Builder()
            .number(count)
            .type(BadgeDrawable.TYPE_NUMBER)
            .badgeColor(context.getThemedColor(R.color.secondary_light))
            .textColor(context.getThemedColor(R.color.on_secondary_light))
            .padding(badgePaddingH, badgePaddingV, badgePaddingH, badgePaddingV, badgePaddingH)
            .build()
        return badgeDrawable.toSpannable()
    }

    @Suppress("DEPRECATION")
    private fun launchActivity(
        activityClass: Class<*>,
        bundle: Bundle? = null,
        clearTop: Boolean = false,
        reorderToFront: Boolean = false
    ) {
        val intent = Intent(requireActivity(), activityClass)
        // If FLAG_ACTIVITY_CLEAR_TOP is not set,
        // it can interfere with Double-Back (press back twice) to exit
        if (clearTop) intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        //if (reorderToFront) intent.flags = intent.flags or Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
        if (bundle != null) intent.putExtras(bundle)
        requireContext().startActivity(intent)
        activity?.apply {
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                overridePendingTransition(0, 0)
            }
            EventBus.getDefault().post(CommunicationEvent(CommunicationEvent.Type.CLOSE_DRAWER))
        }
        if (reorderToFront) activity?.finish()
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
        text: CharSequence,
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

    private fun getMenu(m: Menu, navItem: NavItem, subItem: Int = 0): MenuItem? {
        return m.findItem(menuId(navItem, subItem))
    }

    override fun onSiteSelected(site: Site, altCode: Int) {
        launchBrowserFor(requireContext(), site)
    }
}