package me.devsaki.hentoid.activities

import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.isVisible
import androidx.customview.widget.ViewDragHelper
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.select.SelectExtension
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.SearchRecord
import me.devsaki.hentoid.databinding.ActivityLibraryBinding
import me.devsaki.hentoid.databinding.FragmentLibraryBinding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.AppUpdatedEvent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.library.LibraryArchiveDialogFragment
import me.devsaki.hentoid.fragments.library.LibraryBottomGroupsFragment
import me.devsaki.hentoid.fragments.library.LibraryBottomSortFilterFragment
import me.devsaki.hentoid.fragments.library.LibraryContentFragment
import me.devsaki.hentoid.fragments.library.LibraryGroupsFragment
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment.Companion.invoke
import me.devsaki.hentoid.ui.InputDialog
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.DebouncerK
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.LocaleHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.SearchHelper.AdvancedSearchCriteria
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.TooltipHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.FileHelper.MemoryUsageFigures
import me.devsaki.hentoid.util.file.PermissionHelper
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle
import me.devsaki.hentoid.widget.GroupSearchManager.GroupSearchBundle
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber

class LibraryActivity : BaseActivity() {

    // ======== COMMUNICATION
    // Viewmodel
    private lateinit var viewModel: LibraryViewModel

    // Settings listener
    private val prefsListener =
        OnSharedPreferenceChangeListener { _, k: String? ->
            onSharedPreferenceChanged(k)
        }


    // ======== UI
    private var activityBinding: ActivityLibraryBinding? = null
    private var binding: FragmentLibraryBinding? = null

    // Action view associated with search menu button
    private var actionSearchView: SearchView? = null

    // === Toolbar
    private var searchMenu: MenuItem? = null
    private var displayTypeMenu: MenuItem? = null
    private var reorderMenu: MenuItem? = null
    private var reorderConfirmMenu: MenuItem? = null
    private var reorderCancelMenu: MenuItem? = null
    private var newGroupMenu: MenuItem? = null
    private var sortMenu: MenuItem? = null

    // === Selection toolbar
    private var editMenu: MenuItem? = null
    private var deleteMenu: MenuItem? = null
    private var completedMenu: MenuItem? = null
    private var resetReadStatsMenu: MenuItem? = null
    private var shareMenu: MenuItem? = null
    private var archiveMenu: MenuItem? = null
    private var changeGroupMenu: MenuItem? = null
    private var folderMenu: MenuItem? = null
    private var redownloadMenu: MenuItem? = null
    private var downloadStreamedMenu: MenuItem? = null
    private var streamMenu: MenuItem? = null
    private var groupCoverMenu: MenuItem? = null
    private var mergeMenu: MenuItem? = null
    private var splitMenu: MenuItem? = null
    private var transformMenu: MenuItem? = null

    private var pagerAdapter: FragmentStateAdapter? = null


    // ======== DATA SYNC
    private val searchRecords: MutableList<SearchRecord> = ArrayList()

    // ======== INNER VARIABLES
    // Used to ignore native calls to onQueryTextChange
    private var invalidateNextQueryTextChange = false

    // TODO
    private var preventShowSearchHistoryNextExpand = false

    // TODO
    private var searchHistory: PowerMenu? = null

    // Current text search query; one per tab
    private val query = mutableListOf<String?>("", "")

    // Current metadata search query; one per tab
    private val advSearchCriteria = mutableListOf(
        AdvancedSearchCriteria(ArrayList(), "", ContentHelper.Location.ANY, ContentHelper.Type.ANY),
        AdvancedSearchCriteria(
            ArrayList(), "", ContentHelper.Location.ANY, ContentHelper.Type.ANY
        )
    )

    // True if item positioning edit mode is on (only available for specific groupings)
    private var editMode = false

    // Titles of each of the Viewpager2's tabs
    private val titles: MutableMap<Int, String> = HashMap()

    // TODO doc
    private var group: Group? = null

    // TODO doc
    private var grouping = Preferences.getGroupingDisplay()

    // TODO doc
    private var contentSearchBundle: Bundle? = null

    // TODO doc
    private var groupSearchBundle: Bundle? = null

    // Used to avoid closing search panel immediately when user uses backspace to correct what he typed
    private lateinit var searchClearDebouncer: DebouncerK<Int>


    // === PUBLIC ACCESSORS (to be used by fragments)
    fun getSelectionToolbar(): Toolbar? {
        return binding?.librarySelectionToolbar
    }

    fun getQuery(): String {
        return query[getCurrentFragmentIndex()] ?: ""
    }

    fun setQuery(query: String?) {
        this.query[getCurrentFragmentIndex()] = query
    }

    fun getAdvSearchCriteria(): AdvancedSearchCriteria {
        return advSearchCriteria[getCurrentFragmentIndex()]
    }

    fun setAdvancedSearchCriteria(criteria: AdvancedSearchCriteria) {
        advSearchCriteria[getCurrentFragmentIndex()] = criteria
    }

    fun clearAdvancedSearchCriteria() {
        advSearchCriteria[getCurrentFragmentIndex()] = AdvancedSearchCriteria()
    }

    fun isEditMode(): Boolean {
        return editMode
    }

    fun setEditMode(editMode: Boolean) {
        this.editMode = editMode
        signalFragment(1, CommunicationEvent.EV_UPDATE_EDIT_MODE, "")
        updateToolbar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityBinding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        searchClearDebouncer = DebouncerK(this.lifecycleScope, 1500) { clearSearch() }

        activityBinding?.drawerLayout?.let {
            it.addDrawerListener(object : ActionBarDrawerToggle(
                this,
                activityBinding?.drawerLayout,
                binding?.libraryToolbar,
                R.string.open_drawer,
                R.string.close_drawer
            ) {
                /** Called when a drawer has settled in a completely closed state.  */
                override fun onDrawerClosed(view: View) {
                    super.onDrawerClosed(view)
                    EventBus.getDefault().post(
                        CommunicationEvent(
                            CommunicationEvent.EV_CLOSED,
                            CommunicationEvent.RC_DRAWER,
                            ""
                        )
                    )
                }
            })

            // Hack DrawerLayout to make the drag zone larger
            // Source : https://stackoverflow.com/a/36157701/8374722
            try {
                // get dragger responsible for the dragging of the left drawer
                val draggerField = DrawerLayout::class.java.getDeclaredField("mLeftDragger")
                draggerField.isAccessible = true
                val vdh = draggerField[it] as ViewDragHelper

                // get access to the private field which defines
                // how far from the edge dragging can start
                val edgeSizeField = ViewDragHelper::class.java.getDeclaredField("mEdgeSize")
                edgeSizeField.isAccessible = true

                // increase the edge size - while x2 should be good enough,
                // try bigger values to easily see the difference
                val origEdgeSizeInt = edgeSizeField.getInt(vdh)
                val newEdgeSize = origEdgeSizeInt * 2
                edgeSizeField.setInt(vdh, newEdgeSize)
                Timber.d("Left drawer : new drag size of %d pixels", newEdgeSize)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            openNavigationDrawer()
            Preferences.setIsFirstRunProcessComplete(true)
        }
        val vmFactory = ViewModelFactory(application)
        viewModel =
            ViewModelProvider(this@LibraryActivity, vmFactory)[LibraryViewModel::class.java]

        viewModel.contentSearchManagerBundle.observe(this)
        { b: Bundle? -> contentSearchBundle = b }

        viewModel.group.observe(this) { g: Group? ->
            group = g
            updateToolbar()
        }

        viewModel.groupSearchManagerBundle.observe(this) { b: Bundle? ->
            groupSearchBundle = b
            val searchBundle = GroupSearchBundle(b!!)
            onGroupingChanged(searchBundle.groupingId)
        }

        viewModel.searchRecords.observe(this) { records: List<SearchRecord>? ->
            searchRecords.clear()
            searchRecords.addAll(records!!)
        }

        if (!Preferences.getRecentVisibility()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        Preferences.registerPrefsChangedListener(prefsListener)
        pagerAdapter = LibraryPagerAdapter(this)
        initToolbar()
        initSelectionToolbar()
        initUI()
        updateToolbar()
        updateSelectionToolbar(0, 0, 0, 0, 0, 0)
        onCreated()

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onAppUpdated(event: AppUpdatedEvent?) {
        EventBus.getDefault().removeStickyEvent(event)
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) invoke(this)
    }

    override fun onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener)
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)

        // Empty all handlers to avoid leaks
        binding?.libraryToolbar?.setOnMenuItemClickListener(null)
        binding?.librarySelectionToolbar?.apply {
            setOnMenuItemClickListener(null)
            setNavigationOnClickListener(null)
        }
        binding = null
        activityBinding = null
        super.onDestroy()
    }

    override fun onRestart() {
        // Change locale if set manually
        LocaleHelper.convertLocaleToEnglish(this)
        super.onRestart()
    }

    private fun onCreated() {
        // Display search bar tooltip _after_ the left drawer closes (else it displays over it)
        binding?.let {
            if (Preferences.isFirstRunProcessComplete()) TooltipHelper.showTooltip(
                this, R.string.help_search, ArrowOrientation.TOP,
                it.libraryToolbar, this
            )
            updateAlertBanner()
        }
    }

    override fun onStart() {
        super.onStart()
        val previouslyViewedContent = Preferences.getReaderCurrentContent()
        val previouslyViewedPage = Preferences.getReaderCurrentPageNum()
        if (previouslyViewedContent > -1 && previouslyViewedPage > -1 && !ReaderActivity.isRunning()) {
            binding?.let {
                val snackbar: Snackbar =
                    Snackbar.make(
                        it.libraryPager,
                        R.string.resume_closed,
                        BaseTransientBottomBar.LENGTH_LONG
                    )
                snackbar.setAction(R.string.resume) {
                    Timber.i(
                        "Reopening book %d from page %d",
                        previouslyViewedContent,
                        previouslyViewedPage
                    )
                    val dao: CollectionDAO = ObjectBoxDAO(this)
                    try {
                        val c = dao.selectContent(previouslyViewedContent)
                        if (c != null) ContentHelper.openReader(
                            this,
                            c,
                            previouslyViewedPage,
                            contentSearchBundle,
                            false,
                            false
                        )
                    } finally {
                        dao.cleanup()
                    }
                }
                snackbar.show()
            }
            // Only show that once
            Preferences.setReaderCurrentContent(-1)
            Preferences.setReaderCurrentPageNum(-1)
        }
    }

    /**
     * Initialize the UI components
     */
    private fun initUI() {
        // Search bar
        binding?.searchSubbar?.apply {
            // Link to advanced search
            advancedSearchBtn.setOnClickListener { onAdvancedSearchButtonClick() }

            // Save search
            searchSaveBtn.setOnClickListener { saveSearchAsGroup() }

            // Clear search
            searchClearBtn.setOnClickListener {
                setQuery("")
                clearAdvancedSearchCriteria()
                actionSearchView!!.setQuery("", false)
                hideSearchSubBar()
                signalCurrentFragment(CommunicationEvent.EV_SEARCH, "")
            }

            // Main tabs
            binding?.libraryPager?.apply {
                isUserInputEnabled = false // Disable swipe to change tabs
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        enableCurrentFragment()
                        hideSearchSubBar()
                        updateToolbar()
                        updateSelectionToolbar(0, 0, 0, 0, 0, 0)
                    }
                })
                adapter = pagerAdapter
            }
            updateDisplay(Preferences.getGroupingDisplay().id)
        }
    }

    private fun updateAlertBanner() {
        binding?.alertBanner?.apply {
            // Remind user that the app is in browser mode
            if (Preferences.isBrowserMode()) {
                libraryAlertTxt.setText(R.string.alert_browser_mode)
                libraryAlertTxt.visibility = View.VISIBLE
                libraryAlertIcon.visibility = View.GONE
                libraryAlertFixBtn.visibility = View.GONE
            } else if (!PermissionHelper.checkExternalStorageReadWritePermission(this@LibraryActivity)) { // Warn about permissions being lost
                libraryAlertTxt.setText(R.string.alert_permissions_lost)
                libraryAlertTxt.visibility = View.VISIBLE
                libraryAlertIcon.visibility = View.VISIBLE
                libraryAlertFixBtn.setOnClickListener { fixPermissions() }
                libraryAlertFixBtn.visibility = View.VISIBLE
            } else if (!PermissionHelper.checkNotificationPermission(this@LibraryActivity)) { // Warn about notiftications not being enabled
                libraryAlertTxt.setText(R.string.alert_notifications)
                libraryAlertTxt.visibility = View.VISIBLE
                libraryAlertIcon.visibility = View.VISIBLE
                libraryAlertFixBtn.setOnClickListener { fixNotifications() }
                libraryAlertFixBtn.visibility = View.VISIBLE
            } else if (isLowOnSpace()) { // Display low space alert
                libraryAlertTxt.setText(R.string.alert_low_memory)
                libraryAlertTxt.visibility = View.VISIBLE
                libraryAlertIcon.visibility = View.VISIBLE
                libraryAlertFixBtn.visibility = View.GONE
            } else {
                libraryAlertTxt.visibility = View.GONE
                libraryAlertIcon.visibility = View.GONE
                libraryAlertFixBtn.visibility = View.GONE
            }
        }
    }

    private fun updateDisplay(targetGroupingId: Int) {
        pagerAdapter?.notifyDataSetChanged()
        if (targetGroupingId == Grouping.FLAT.id) { // Display books right away
            binding?.libraryPager?.currentItem = 1
        }
        enableCurrentFragment()
    }

    private fun initToolbar() {
        binding?.apply {
            searchMenu = libraryToolbar.menu.findItem(R.id.action_search)
            searchMenu?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    showSearchSubBar(true, null, null, !preventShowSearchHistoryNextExpand)
                    preventShowSearchHistoryNextExpand = false
                    invalidateNextQueryTextChange = true

                    // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                    if (getQuery().isNotEmpty()) // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                        Handler(Looper.getMainLooper()).postDelayed({
                            invalidateNextQueryTextChange = true
                            actionSearchView!!.setQuery(getQuery(), false)
                        }, 100)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    if (!isSearchQueryActive()) {
                        hideSearchSubBar()
                    }
                    invalidateNextQueryTextChange = true
                    return true
                }
            })
            displayTypeMenu = libraryToolbar.menu.findItem(R.id.action_display_type)
            if (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay())
                displayTypeMenu?.setIcon(R.drawable.ic_view_gallery)
            else displayTypeMenu?.setIcon(R.drawable.ic_view_list)
            reorderMenu = libraryToolbar.menu.findItem(R.id.action_edit)
            reorderCancelMenu = libraryToolbar.menu.findItem(R.id.action_edit_cancel)
            reorderConfirmMenu = libraryToolbar.menu.findItem(R.id.action_edit_confirm)
            newGroupMenu = libraryToolbar.menu.findItem(R.id.action_group_new)
            sortMenu = libraryToolbar.menu.findItem(R.id.action_sort_filter)
            actionSearchView = searchMenu?.actionView as SearchView?
            actionSearchView?.apply {
                imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
                setIconifiedByDefault(true)
                queryHint = getString(R.string.library_search_hint)
                searchSubbar.searchClearBtn.setOnClickListener {
                    invalidateNextQueryTextChange = true
                    setQuery("", false)
                    isIconified = true
                    clearSearch() // Immediately; don't use the debouncer
                }

                // Change display when text query is typed
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(s: String): Boolean {
                        setQuery(s.trim { it <= ' ' })
                        signalCurrentFragment(CommunicationEvent.EV_SEARCH, query.toString())
                        clearFocus()
                        return true
                    }

                    override fun onQueryTextChange(s: String): Boolean {
                        if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                            invalidateNextQueryTextChange = false
                        } else if (s.isEmpty()) {
                            searchClearDebouncer.submit(1)
                        } else searchClearDebouncer.clear()
                        return true
                    }
                })
            }
        }
    }

    private fun clearSearch() {
        setQuery("")
        getAdvSearchCriteria().query = ""
        signalCurrentFragment(CommunicationEvent.EV_SEARCH, getQuery())
        binding?.searchSubbar?.apply {
            searchClearBtn.visibility = View.GONE
            searchSaveBtn.visibility = View.GONE
        }
    }

    fun initFragmentToolbars(
        selectExtension: SelectExtension<*>,
        toolbarOnItemClicked: Toolbar.OnMenuItemClickListener,
        selectionToolbarOnItemClicked: Toolbar.OnMenuItemClickListener
    ) {
        binding?.apply {
            libraryToolbar.setOnMenuItemClickListener(toolbarOnItemClicked)
            librarySelectionToolbar.setOnMenuItemClickListener(selectionToolbarOnItemClicked)
            librarySelectionToolbar.setNavigationOnClickListener {
                selectExtension.deselect(selectExtension.selections.toMutableSet())
                librarySelectionToolbar.visibility = View.GONE
            }
        }
    }

    fun updateSearchBarOnResults(nonEmptyResults: Boolean) {
        actionSearchView?.let {
            if (isSearchQueryActive()) {
                if (getQuery().isNotEmpty()) {
                    it.setQuery(getQuery(), false)
                    expandSearchMenu()
                } else if (nonEmptyResults) {
                    collapseSearchMenu()
                }
                showSearchSubBar(
                    !isGroupDisplayed(),
                    true,
                    !getAdvSearchCriteria().isEmpty(),
                    false
                )
            } else {
                collapseSearchMenu()
                if (it.query.isNotEmpty()) actionSearchView!!.setQuery("", false)
                hideSearchSubBar()
            }
        }
    }

    /**
     * Callback method used when a sort method is selected in the sort drop-down menu
     * Updates the UI according to the chosen sort method
     *
     * @param menuItem Toolbar of the fragment
     */
    fun toolbarOnItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_display_type -> {
                var displayType = Preferences.getLibraryDisplay()
                displayType =
                    if (Preferences.Constant.LIBRARY_DISPLAY_LIST == displayType) Preferences.Constant.LIBRARY_DISPLAY_GRID else Preferences.Constant.LIBRARY_DISPLAY_LIST
                Preferences.setLibraryDisplay(displayType)
            }

            R.id.action_browse_groups -> LibraryBottomGroupsFragment.invoke(
                this,
                this.supportFragmentManager
            )

            R.id.action_sort_filter -> LibraryBottomSortFilterFragment.invoke(
                this, this.supportFragmentManager, isGroupDisplayed(),
                group != null && group!!.grouping == Grouping.CUSTOM && 1 == group!!.getSubtype()
            )

            else -> return false
        }
        return true
    }

    private fun showSearchSubBar(
        showAdvancedSearch: Boolean,
        showClear: Boolean?,
        showSaveSearch: Boolean?,
        showSearchHistory: Boolean
    ) {
        binding?.searchSubbar?.apply {
            advancedSearchBackground.visibility = View.VISIBLE
            advancedSearchBtn.isVisible = (showAdvancedSearch && !isGroupDisplayed())
            if (showClear != null) searchClearBtn.isVisible = showClear
            if (showSaveSearch != null) searchSaveBtn.isVisible =
                (showSaveSearch && !isGroupDisplayed())
        }

        if (showSearchHistory && searchRecords.isNotEmpty()) {
            val powerMenuBuilder = PowerMenu.Builder(this).setAnimation(MenuAnimation.DROP_DOWN)
                .setLifecycleOwner(this).setTextColor(
                    ContextCompat.getColor(this, R.color.white_opacity_87)
                ).setTextTypeface(Typeface.DEFAULT).setShowBackground(false).setWidth(
                    resources.getDimension(R.dimen.dialog_width).toInt()
                ).setMenuColor(ContextCompat.getColor(this, R.color.medium_gray)).setTextSize(
                    Helper.dimensAsDp(this, R.dimen.text_subtitle_2)
                ).setAutoDismiss(true)
            for (i in searchRecords.indices.reversed()) powerMenuBuilder.addItem(
                PowerMenuItem(
                    searchRecords[i].label,
                    false,
                    R.drawable.ic_clock,
                    null,
                    null,
                    searchRecords[i]
                )
            )
            powerMenuBuilder.addItem(
                PowerMenuItem(
                    resources.getString(R.string.clear_search_history),
                    false
                )
            )
            searchHistory = powerMenuBuilder.build()
            searchHistory?.apply {
                setOnMenuItemClickListener { _: Int, (_, _, _, _, _, tag): PowerMenuItem ->
                    if (tag != null) { // Tap on search record
                        val record = tag as SearchRecord?
                        val searchUri = Uri.parse(record!!.searchString)
                        var targetQuery = searchUri.path
                        if (targetQuery!!.isNotEmpty()) targetQuery =
                            targetQuery.substring(1) // Remove the leading '/'
                        setQuery(targetQuery)
                        setAdvancedSearchCriteria(SearchActivityBundle.parseSearchUri(searchUri))
                        if (getAdvSearchCriteria().isEmpty()) { // Universal search
                            if (getQuery().isNotEmpty()) viewModel.searchContentUniversal(getQuery())
                        } else { // Advanced search
                            viewModel.searchContent(
                                getQuery(),
                                getAdvSearchCriteria(),
                                searchUri
                            )
                        }
                    } else { // Clear history
                        val builder = MaterialAlertDialogBuilder(this@LibraryActivity)
                        builder.setMessage(resources.getString(R.string.clear_search_history_confirm))
                            .setPositiveButton(R.string.yes) { _, _ -> viewModel.clearSearchHistory() }
                            .setNegativeButton(R.string.no) { _, _ -> }.create().show()
                    }
                }
                setIconColor(
                    ContextCompat.getColor(
                        this@LibraryActivity,
                        R.color.white_opacity_87
                    )
                )
                showAsDropDown(binding?.searchSubbar?.advancedSearchBackground)
            }
        } else if (!showSearchHistory) searchHistory?.dismiss()

    }

    fun hideSearchSubBar() {
        binding?.searchSubbar?.apply {
            advancedSearchBackground.visibility = View.GONE
            advancedSearchBtn.visibility = View.GONE
            searchClearBtn.visibility = View.GONE
            searchSaveBtn.visibility = View.GONE
        }
        searchHistory?.dismiss()
    }

    fun closeLeftDrawer(): Boolean {
        activityBinding?.apply {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
        }
        return false
    }

    fun collapseSearchMenu(): Boolean {
        searchMenu?.apply {
            if (isActionViewExpanded) {
                collapseActionView()
                return true
            }
        }
        return false
    }

    fun expandSearchMenu() {
        searchMenu?.apply {
            if (isActionViewExpanded) {
                preventShowSearchHistoryNextExpand = true
                expandActionView()
            }
        }
    }

    private fun initSelectionToolbar() {
        binding?.librarySelectionToolbar?.apply {
            menu.clear()
            inflateMenu(R.menu.library_selection_menu)
            Helper.tryShowMenuIcons(this@LibraryActivity, menu)
            menu.apply {
                editMenu = findItem(R.id.action_edit)
                deleteMenu = findItem(R.id.action_delete)
                completedMenu = findItem(R.id.action_completed)
                resetReadStatsMenu = findItem(R.id.action_reset_read)
                shareMenu = findItem(R.id.action_share)
                archiveMenu = findItem(R.id.action_archive)
                changeGroupMenu = findItem(R.id.action_change_group)
                folderMenu = findItem(R.id.action_open_folder)
                redownloadMenu = findItem(R.id.action_redownload)
                downloadStreamedMenu = findItem(R.id.action_download)
                streamMenu = findItem(R.id.action_stream)
                groupCoverMenu = findItem(R.id.action_set_group_cover)
                mergeMenu = findItem(R.id.action_merge)
                splitMenu = findItem(R.id.action_split)
                transformMenu = findItem(R.id.action_transform)
            }
        }
    }

    /**
     * Callback for any change in Preferences
     */
    private fun onSharedPreferenceChanged(key: String?) {
        Timber.i("Prefs change detected : %s", key)
        when (key) {
            Preferences.Key.COLOR_THEME, Preferences.Key.LIBRARY_DISPLAY -> {
                // Restart the app with the library activity on top
                val intent = Intent(this, LibraryActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                finish()
                startActivity(intent)
            }

            Preferences.Key.PRIMARY_STORAGE_URI, Preferences.Key.EXTERNAL_LIBRARY_URI -> {
                updateDisplay(Grouping.FLAT.id)
                viewModel.setGrouping(Grouping.FLAT.id)
            }

            Preferences.Key.BROWSER_MODE -> updateAlertBanner()
            else -> {}
        }
    }

    /**
     * Handler for the "Advanced search" button
     */
    private fun onAdvancedSearchButtonClick() {
        signalCurrentFragment(CommunicationEvent.EV_ADVANCED_SEARCH, null)
    }

    private fun onGroupingChanged(targetGroupingId: Int) {
        val targetGrouping = Grouping.searchById(targetGroupingId)
        if (grouping.id != targetGroupingId) {
            // Reset custom book ordering if reverting to a grouping where that doesn't apply
            if (!targetGrouping.canReorderBooks() && Preferences.Constant.ORDER_FIELD_CUSTOM == Preferences.getContentSortField()) {
                Preferences.setContentSortField(Preferences.Default.ORDER_CONTENT_FIELD)
            }
            // Reset custom group ordering if reverting to a grouping where that doesn't apply
            if (!targetGrouping.canReorderGroups() && Preferences.Constant.ORDER_FIELD_CUSTOM == Preferences.getGroupSortField()) {
                Preferences.setGroupSortField(Preferences.Default.ORDER_GROUP_FIELD)
            }

            // Go back to groups tab if we're not
            if (targetGroupingId != Grouping.FLAT.id) goBackToGroups()

            // Update screen display if needed (flat <-> the rest)
            if (grouping == Grouping.FLAT || targetGroupingId == Grouping.FLAT.id) updateDisplay(
                targetGroupingId
            )
            grouping = targetGrouping
            updateToolbar()
        }
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    fun updateTitle(totalSelectedCount: Long, totalCount: Long) {
        val title: String = if (totalSelectedCount == totalCount) resources.getQuantityString(
            R.plurals.number_of_items,
            totalSelectedCount.toInt(),
            totalSelectedCount.toInt()
        ) else {
            resources.getQuantityString(
                R.plurals.number_of_book_search_results,
                totalSelectedCount.toInt(),
                totalSelectedCount.toInt(),
                totalCount
            )
        }
        binding?.apply {
            libraryToolbar.title = title
            titles[libraryPager.currentItem] = title
        }
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    fun isSearchQueryActive(): Boolean {
        return getQuery().isNotEmpty() || !getAdvSearchCriteria().isEmpty()
    }

    private fun fixPermissions() {
        if (PermissionHelper.requestExternalStorageReadWritePermission(
                this,
                PermissionHelper.RQST_STORAGE_PERMISSION
            )
        ) updateAlertBanner()
    }

    private fun fixNotifications() {
        if (PermissionHelper.requestNotificationPermission(
                this,
                PermissionHelper.RQST_NOTIFICATION_PERMISSION
            )
        ) updateAlertBanner()
    }

    private fun isLowOnSpace(): Boolean {
        return isLowOnSpace(StorageLocation.PRIMARY_1) || isLowOnSpace(StorageLocation.PRIMARY_2)
    }

    private fun isLowOnSpace(location: StorageLocation): Boolean {
        val rootFolder =
            FileHelper.getDocumentFromTreeUriString(this, Preferences.getStorageUri(location))
                ?: return false
        val freeSpaceRatio = MemoryUsageFigures(this, rootFolder).freeUsageRatio100
        return freeSpaceRatio < 100 - Preferences.getMemoryAlertThreshold()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) return
        binding?.alertBanner?.apply {
            if (PermissionHelper.RQST_STORAGE_PERMISSION == requestCode) {
                if (permissions.size < 2) return
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    libraryAlertTxt.visibility = View.GONE
                    libraryAlertIcon.visibility = View.GONE
                    libraryAlertFixBtn.visibility = View.GONE
                } // Don't show rationales here; the alert still displayed on screen should be enough
            } else if (PermissionHelper.RQST_NOTIFICATION_PERMISSION == requestCode) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    libraryAlertTxt.visibility = View.GONE
                    libraryAlertIcon.visibility = View.GONE
                    libraryAlertFixBtn.visibility = View.GONE
                } // Don't show rationales here; the alert still displayed on screen should be enough
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun closeNavigationDrawer() {
        activityBinding?.drawerLayout?.closeDrawer(GravityCompat.START)
    }

    fun openNavigationDrawer() {
        activityBinding?.drawerLayout?.openDrawer(GravityCompat.START)
    }

    private fun isGroupDisplayed(): Boolean {
        return 0 == binding?.libraryPager?.currentItem
    }

    fun goBackToGroups() {
        if (isGroupDisplayed()) return
        enableFragment(0)
        setEditMode(false)

        // Reset any active Content filter
        viewModel.setContentFavouriteFilter(false)
        viewModel.setContentRatingFilter(-1)
        viewModel.setCompletedFilter(false)
        viewModel.setNotCompletedFilter(false)
        viewModel.searchGroup()
        binding?.apply {
            libraryPager.currentItem = 0
            if (titles.containsKey(0)) libraryToolbar.title = titles[0]
        }
    }

    fun showBooksInGroup(group: Group?) {
        enableFragment(1)
        viewModel.setGroup(group, true)
        binding?.libraryPager?.currentItem = 1
    }

    fun isFilterActive(): Boolean {
        if (isSearchQueryActive()) {
            setQuery("")
            clearAdvancedSearchCriteria()
            collapseSearchMenu()
            hideSearchSubBar()
        }
        if (isGroupDisplayed() && groupSearchBundle != null) {
            val bundle = GroupSearchBundle(groupSearchBundle!!)
            return bundle.isFilterActive()
        } else if (!isGroupDisplayed() && contentSearchBundle != null) {
            val bundle = ContentSearchBundle(contentSearchBundle!!)
            return bundle.isFilterActive()
        }
        return false
    }

    private fun updateToolbar() {
        val currentGrouping = Preferences.getGroupingDisplay()
        displayTypeMenu!!.isVisible = !editMode
        searchMenu!!.isVisible = !editMode
        newGroupMenu!!.isVisible =
            !editMode && isGroupDisplayed() && currentGrouping.canReorderGroups() // Custom groups only
        reorderConfirmMenu!!.isVisible = editMode
        reorderCancelMenu!!.isVisible = editMode
        sortMenu!!.isVisible = !editMode
        var isToolbarNavigationDrawer = true
        if (isGroupDisplayed()) {
            reorderMenu!!.isVisible = currentGrouping.canReorderGroups()
        } else {
            reorderMenu!!.isVisible =
                currentGrouping.canReorderBooks() && group != null && group!!.getSubtype() != 1
            isToolbarNavigationDrawer = currentGrouping == Grouping.FLAT
        }
        binding?.libraryToolbar?.apply {
            if (isToolbarNavigationDrawer) { // Open the left drawer
                setNavigationIcon(R.drawable.ic_drawer)
                setNavigationOnClickListener { openNavigationDrawer() }
            } else { // Go back to groups
                setNavigationIcon(R.drawable.ic_arrow_back)
                setNavigationOnClickListener { goBackToGroups() }
            }
        }
        signalCurrentFragment(CommunicationEvent.EV_UPDATE_TOOLBAR, null)
    }

    fun updateSelectionToolbar(
        selectedTotalCount: Long,
        selectedProcessedCount: Long,
        selectedLocalCount: Long,
        selectedStreamedCount: Long,
        selectedNonArchiveExternalCount: Long,
        selectedArchiveExternalCount: Long
    ) {
        val isMultipleSelection = selectedTotalCount > 1
        val hasProcessed = selectedProcessedCount > 0
        val selectedDownloadedCount = selectedLocalCount - selectedStreamedCount
        val selectedExternalCount = selectedNonArchiveExternalCount + selectedArchiveExternalCount
        binding?.librarySelectionToolbar?.title = resources.getQuantityString(
            R.plurals.items_selected,
            selectedTotalCount.toInt(),
            selectedTotalCount.toInt()
        )
        if (isGroupDisplayed()) {
            editMenu!!.isVisible =
                !hasProcessed && !isMultipleSelection && Preferences.getGroupingDisplay()
                    .canReorderGroups()
            deleteMenu!!.isVisible = !hasProcessed
            shareMenu!!.isVisible = false
            completedMenu!!.isVisible = false
            resetReadStatsMenu!!.isVisible = false
            archiveMenu!!.isVisible = !hasProcessed
            changeGroupMenu!!.isVisible = false
            folderMenu!!.isVisible = false
            redownloadMenu!!.isVisible = false
            downloadStreamedMenu!!.isVisible = false
            streamMenu!!.isVisible = false
            groupCoverMenu!!.isVisible = false
            mergeMenu!!.isVisible = false
            splitMenu!!.isVisible = false
            transformMenu!!.isVisible = false
        } else { // Flat view
            editMenu!!.isVisible = !hasProcessed
            deleteMenu!!.isVisible =
                !hasProcessed && ((selectedLocalCount > 0 || selectedStreamedCount > 0) && 0L == selectedExternalCount || selectedExternalCount > 0 && Preferences.isDeleteExternalLibrary())
            completedMenu!!.isVisible = true
            resetReadStatsMenu!!.isVisible = true
            shareMenu!!.isVisible = 0L == selectedArchiveExternalCount
            archiveMenu!!.isVisible = !hasProcessed
            changeGroupMenu!!.isVisible = !hasProcessed
            folderMenu!!.isVisible = !isMultipleSelection
            redownloadMenu!!.isVisible = !hasProcessed && selectedDownloadedCount > 0
            downloadStreamedMenu!!.isVisible = !hasProcessed && selectedStreamedCount > 0
            streamMenu!!.isVisible = !hasProcessed && selectedDownloadedCount > 0
            groupCoverMenu!!.isVisible =
                !isMultipleSelection && Preferences.getGroupingDisplay() != Grouping.FLAT
            // Can only merge downloaded, streamed or non-archive external content together
            mergeMenu!!.isVisible =
                !hasProcessed && (selectedLocalCount > 1 && 0L == selectedStreamedCount && 0L == selectedExternalCount || selectedStreamedCount > 1 && 0L == selectedLocalCount && 0L == selectedExternalCount || selectedNonArchiveExternalCount > 1 && 0L == selectedArchiveExternalCount && 0L == selectedLocalCount && 0L == selectedStreamedCount)
            splitMenu!!.isVisible =
                !hasProcessed && !isMultipleSelection && 1L == selectedLocalCount
            transformMenu!!.isVisible =
                !hasProcessed && 0L == selectedStreamedCount && 0L == selectedArchiveExternalCount
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param contents Items to be deleted if the answer is yes
     */
    fun askDeleteItems(
        contents: List<Content?>,
        groups: List<Group?>,
        onSuccess: Runnable?,
        selectExtension: SelectExtension<*>
    ) {
        val builder = MaterialAlertDialogBuilder(this)
        val count = if (groups.isNotEmpty()) groups.size else contents.size
        if (count > 1000) {
            // TODO provide a link to the mass-delete tool when it's ready (#992)
            snack(R.string.delete_limit)
        } else {
            val title = resources.getQuantityString(R.plurals.ask_delete_multiple, count, count)
            builder.setMessage(title).setPositiveButton(R.string.yes) { _, _ ->
                selectExtension.deselect(selectExtension.selections.toMutableSet())
                viewModel.deleteItems(contents, groups, false, onSuccess)
            }.setNegativeButton(R.string.no) { _, _ ->
                selectExtension.deselect(selectExtension.selections.toMutableSet())
            }
                .setOnCancelListener {
                    selectExtension.deselect(selectExtension.selections.toMutableSet())
                }.create().show()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return
        if (ProcessEvent.EventType.COMPLETE != event.eventType) return
        processEvent(event)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return
        if (ProcessEvent.EventType.COMPLETE != event.eventType) return
        EventBus.getDefault().removeStickyEvent(event)
        processEvent(event)
    }

    private fun processEvent(event: ProcessEvent) {
        var msg = ""
        val nbGroups = event.elementsOKOther
        val nbContent = event.elementsOK
        if (nbGroups > 0) msg += resources.getQuantityString(
            R.plurals.delete_success_groups,
            nbGroups,
            nbGroups
        )
        if (nbContent > 0) {
            if (msg.isNotEmpty()) msg += " & "
            msg += resources.getQuantityString(R.plurals.delete_success_books, nbContent, nbContent)
        }
        msg += " " + resources.getString(R.string.delete_success)
        snack(msg)
    }


    /**
     * Display the yes/no dialog to make sure the user really wants to archive selected items
     *
     * @param items Items to be archived if the answer is yes
     */
    fun askArchiveItems(items: List<Content>, selectExtension: SelectExtension<*>) {
        if (items.size > 1000) {
            snack(R.string.archive_limit)
            return
        }
        if (items.isEmpty()) {
            snack(R.string.invalid_selection_generic)
            return
        }
        selectExtension.deselect(selectExtension.selections.toMutableSet())
        LibraryArchiveDialogFragment.invoke(this, items)
    }

    private fun signalCurrentFragment(eventType: Int, message: String?) {
        signalFragment(getCurrentFragmentIndex(), eventType, message)
    }

    private fun signalFragment(fragmentIndex: Int, eventType: Int, message: String?) {
        EventBus.getDefault().post(
            CommunicationEvent(
                eventType,
                if (0 == fragmentIndex) CommunicationEvent.RC_GROUPS else CommunicationEvent.RC_CONTENTS,
                message
            )
        )
    }

    private fun enableCurrentFragment() {
        enableFragment(getCurrentFragmentIndex())
    }

    private fun getCurrentFragmentIndex(): Int {
        return if (isGroupDisplayed()) 0 else 1
    }

    private fun enableFragment(fragmentIndex: Int) {
        EventBus.getDefault().post(
            CommunicationEvent(
                CommunicationEvent.EV_ENABLE,
                if (0 == fragmentIndex) CommunicationEvent.RC_GROUPS else CommunicationEvent.RC_CONTENTS,
                null
            )
        )
        EventBus.getDefault().post(
            CommunicationEvent(
                CommunicationEvent.EV_DISABLE,
                if (0 == fragmentIndex) CommunicationEvent.RC_CONTENTS else CommunicationEvent.RC_GROUPS,
                null
            )
        )
    }

    private fun saveSearchAsGroup() {
        val criteria = getAdvSearchCriteria()
        InputDialog.invokeInputDialog(
            this,
            R.string.group_new_name_dynamic,
            criteria.toString(this),
            { s: String? ->
                viewModel.newGroup(
                    Grouping.DYNAMIC,
                    s!!, SearchActivityBundle.buildSearchUri(criteria, null).toString()
                ) { onNewSearchGroupNameExists() }
            },
            null
        )
    }

    private fun onNewSearchGroupNameExists() {
        ToastHelper.toast(R.string.group_name_exists)
        saveSearchAsGroup()
    }

    private fun snack(res: Int) {
        snack(resources.getString(res))
    }

    private fun snack(msg: String) {
        binding?.libraryPager?.let {
            Snackbar.make(it, msg, BaseTransientBottomBar.LENGTH_LONG).show()
        }
    }

    /**
     * ============================== SUBCLASS
     */
    private class LibraryPagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return if (0 == position) {
                LibraryGroupsFragment()
            } else {
                LibraryContentFragment()
            }
        }

        override fun getItemCount(): Int {
            return 2
        }
    }

    companion object {
        @StringRes
        fun getNameFromFieldCode(prefFieldCode: Int): Int {
            return when (prefFieldCode) {
                Preferences.Constant.ORDER_FIELD_TITLE -> R.string.sort_title
                Preferences.Constant.ORDER_FIELD_ARTIST -> R.string.sort_artist
                Preferences.Constant.ORDER_FIELD_NB_PAGES -> R.string.sort_pages
                Preferences.Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE -> R.string.sort_dl_date
                Preferences.Constant.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE -> R.string.sort_dl_completion_date
                Preferences.Constant.ORDER_FIELD_UPLOAD_DATE -> R.string.sort_uplodad_date
                Preferences.Constant.ORDER_FIELD_READ_DATE -> R.string.sort_read_date
                Preferences.Constant.ORDER_FIELD_READS -> R.string.sort_reads
                Preferences.Constant.ORDER_FIELD_SIZE -> R.string.sort_size
                Preferences.Constant.ORDER_FIELD_READ_PROGRESS -> R.string.sort_reading_progress
                Preferences.Constant.ORDER_FIELD_CUSTOM -> R.string.sort_custom
                Preferences.Constant.ORDER_FIELD_RANDOM -> R.string.sort_random
                Preferences.Constant.ORDER_FIELD_CHILDREN -> R.string.sort_books
                else -> R.string.sort_invalid
            }
        }
    }
}