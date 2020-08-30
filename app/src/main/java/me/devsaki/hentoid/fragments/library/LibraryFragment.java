package me.devsaki.hentoid.fragments.library;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.Group;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.mikepenz.fastadapter.extensions.ExtensionsFactories;
import com.mikepenz.fastadapter.select.SelectExtensionFactory;
import com.skydoves.balloon.ArrowOrientation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.SearchActivity;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.TooltipUtil;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class LibraryFragment extends Fragment {

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
    private MenuItem itemDelete;
    private MenuItem itemShare;
    private MenuItem itemArchive;
    private MenuItem itemFolder;
    private MenuItem itemRedownload;
    private MenuItem itemDeleteAll;


    // ======== VARIABLES
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;

    // Used to auto-hide the sort controls bar when no activity is detected
    private final Debouncer<Boolean> sortCommandsAutoHide = new Debouncer<>(2500, this::hideSearchSortBar);


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ExtensionsFactories.INSTANCE.register(new SelectExtensionFactory());
        EventBus.getDefault().register(this);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_library, container, false);

        Preferences.registerPrefsChangedListener(prefsListener);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(LibraryViewModel.class);

        initUI(rootView);
        initToolbar(rootView);
        initSelectionToolbar(rootView);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Display search bar tooltip _after_ the left drawer closes (else it displays over it)
        if (Preferences.isFirstRunProcessComplete())
            TooltipUtil.showTooltip(requireContext(), R.string.help_search, ArrowOrientation.TOP, toolbar, getViewLifecycleOwner());

        // Display permissions alert if required
        if (!PermissionUtil.checkExternalStorageReadWritePermission(requireActivity())) {
            ((TextView) requireViewById(view, R.id.library_alert_txt)).setText(R.string.permissions_lost);
            requireViewById(view, R.id.library_alert_fix_btn).setOnClickListener(v -> fixPermissions());
            permissionsAlertBar.setVisibility(View.VISIBLE);
        } else if (isLowOnSpace()) { // Else display low space alert
            ((TextView) requireViewById(view, R.id.library_alert_txt)).setText(R.string.low_memory);
            permissionsAlertBar.setVisibility(View.GONE);
            storageAlertBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Initialize the UI components
     *
     * @param rootView Root view of the library screen
     */
    private void initUI(@NonNull View rootView) {
        // Permissions alert bar
        permissionsAlertBar = requireViewById(rootView, R.id.library_permissions_alert_group);
        storageAlertBar = requireViewById(rootView, R.id.library_storage_alert_group);

        // Search bar
        advancedSearchBar = requireViewById(rootView, R.id.advanced_search_background);

        // Link to advanced search
        advancedSearchButton = requireViewById(rootView, R.id.advanced_search_btn);
        advancedSearchButton.setOnClickListener(v -> onAdvancedSearchButtonClick());

        // Clear search
        searchClearButton = requireViewById(rootView, R.id.search_clear_btn);
        searchClearButton.setOnClickListener(v -> {
            mainSearchView.setQuery("", false);
            hideSearchSortBar(false);
            viewModel.searchUniversal("");
        });

        // Sort controls
        sortDirectionButton = requireViewById(rootView, R.id.sort_direction_btn);
        sortDirectionButton.setImageResource(Preferences.isContentSortDesc() ? R.drawable.ic_simple_arrow_up : R.drawable.ic_simple_arrow_down);
        sortDirectionButton.setOnClickListener(v -> {
            boolean sortDesc = !Preferences.isContentSortDesc();
            Preferences.setContentSortDesc(sortDesc);
            // Update icon
            sortDirectionButton.setImageResource(sortDesc ? R.drawable.ic_simple_arrow_up : R.drawable.ic_simple_arrow_down);
            // Run a new search
            viewModel.updateOrder();
            sortCommandsAutoHide.submit(true);
        });
        sortFieldButton = requireViewById(rootView, R.id.sort_field_btn);
        sortFieldButton.setText(getNameFromFieldCode(Preferences.getContentSortField()));
        sortFieldButton.setOnClickListener(v -> {
            // Load and display the field popup menu
            PopupMenu popup = new PopupMenu(requireContext(), sortDirectionButton);
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

        FragmentStateAdapter pagerAdapter = new LibraryPagerAdapter(getActivity());
        ViewPager2 viewPager = requireViewById(rootView, R.id.library_pager);
        viewPager.setUserInputEnabled(false); // Disable swipe to change tabs
        viewPager.setAdapter(pagerAdapter);
    }

    private void initToolbar(@NonNull View rootView) {
        toolbar = requireViewById(rootView, R.id.library_toolbar);
        Activity activity = requireActivity();
        toolbar.setNavigationOnClickListener(v -> ((LibraryActivity) activity).openNavigationDrawer());

        searchMenu = toolbar.getMenu().findItem(R.id.action_search);
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                showSearchSortBar(true, false, null);
                invalidateNextQueryTextChange = true;

                // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                /*
                if (!query.isEmpty())
                    // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                    new Handler().postDelayed(() -> {
                        invalidateNextQueryTextChange = true;
                        mainSearchView.setQuery(query, false);
                    }, 100);

                 */

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                /*
                if (!isSearchQueryActive()) {
                    hideSearchSortBar(false);
                }

                 */
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
//                query = s;
//                viewModel.searchUniversal(query);
                mainSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
//                    query = "";
//                    viewModel.searchUniversal(query);
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

    private int getFieldCodeFromMenuId(@IdRes int menuId) {
        switch (menuId) {
            case (R.id.sort_title):
                return Preferences.Constant.ORDER_FIELD_TITLE;
            case (R.id.sort_artist):
                return Preferences.Constant.ORDER_FIELD_ARTIST;
            case (R.id.sort_pages):
                return Preferences.Constant.ORDER_FIELD_NB_PAGES;
            case (R.id.sort_dl_date):
                return Preferences.Constant.ORDER_FIELD_DOWNLOAD_DATE;
            case (R.id.sort_read_date):
                return Preferences.Constant.ORDER_FIELD_READ_DATE;
            case (R.id.sort_reads):
                return Preferences.Constant.ORDER_FIELD_READS;
            case (R.id.sort_size):
                return Preferences.Constant.ORDER_FIELD_SIZE;
            case (R.id.sort_random):
                return Preferences.Constant.ORDER_FIELD_RANDOM;
            default:
                return Preferences.Constant.ORDER_FIELD_NONE;
        }
    }

    private int getNameFromFieldCode(int prefFieldCode) {
        switch (prefFieldCode) {
            case (Preferences.Constant.ORDER_FIELD_TITLE):
                return R.string.sort_title;
            case (Preferences.Constant.ORDER_FIELD_ARTIST):
                return R.string.sort_artist;
            case (Preferences.Constant.ORDER_FIELD_NB_PAGES):
                return R.string.sort_pages;
            case (Preferences.Constant.ORDER_FIELD_DOWNLOAD_DATE):
                return R.string.sort_dl_date;
            case (Preferences.Constant.ORDER_FIELD_READ_DATE):
                return R.string.sort_read_date;
            case (Preferences.Constant.ORDER_FIELD_READS):
                return R.string.sort_reads;
            case (Preferences.Constant.ORDER_FIELD_SIZE):
                return R.string.sort_size;
            case (Preferences.Constant.ORDER_FIELD_RANDOM):
                return R.string.sort_random;
            default:
                return R.string.sort_invalid;
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
//        if (hideSortOnly && isSearchActive) searchClearButton.setVisibility(View.VISIBLE);
    }

    private void initSelectionToolbar(@NonNull View rootView) {
        selectionToolbar = requireViewById(rootView, R.id.library_selection_toolbar);
        selectionToolbar.setNavigationOnClickListener(v -> {
//            selectExtension.deselect();
            selectionToolbar.setVisibility(View.GONE);
        });

        itemDelete = selectionToolbar.getMenu().findItem(R.id.action_delete);
        itemShare = selectionToolbar.getMenu().findItem(R.id.action_share);
        itemArchive = selectionToolbar.getMenu().findItem(R.id.action_archive);
        itemFolder = selectionToolbar.getMenu().findItem(R.id.action_open_folder);
        itemRedownload = selectionToolbar.getMenu().findItem(R.id.action_redownload);
        itemDeleteAll = selectionToolbar.getMenu().findItem(R.id.action_delete_all);
    }

    private void updateSelectionToolbar(long selectedTotalCount, long selectedLocalCount) {
        boolean isMultipleSelection = selectedTotalCount > 1;

        itemDelete.setVisible(!isMultipleSelection && (1 == selectedLocalCount || Preferences.isDeleteExternalLibrary()));
        itemShare.setVisible(!isMultipleSelection && 1 == selectedLocalCount);
        itemArchive.setVisible(!isMultipleSelection);
        itemFolder.setVisible(!isMultipleSelection);
        itemRedownload.setVisible(selectedLocalCount > 0);
        itemDeleteAll.setVisible(isMultipleSelection && (selectedLocalCount > 0 || Preferences.isDeleteExternalLibrary()));

        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedTotalCount, (int) selectedTotalCount));
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
        if (!BuildConfig.DEBUG) UpdateSuccessDialogFragment.invoke(getParentFragmentManager());
    }

    /**
     * Called when returning from the Advanced Search screen
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 999
                && resultCode == Activity.RESULT_OK
                && data != null && data.getExtras() != null) {
            Uri searchUri = new SearchActivityBundle.Parser(data.getExtras()).getUri();

            /*
            if (searchUri != null) {
                query = searchUri.getPath();
                metadata = SearchActivityBundle.Parser.parseSearchUri(searchUri);
                viewModel.search(query, metadata);
            }

             */
        }
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
            Intent intent = requireActivity().getIntent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            requireActivity().finish();
            startActivity(intent);
        }
    }

    /**
     * Handler for the "Advanced search" button
     */
    private void onAdvancedSearchButtonClick() {
        Intent search = new Intent(this.getContext(), SearchActivity.class);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder();
/*
        if (!metadata.isEmpty())
            builder.setUri(SearchActivityBundle.Builder.buildSearchUri(metadata));

 */
        search.putExtras(builder.getBundle());

        startActivityForResult(search, 999);
        searchMenu.collapseActionView();
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

    private void fixPermissions() {
        PermissionUtil.requestExternalStorageReadWritePermission(this, PermissionUtil.RQST_STORAGE_PERMISSION);
    }

    private boolean isLowOnSpace() {
        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(requireActivity(), Preferences.getStorageUri());
        if (null == rootFolder) return false;

        double freeSpaceRatio = new FileHelper.MemoryUsageFigures(requireActivity(), rootFolder).getFreeUsageRatio100();
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

    private static class LibraryPagerAdapter extends FragmentStateAdapter {
        LibraryPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (Grouping.FLAT.getId() == Preferences.getGroupingDisplay()) {
                return new LibraryBooksFragment();
            } else {
                if (0 == position) return new LibraryGroupsFragment();
                else return new LibraryBooksFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
