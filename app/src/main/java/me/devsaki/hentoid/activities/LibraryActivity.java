package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.Group;
import androidx.core.view.GravityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.annimon.stream.function.Consumer;
import com.skydoves.balloon.ArrowOrientation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.fragments.library.LibraryBooksFragment;
import me.devsaki.hentoid.fragments.library.LibraryGroupsFragment;
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.TooltipUtil;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

public class LibraryActivity extends BaseActivity {

    private DrawerLayout drawerLayout;

    private OnBackPressedCallback callback;


    // ======== COMMUNICATION
    // Viewmodel
    private LibraryViewModel viewModel;
    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> onSharedPreferenceChanged(k);


    // ======== UI
    // Action view associated with search menu button
    private SearchView mainSearchView;

    // ==== Advanced search / sort bar
    // Grey background of the advanced search / sort bar
    private View advancedSearchBar;
    // Advanced search text button
    private View advancedSearchButton;
    // CLEAR button
    private TextView searchClearButton;
    // Sort direction button
    private ImageView sortDirectionButton;
    // Sort field button
    private TextView sortFieldButton;

    // === TOOLBAR
    private Toolbar toolbar;
    // "Search" button on top menu
    private MenuItem searchMenu;
    // "Toggle favourites" button on top menu
    private MenuItem favsMenu;
    // Alert bars
    private Group permissionsAlertBar;
    private Group storageAlertBar;

    // === SELECTION TOOLBAR
    private Toolbar selectionToolbar;

    private ViewPager2 viewPager;

    // === SEARCH CALLBACKS
    private List<Consumer<String>> searchAction = new ArrayList<>();
    private List<Runnable> advSearchAction = new ArrayList<>();


    // ======== VARIABLES
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;
    // Current text search query
    private String query = "";
    // Current metadata search query
    private List<Attribute> metadata = Collections.emptyList();


    // Used to auto-hide the sort controls bar when no activity is detected
    private final Debouncer<Boolean> sortCommandsAutoHide = new Debouncer<>(2500, this::hideSearchSortBar);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_library);
        drawerLayout = findViewById(R.id.drawer_layout);

        callback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                closeNavigationDrawer();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            openNavigationDrawer();
            Preferences.setIsFirstRunProcessComplete(true);
        }

        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(LibraryViewModel.class);

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        Preferences.registerPrefsChangedListener(prefsListener);

        initUI();
        initToolbar();
        initSelectionToolbar();

        onCreated();

        EventBus.getDefault().register(this);
    }

    private void onCreated() {
        toolbar.setOnMenuItemClickListener(this::toolbarOnItemClicked);

        // Display search bar tooltip _after_ the left drawer closes (else it displays over it)
        if (Preferences.isFirstRunProcessComplete())
            TooltipUtil.showTooltip(this, R.string.help_search, ArrowOrientation.TOP, toolbar, this);

        // Display permissions alert if required
        if (!PermissionUtil.checkExternalStorageReadWritePermission(this)) {
            ((TextView) findViewById(R.id.library_alert_txt)).setText(R.string.permissions_lost);
            findViewById(R.id.library_alert_fix_btn).setOnClickListener(v -> fixPermissions());
            permissionsAlertBar.setVisibility(View.VISIBLE);
        } else if (isLowOnSpace()) { // Else display low space alert
            ((TextView) findViewById(R.id.library_alert_txt)).setText(R.string.low_memory);
            permissionsAlertBar.setVisibility(View.GONE);
            storageAlertBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Initialize the UI components
     */
    private void initUI() {
        // Permissions alert bar
        permissionsAlertBar = findViewById(R.id.library_permissions_alert_group);
        storageAlertBar = findViewById(R.id.library_storage_alert_group);

        // Search bar
        advancedSearchBar = findViewById(R.id.advanced_search_background);

        // Link to advanced search
        advancedSearchButton = findViewById(R.id.advanced_search_btn);
        advancedSearchButton.setOnClickListener(v -> onAdvancedSearchButtonClick());

        // Clear search
        searchClearButton = findViewById(R.id.search_clear_btn);
        searchClearButton.setOnClickListener(v -> {
            query = "";
            metadata.clear();
            mainSearchView.setQuery("", false);
            hideSearchSortBar(false);
            searchAction.get(viewPager.getCurrentItem()).accept("");
        });

        // Sort controls
        sortDirectionButton = findViewById(R.id.sort_direction_btn);
        /*
        sortDirectionButton.setImageResource(Preferences.isContentSortDesc() ? R.drawable.ic_simple_arrow_down : R.drawable.ic_simple_arrow_up);
        sortDirectionButton.setOnClickListener(v -> {
            boolean sortDesc = !Preferences.isContentSortDesc();
            Preferences.setContentSortDesc(sortDesc);
            // Update icon
            sortDirectionButton.setImageResource(sortDesc ? R.drawable.ic_simple_arrow_down : R.drawable.ic_simple_arrow_up);
            // Run a new search
            viewModel.updateOrder();
            sortCommandsAutoHide.submit(true);
        });

         */
        sortFieldButton = findViewById(R.id.sort_field_btn);
        /*
        sortFieldButton.setText(getNameFromFieldCode(Preferences.getContentSortField()));
        sortFieldButton.setOnClickListener(v -> {
            // Load and display the field popup menu
            PopupMenu popup = new PopupMenu(this, sortDirectionButton);
            popup.getMenuInflater()
                    .inflate(R.menu.library_sort_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                // Update button text
                sortFieldButton.setText(item.getTitle());
                item.setChecked(true);
                int fieldCode = getFieldCodeFromMenuId(item.getItemId());
                if (fieldCode == Preferences.Constant.ORDER_FIELD_RANDOM)
                    RandomSeedSingleton.getInstance().renewSeed();

                Preferences.setContentSortField(fieldCode);
                // Run a new search
                viewModel.updateOrder();
                sortCommandsAutoHide.submit(true);
                return true;
            });
            popup.show(); //showing popup menu
            sortCommandsAutoHide.submit(true);
        }); //closing the setOnClickListener method

         */

        FragmentStateAdapter pagerAdapter = new LibraryPagerAdapter(this);
        viewPager = findViewById(R.id.library_pager);
        viewPager.setUserInputEnabled(false); // Disable swipe to change tabs
        viewPager.setAdapter(pagerAdapter);
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
                    new Handler().postDelayed(() -> {
                        invalidateNextQueryTextChange = true;
                        mainSearchView.setQuery(query, false);
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

        favsMenu = toolbar.getMenu().findItem(R.id.action_favourites);
        updateFavouriteFilter();

        mainSearchView = (SearchView) searchMenu.getActionView();
        mainSearchView.setIconifiedByDefault(true);
        mainSearchView.setQueryHint(getString(R.string.search_hint));
        // Change display when text query is typed
        mainSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                query = s;
                searchAction.get(viewPager.getCurrentItem()).accept(query);
                mainSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
                    query = "";
                    searchAction.get(viewPager.getCurrentItem()).accept(query);
                    searchClearButton.setVisibility(View.GONE);
                }

                return true;
            }
        });
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    public Toolbar getSelectionToolbar() {
        return selectionToolbar;
    }

    public ImageView getSortDirectionButton() {
        return sortDirectionButton;
    }

    public TextView getSortFieldButton() {
        return sortFieldButton;
    }

    public void sortCommandsAutoHide(boolean hideSortOnly) {
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
    private boolean toolbarOnItemClicked(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_favourites:
                menuItem.setChecked(!menuItem.isChecked());
                updateFavouriteFilter();
                viewModel.toggleFavouriteFilter();
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
        advancedSearchBar.setVisibility(View.VISIBLE);
        if (showAdvancedSearch != null)
            advancedSearchButton.setVisibility(showAdvancedSearch ? View.VISIBLE : View.GONE);

        if (showClear != null)
            searchClearButton.setVisibility(showClear ? View.VISIBLE : View.GONE);

        if (showSort != null) {
            if (showSort) searchClearButton.setVisibility(View.GONE);
            sortDirectionButton.setVisibility(showSort ? View.VISIBLE : View.GONE);
            sortFieldButton.setVisibility(showSort ? View.VISIBLE : View.GONE);
        }
    }

    private void hideSearchSortBar(boolean hideSortOnly) {
        boolean isSearchVisible = (View.VISIBLE == advancedSearchButton.getVisibility() || View.VISIBLE == searchClearButton.getVisibility());

        if (!hideSortOnly || !isSearchVisible)
            advancedSearchBar.setVisibility(View.GONE);

        if (!hideSortOnly) {
            advancedSearchButton.setVisibility(View.GONE);
            searchClearButton.setVisibility(View.GONE);
        }

        sortDirectionButton.setVisibility(View.GONE);
        sortFieldButton.setVisibility(View.GONE);

        // Restore CLEAR button if it's needed
        if (hideSortOnly && isSearchQueryActive()) searchClearButton.setVisibility(View.VISIBLE);
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
    }

    /**
     * Update favourite filter button appearance on the action bar
     */
    private void updateFavouriteFilter() {
        favsMenu.setIcon(favsMenu.isChecked() ? R.drawable.ic_filter_favs_on : R.drawable.ic_filter_favs_off);
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
        super.onDestroy();
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(String key) {
        Timber.i("Prefs change detected : %s", key);
        if (Preferences.Key.PREF_COLOR_THEME.equals(key)) {
            // Restart the app with the library activity on top
            Intent intent = getIntent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            finish();
            startActivity(intent);
        }
    }

    /**
     * Handler for the "Advanced search" button
     */
    private void onAdvancedSearchButtonClick() {
        Runnable action = advSearchAction.get(viewPager.getCurrentItem());
        if (action != null) action.run();
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

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    public boolean isSearchQueryActive() {
        return (!query.isEmpty() || !metadata.isEmpty());
    }

    private void fixPermissions() {
        PermissionUtil.requestExternalStorageReadWritePermission(this, PermissionUtil.RQST_STORAGE_PERMISSION);
    }

    private boolean isLowOnSpace() {
        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(this, Preferences.getStorageUri());
        if (null == rootFolder) return false;

        double freeSpaceRatio = new FileHelper.MemoryUsageFigures(this, rootFolder).getFreeUsageRatio100();
        return (freeSpaceRatio < 100 - Preferences.getMemoryAlertThreshold());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PermissionUtil.RQST_STORAGE_PERMISSION) return;
        if (permissions.length < 2) return;
        if (grantResults.length == 0) return;
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionsAlertBar.setVisibility(View.GONE);
        } // Don't show rationales here; the alert still displayed on screen should be enough
    }

    public void closeNavigationDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
        callback.setEnabled(false);
    }

    public void openNavigationDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
        callback.setEnabled(true);
    }


    private class LibraryPagerAdapter extends FragmentStateAdapter {
        LibraryPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            searchAction.clear();
            if (Grouping.FLAT.getId() == Preferences.getGroupingDisplay()) {
                LibraryBooksFragment result = new LibraryBooksFragment();
                searchAction.add(result::onSearch);
                advSearchAction.add(result::onAdvancedSearchButtonClick);
                return result;
            } else {
                if (0 == position) {
                    LibraryGroupsFragment result = new LibraryGroupsFragment();
//                    searchAction.add(result::onSearch);
//                    advSearchAction.add(result::onAdvancedSearchButtonClick);
                    return result;
                } else {
                    LibraryBooksFragment result = new LibraryBooksFragment();
                    searchAction.add(result::onSearch);
                    advSearchAction.add(result::onAdvancedSearchButtonClick);
                    return result;
                }
            }
        }

        @Override
        public int getItemCount() {
            return (Grouping.FLAT.getId() == Preferences.getGroupingDisplay()) ? 1 : 2;
        }
    }
}
