package me.devsaki.hentoid.activities

import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.select.SelectExtension
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.LibraryActivityBundle
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle
import me.devsaki.hentoid.core.convertLocaleToEnglish
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.SearchRecord
import me.devsaki.hentoid.databinding.ActivityLibraryBinding
import me.devsaki.hentoid.databinding.FragmentLibraryBinding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.events.AppUpdatedEvent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.library.LibraryArchiveDialogFragment
import me.devsaki.hentoid.fragments.library.LibraryBottomGroupsFragment
import me.devsaki.hentoid.fragments.library.LibraryBottomSortFilterFragment
import me.devsaki.hentoid.fragments.library.LibraryContentFragment
import me.devsaki.hentoid.fragments.library.LibraryFoldersFragment
import me.devsaki.hentoid.fragments.library.LibraryGroupsFragment
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment.Companion.invoke
import me.devsaki.hentoid.ui.invokeInputDialog
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Location
import me.devsaki.hentoid.util.SearchCriteria
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Type
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.file.RQST_NOTIFICATION_PERMISSION
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.checkExternalStorageReadWritePermission
import me.devsaki.hentoid.util.file.checkNotificationPermission
import me.devsaki.hentoid.util.file.isLowDeviceStorage
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.util.file.requestNotificationPermission
import me.devsaki.hentoid.util.openReader
import me.devsaki.hentoid.util.runExternalImport
import me.devsaki.hentoid.util.showTooltip
import me.devsaki.hentoid.util.snack
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.tryShowMenuIcons
import me.devsaki.hentoid.viewholders.TextItem
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle
import me.devsaki.hentoid.widget.FolderSearchManager
import me.devsaki.hentoid.widget.GroupSearchManager.GroupSearchBundle
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.time.Instant

private const val BEHOLDER_DELAY_MS = 2 * 60 * 1000 // 2 min

class LibraryActivity : BaseActivity(), LibraryArchiveDialogFragment.Parent {

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

    // List / grid view
    private var displayTypeMenu: MenuItem? = null

    // Grid size selection menu
    private val gridSizeItemAdapter = ItemAdapter<TextItem<Int>>()
    private val gridSizefastAdapter = FastAdapter.with(gridSizeItemAdapter)

    // Reorder books (only when inside a group that allows it)
    private var reorderMenu: MenuItem? = null
    private var reorderConfirmMenu: MenuItem? = null
    private var reorderCancelMenu: MenuItem? = null
    private var newGroupMenu: MenuItem? = null
    private var sortMenu: MenuItem? = null

    // === Selection toolbar
    private var editMenu: MenuItem? = null
    private var deleteMenu: MenuItem? = null
    private var detachMenu: MenuItem? = null
    private var refreshMenu: MenuItem? = null
    private var completedMenu: MenuItem? = null
    private var resetReadStatsMenu: MenuItem? = null
    private var rateMenu: MenuItem? = null
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
    private var exportMetaMenu: MenuItem? = null

    private var pagerAdapter: FragmentStateAdapter? = null


    // ======== DATA SYNC
    private val searchRecords: MutableList<SearchRecord> = ArrayList()

    // ======== INNER VARIABLES
    // Used to ignore native calls to onQueryTextChange
    private var invalidateNextQueryTextChange = false

    // Used to prevent search history to show up when unneeded
    private var preventShowSearchHistoryNextExpand = false

    // Menu for search history; useful to dismiss when search bar is dismissed
    private var searchHistory: PowerMenu? = null

    // True if display settings have been changed
    private var hasChangedDisplaySettings = false

    // Current search criteria; one per tab
    private val searchCriteria = mutableListOf(
        SearchCriteria("", HashSet(), Location.ANY, Type.ANY),
        SearchCriteria("", HashSet(), Location.ANY, Type.ANY),
        SearchCriteria("", HashSet(), Location.ANY, Type.ANY)
    )

    // True if item positioning edit mode is on (only available for specific groupings)
    private var editMode = false

    // Titles of each of the Viewpager2's tabs
    private val titles: MutableMap<Int, String> = HashMap()

    // Current group
    private var group: Group? = null

    // Current grouping
    private var grouping = Settings.getGroupingDisplayG()

    // Current Content search query
    private var contentSearchBundle: Bundle? = null

    // Current Group search query
    private var groupSearchBundle: Bundle? = null

    // Current Folder  search query
    private var folderSearchBundle: Bundle? = null

    // Used to avoid closing search panel immediately when user uses backspace to correct what he typed
    private lateinit var searchClearDebouncer: Debouncer<Int>


    // === PUBLIC ACCESSORS (to be used by fragments)
    fun getSelectionToolbar(): Toolbar? {
        return binding?.selectionToolbar
    }

    fun getQuery(): String {
        return getSearchCriteria().query
    }

    fun setQuery(query: String) {
        getSearchCriteria().query = query
    }

    fun getSearchCriteria(): SearchCriteria {
        return searchCriteria[getCurrentFragmentIndex()]
    }

    fun setAdvancedSearchCriteria(criteria: SearchCriteria) {
        searchCriteria[getCurrentFragmentIndex()] = criteria
    }

    private fun clearAdvancedSearchCriteria() {
        searchCriteria[getCurrentFragmentIndex()] = SearchCriteria()
    }

    fun isEditMode(): Boolean {
        return editMode
    }

    fun setEditMode(editMode: Boolean) {
        this.editMode = editMode
        signalFragment(1, CommunicationEvent.Type.UPDATE_EDIT_MODE, "")
        updateToolbar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityBinding = ActivityLibraryBinding.inflate(layoutInflater)
        binding = activityBinding?.fragment
        setContentView(activityBinding?.root)

        searchClearDebouncer = Debouncer(this.lifecycleScope, 1500) { clearSearch() }

        activityBinding?.drawerLayout?.let {
            it.addDrawerListener(object : ActionBarDrawerToggle(
                this,
                activityBinding?.drawerLayout,
                binding?.toolbar,
                R.string.open_drawer,
                R.string.close_drawer
            ) {
                /** Called when a drawer has settled in a completely closed state.  */
                override fun onDrawerClosed(view: View) {
                    super.onDrawerClosed(view)
                    EventBus.getDefault().post(
                        CommunicationEvent(
                            CommunicationEvent.Type.CLOSED,
                            CommunicationEvent.Recipient.DRAWER
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
        if (!Settings.isFirstRunProcessComplete) {
            // first run of the app starts with the nav drawer open
            openNavigationDrawer()
            Settings.isFirstRunProcessComplete = true
        }
        val vmFactory = ViewModelFactory(application)
        viewModel =
            ViewModelProvider(this@LibraryActivity, vmFactory)[LibraryViewModel::class.java]

        viewModel.contentSearchBundle.observe(this) { contentSearchBundle = it }
        viewModel.groupSearchBundle.observe(this) { groupSearchBundle = it }
        viewModel.folderSearchBundle.observe(this) { folderSearchBundle = it }

        viewModel.group.observe(this) { g: Group? ->
            group = g
            updateToolbar()
        }

        viewModel.searchRecords.observe(this)
        { records ->
            searchRecords.clear()
            searchRecords.addAll(records)
        }

        if (!Settings.recentVisibility) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        Settings.registerPrefsChangedListener(prefsListener)
        pagerAdapter = LibraryPagerAdapter(this)
        initToolbar()
        initSelectionToolbar()
        initUI()
        updateToolbar()
        updateSelectionToolbar(0, 0, 0, 0, 0, 0)
        onCreated(intent.extras)

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onAppUpdated(event: AppUpdatedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) invoke(this)
    }

    override fun onDestroy() {
        Settings.unregisterPrefsChangedListener(prefsListener)
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)

        // Empty all handlers to avoid leaks
        binding?.toolbar?.setOnMenuItemClickListener(null)
        binding?.selectionToolbar?.apply {
            setOnMenuItemClickListener(null)
            setNavigationOnClickListener(null)
        }
        binding = null
        activityBinding = null
        super.onDestroy()
    }

    override fun onRestart() {
        // Change locale if set manually
        this.convertLocaleToEnglish()
        super.onRestart()
    }

    private fun onCreated(startBundle: Bundle?) {
        // Display search bar tooltip _after_ the left drawer closes (else it displays over it)
        binding?.let {
            if (Settings.isFirstRunProcessComplete) this.showTooltip(
                R.string.help_search, ArrowOrientation.TOP,
                it.toolbar, this
            )
            updateAlertBanner()
            if (hasChangedGridDisplay) {
                it.gridSizeBanner.root.alpha = 1f
                it.gridSizeBanner.root.isVisible = true
                hasChangedGridDisplay = false
                // Fade away after 3s
                Debouncer<Int>(
                    this.lifecycleScope,
                    3000
                ) {
                    binding?.gridSizeBanner?.root?.apply {
                        animate()
                            .alpha(0f)
                            .setDuration(1000)
                            .setListener(null)
                        isVisible = false
                    }
                }.submit(1)
            }
        }

        // Reset search filters to those transmitted through the Activity start Intent
        if (startBundle != null && !startBundle.isEmpty) {
            LibraryActivityBundle(startBundle).apply {
                contentSearchArgs?.let {
                    ContentSearchBundle(it).apply {
                        // Apply search filters
                        if (filterBookFavourites) viewModel.setContentFavouriteFilter(true)
                        if (filterBookNonFavourites) viewModel.setContentNonFavouriteFilter(true)
                        if (filterBookCompleted) viewModel.setCompletedFilter(true)
                        if (filterBookNotCompleted) viewModel.setNotCompletedFilter(true)
                        if (filterRating > -1) viewModel.setContentRatingFilter(filterRating)
                    }
                }
                groupSearchArgs?.let {
                    GroupSearchBundle(it).apply {
                        // Apply search filters
                        if (filterFavourites) viewModel.setContentFavouriteFilter(true)
                        if (filterNonFavourites) viewModel.setContentNonFavouriteFilter(true)
                        if (filterRating > -1) viewModel.setContentRatingFilter(filterRating)
                    }
                }

            }
        }
    }

    override fun onStart() {
        super.onStart()
        considerResumeReading()
        considerRefreshExtLib()
    }

    private fun considerResumeReading() {
        val previouslyViewedContent = Settings.readerCurrentContent
        if (previouslyViewedContent > -1 && !ReaderActivity.isRunning()) {
            binding?.let {
                val snackbar: Snackbar =
                    Snackbar.make(
                        it.libraryPager,
                        R.string.resume_closed,
                        BaseTransientBottomBar.LENGTH_LONG
                    )
                snackbar.setAction(R.string.resume) {
                    Timber.i(
                        "Reopening book %d",
                        previouslyViewedContent
                    )
                    val dao: CollectionDAO = ObjectBoxDAO()
                    try {
                        val c = dao.selectContent(previouslyViewedContent)
                        if (c != null) openReader(
                            this,
                            c,
                            -1,
                            contentSearchBundle,
                            forceShowGallery = false,
                            newTask = false
                        )
                    } finally {
                        dao.cleanup()
                    }
                }
                snackbar.show()
            }
            // Only show that once
            Settings.readerCurrentContent = -1
        }
    }

    private fun considerRefreshExtLib() {
        if (Settings.externalLibraryUri.isEmpty()) return

        // Wait at least X minutes between auto-refreshes to limit resource consumption
        val now = Instant.now().toEpochMilli()
        if (now - Settings.latestBeholderTimestamp < BEHOLDER_DELAY_MS) {
            Timber.d("External library auto-refresh delay not passed")
            return
        }
        Settings.latestBeholderTimestamp = now

        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            if (dao.countAllExternalBooks() < 1000) runExternalImport(this, true)
        } finally {
            dao.cleanup()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasChangedDisplaySettings) resetActivity()
    }

    /**
     * Initialize the UI components
     */
    private fun initUI() {
        // Search bar
        binding?.advancedSearch?.apply {
            // Link to advanced search
            advancedSearchBtn.setOnClickListener { onAdvancedSearchButtonClick() }

            // Save search
            searchSaveBtn.setOnClickListener { saveSearchAsGroup() }

            // Clear search
            searchClearBtn.setOnClickListener {
                clearAdvancedSearchCriteria()
                actionSearchView?.setQuery("", false)
                hideSearchSubBar()
                signalCurrentFragment(CommunicationEvent.Type.SEARCH, "")
            }

            // Main tabs
            binding?.libraryPager?.apply {
                isUserInputEnabled = false // Disable swipe to change tabs
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        this@LibraryActivity.onPageSelected()
                    }
                })
                adapter = pagerAdapter
            }
            updateDisplay(Settings.groupingDisplay)
        }
        // Grid size choice
        binding?.gridSizeBanner?.let {
            it.recyclerView.adapter = gridSizefastAdapter
            val labels = resources.getStringArray(R.array.pref_grid_card_width_entries)
            val values =
                resources.getStringArray(R.array.pref_grid_card_width_values).map { it.toInt() }
            val gridSizePref = Settings.libraryGridCardWidthDP
            labels.forEachIndexed { index, s ->
                val item = TextItem(
                    s, index,
                    draggable = false,
                    reformatCase = false,
                    isHighlighted = values[index] == gridSizePref,
                    centered = true
                )
                item.isSimple = true
                item.isSelected = item.isHighlighted
                gridSizeItemAdapter.add(item)
            }
            gridSizefastAdapter.onClickListener =
                { _, _, _, p ->
                    Settings.libraryGridCardWidthDP = values[p]
                    hasChangedGridDisplay = true
                    resetActivity()
                    true
                }
        }
    }

    private fun updateAlertBanner() {
        binding?.alertBanner?.apply {
            // Remind user that the app is in browser mode
            if (Settings.isBrowserMode) {
                alertTxt.setText(R.string.alert_browser_mode)
                alertTxt.visibility = View.VISIBLE
                alertIcon.visibility = View.GONE
                alertFixBtn.visibility = View.GONE
            } else if (!this@LibraryActivity.checkExternalStorageReadWritePermission()) { // Warn about permissions being lost
                alertTxt.setText(R.string.alert_permissions_lost)
                alertTxt.visibility = View.VISIBLE
                alertIcon.visibility = View.VISIBLE
                alertFixBtn.setOnClickListener { fixPermissions() }
                alertFixBtn.visibility = View.VISIBLE
            } else if (!this@LibraryActivity.checkNotificationPermission()) { // Warn about notiftications not being enabled
                alertTxt.setText(R.string.alert_notifications)
                alertTxt.visibility = View.VISIBLE
                alertIcon.visibility = View.VISIBLE
                alertFixBtn.setOnClickListener { fixNotifications() }
                alertFixBtn.visibility = View.VISIBLE
            } else if (this@LibraryActivity.isLowDeviceStorage()) { // Display low device storage alert
                alertTxt.setText(R.string.alert_low_memory)
                alertTxt.visibility = View.VISIBLE
                alertIcon.visibility = View.VISIBLE
                alertFixBtn.visibility = View.GONE
            } else if (isLowDatabaseStorage()) { // Display low database storage alert
                alertTxt.setText(R.string.alert_low_memory_db)
                alertTxt.visibility = View.VISIBLE
                alertIcon.visibility = View.VISIBLE
                alertFixBtn.visibility = View.GONE
            } else {
                alertTxt.visibility = View.GONE
                alertIcon.visibility = View.GONE
                alertFixBtn.visibility = View.GONE
            }
        }
    }

    private fun updateDisplay(targetGroupingId: Int) {
        pagerAdapter?.notifyDataSetChanged()
        if (targetGroupingId == Grouping.FLAT.id) { // Display books right away
            binding?.libraryPager?.currentItem = 1
        } else if (targetGroupingId == Grouping.FOLDERS.id) { // Display folders
            binding?.libraryPager?.currentItem = 2
        }
    }

    private fun initToolbar() {
        binding?.apply {
            searchMenu = toolbar.menu.findItem(R.id.action_search)
            searchMenu?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    showSearchSubBar(
                        !isGroupDisplayed() && !isFoldersDisplayed(),
                        null,
                        null,
                        !preventShowSearchHistoryNextExpand
                    )
                    preventShowSearchHistoryNextExpand = false
                    invalidateNextQueryTextChange = true

                    // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                    if (getQuery().isNotEmpty()) // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                        Handler(Looper.getMainLooper()).postDelayed({
                            invalidateNextQueryTextChange = true
                            actionSearchView?.setQuery(getQuery(), false)
                        }, 100)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    if (!isSearchQueryActive()) hideSearchSubBar()
                    invalidateNextQueryTextChange = true
                    return true
                }
            })
            displayTypeMenu = toolbar.menu.findItem(R.id.action_display_type)
            if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay)
                displayTypeMenu?.setIcon(R.drawable.ic_view_gallery)
            else displayTypeMenu?.setIcon(R.drawable.ic_view_list)
            reorderMenu = toolbar.menu.findItem(R.id.action_edit)
            reorderCancelMenu = toolbar.menu.findItem(R.id.action_edit_cancel)
            reorderConfirmMenu = toolbar.menu.findItem(R.id.action_edit_confirm)
            newGroupMenu = toolbar.menu.findItem(R.id.action_group_new)
            sortMenu = toolbar.menu.findItem(R.id.action_sort_filter)
            actionSearchView = searchMenu?.actionView as SearchView?
            actionSearchView?.apply {
                imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
                setIconifiedByDefault(true)
                queryHint = getString(R.string.library_search_hint)
                advancedSearch.searchClearBtn.setOnClickListener {
                    invalidateNextQueryTextChange = true
                    setQuery("", false)
                    isIconified = true
                    clearSearch() // Immediately; don't use the debouncer
                }

                // Change display when text query is typed
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(s: String): Boolean {
                        setQuery(s.trim())
                        signalCurrentFragment(CommunicationEvent.Type.SEARCH, query.toString())
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
        getSearchCriteria().query = ""
        signalCurrentFragment(CommunicationEvent.Type.SEARCH, getQuery())
        binding?.advancedSearch?.apply {
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
            toolbar.setOnMenuItemClickListener(toolbarOnItemClicked)
            selectionToolbar.setOnMenuItemClickListener(selectionToolbarOnItemClicked)
            selectionToolbar.setNavigationOnClickListener {
                selectExtension.deselect(selectExtension.selections.toMutableSet())
                selectionToolbar.visibility = View.GONE
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
                    !isGroupDisplayed() && !isFoldersDisplayed(),
                    showClear = true,
                    showSaveSearch = !isFoldersDisplayed(),
                    showSearchHistory = false
                )
            } else {
                collapseSearchMenu()
                if (it.query.isNotEmpty()) actionSearchView?.setQuery("", false)
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
                var displayType = Settings.libraryDisplay
                displayType =
                    if (Settings.Value.LIBRARY_DISPLAY_LIST == displayType) Settings.Value.LIBRARY_DISPLAY_GRID else Settings.Value.LIBRARY_DISPLAY_LIST
                Settings.libraryDisplay = displayType
                hasChangedGridDisplay = Settings.Value.LIBRARY_DISPLAY_GRID == displayType
                resetActivity()
            }

            R.id.action_browse_groups -> LibraryBottomGroupsFragment.invoke(
                this,
                this.supportFragmentManager
            )

            R.id.action_sort_filter -> LibraryBottomSortFilterFragment.invoke(
                this, this.supportFragmentManager, isGroupDisplayed(),
                group != null && group!!.isUngroupedGroup,
                isFoldersDisplayed()
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
        binding?.advancedSearch?.apply {
            advancedSearchBtn.isVisible = showAdvancedSearch && !isGroupDisplayed()
            if (showClear != null) searchClearBtn.isVisible = showClear
            if (showSaveSearch != null) searchSaveBtn.isVisible =
                showSaveSearch && !isGroupDisplayed()
            background.isVisible =
                advancedSearchBtn.isVisible || searchClearBtn.isVisible || searchSaveBtn.isVisible
        }

        if (showSearchHistory && searchRecords.isNotEmpty()) {
            val powerMenuBuilder = PowerMenu.Builder(this).setAnimation(MenuAnimation.DROP_DOWN)
                .setLifecycleOwner(this).setTextColor(
                    ContextCompat.getColor(this, R.color.white_opacity_87)
                ).setTextTypeface(Typeface.DEFAULT).setShowBackground(false).setWidth(
                    resources.getDimension(R.dimen.dialog_width).toInt()
                ).setMenuColor(ContextCompat.getColor(this, R.color.medium_gray)).setTextSize(
                    dimensAsDp(this, R.dimen.text_subtitle_2)
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
                setOnMenuItemClickListener { _, (_, _, _, _, _, tag): PowerMenuItem ->
                    if (tag != null) { // Tap on search record
                        (tag as SearchRecord?)?.let {
                            val searchUri = it.searchString.toUri()
                            setAdvancedSearchCriteria(SearchActivityBundle.parseSearchUri(searchUri))
                            if (getSearchCriteria().isEmpty()) { // Universal search
                                if (getQuery().isNotEmpty())
                                    viewModel.searchContentUniversal(getQuery())
                            } else { // Advanced search
                                viewModel.searchContent(
                                    getQuery(),
                                    getSearchCriteria(),
                                    searchUri
                                )
                            }
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
                binding?.apply {
                    val anchor =
                        if (advancedSearch.background.isVisible) advancedSearch.background else toolbar
                    showAsDropDown(anchor)
                }
            }
        } else if (!showSearchHistory) searchHistory?.dismiss()
    }

    fun hideSearchSubBar() {
        binding?.advancedSearch?.apply {
            background.visibility = View.GONE
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

    private fun expandSearchMenu() {
        searchMenu?.apply {
            if (isActionViewExpanded) {
                preventShowSearchHistoryNextExpand = true
                expandActionView()
            }
        }
    }

    private fun initSelectionToolbar() {
        binding?.selectionToolbar?.apply {
            menu.clear()
            inflateMenu(R.menu.library_selection_menu)
            tryShowMenuIcons(this@LibraryActivity, menu)
            menu.apply {
                editMenu = findItem(R.id.action_edit)
                deleteMenu = findItem(R.id.action_delete)
                detachMenu = findItem(R.id.action_detach)
                refreshMenu = findItem(R.id.action_refresh)
                completedMenu = findItem(R.id.action_completed)
                resetReadStatsMenu = findItem(R.id.action_reset_read)
                rateMenu = findItem(R.id.action_rate)
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
                exportMetaMenu = findItem(R.id.action_export_metadata)
            }
        }
    }

    /**
     * Callback for any change in Preferences
     */
    private fun onSharedPreferenceChanged(key: String?) {
        Timber.v("Prefs change detected : %s", key)
        AchievementsManager.checkPrefs()
        when (key) {
            Settings.Key.COLOR_THEME,
            Settings.Key.LIBRARY_DISPLAY,
            Settings.Key.LIBRARY_DISPLAY_GRID_STORAGE,
            Settings.Key.LIBRARY_DISPLAY_GRID_LANG,
            Settings.Key.LIBRARY_DISPLAY_GRID_FAV,
            Settings.Key.LIBRARY_DISPLAY_GRID_RATING,
            Settings.Key.LIBRARY_DISPLAY_GRID_SOURCE,
            Settings.Key.LIBRARY_DISPLAY_GRID_TITLE,
            Settings.Key.LIBRARY_GRID_CARD_WIDTH,
            Settings.Key.LIBRARY_DISPLAY_GROUP_FIGURE
                -> {
                hasChangedDisplaySettings = true
            }

            Settings.Key.PRIMARY_STORAGE_URI, Settings.Key.EXTERNAL_LIBRARY_URI -> {
                updateDisplay(Grouping.FLAT.id)
                viewModel.setGrouping(Grouping.FLAT.id)
            }

            Settings.Key.BROWSER_MODE -> updateAlertBanner()

            Settings.Key.GROUPING_DISPLAY -> onGroupingChanged(Settings.groupingDisplay)
            else -> {}
        }
    }

    /**
     * Handler for the "Advanced search" button
     */
    private fun onAdvancedSearchButtonClick() {
        signalCurrentFragment(CommunicationEvent.Type.ADVANCED_SEARCH)
    }

    private fun onGroupingChanged(targetGroupingId: Int) {
        val targetGrouping = Grouping.searchById(targetGroupingId)
        if (grouping.id != targetGroupingId) {
            // Reset custom book ordering if reverting to a grouping where that doesn't apply
            if (!targetGrouping.canReorderBooks && Settings.Value.ORDER_FIELD_CUSTOM == Settings.contentSortField) {
                Settings.contentSortField = Settings.Default.ORDER_CONTENT_FIELD
            }
            // Reset custom group ordering if reverting to a grouping where that doesn't apply
            if (!targetGrouping.canReorderGroups && Settings.Value.ORDER_FIELD_CUSTOM == Settings.groupSortField) {
                Settings.groupSortField = Settings.Default.ORDER_GROUP_FIELD
            }

            when (targetGrouping) {
                Grouping.FLAT, Grouping.FOLDERS -> updateDisplay(targetGroupingId)
                else -> goBackToGroups()
            }

            grouping = targetGrouping
            updateToolbar()
        }
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    fun updateTitle(totalSelectedCount: Int, totalCount: Int) {
        val title: String = if (totalSelectedCount == totalCount) resources.getQuantityString(
            R.plurals.number_of_items,
            totalSelectedCount,
            totalSelectedCount
        ) else {
            resources.getQuantityString(
                R.plurals.number_of_book_search_results,
                totalSelectedCount,
                totalSelectedCount,
                totalCount
            )
        }
        binding?.apply {
            toolbar.title = title
            titles[libraryPager.currentItem] = title
        }
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    fun isSearchQueryActive(): Boolean {
        return getQuery().isNotEmpty() || !getSearchCriteria().isEmpty()
    }

    private fun fixPermissions() {
        if (this.requestExternalStorageReadWritePermission(RQST_STORAGE_PERMISSION)) updateAlertBanner()
    }

    private fun fixNotifications() {
        if (this.requestNotificationPermission(RQST_NOTIFICATION_PERMISSION)) updateAlertBanner()
    }

    private fun isLowDatabaseStorage(): Boolean {
        val dbMaxSizeKb = Settings.maxDbSizeKb
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            return dao.getDbSizeBytes() / 1024f / dbMaxSizeKb < 0.02
        } finally {
            dao.cleanup()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) return
        binding?.alertBanner?.apply {
            if (RQST_STORAGE_PERMISSION == requestCode) {
                if (permissions.size < 2) return
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    alertTxt.visibility = View.GONE
                    alertIcon.visibility = View.GONE
                    alertFixBtn.visibility = View.GONE
                } // Don't show rationales here; the alert still displayed on screen should be enough
            } else if (RQST_NOTIFICATION_PERMISSION == requestCode) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    alertTxt.visibility = View.GONE
                    alertIcon.visibility = View.GONE
                    alertFixBtn.visibility = View.GONE
                } // Don't show rationales here; the alert still displayed on screen should be enough
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun closeNavigationDrawer() {
        activityBinding?.drawerLayout?.closeDrawer(GravityCompat.START)
    }

    private fun openNavigationDrawer() {
        activityBinding?.drawerLayout?.openDrawer(GravityCompat.START)
    }

    fun isGroupDisplayed(): Boolean {
        return 0 == binding?.libraryPager?.currentItem
    }

    fun isContentDisplayed(): Boolean {
        return 1 == binding?.libraryPager?.currentItem
    }

    fun isFoldersDisplayed(): Boolean {
        return 2 == binding?.libraryPager?.currentItem
    }

    fun goBackToGroups() {
        if (isGroupDisplayed()) return
        setEditMode(false)

        // Reset any active Content filter
        viewModel.setContentFavouriteFilter(false)
        viewModel.setContentRatingFilter(-1)
        viewModel.setCompletedFilter(false)
        viewModel.setNotCompletedFilter(false)
        viewModel.searchGroup()
        binding?.apply {
            libraryPager.currentItem = 0
            if (titles.containsKey(0)) toolbar.title = titles[0]
        }
    }

    fun showBooksInGroup(group: Group) {
        binding?.libraryPager?.currentItem = 1
        viewModel.setGroup(group, true)
    }

    fun isFilterActive(): Boolean {
        if (isSearchQueryActive()) {
            clearAdvancedSearchCriteria()
            collapseSearchMenu()
            hideSearchSubBar()
        }
        if (isGroupDisplayed() && groupSearchBundle != null) {
            val bundle = GroupSearchBundle(groupSearchBundle!!)
            return bundle.isFilterActive()
        } else if (isContentDisplayed() && contentSearchBundle != null) {
            val bundle = ContentSearchBundle(contentSearchBundle!!)
            return bundle.isFilterActive()
        } else if (isFoldersDisplayed() && folderSearchBundle != null) {
            val bundle = FolderSearchManager.FolderSearchBundle(folderSearchBundle!!)
            return bundle.isFilterActive()
        }
        return false
    }

    private fun updateToolbar() {
        val currentGrouping = Settings.getGroupingDisplayG()
        displayTypeMenu?.isVisible = !editMode
        searchMenu?.isVisible = !editMode
        actionSearchView?.queryHint =
            getString(if (isGroupDisplayed()) R.string.group_search_hint else R.string.library_search_hint)
        newGroupMenu?.isVisible =
            !editMode && isGroupDisplayed() && currentGrouping.canReorderGroups // Custom groups only
        reorderConfirmMenu?.isVisible = editMode
        reorderCancelMenu?.isVisible = editMode
        sortMenu?.isVisible = !editMode
        if (isGroupDisplayed()) {
            reorderMenu?.isVisible = !editMode && currentGrouping.canReorderGroups
        } else {
            reorderMenu?.isVisible =
                !editMode && currentGrouping.canReorderBooks && group != null && group!!.subtype != 1
        }
        signalCurrentFragment(CommunicationEvent.Type.UPDATE_TOOLBAR)
    }

    fun updateSelectionToolbar(
        selectedTotalCount: Int,
        selectedProcessedCount: Int,
        selectedLocalCount: Int,
        selectedStreamedCount: Int,
        selectedNonArchivePdfExternalCount: Int,
        selectedArchivePdfExternalCount: Int,
        selectedRoots: Int = 0,
        insideExtLib: Boolean = false
    ) {
        val isMultipleSelection = selectedTotalCount > 1
        val hasProcessed = selectedProcessedCount > 0
        val selectedDownloadedCount = selectedLocalCount - selectedStreamedCount
        val selectedExternalCount =
            selectedNonArchivePdfExternalCount + selectedArchivePdfExternalCount
        binding?.selectionToolbar?.title = resources.getQuantityString(
            R.plurals.items_selected,
            selectedTotalCount.toInt(),
            selectedTotalCount.toInt()
        )
        if (isGroupDisplayed()) {
            editMenu?.isVisible = !hasProcessed && !isMultipleSelection
                    && Settings.getGroupingDisplayG().canReorderGroups
            deleteMenu?.isVisible = !hasProcessed
            detachMenu?.isVisible = false
            refreshMenu?.isVisible = false
            shareMenu?.isVisible = false
            completedMenu?.isVisible = false
            resetReadStatsMenu?.isVisible = false
            rateMenu?.isVisible = isMultipleSelection
            archiveMenu?.isVisible = !hasProcessed
            changeGroupMenu?.isVisible = false
            folderMenu?.isVisible = false
            redownloadMenu?.isVisible = false
            downloadStreamedMenu?.isVisible = false
            streamMenu?.isVisible = false
            groupCoverMenu?.isVisible = false
            mergeMenu?.isVisible = false
            splitMenu?.isVisible = false
            transformMenu?.isVisible = false
            exportMetaMenu?.isVisible = selectedTotalCount > 0
        } else if (isFoldersDisplayed()) {
            editMenu?.isVisible = false
            deleteMenu?.isVisible = selectedTotalCount > 0 && 0 == selectedRoots
            // Can't detach external library root (that's the job of the storage screen)
            detachMenu?.isVisible = selectedRoots > 0 && !insideExtLib
            // Can't refresh external library root (that's the job of the storage screen)
            refreshMenu?.isVisible = insideExtLib && selectedTotalCount > 0 && 0 == selectedRoots
            shareMenu?.isVisible = false
            completedMenu?.isVisible = false
            resetReadStatsMenu?.isVisible = false
            rateMenu?.isVisible = false
            archiveMenu?.isVisible = false
            changeGroupMenu?.isVisible = false
            folderMenu?.isVisible = 1 == selectedTotalCount
            redownloadMenu?.isVisible = false
            downloadStreamedMenu?.isVisible = false
            streamMenu?.isVisible = false
            groupCoverMenu?.isVisible = false
            mergeMenu?.isVisible = false
            splitMenu?.isVisible = false
            transformMenu?.isVisible = false
            exportMetaMenu?.isVisible = false
        } else { // Books
            editMenu?.isVisible = !hasProcessed
            deleteMenu?.isVisible =
                !hasProcessed && ((selectedLocalCount > 0 || selectedStreamedCount > 0) && 0 == selectedExternalCount || selectedExternalCount > 0 && Settings.isDeleteExternalLibrary)
            detachMenu?.isVisible = false
            refreshMenu?.isVisible = false
            completedMenu?.isVisible = true
            resetReadStatsMenu?.isVisible = true
            rateMenu?.isVisible = isMultipleSelection
            shareMenu?.isVisible = true
            archiveMenu?.isVisible = !hasProcessed
            changeGroupMenu?.isVisible = !hasProcessed
            folderMenu?.isVisible = !isMultipleSelection
            redownloadMenu?.isVisible = !hasProcessed && selectedDownloadedCount > 0
            downloadStreamedMenu?.isVisible = !hasProcessed && selectedStreamedCount > 0
            streamMenu?.isVisible = !hasProcessed && selectedDownloadedCount > 0
            groupCoverMenu?.isVisible =
                !isMultipleSelection && Settings.getGroupingDisplayG() != Grouping.FLAT
            // Can only merge downloaded, streamed or non-archive external content together
            mergeMenu?.isVisible = !hasProcessed && (
                    selectedLocalCount > 1 && 0 == selectedStreamedCount && 0 == selectedExternalCount
                            || selectedStreamedCount > 1 && 0 == selectedLocalCount && 0 == selectedExternalCount
                            || selectedExternalCount > 1 && 0 == selectedLocalCount && 0 == selectedStreamedCount
                    )
            splitMenu?.isVisible =
                !hasProcessed && !isMultipleSelection && (1 == selectedLocalCount || 1 == selectedExternalCount)
            transformMenu?.isVisible =
                !hasProcessed && 0 == selectedStreamedCount && 0 == selectedArchivePdfExternalCount
            exportMetaMenu?.isVisible = false
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param contentIds    Items to be deleted if the answer is yes
     * @param groupIds      Groups to be deleted if the answer is yes
     */
    fun askDeleteItems(
        contentIds: List<Long>,
        groupIds: List<Long>,
        onSuccess: Runnable?,
        selectExtension: SelectExtension<*>
    ) {
        val builder = MaterialAlertDialogBuilder(this)
        val count = if (groupIds.isNotEmpty()) groupIds.size else contentIds.size
        if (count > 1000) {
            // TODO provide a link to the mass-delete tool when it's ready (#992)
            snack(R.string.delete_limit)
        } else {
            val title = resources.getQuantityString(R.plurals.ask_delete_multiple, count, count)
            builder.setMessage(title).setPositiveButton(R.string.yes) { _, _ ->
                selectExtension.deselect(selectExtension.selections.toMutableSet())
                viewModel.deleteItems(contentIds, groupIds, false, onSuccess)
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
        if (ProcessEvent.Type.COMPLETE != event.eventType) return
        processEvent(event)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return
        if (ProcessEvent.Type.COMPLETE != event.eventType) return
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

    override fun leaveSelectionMode() {
        signalCurrentFragment(CommunicationEvent.Type.UNSELECT)
    }

    private fun signalCurrentFragment(eventType: CommunicationEvent.Type, message: String = "") {
        signalFragment(getCurrentFragmentIndex(), eventType, message)
    }

    private fun signalFragment(
        fragmentIndex: Int,
        eventType: CommunicationEvent.Type,
        message: String
    ) {
        EventBus.getDefault().post(
            CommunicationEvent(
                eventType,
                when (fragmentIndex) {
                    1 -> CommunicationEvent.Recipient.CONTENTS
                    2 -> CommunicationEvent.Recipient.FOLDERS
                    else -> CommunicationEvent.Recipient.GROUPS
                },
                message
            )
        )
    }

    private fun getCurrentFragmentIndex(): Int {
        return if (isGroupDisplayed()) 0 else if (isFoldersDisplayed()) 2 else 1
    }

    private fun saveSearchAsGroup() {
        val criteria = getSearchCriteria()
        invokeInputDialog(
            this,
            R.string.group_new_name_dynamic,
            { s: String ->
                viewModel.newGroup(
                    Grouping.DYNAMIC,
                    s, SearchActivityBundle.buildSearchUri(criteria, null).toString()
                ) { onNewSearchGroupNameExists() }
            },
            criteria.toString(this)
        )
    }

    private fun onNewSearchGroupNameExists() {
        toast(R.string.group_name_exists)
        saveSearchAsGroup()
    }

    private fun onPageSelected() {
        hideSearchSubBar()
        updateToolbar()
        updateSelectionToolbar(0, 0, 0, 0, 0, 0)
    }

    /**
     * Restart the app with the library activity on top
     */
    private fun resetActivity() {
        val intent = Intent(this, LibraryActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val outBundle = LibraryActivityBundle()
        contentSearchBundle?.let {
            outBundle.contentSearchArgs = it
        }
        groupSearchBundle?.let {
            outBundle.groupSearchArgs = it
        }
        intent.putExtras(outBundle.bundle)

        finish()
        startActivity(intent)
    }

    /**
     * ============================== SUBCLASS
     */
    private class LibraryPagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                1 -> LibraryContentFragment()
                2 -> LibraryFoldersFragment()
                else -> LibraryGroupsFragment()
            }
        }

        override fun getItemCount(): Int {
            return 3
        }
    }
}

// == VALUES TO PASS ACROSS ACTIVITY RESET (that shouldn't be saved when closing the app)
var hasChangedGridDisplay = false

