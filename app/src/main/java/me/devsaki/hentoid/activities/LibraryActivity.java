package me.devsaki.hentoid.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.events.CommunicationEvent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.fragments.library.LibraryContentFragment;
import me.devsaki.hentoid.fragments.library.LibraryGroupsFragment;
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment;
import me.devsaki.hentoid.notification.archive.ArchiveCompleteNotification;
import me.devsaki.hentoid.notification.archive.ArchiveNotificationChannel;
import me.devsaki.hentoid.notification.archive.ArchiveProgressNotification;
import me.devsaki.hentoid.notification.archive.ArchiveStartNotification;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.PermissionHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.TooltipHelper;
import me.devsaki.hentoid.util.notification.NotificationManager;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_ADVANCED_SEARCH;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_CLOSED;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_DISABLE;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_ENABLE;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_SEARCH;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_UPDATE_SORT;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_CONTENTS;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_DRAWER;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_GROUPS;

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
    private View searchSortBar;
    // Advanced search text button
    private View advancedSearchButton;
    // Show artists / groups button
    private TextView showArtistsGroupsButton;
    // CLEAR button
    private View searchClearButton;
    // Sort direction button
    private ImageView sortDirectionButton;
    // Sort reshuffle button
    private ImageView sortReshuffleButton;
    // Sort field button
    private TextView sortFieldButton;

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
    // "Edit mode" / "Validate edit" button on top menu
    private MenuItem reorderMenu;
    // "Cancel edit" button on top menu
    private MenuItem reorderCancelMenu;
    // "Create new group" button on top menu
    private MenuItem newGroupMenu;
    // "Toggle completed" button on top menu
    private MenuItem completedFilterMenu;
    // "Toggle favourites" button on top menu
    private MenuItem favsMenu;
    // "Sort" button on top menu
    private MenuItem sortMenu;
    // Alert bars
    private PopupMenu autoHidePopup;

    // === Selection toolbar
    private Toolbar selectionToolbar;
    private MenuItem editNameMenu;
    private MenuItem deleteMenu;
    private MenuItem completedMenu;
    private MenuItem shareMenu;
    private MenuItem archiveMenu;
    private MenuItem changeGroupMenu;
    private MenuItem folderMenu;
    private MenuItem redownloadMenu;
    private MenuItem coverMenu;

    private ViewPager2 viewPager;


    // === NOTIFICATIONS
    // Notification for book archival
    private NotificationManager archiveNotificationManager;
    private int archiveProgress;
    private int archiveMax;


    // ======== VARIABLES
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;
    // Current text search query
    private String query = "";
    // Current metadata search query
    private List<Attribute> metadata = Collections.emptyList();
    // True if item positioning edit mode is on (only available for specific groupings)
    private boolean editMode = false;
    // True if there's at least one existing custom group; false instead
    private boolean isCustomGroupingAvailable;
    // Titles of each of the Viewpager2's tabs
    private final Map<Integer, String> titles = new HashMap<>();
    // TODO doc
    private boolean isGroupFavsChecked = false;


    // Used to auto-hide the sort controls bar when no activity is detected
    private Debouncer<Boolean> sortCommandsAutoHide;


    // === PUBLIC ACCESSORS (to be used by fragments)

    public Toolbar getSelectionToolbar() {
        return selectionToolbar;
    }

    public ImageView getSortDirectionButton() {
        return sortDirectionButton;
    }

    public View getSortReshuffleButton() {
        return sortReshuffleButton;
    }

    public TextView getSortFieldButton() {
        return sortFieldButton;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<Attribute> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<Attribute> metadata) {
        this.metadata = metadata;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        updateToolbar();
    }

    public void toggleEditMode() {
        setEditMode(!editMode);
    }

    public boolean isGroupFavsChecked() {
        return isGroupFavsChecked;
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
        viewModel.isCustomGroupingAvailable().observe(this, b -> this.isCustomGroupingAvailable = b);

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        Preferences.registerPrefsChangedListener(prefsListener);

        initUI();
        initToolbar();
        initSelectionToolbar();

        onCreated();
        sortCommandsAutoHide = new Debouncer<>(this, 3000, this::hideSearchSortBar);

        EventBus.getDefault().register(this);
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
        EventBus.getDefault().unregister(this);
        if (archiveNotificationManager != null) archiveNotificationManager.cancel();

        // Empty all handlers to avoid leaks
        if (toolbar != null) toolbar.setOnMenuItemClickListener(null);
        if (selectionToolbar != null) {
            selectionToolbar.setOnMenuItemClickListener(null);
            selectionToolbar.setNavigationOnClickListener(null);
        }
        super.onDestroy();
    }

    private void onCreated() {
        // Display search bar tooltip _after_ the left drawer closes (else it displays over it)
        if (Preferences.isFirstRunProcessComplete())
            TooltipHelper.showTooltip(this, R.string.help_search, ArrowOrientation.TOP, toolbar, this);

        // Display permissions alert if required
        if (!PermissionHelper.checkExternalStorageReadWritePermission(this)) {
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
        if (previouslyViewedContent > -1 && previouslyViewedPage > -1 && !ImageViewerActivity.isRunning) {
            Snackbar snackbar = Snackbar.make(viewPager, R.string.resume_closed, BaseTransientBottomBar.LENGTH_LONG);
            snackbar.setAction(R.string.resume, v -> {
                Timber.i("Reopening book %d from page %d", previouslyViewedContent, previouslyViewedPage);
                CollectionDAO dao = new ObjectBoxDAO(this);
                try {
                    Content c = dao.selectContent(previouslyViewedContent);
                    if (c != null)
                        ContentHelper.openHentoidViewer(this, c, previouslyViewedPage, viewModel.getSearchManagerBundle());
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
        searchSortBar = findViewById(R.id.advanced_search_background);

        // "Group by" menu
        View groupByButton = findViewById(R.id.group_by_btn);
        groupByButton.setOnClickListener(this::onGroupByButtonClick);

        // Link to advanced search
        advancedSearchButton = findViewById(R.id.advanced_search_btn);
        advancedSearchButton.setOnClickListener(v -> onAdvancedSearchButtonClick());

        // "Show artists/groups" menu (group tab only when Grouping.ARTIST is on)
        showArtistsGroupsButton = findViewById(R.id.groups_visibility_btn);
        showArtistsGroupsButton.setText(getArtistsGroupsTextFromPrefs());
        showArtistsGroupsButton.setOnClickListener(this::onGroupsVisibilityButtonClick);

        // Clear search
        searchClearButton = findViewById(R.id.search_clear_btn);
        searchClearButton.setOnClickListener(v -> {
            query = "";
            metadata.clear();
            actionSearchView.setQuery("", false);
            hideSearchSortBar(false);
            signalCurrentFragment(EV_SEARCH, "");
        });

        // Sort controls
        sortDirectionButton = findViewById(R.id.sort_direction_btn);
        sortReshuffleButton = findViewById(R.id.sort_reshuffle_btn);
        sortFieldButton = findViewById(R.id.sort_field_btn);

        // Main tabs
        viewPager = findViewById(R.id.library_pager);
        viewPager.setUserInputEnabled(false); // Disable swipe to change tabs
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                enableCurrentFragment();
                hideSearchSortBar(false);
                updateToolbar();
                updateSelectionToolbar(0, 0);
            }
        });

        updateDisplay();
    }

    private void updateDisplay() {
        FragmentStateAdapter pagerAdapter = new LibraryPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        pagerAdapter.notifyDataSetChanged();
        enableCurrentFragment();
    }

    private void initToolbar() {
        toolbar = findViewById(R.id.library_toolbar);
        toolbar.setNavigationOnClickListener(v -> openNavigationDrawer());

        searchMenu = toolbar.getMenu().findItem(R.id.action_search);
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                showSearchSortBar(true, false, null);
                invalidateNextQueryTextChange = true;

                // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                if (!query.isEmpty())
                    // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        invalidateNextQueryTextChange = true;
                        actionSearchView.setQuery(query, false);
                    }, 100);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (!isSearchQueryActive()) {
                    hideSearchSortBar(false);
                }
                invalidateNextQueryTextChange = true;
                return true;
            }
        });
        completedFilterMenu = toolbar.getMenu().findItem(R.id.action_completed_filter);
        favsMenu = toolbar.getMenu().findItem(R.id.action_favourites);
        updateFavouriteFilter();

        reorderMenu = toolbar.getMenu().findItem(R.id.action_edit);
        reorderCancelMenu = toolbar.getMenu().findItem(R.id.action_edit_cancel);
        newGroupMenu = toolbar.getMenu().findItem(R.id.action_group_new);
        sortMenu = toolbar.getMenu().findItem(R.id.action_order);

        actionSearchView = (SearchView) searchMenu.getActionView();
        actionSearchView.setIconifiedByDefault(true);
        actionSearchView.setQueryHint(getString(R.string.search_hint));
        // Change display when text query is typed
        actionSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                query = s;
                signalCurrentFragment(EV_SEARCH, query);
                actionSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
                    query = "";
                    signalCurrentFragment(EV_SEARCH, query);
                    searchClearButton.setVisibility(View.GONE);
                }

                return true;
            }
        });
        // Update icons visibility
        updateToolbar();
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

    public void sortCommandsAutoHide(boolean hideSortOnly, PopupMenu popup) {
        this.autoHidePopup = popup;
        sortCommandsAutoHide.submit(hideSortOnly);
    }

    public void updateSearchBarOnResults(boolean nonEmptyResults) {
        if (isSearchQueryActive()) {
            showSearchSortBar(true, true, false);
            if (nonEmptyResults) collapseSearchMenu();
        } else {
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
            case R.id.action_completed_filter:
                if (!menuItem.isChecked())
                    askFilterCompleted();
                else {
                    completedFilterMenu.setChecked(!completedFilterMenu.isChecked());
                    updateCompletedFilter();
                    viewModel.resetCompletedFilter();
                }
                break;
            case R.id.action_favourites:
                menuItem.setChecked(!menuItem.isChecked());
                updateFavouriteFilter();
                if (isGroupDisplayed()) {
                    isGroupFavsChecked = menuItem.isChecked();
                    viewModel.searchGroup(Preferences.getGroupingDisplay(), query, Preferences.getGroupSortField(), Preferences.isGroupSortDesc(), Preferences.getArtistGroupVisibility(), isGroupFavsChecked);
                } else
                    viewModel.toggleContentFavouriteFilter();
                break;
            case R.id.action_order:
                showSearchSortBar(null, null, true);
                sortCommandsAutoHide.submit(true);
                break;
            default:
                return false;
        }
        return true;
    }


    private void showSearchSortBar(Boolean showAdvancedSearch, Boolean showClear, Boolean showSort) {
        if (showSort != null && showSort && View.VISIBLE == sortFieldButton.getVisibility()) {
            hideSearchSortBar(View.GONE != advancedSearchButton.getVisibility());
            return;
        }

        searchSortBar.setVisibility(View.VISIBLE);
        if (showAdvancedSearch != null)
            advancedSearchButton.setVisibility(showAdvancedSearch && !isGroupDisplayed() ? View.VISIBLE : View.GONE);

        if (showClear != null)
            searchClearButton.setVisibility(showClear ? View.VISIBLE : View.GONE);

        if (showSort != null) {
            sortFieldButton.setVisibility(showSort ? View.VISIBLE : View.GONE);
            if (showSort) {
                boolean isRandom = (!isGroupDisplayed() && Preferences.Constant.ORDER_FIELD_RANDOM == Preferences.getContentSortField());
                sortDirectionButton.setVisibility(isRandom ? View.GONE : View.VISIBLE);
                sortReshuffleButton.setVisibility(isRandom ? View.VISIBLE : View.GONE);
                searchClearButton.setVisibility(View.GONE);
            } else {
                sortDirectionButton.setVisibility(View.GONE);
                sortReshuffleButton.setVisibility(View.GONE);
            }
        }

        if (isGroupDisplayed() && Preferences.getGroupingDisplay().equals(Grouping.ARTIST)) {
            showArtistsGroupsButton.setVisibility(View.VISIBLE);
        } else {
            showArtistsGroupsButton.setVisibility(View.GONE);
        }
    }

    public void hideSearchSortBar(boolean hideSortOnly) {
        boolean isSearchVisible = (View.VISIBLE == advancedSearchButton.getVisibility() || View.VISIBLE == searchClearButton.getVisibility());

        if (!hideSortOnly || !isSearchVisible)
            searchSortBar.setVisibility(View.GONE);

        if (!hideSortOnly) {
            advancedSearchButton.setVisibility(View.GONE);
            searchClearButton.setVisibility(View.GONE);
        }

        sortDirectionButton.setVisibility(View.GONE);
        sortReshuffleButton.setVisibility(View.GONE);
        sortFieldButton.setVisibility(View.GONE);
        showArtistsGroupsButton.setVisibility(View.GONE);

        if (autoHidePopup != null) autoHidePopup.dismiss();

        // Restore CLEAR button if it's needed
        if (hideSortOnly && isSearchQueryActive()) searchClearButton.setVisibility(View.VISIBLE);
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

    private void initSelectionToolbar() {
        selectionToolbar = findViewById(R.id.library_selection_toolbar);
        selectionToolbar.getMenu().clear();
        selectionToolbar.inflateMenu(R.menu.library_selection_menu);

        editNameMenu = selectionToolbar.getMenu().findItem(R.id.action_edit_name);
        deleteMenu = selectionToolbar.getMenu().findItem(R.id.action_delete);
        completedMenu = selectionToolbar.getMenu().findItem(R.id.action_completed);
        shareMenu = selectionToolbar.getMenu().findItem(R.id.action_share);
        archiveMenu = selectionToolbar.getMenu().findItem(R.id.action_archive);
        changeGroupMenu = selectionToolbar.getMenu().findItem(R.id.action_change_group);
        folderMenu = selectionToolbar.getMenu().findItem(R.id.action_open_folder);
        redownloadMenu = selectionToolbar.getMenu().findItem(R.id.action_redownload);
        coverMenu = selectionToolbar.getMenu().findItem(R.id.action_set_cover);

        updateSelectionToolbar(0, 0);
    }

    private Grouping getGroupingFromMenuId(@IdRes int menuId) {
        switch (menuId) {
            case (R.id.groups_flat):
                return Grouping.FLAT;
            case (R.id.groups_by_artist):
                return Grouping.ARTIST;
            case (R.id.groups_by_dl_date):
                return Grouping.DL_DATE;
            case (R.id.groups_custom):
                return Grouping.CUSTOM;
            default:
                return Grouping.NONE;
        }
    }

    private @IdRes
    int getMenuIdFromGrouping(Grouping grouping) {
        switch (grouping) {
            case ARTIST:
                return R.id.groups_by_artist;
            case DL_DATE:
                return R.id.groups_by_dl_date;
            case CUSTOM:
                return R.id.groups_custom;
            case FLAT:
            case NONE:
            default:
                return R.id.groups_flat;
        }
    }

    private int getVisibilityCodeFromMenuId(@IdRes int menuId) {
        switch (menuId) {
            case (R.id.show_artists):
                return Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS;
            case (R.id.show_groups):
                return Preferences.Constant.ARTIST_GROUP_VISIBILITY_GROUPS;
            case (R.id.show_artists_and_groups):
            default:
                return Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS;
        }
    }

    /**
     * Update completed filter button appearance on the action bar
     */
    private void updateCompletedFilter() {
        completedFilterMenu.setIcon(completedFilterMenu.isChecked() ? R.drawable.ic_completed_filter_on : R.drawable.ic_completed_filter_off);
    }

    /**
     * Update favourite filter button appearance on the action bar
     */
    private void updateFavouriteFilter() {
        favsMenu.setIcon(favsMenu.isChecked() ? R.drawable.ic_filter_favs_on : R.drawable.ic_filter_favs_off);
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
                Intent intent = getIntent();
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                finish();
                startActivity(intent);
                break;
            case Preferences.Key.SD_STORAGE_URI:
            case Preferences.Key.EXTERNAL_LIBRARY_URI:
                Preferences.setGroupingDisplay(Grouping.FLAT.getId());
                viewModel.setGroup(null);
                updateDisplay();
                break;
            case Preferences.Key.GROUPING_DISPLAY:
            case Preferences.Key.ARTIST_GROUP_VISIBILITY:
                viewModel.setGrouping(Preferences.getGroupingDisplay(), Preferences.getGroupSortField(), Preferences.isGroupSortDesc(), Preferences.getArtistGroupVisibility(), isGroupFavsChecked);
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

    /**
     * Handler for the "Group by" button
     */
    private void onGroupByButtonClick(View groupByButton) {
        // Load and display the field popup menu
        PopupMenu popup = new PopupMenu(this, groupByButton);
        popup.getMenuInflater()
                .inflate(R.menu.library_groups_popup, popup.getMenu());

        popup.getMenu().findItem(R.id.groups_custom).setVisible(isCustomGroupingAvailable);

        // Mark current grouping
        MenuItem currentItem = popup.getMenu().findItem(getMenuIdFromGrouping(Preferences.getGroupingDisplay()));
        currentItem.setTitle(currentItem.getTitle() + " <");

        popup.setOnMenuItemClickListener(item -> {
            Grouping currentGrouping = Preferences.getGroupingDisplay();
            Grouping selectedGrouping = getGroupingFromMenuId(item.getItemId());
            // Don't do anything if the current group is selected
            if (currentGrouping.equals(selectedGrouping)) return false;

            Preferences.setGroupingDisplay(selectedGrouping.getId());
            isGroupFavsChecked = false;
            favsMenu.setChecked(false);
            updateFavouriteFilter();

            // Reset custom book ordering if reverting to a grouping where that doesn't apply
            if (!selectedGrouping.canReorderBooks()
                    && Preferences.Constant.ORDER_FIELD_CUSTOM == Preferences.getContentSortField()) {
                Preferences.setContentSortField(Preferences.Default.ORDER_CONTENT_FIELD);
            }
            // Reset custom group ordering if reverting to a grouping where that doesn't apply
            if (!selectedGrouping.canReorderGroups()
                    && Preferences.Constant.ORDER_FIELD_CUSTOM == Preferences.getGroupSortField()) {
                Preferences.setGroupSortField(Preferences.Default.ORDER_GROUP_FIELD);
            }

            // Go back to groups tab if we're not
            goBackToGroups();

            // Update screen display if needed (flat <-> the rest)
            if (currentGrouping.equals(Grouping.FLAT) || selectedGrouping.equals(Grouping.FLAT))
                updateDisplay();
            sortCommandsAutoHide(true, popup);
            return true;
        });
        popup.show(); //showing popup menu
        sortCommandsAutoHide(true, popup);
    }

    private String getArtistsGroupsTextFromPrefs() {
        switch (Preferences.getArtistGroupVisibility()) {
            case Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS:
                return getResources().getString(R.string.show_artists);
            case Preferences.Constant.ARTIST_GROUP_VISIBILITY_GROUPS:
                return getResources().getString(R.string.show_groups);
            case Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS:
                return getResources().getString(R.string.show_artists_and_groups);
            default:
                return "";
        }
    }

    /**
     * Handler for the "Show artists/groups" button
     */
    private void onGroupsVisibilityButtonClick(View groupsVisibilityButton) {
        // Load and display the visibility popup menu
        PopupMenu popup = new PopupMenu(this, groupsVisibilityButton);
        popup.getMenuInflater().inflate(R.menu.library_groups_visibility_popup, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            item.setChecked(true);
            int code = getVisibilityCodeFromMenuId(item.getItemId());
            Preferences.setArtistGroupVisibility(code);
            showArtistsGroupsButton.setText(getArtistsGroupsTextFromPrefs());
            sortCommandsAutoHide(true, popup);
            return true;
        });
        popup.show();
        sortCommandsAutoHide(true, popup);
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    public void updateTitle(long totalSelectedCount, long totalCount) {
        String title;
        if (totalSelectedCount == totalCount)
            title = totalCount + " items";
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
        return (!query.isEmpty() || !metadata.isEmpty());
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
        return (0 == viewPager.getCurrentItem() && !Preferences.getGroupingDisplay().equals(Grouping.FLAT));
    }

    public void goBackToGroups() {
        if (isGroupDisplayed()) return;

        enableFragment(0);
        viewModel.searchGroup(Preferences.getGroupingDisplay(), query, Preferences.getGroupSortField(), Preferences.isGroupSortDesc(), Preferences.getArtistGroupVisibility(), isGroupFavsChecked);
        viewPager.setCurrentItem(0);
        if (titles.containsKey(0)) toolbar.setTitle(titles.get(0));
    }

    public void showBooksInGroup(me.devsaki.hentoid.database.domains.Group group) {
        enableFragment(1);
        viewModel.setGroup(group);
        viewPager.setCurrentItem(1);
    }

    private void updateToolbar() {
        Grouping currentGrouping = Preferences.getGroupingDisplay();

        searchMenu.setVisible(!editMode);
        newGroupMenu.setVisible(!editMode && isGroupDisplayed() && currentGrouping.canReorderGroups()); // Custom groups only
        favsMenu.setVisible(!editMode);
        reorderMenu.setIcon(editMode ? R.drawable.ic_check : R.drawable.ic_reorder_lines);
        reorderCancelMenu.setVisible(editMode);
        sortMenu.setVisible(!editMode);
        completedFilterMenu.setVisible(!editMode && !isGroupDisplayed());

        if (isGroupDisplayed()) reorderMenu.setVisible(currentGrouping.canReorderGroups());
        else reorderMenu.setVisible(currentGrouping.canReorderBooks());

        signalCurrentFragment(EV_UPDATE_SORT, null);
    }

    public void updateSelectionToolbar(long selectedTotalCount, long selectedLocalCount) {
        boolean isMultipleSelection = selectedTotalCount > 1;
        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedTotalCount, (int) selectedTotalCount));

        if (isGroupDisplayed()) {
            editNameMenu.setVisible(!isMultipleSelection && Preferences.getGroupingDisplay().canReorderGroups());
            deleteMenu.setVisible(true);
            shareMenu.setVisible(false);
            completedMenu.setVisible(false);
            archiveMenu.setVisible(true);
            changeGroupMenu.setVisible(false);
            folderMenu.setVisible(false);
            redownloadMenu.setVisible(false);
            coverMenu.setVisible(false);
        } else {
            editNameMenu.setVisible(false);
            deleteMenu.setVisible(selectedLocalCount > 0 || Preferences.isDeleteExternalLibrary());
            completedMenu.setVisible(true);
            shareMenu.setVisible(!isMultipleSelection && 1 == selectedLocalCount);
            archiveMenu.setVisible(true);
            changeGroupMenu.setVisible(true);
            folderMenu.setVisible(!isMultipleSelection);
            redownloadMenu.setVisible(selectedLocalCount > 0);
            coverMenu.setVisible(!isMultipleSelection && !Preferences.getGroupingDisplay().equals(Grouping.FLAT));
        }
    }

    public void askFilterCompleted() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        String title = getString(R.string.ask_filter_completed);
        builder.setMessage(title)
                .setPositiveButton(R.string.filter_not_completed,
                        (dialog, which) -> {
                            completedFilterMenu.setChecked(!completedFilterMenu.isChecked());
                            updateCompletedFilter();
                            viewModel.toggleNotCompletedFilter();
                        })
                .setNegativeButton(R.string.filter_completed,
                        (dialog, which) -> {
                            completedFilterMenu.setChecked(!completedFilterMenu.isChecked());
                            updateCompletedFilter();
                            viewModel.toggleCompletedFilter();
                        })
                .create().show();
    }


    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param contents Items to be deleted if the answer is yes
     */
    public void askDeleteItems(
            @NonNull final List<Content> contents,
            @NonNull final List<me.devsaki.hentoid.database.domains.Group> groups,
            @Nullable final Runnable onSuccess,
            @NonNull final SelectExtension<?> selectExtension) {
        // TODO display the number of books and groups that will be deleted
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        int count = !groups.isEmpty() ? groups.size() : contents.size();
        String title = getResources().getQuantityString(R.plurals.ask_delete_multiple, count);
        builder.setMessage(title)
                .setPositiveButton(R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect(selectExtension.getSelections());
                            viewModel.deleteItems(contents, groups, false);
                        })
                .setNegativeButton(R.string.no,
                        (dialog, which) -> selectExtension.deselect(selectExtension.getSelections()))
                .setOnCancelListener(dialog -> selectExtension.deselect(selectExtension.getSelections()))
                .create().show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProcessEvent(ProcessEvent event) {
        // Filter on delete complete event
        if (R.id.delete_service != event.processId) return;
        if (ProcessEvent.EventType.COMPLETE != event.eventType) return;
        String msg = "";
        int nbGroups = event.elementsOKOther;
        int nbContent = event.elementsOK;
        if (nbGroups > 0)
            msg += getResources().getQuantityString(R.plurals.delete_success_groups, nbGroups, nbGroups);
        if (nbContent > 0) {
            if (!msg.isEmpty()) msg += " and ";
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
        // TODO display the number of books to archive
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        String title = getResources().getQuantityString(R.plurals.ask_archive_multiple, items.size());
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
                .setAction("OPEN FOLDER", v -> FileHelper.openFile(this, FileHelper.getDownloadsFolder()))
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
        signalFragment(isGroupDisplayed() ? 0 : 1, eventType, message);
    }

    private void signalFragment(int fragmentIndex, int eventType, @Nullable String message) {
        EventBus.getDefault().post(new CommunicationEvent(eventType, (0 == fragmentIndex) ? RC_GROUPS : RC_CONTENTS, message));
    }

    private void enableCurrentFragment() {
        if (isGroupDisplayed()) enableFragment(0);
        else enableFragment(1);
    }

    private void enableFragment(int fragmentIndex) {
        EventBus.getDefault().post(new CommunicationEvent(EV_ENABLE, (0 == fragmentIndex) ? RC_GROUPS : RC_CONTENTS, null));
        EventBus.getDefault().post(new CommunicationEvent(EV_DISABLE, (0 == fragmentIndex) ? RC_CONTENTS : RC_GROUPS, null));
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
            if (Grouping.FLAT.equals(Preferences.getGroupingDisplay())) {
                return new LibraryContentFragment();
            } else {
                if (0 == position) {
                    return new LibraryGroupsFragment();
                } else {
                    return new LibraryContentFragment();
                }
            }
        }

        @Override
        public int getItemCount() {
            return (Grouping.FLAT.equals(Preferences.getGroupingDisplay())) ? 1 : 2;
        }
    }
}
