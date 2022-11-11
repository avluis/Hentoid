package me.devsaki.hentoid.activities;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_ADVANCED_SEARCH;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_CLOSED;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_DISABLE;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_ENABLE;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_SEARCH;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_UPDATE_TOOLBAR;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_CONTENTS;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_DRAWER;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_GROUPS;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.SearchRecord;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.events.CommunicationEvent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.fragments.library.LibraryBottomGroupsFragment;
import me.devsaki.hentoid.fragments.library.LibraryBottomSortFilterFragment;
import me.devsaki.hentoid.fragments.library.LibraryContentFragment;
import me.devsaki.hentoid.fragments.library.LibraryGroupsFragment;
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment;
import me.devsaki.hentoid.notification.archive.ArchiveCompleteNotification;
import me.devsaki.hentoid.notification.archive.ArchiveNotificationChannel;
import me.devsaki.hentoid.notification.archive.ArchiveProgressNotification;
import me.devsaki.hentoid.notification.archive.ArchiveStartNotification;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LocaleHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.SearchHelper;
import me.devsaki.hentoid.util.TooltipHelper;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.file.PermissionHelper;
import me.devsaki.hentoid.util.notification.NotificationManager;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.ContentSearchManager;
import me.devsaki.hentoid.widget.GroupSearchManager;
import timber.log.Timber;

@SuppressLint("NonConstantResourceId")
public class LibraryActivity extends BaseActivity {

    private DrawerLayout drawerLayout;

    // ======== COMMUNICATION
    // Viewmodel
    private LibraryViewModel viewModel;
    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> onSharedPreferenceChanged(k);


    // ======== UI
    // Action view associated with search menu button
    private SearchView actionSearchView;

    // ==== Advanced search / sort bar
    // Grey background of the advanced search / sort bar
    private View searchSubBar;
    // Advanced search text button
    private View advancedSearchButton;
    // CLEAR button
    private View searchClearButton;

    // === Alert bar
    // Background and text of the alert bar
    private TextView alertTxt;
    // Icon of the alert bar
    private View alertIcon;
    // Action button ("fix") of the alert bar
    private View alertFixBtn;

    // === Toolbar
    private Toolbar toolbar;
    // "Search" button on top menu
    private MenuItem searchMenu;
    // "Display type" button on top menu
    private MenuItem displayTypeMenu;
    // "Edit mode" button on top menu
    private MenuItem reorderMenu;
    // "Confirm edit" button on top menu
    private MenuItem reorderConfirmMenu;
    // "Cancel edit" button on top menu
    private MenuItem reorderCancelMenu;
    // "Create new group" button on top menu
    private MenuItem newGroupMenu;
    // "Sort" button on top menu
    private MenuItem sortMenu;

    // === Selection toolbar
    private Toolbar selectionToolbar;
    private MenuItem editMenu;
    private MenuItem deleteMenu;
    private MenuItem completedMenu;
    private MenuItem resetReadStatsMenu;
    private MenuItem shareMenu;
    private MenuItem archiveMenu;
    private MenuItem changeGroupMenu;
    private MenuItem folderMenu;
    private MenuItem redownloadMenu;
    private MenuItem downloadStreamedMenu;
    private MenuItem streamMenu;
    private MenuItem groupCoverMenu;
    private MenuItem mergeMenu;
    private MenuItem splitMenu;

    private ViewPager2 viewPager;
    private FragmentStateAdapter pagerAdapter;


    // === NOTIFICATIONS
    // Notification for book archival
    private NotificationManager archiveNotificationManager;
    private int archiveProgress;
    private int archiveMax;

    // ======== DATA SYNC
    private final List<SearchRecord> searchRecords = new ArrayList<>();

    // ======== INNER VARIABLES
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;
    // Current text search query; one per tab
    private final List<String> query = Arrays.asList("", "");
    // Current metadata search query; one per tab
    private final List<SearchHelper.AdvancedSearchCriteria> advSearchCriteria = Arrays.asList(new SearchHelper.AdvancedSearchCriteria(new ArrayList<>(), ContentHelper.Location.ANY, ContentHelper.Type.ANY), new SearchHelper.AdvancedSearchCriteria(new ArrayList<>(), ContentHelper.Location.ANY, ContentHelper.Type.ANY));
    // True if item positioning edit mode is on (only available for specific groupings)
    private boolean editMode = false;
    // Titles of each of the Viewpager2's tabs
    private final Map<Integer, String> titles = new HashMap<>();
    // TODO doc
    private Group group = null;
    // TODO doc
    private Grouping grouping = Preferences.getGroupingDisplay();
    // TODO doc
    private Bundle contentSearchBundle = null;
    // TODO doc
    private Bundle groupSearchBundle = null;


    // === PUBLIC ACCESSORS (to be used by fragments)

    public Toolbar getSelectionToolbar() {
        return selectionToolbar;
    }

    public String getQuery() {
        return query.get(getCurrentFragmentIndex());
    }

    public void setQuery(String query) {
        this.query.set(getCurrentFragmentIndex(), query);
    }

    public SearchHelper.AdvancedSearchCriteria getAdvSearchCriteria() {
        return advSearchCriteria.get(getCurrentFragmentIndex());
    }

    public void setAdvancedSearchCriteria(@NonNull SearchHelper.AdvancedSearchCriteria criteria) {
        advSearchCriteria.set(getCurrentFragmentIndex(), criteria);
    }

    public void clearAdvancedSearchCriteria() {
        advSearchCriteria.set(getCurrentFragmentIndex(), SearchHelper.Companion.getEmptyAdvancedSearchCriteria());
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        updateToolbar();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_library);
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.addDrawerListener(new ActionBarDrawerToggle(this, drawerLayout,
                toolbar, R.string.open_drawer, R.string.close_drawer) {

            /** Called when a drawer has settled in a completely closed state. */
            @Override
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                EventBus.getDefault().post(new CommunicationEvent(EV_CLOSED, RC_DRAWER, ""));
            }

        });

        // Hack DrawerLayout to make the drag zone larger
        // Source : https://stackoverflow.com/a/36157701/8374722
        try {
            // get dragger responsible for the dragging of the left drawer
            Field draggerField = DrawerLayout.class.getDeclaredField("mLeftDragger");
            draggerField.setAccessible(true);
            ViewDragHelper vdh = (ViewDragHelper) draggerField.get(drawerLayout);

            // get access to the private field which defines
            // how far from the edge dragging can start
            Field edgeSizeField = ViewDragHelper.class.getDeclaredField("mEdgeSize");
            edgeSizeField.setAccessible(true);

            // increase the edge size - while x2 should be good enough,
            // try bigger values to easily see the difference
            Integer origEdgeSizeInt = (Integer) edgeSizeField.get(vdh);
            if (origEdgeSizeInt != null) {
                int origEdgeSize = origEdgeSizeInt;
                int newEdgeSize = origEdgeSize * 2;
                edgeSizeField.setInt(vdh, newEdgeSize);
                Timber.d("Left drawer : new drag size of %d pixels", newEdgeSize);
            }
        } catch (Exception e) {
            Timber.e(e);
        }

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            openNavigationDrawer();
            Preferences.setIsFirstRunProcessComplete(true);
        }

        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(LibraryViewModel.class);
        viewModel.getContentSearchManagerBundle().observe(this, b -> contentSearchBundle = b);
        viewModel.getGroup().observe(this, g -> {
            group = g;
            updateToolbar();
        });
        viewModel.getGroupSearchManagerBundle().observe(this, b -> {
            groupSearchBundle = b;
            GroupSearchManager.GroupSearchBundle searchBundle = new GroupSearchManager.GroupSearchBundle(b);
            onGroupingChanged(searchBundle.getGroupingId());
        });
        viewModel.getSearchRecords().observe(this, records -> {
            searchRecords.clear();
            searchRecords.addAll(records);
        });

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        Preferences.registerPrefsChangedListener(prefsListener);
        pagerAdapter = new LibraryPagerAdapter(this);

        initToolbar();
        initSelectionToolbar();
        initUI();
        updateToolbar();
        updateSelectionToolbar(0, 0, 0, 0, 0);

        onCreated();

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onAppUpdated(AppUpdatedEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) UpdateSuccessDialogFragment.invoke(getSupportFragmentManager());
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener);
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        if (archiveNotificationManager != null) archiveNotificationManager.cancel();

        // Empty all handlers to avoid leaks
        if (toolbar != null) toolbar.setOnMenuItemClickListener(null);
        if (selectionToolbar != null) {
            selectionToolbar.setOnMenuItemClickListener(null);
            selectionToolbar.setNavigationOnClickListener(null);
        }
        super.onDestroy();
    }

    @Override
    public void onRestart() {
        // Change locale if set manually
        LocaleHelper.convertLocaleToEnglish(this);

        super.onRestart();
    }

    private void onCreated() {
        // Display search bar tooltip _after_ the left drawer closes (else it displays over it)
        if (Preferences.isFirstRunProcessComplete())
            TooltipHelper.showTooltip(this, R.string.help_search, ArrowOrientation.TOP, toolbar, this);

        // Display permissions alert if required
        if (!PermissionHelper.checkExternalStorageReadWritePermission(this) &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            alertTxt.setText(R.string.permissions_lost);
            alertFixBtn.setOnClickListener(v -> fixPermissions());
            alertTxt.setVisibility(View.VISIBLE);
            alertIcon.setVisibility(View.VISIBLE);
            alertFixBtn.setVisibility(View.VISIBLE);
        } else if (isLowOnSpace()) { // Else display low space alert
            alertTxt.setText(R.string.low_memory);
            alertTxt.setVisibility(View.VISIBLE);
            alertIcon.setVisibility(View.VISIBLE);
            alertFixBtn.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        final long previouslyViewedContent = Preferences.getViewerCurrentContent();
        final int previouslyViewedPage = Preferences.getViewerCurrentPageNum();
        if (previouslyViewedContent > -1 && previouslyViewedPage > -1 && !ReaderActivity.isRunning()) {
            Snackbar snackbar = Snackbar.make(viewPager, R.string.resume_closed, BaseTransientBottomBar.LENGTH_LONG);
            snackbar.setAction(R.string.resume, v -> {
                Timber.i("Reopening book %d from page %d", previouslyViewedContent, previouslyViewedPage);
                CollectionDAO dao = new ObjectBoxDAO(this);
                try {
                    Content c = dao.selectContent(previouslyViewedContent);
                    if (c != null)
                        ContentHelper.openReader(this, c, previouslyViewedPage, contentSearchBundle, false);
                } finally {
                    dao.cleanup();
                }
            });
            snackbar.show();
            // Only show that once
            Preferences.setViewerCurrentContent(-1);
            Preferences.setViewerCurrentPageNum(-1);
        }
    }

    /**
     * Initialize the UI components
     */
    private void initUI() {
        // Permissions alert bar
        alertTxt = findViewById(R.id.library_alert_txt);
        alertIcon = findViewById(R.id.library_alert_icon);
        alertFixBtn = findViewById(R.id.library_alert_fix_btn);

        // Search bar
        searchSubBar = findViewById(R.id.advanced_search_background);

        // Link to advanced search
        advancedSearchButton = findViewById(R.id.advanced_search_btn);
        advancedSearchButton.setOnClickListener(v -> onAdvancedSearchButtonClick());

        // Clear search
        searchClearButton = findViewById(R.id.search_clear_btn);
        searchClearButton.setOnClickListener(v -> {
            setQuery("");
            clearAdvancedSearchCriteria();
            actionSearchView.setQuery("", false);
            hideSearchSubBar();
            signalCurrentFragment(EV_SEARCH, "");
        });

        // Main tabs
        viewPager = findViewById(R.id.library_pager);
        viewPager.setUserInputEnabled(false); // Disable swipe to change tabs
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                enableCurrentFragment();
                hideSearchSubBar();
                updateToolbar();
                updateSelectionToolbar(0, 0, 0, 0, 0);
            }
        });
        viewPager.setAdapter(pagerAdapter);

        updateDisplay(Preferences.getGroupingDisplay().getId());
    }

    private void updateDisplay(int targetGroupingId) {
        pagerAdapter.notifyDataSetChanged();
        if (targetGroupingId == Grouping.FLAT.getId()) { // Display books right away
            viewPager.setCurrentItem(1);
//            viewModel.searchContent();
        }
        enableCurrentFragment();
    }

    private void initToolbar() {
        toolbar = findViewById(R.id.library_toolbar);

        searchMenu = toolbar.getMenu().findItem(R.id.action_search);
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                showSearchSubBar(true, null, true);
                invalidateNextQueryTextChange = true;

                // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                if (!getQuery().isEmpty())
                    // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        invalidateNextQueryTextChange = true;
                        actionSearchView.setQuery(getQuery(), false);
                    }, 100);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (!isSearchQueryActive()) {
                    hideSearchSubBar();
                }
                invalidateNextQueryTextChange = true;
                return true;
            }
        });

        displayTypeMenu = toolbar.getMenu().findItem(R.id.action_display_type);
        if (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay())
            displayTypeMenu.setIcon(R.drawable.ic_view_gallery);
        else
            displayTypeMenu.setIcon(R.drawable.ic_view_list);
        reorderMenu = toolbar.getMenu().findItem(R.id.action_edit);
        reorderCancelMenu = toolbar.getMenu().findItem(R.id.action_edit_cancel);
        reorderConfirmMenu = toolbar.getMenu().findItem(R.id.action_edit_confirm);
        newGroupMenu = toolbar.getMenu().findItem(R.id.action_group_new);
        sortMenu = toolbar.getMenu().findItem(R.id.action_sort_filter);

        actionSearchView = (SearchView) searchMenu.getActionView();
        actionSearchView.setIconifiedByDefault(true);
        actionSearchView.setQueryHint(getString(R.string.library_search_hint));
        // Change display when text query is typed
        actionSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                setQuery(s);
                signalCurrentFragment(EV_SEARCH, getQuery());
                actionSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
                    setQuery("");
                    signalCurrentFragment(EV_SEARCH, getQuery());
                    searchClearButton.setVisibility(View.GONE);
                }

                return true;
            }
        });
    }

    public void initFragmentToolbars(
            @NonNull final SelectExtension<?> selectExtension,
            @NonNull final Toolbar.OnMenuItemClickListener toolbarOnItemClicked,
            @NonNull final Toolbar.OnMenuItemClickListener selectionToolbarOnItemClicked
    ) {
        toolbar.setOnMenuItemClickListener(toolbarOnItemClicked);
        if (selectionToolbar != null) {
            selectionToolbar.setOnMenuItemClickListener(selectionToolbarOnItemClicked);
            selectionToolbar.setNavigationOnClickListener(v -> {
                selectExtension.deselect(selectExtension.getSelections());
                selectionToolbar.setVisibility(View.GONE);
            });
        }
    }

    public void updateSearchBarOnResults(boolean nonEmptyResults) {
        if (isSearchQueryActive()) {
            if (!getQuery().isEmpty()) {
                actionSearchView.setQuery(getQuery(), false);
                expandSearchMenu();
            } else if (nonEmptyResults) {
                collapseSearchMenu();
            }
            showSearchSubBar(!isGroupDisplayed(), true, false);
        } else {
            collapseSearchMenu();
            if (actionSearchView.getQuery().length() > 0)
                actionSearchView.setQuery("", false);
            searchClearButton.setVisibility(View.GONE);
        }
    }

    /**
     * Callback method used when a sort method is selected in the sort drop-down menu
     * Updates the UI according to the chosen sort method
     *
     * @param menuItem Toolbar of the fragment
     */
    public boolean toolbarOnItemClicked(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_display_type:
                int displayType = Preferences.getLibraryDisplay();
                if (Preferences.Constant.LIBRARY_DISPLAY_LIST == displayType)
                    displayType = Preferences.Constant.LIBRARY_DISPLAY_GRID;
                else displayType = Preferences.Constant.LIBRARY_DISPLAY_LIST;
                Preferences.setLibraryDisplay(displayType);
                break;
            case R.id.action_browse_groups:
                LibraryBottomGroupsFragment.invoke(
                        this,
                        this.getSupportFragmentManager()
                );
                break;
            case R.id.action_sort_filter:
                LibraryBottomSortFilterFragment.invoke(
                        this,
                        this.getSupportFragmentManager(),
                        isGroupDisplayed(),
                        group != null && group.grouping.equals(Grouping.CUSTOM) && 1 == group.getSubtype()
                );
                break;
            default:
                return false;
        }
        return true;
    }

    private void showSearchSubBar(boolean showAdvancedSearch, Boolean showClear, boolean showSearchHistory) {
        searchSubBar.setVisibility(View.VISIBLE);
        advancedSearchButton.setVisibility(showAdvancedSearch && !isGroupDisplayed() ? View.VISIBLE : View.GONE);
        if (showClear != null)
            searchClearButton.setVisibility(showClear ? View.VISIBLE : View.GONE);

        if (showSearchHistory && !searchRecords.isEmpty()) {
            PowerMenu.Builder powerMenuBuilder = new PowerMenu.Builder(this)
                    .setAnimation(MenuAnimation.DROP_DOWN)
                    .setLifecycleOwner(this)
                    .setTextColor(ContextCompat.getColor(this, R.color.white_opacity_87))
                    .setTextTypeface(Typeface.DEFAULT)
                    .setShowBackground(false)
                    .setWidth((int) getResources().getDimension(R.dimen.dialog_width))
                    .setMenuColor(ContextCompat.getColor(this, R.color.medium_gray))
                    .setTextSize(Helper.dimensAsDp(this, R.dimen.text_subtitle_2))
                    .setAutoDismiss(true);

            for (int i = searchRecords.size() - 1; i >= 0; i--)
                powerMenuBuilder.addItem(new PowerMenuItem(searchRecords.get(i).getLabel(), R.drawable.ic_clock, false, searchRecords.get(i)));
            powerMenuBuilder.addItem(new PowerMenuItem(getResources().getString(R.string.clear_search_history), false));

            PowerMenu powerMenu = powerMenuBuilder.build();
            powerMenu.setOnMenuItemClickListener((position, item) -> {
                if (item.getTag() != null) { // Tap on search record
                    SearchRecord record = (SearchRecord) item.getTag();
                    Uri searchUri = Uri.parse(record.getSearchString());
                    String targetQuery = searchUri.getPath();
                    if (!targetQuery.isEmpty())
                        targetQuery = targetQuery.substring(1); // Remove the leading '/'
                    setQuery(targetQuery);
                    setAdvancedSearchCriteria(SearchActivityBundle.Companion.parseSearchUri(searchUri));
                    if (getAdvSearchCriteria().isEmpty()) { // Universal search
                        if (!getQuery().isEmpty()) viewModel.searchContentUniversal(getQuery());
                    } else { // Advanced search
                        viewModel.searchContent(getQuery(), getAdvSearchCriteria(), searchUri);
                    }
                } else { // Clear history
                    viewModel.clearSearchHistory();
//                    powerMenu.dismiss();
                }
            });

            powerMenu.setIconColor(ContextCompat.getColor(this, R.color.white_opacity_87));
            powerMenu.showAsDropDown(searchSubBar);
        }
    }

    public void hideSearchSubBar() {
        searchSubBar.setVisibility(View.GONE);
        advancedSearchButton.setVisibility(View.GONE);
        searchClearButton.setVisibility(View.GONE);
    }

    public boolean closeLeftDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    public boolean collapseSearchMenu() {
        if (searchMenu != null && searchMenu.isActionViewExpanded()) {
            searchMenu.collapseActionView();
            return true;
        }
        return false;
    }

    public void expandSearchMenu() {
        if (searchMenu != null && !searchMenu.isActionViewExpanded()) {
            searchMenu.expandActionView();
        }
    }

    private void initSelectionToolbar() {
        selectionToolbar = findViewById(R.id.library_selection_toolbar);
        selectionToolbar.getMenu().clear();
        selectionToolbar.inflateMenu(R.menu.library_selection_menu);
        Helper.tryShowMenuIcons(this, selectionToolbar.getMenu());

        editMenu = selectionToolbar.getMenu().findItem(R.id.action_edit);
        deleteMenu = selectionToolbar.getMenu().findItem(R.id.action_delete);
        completedMenu = selectionToolbar.getMenu().findItem(R.id.action_completed);
        resetReadStatsMenu = selectionToolbar.getMenu().findItem(R.id.action_reset_read);
        shareMenu = selectionToolbar.getMenu().findItem(R.id.action_share);
        archiveMenu = selectionToolbar.getMenu().findItem(R.id.action_archive);
        changeGroupMenu = selectionToolbar.getMenu().findItem(R.id.action_change_group);
        folderMenu = selectionToolbar.getMenu().findItem(R.id.action_open_folder);
        redownloadMenu = selectionToolbar.getMenu().findItem(R.id.action_redownload);
        downloadStreamedMenu = selectionToolbar.getMenu().findItem(R.id.action_download);
        streamMenu = selectionToolbar.getMenu().findItem(R.id.action_stream);
        groupCoverMenu = selectionToolbar.getMenu().findItem(R.id.action_set_group_cover);
        mergeMenu = selectionToolbar.getMenu().findItem(R.id.action_merge);
        splitMenu = selectionToolbar.getMenu().findItem(R.id.action_split);
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(String key) {
        Timber.i("Prefs change detected : %s", key);
        switch (key) {
            case Preferences.Key.COLOR_THEME:
            case Preferences.Key.LIBRARY_DISPLAY:
                // Restart the app with the library activity on top
                Intent intent = new Intent(this, LibraryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                finish();
                startActivity(intent);
                break;
            case Preferences.Key.SD_STORAGE_URI:
            case Preferences.Key.EXTERNAL_LIBRARY_URI:
                updateDisplay(Grouping.FLAT.getId());
                viewModel.setGrouping(Grouping.FLAT.getId());
                break;
            default:
                // Nothing to handle there
        }
    }

    /**
     * Handler for the "Advanced search" button
     */
    private void onAdvancedSearchButtonClick() {
        signalCurrentFragment(EV_ADVANCED_SEARCH, null);
    }

    private void onGroupingChanged(int targetGroupingId) {
        Grouping targetGrouping = Grouping.searchById(targetGroupingId);
        if (grouping.getId() != targetGroupingId) {
            // Reset custom book ordering if reverting to a grouping where that doesn't apply
            if (!targetGrouping.canReorderBooks()
                    && Preferences.Constant.ORDER_FIELD_CUSTOM == Preferences.getContentSortField()) {
                Preferences.setContentSortField(Preferences.Default.ORDER_CONTENT_FIELD);
            }
            // Reset custom group ordering if reverting to a grouping where that doesn't apply
            if (!targetGrouping.canReorderGroups()
                    && Preferences.Constant.ORDER_FIELD_CUSTOM == Preferences.getGroupSortField()) {
                Preferences.setGroupSortField(Preferences.Default.ORDER_GROUP_FIELD);
            }

            // Go back to groups tab if we're not
            if (targetGroupingId != Grouping.FLAT.getId())
                goBackToGroups();

            // Update screen display if needed (flat <-> the rest)
            if (grouping.equals(Grouping.FLAT) || targetGroupingId == Grouping.FLAT.getId())
                updateDisplay(targetGroupingId);

            grouping = targetGrouping;
            updateToolbar();
        }
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    public void updateTitle(long totalSelectedCount, long totalCount) {
        String title;
        if (totalSelectedCount == totalCount)
            title = getResources().getQuantityString(R.plurals.number_of_items, (int) totalSelectedCount, (int) totalSelectedCount);
        else {
            title = getResources().getQuantityString(R.plurals.number_of_book_search_results, (int) totalSelectedCount, (int) totalSelectedCount, totalCount);
        }
        toolbar.setTitle(title);
        titles.put(viewPager.getCurrentItem(), title);
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    public boolean isSearchQueryActive() {
        return (!getQuery().isEmpty() || !getAdvSearchCriteria().isEmpty());
    }

    private void fixPermissions() {
        PermissionHelper.requestExternalStorageReadWritePermission(this, PermissionHelper.RQST_STORAGE_PERMISSION);
    }

    private boolean isLowOnSpace() {
        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(this, Preferences.getStorageUri());
        if (null == rootFolder) return false;

        double freeSpaceRatio = new FileHelper.MemoryUsageFigures(this, rootFolder).getFreeUsageRatio100();
        return (freeSpaceRatio < 100 - Preferences.getMemoryAlertThreshold());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != PermissionHelper.RQST_STORAGE_PERMISSION) return;
        if (permissions.length < 2) return;
        if (grantResults.length == 0) return;
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            alertTxt.setVisibility(View.GONE);
            alertIcon.setVisibility(View.GONE);
            alertFixBtn.setVisibility(View.GONE);
        } // Don't show rationales here; the alert still displayed on screen should be enough
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void closeNavigationDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    public void openNavigationDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    private boolean isGroupDisplayed() {
        return 0 == viewPager.getCurrentItem();
    }

    public void goBackToGroups() {
        if (isGroupDisplayed()) return;

        enableFragment(0);
        viewModel.searchGroup();
        viewPager.setCurrentItem(0);
        if (titles.containsKey(0)) toolbar.setTitle(titles.get(0));
        //toolbar.setNavigationIcon(R.drawable.ic_drawer);
    }

    public void showBooksInGroup(Group group) {
        enableFragment(1);
        viewModel.setGroup(group, true);
        viewPager.setCurrentItem(1);
        //toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
    }

    public boolean isFilterActive() {
        if (isSearchQueryActive()) {
            setQuery("");
            clearAdvancedSearchCriteria();
            collapseSearchMenu();
            hideSearchSubBar();
        }
        if (isGroupDisplayed() && groupSearchBundle != null) {
            GroupSearchManager.GroupSearchBundle bundle = new GroupSearchManager.GroupSearchBundle(groupSearchBundle);
            return bundle.isFilterActive();
        } else if (!isGroupDisplayed() && contentSearchBundle != null) {
            ContentSearchManager.ContentSearchBundle bundle = new ContentSearchManager.ContentSearchBundle(contentSearchBundle);
            return bundle.isFilterActive();
        }
        return false;
    }

    private void updateToolbar() {
        Grouping currentGrouping = Preferences.getGroupingDisplay();

        displayTypeMenu.setVisible(!editMode);
        searchMenu.setVisible(!editMode);
        newGroupMenu.setVisible(!editMode && isGroupDisplayed() && currentGrouping.canReorderGroups()); // Custom groups only
        reorderConfirmMenu.setVisible(editMode);
        reorderCancelMenu.setVisible(editMode);
        sortMenu.setVisible(!editMode);

        boolean isToolbarNavigationDrawer = true;
        if (isGroupDisplayed()) {
            reorderMenu.setVisible(currentGrouping.canReorderGroups());
        } else {
            reorderMenu.setVisible(currentGrouping.canReorderBooks() && group != null && group.getSubtype() != 1);
            isToolbarNavigationDrawer = currentGrouping.equals(Grouping.FLAT);
        }

        if (isToolbarNavigationDrawer) { // Open the left drawer
            toolbar.setNavigationIcon(R.drawable.ic_drawer);
            toolbar.setNavigationOnClickListener(v -> openNavigationDrawer());
        } else { // Go back to groups
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> goBackToGroups());
        }

        signalCurrentFragment(EV_UPDATE_TOOLBAR, null);
    }

    public void updateSelectionToolbar(
            long selectedTotalCount,
            long selectedLocalCount,
            long selectedStreamedCount,
            long selectedNonArchiveExternalCount,
            long selectedArchiveExternalCount) {
        boolean isMultipleSelection = selectedTotalCount > 1;
        long selectedDownloadedCount = selectedLocalCount - selectedStreamedCount;
        long selectedExternalCount = selectedNonArchiveExternalCount + selectedArchiveExternalCount;
        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedTotalCount, (int) selectedTotalCount));

        if (isGroupDisplayed()) {
            editMenu.setVisible(!isMultipleSelection && Preferences.getGroupingDisplay().canReorderGroups());
            deleteMenu.setVisible(true);
            shareMenu.setVisible(false);
            completedMenu.setVisible(false);
            resetReadStatsMenu.setVisible(false);
            archiveMenu.setVisible(true);
            changeGroupMenu.setVisible(false);
            folderMenu.setVisible(false);
            redownloadMenu.setVisible(false);
            downloadStreamedMenu.setVisible(false);
            streamMenu.setVisible(false);
            groupCoverMenu.setVisible(false);
            mergeMenu.setVisible(false);
            splitMenu.setVisible(false);
        } else { // Flat view
            editMenu.setVisible(true);
            deleteMenu.setVisible(
                    ((selectedLocalCount > 0 || selectedStreamedCount > 0) && 0 == selectedExternalCount) || (selectedExternalCount > 0 && Preferences.isDeleteExternalLibrary())
            );
            completedMenu.setVisible(true);
            resetReadStatsMenu.setVisible(true);
            shareMenu.setVisible(!isMultipleSelection && 1 == selectedLocalCount);
            archiveMenu.setVisible(true);
            changeGroupMenu.setVisible(true);
            folderMenu.setVisible(!isMultipleSelection);
            redownloadMenu.setVisible(selectedDownloadedCount > 0);
            downloadStreamedMenu.setVisible(selectedStreamedCount > 0);
            streamMenu.setVisible(selectedDownloadedCount > 0);
            groupCoverMenu.setVisible(!isMultipleSelection && !Preferences.getGroupingDisplay().equals(Grouping.FLAT));
            mergeMenu.setVisible(
                    (selectedLocalCount > 1 && 0 == selectedStreamedCount && 0 == selectedExternalCount)
                            || (selectedStreamedCount > 1 && 0 == selectedLocalCount && 0 == selectedExternalCount)
                            || (selectedNonArchiveExternalCount > 1 && 0 == selectedArchiveExternalCount && 0 == selectedLocalCount && 0 == selectedStreamedCount)
            ); // Can only merge downloaded, streamed or non-archive external content together
            splitMenu.setVisible(!isMultipleSelection && 1 == selectedLocalCount);
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param contents Items to be deleted if the answer is yes
     */
    public void askDeleteItems(
            @NonNull final List<Content> contents,
            @NonNull final List<Group> groups,
            @Nullable final Runnable onSuccess,
            @NonNull final SelectExtension<?> selectExtension) {
        // TODO display the number of books and groups that will be deleted
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        int count = !groups.isEmpty() ? groups.size() : contents.size();
        String title = getResources().getQuantityString(R.plurals.ask_delete_multiple, count, count);
        builder.setMessage(title)
                .setPositiveButton(R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect(selectExtension.getSelections());
                            viewModel.deleteItems(contents, groups, false, onSuccess);
                        })
                .setNegativeButton(R.string.no,
                        (dialog, which) -> selectExtension.deselect(selectExtension.getSelections()))
                .setOnCancelListener(dialog -> selectExtension.deselect(selectExtension.getSelections()))
                .create().show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProcessEvent(ProcessEvent event) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return;
        if (ProcessEvent.EventType.COMPLETE != event.eventType) return;
        String msg = "";
        int nbGroups = event.elementsOKOther;
        int nbContent = event.elementsOK;
        if (nbGroups > 0)
            msg += getResources().getQuantityString(R.plurals.delete_success_groups, nbGroups, nbGroups);
        if (nbContent > 0) {
            if (!msg.isEmpty()) msg += " & ";
            msg += getResources().getQuantityString(R.plurals.delete_success_books, nbContent, nbContent);
        }
        msg += " " + getResources().getString(R.string.delete_success);

        Snackbar.make(viewPager, msg, LENGTH_LONG).show();
    }


    /**
     * Display the yes/no dialog to make sure the user really wants to archive selected items
     *
     * @param items Items to be archived if the answer is yes
     */
    public void askArchiveItems(@NonNull final List<Content> items,
                                @NonNull final SelectExtension<?> selectExtension) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        String title = getResources().getQuantityString(R.plurals.ask_archive_multiple, items.size(), items.size());
        builder.setMessage(title)
                .setPositiveButton(R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect(selectExtension.getSelections());
                            ArchiveNotificationChannel.init(this);
                            archiveNotificationManager = new NotificationManager(this, R.id.archive_processing);
                            archiveNotificationManager.cancel();
                            archiveProgress = 0;
                            archiveMax = items.size();
                            archiveNotificationManager.notify(new ArchiveStartNotification());
                            viewModel.archiveContents(items, this::onContentArchiveProgress, this::onContentArchiveSuccess, this::onContentArchiveError);
                        })
                .setNegativeButton(R.string.no,
                        (dialog, which) -> selectExtension.deselect(selectExtension.getSelections()))
                .create().show();
    }

    private void onContentArchiveProgress(Content content) {
        archiveProgress++;
        archiveNotificationManager.notify(new ArchiveProgressNotification(content.getTitle(), archiveProgress, archiveMax));
    }

    /**
     * Callback for the success of the "archive item" action
     */
    private void onContentArchiveSuccess() {
        archiveNotificationManager.notify(new ArchiveCompleteNotification(archiveProgress, false));
        Snackbar.make(viewPager, getResources().getQuantityString(R.plurals.archive_success, archiveProgress, archiveProgress), LENGTH_LONG)
                .setAction(R.string.open_folder, v -> FileHelper.openFile(this, FileHelper.getDownloadsFolder()))
                .show();
    }

    /**
     * Callback for the success of the "archive item" action
     */
    private void onContentArchiveError(Throwable e) {
        Timber.e(e);
        archiveNotificationManager.notify(new ArchiveCompleteNotification(archiveProgress, true));
    }

    private void signalCurrentFragment(int eventType, @Nullable String message) {
        signalFragment(getCurrentFragmentIndex(), eventType, message);
    }

    private void signalFragment(int fragmentIndex, int eventType, @Nullable String message) {
        EventBus.getDefault().post(new CommunicationEvent(eventType, (0 == fragmentIndex) ? RC_GROUPS : RC_CONTENTS, message));
    }

    private void enableCurrentFragment() {
        enableFragment(getCurrentFragmentIndex());
    }

    private int getCurrentFragmentIndex() {
        return isGroupDisplayed() ? 0 : 1;
    }

    private void enableFragment(int fragmentIndex) {
        EventBus.getDefault().post(new CommunicationEvent(EV_ENABLE, (0 == fragmentIndex) ? RC_GROUPS : RC_CONTENTS, null));
        EventBus.getDefault().post(new CommunicationEvent(EV_DISABLE, (0 == fragmentIndex) ? RC_CONTENTS : RC_GROUPS, null));
    }

    public static @StringRes
    int getNameFromFieldCode(int prefFieldCode) {
        switch (prefFieldCode) {
            case (Preferences.Constant.ORDER_FIELD_TITLE):
                return R.string.sort_title;
            case (Preferences.Constant.ORDER_FIELD_ARTIST):
                return R.string.sort_artist;
            case (Preferences.Constant.ORDER_FIELD_NB_PAGES):
                return R.string.sort_pages;
            case (Preferences.Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE):
                return R.string.sort_dl_date;
            case (Preferences.Constant.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE):
                return R.string.sort_dl_completion_date;
            case (Preferences.Constant.ORDER_FIELD_UPLOAD_DATE):
                return R.string.sort_uplodad_date;
            case (Preferences.Constant.ORDER_FIELD_READ_DATE):
                return R.string.sort_read_date;
            case (Preferences.Constant.ORDER_FIELD_READS):
                return R.string.sort_reads;
            case (Preferences.Constant.ORDER_FIELD_SIZE):
                return R.string.sort_size;
            case (Preferences.Constant.ORDER_FIELD_READ_PROGRESS):
                return R.string.sort_reading_progress;
            case (Preferences.Constant.ORDER_FIELD_CUSTOM):
                return R.string.sort_custom;
            case (Preferences.Constant.ORDER_FIELD_RANDOM):
                return R.string.sort_random;
            case (Preferences.Constant.ORDER_FIELD_CHILDREN):
                return R.string.sort_books;
            default:
                return R.string.sort_invalid;
        }
    }

    /**
     * ============================== SUBCLASS
     */

    private static class LibraryPagerAdapter extends FragmentStateAdapter {

        LibraryPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (0 == position) {
                return new LibraryGroupsFragment();
            } else {
                return new LibraryContentFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
