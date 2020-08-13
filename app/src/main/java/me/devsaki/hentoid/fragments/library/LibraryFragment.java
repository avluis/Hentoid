package me.devsaki.hentoid.fragments.library;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.extensions.ExtensionsFactories;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.paged.PagedModelAdapter;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.select.SelectExtensionFactory;
import com.skydoves.balloon.ArrowOrientation;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.SearchActivity;
import me.devsaki.hentoid.activities.bundles.ContentItemBundle;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.util.TooltipUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.exception.FileNotRemovedException;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.LibraryPager;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.annimon.stream.Collectors.toCollection;

public class LibraryFragment extends Fragment implements ErrorsDialogFragment.Parent {

    private static final String KEY_LAST_LIST_POSITION = "last_list_position";


    // ======== COMMUNICATION
    private OnBackPressedCallback callback;
    // Viewmodel
    private LibraryViewModel viewModel;
    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> onSharedPreferenceChanged(k);


    // ======== UI
    // Wrapper for the bottom pager
    private final LibraryPager pager = new LibraryPager(this::handleNewPage);
    // Text that displays in the background when the list is empty
    private TextView emptyText;
    // Action view associated with search menu button
    private SearchView mainSearchView;
    // Main view where books are displayed
    private RecyclerView recyclerView;
    // LayoutManager of the recyclerView
    private LinearLayoutManager llm;

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

    // === FASTADAPTER COMPONENTS AND HELPERS
    private ItemAdapter<ContentItem> itemAdapter;
    private PagedModelAdapter<Content, ContentItem> pagedItemAdapter;
    private FastAdapter<ContentItem> fastAdapter;
    private SelectExtension<ContentItem> selectExtension;


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;
    // Used to ignore native calls to onBookClick right after that book has been deselected
    private boolean invalidateNextBookClick = false;
    // Total number of books in the whole unfiltered library
    private int totalContentCount;
    // True when a new search has been performed and its results have not been handled yet
    // False when the refresh is passive (i.e. not from a direct user action)
    private boolean newSearch = false;
    // Collection of books according to current filters
    private PagedList<Content> library;
    // Position of top item to memorize or restore (used when activity is destroyed and recreated)
    private int topItemPosition = -1;

    // Used to start processing when the recyclerView has finished updating
    private final Debouncer<Integer> listRefreshDebouncer = new Debouncer<>(75, this::onRecyclerUpdated);
    // Used to auto-hide the sort controls bar when no activity is detected
    private final Debouncer<Boolean> sortCommandsAutoHide = new Debouncer<>(2500, this::hideSearchSortBar);


    // === SEARCH PARAMETERS
    // Current text search query
    private String query = "";
    // Current metadata search query
    private List<Attribute> metadata = Collections.emptyList();


    /**
     * Diff calculation rules for list items
     * <p>
     * Created once and for all to be used by FastAdapter in endless mode (=using Android PagedList)
     */
    private final AsyncDifferConfig<Content> asyncDifferConfig = new AsyncDifferConfig.Builder<>(new DiffUtil.ItemCallback<Content>() {
        @Override
        public boolean areItemsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
//            return oldItem.equals(newItem) && oldItem.getCover().equals(newItem.getCover());
//            return oldItem.equals(newItem) && oldItem.getCoverImageUrl().equals(newItem.getCoverImageUrl());
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return oldItem.getUrl().equalsIgnoreCase(newItem.getUrl())
                    && oldItem.getSite().equals(newItem.getSite())
                    && oldItem.getLastReadDate() == newItem.getLastReadDate()
                    && oldItem.getCoverImageUrl().equals(newItem.getCoverImageUrl())
//                    && oldItem.isBeingDeleted() == newItem.isBeingDeleted()
                    && oldItem.isFavourite() == newItem.isFavourite();
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Content oldItem, @NonNull Content newItem) {
            ContentItemBundle.Builder diffBundleBuilder = new ContentItemBundle.Builder();

            if (oldItem.isFavourite() != newItem.isFavourite()) {
                diffBundleBuilder.setIsFavourite(newItem.isFavourite());
            }
            if (oldItem.getReads() != newItem.getReads()) {
                diffBundleBuilder.setReads(newItem.getReads());
            }
            if (!oldItem.getCoverImageUrl().equals(newItem.getCoverImageUrl())) {
                diffBundleBuilder.setCoverUri(newItem.getCover().getFileUri());
            }

            if (diffBundleBuilder.isEmpty()) return null;
            else return diffBundleBuilder.getBundle();
        }

    }).build();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                customBackPress();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ExtensionsFactories.INSTANCE.register(new SelectExtensionFactory());
        //EventBus.getDefault().register(this);
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
        toolbar.setOnMenuItemClickListener(this::toolbarOnItemClicked);
        selectionToolbar.setOnMenuItemClickListener(this::selectionToolbarOnItemClicked);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.getNewSearch().observe(getViewLifecycleOwner(), this::onNewSearch);
        viewModel.getLibraryPaged().observe(getViewLifecycleOwner(), this::onLibraryChanged);
        viewModel.getTotalContent().observe(getViewLifecycleOwner(), this::onTotalContentChanged);

        viewModel.updateOrder(); // Trigger a blank search

        // Display search bar tooltip _after_ the left drawer closes (else it displays over it)
        if (Preferences.isFirstRunProcessComplete())
            TooltipUtil.showTooltip(requireContext(), R.string.help_search, ArrowOrientation.TOP, toolbar, getViewLifecycleOwner());
        // Display pager tooltip
        if (pager.isVisible()) pager.showTooltip(getViewLifecycleOwner());

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
        emptyText = requireViewById(rootView, R.id.library_empty_txt);

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
            query = "";
            mainSearchView.setQuery("", false);
            metadata.clear();
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

        // RecyclerView
        recyclerView = requireViewById(rootView, R.id.library_list);
        llm = (LinearLayoutManager) recyclerView.getLayoutManager();
        new FastScrollerBuilder(recyclerView).build();

        // Pager
        pager.initUI(rootView);
        initPagingMethod(Preferences.getEndlessScroll());
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
                viewModel.searchUniversal(query);
                mainSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
                    query = "";
                    viewModel.searchUniversal(query);
                    searchClearButton.setVisibility(View.GONE);
                }

                return true;
            }
        });
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
        if (hideSortOnly && isSearchQueryActive()) searchClearButton.setVisibility(View.VISIBLE);
    }

    private void initSelectionToolbar(@NonNull View rootView) {
        selectionToolbar = requireViewById(rootView, R.id.library_selection_toolbar);
        selectionToolbar.setNavigationOnClickListener(v -> {
            selectExtension.deselect();
            selectionToolbar.setVisibility(View.GONE);
        });

        itemDelete = selectionToolbar.getMenu().findItem(R.id.action_delete);
        itemShare = selectionToolbar.getMenu().findItem(R.id.action_share);
        itemArchive = selectionToolbar.getMenu().findItem(R.id.action_archive);
        itemFolder = selectionToolbar.getMenu().findItem(R.id.action_open_folder);
        itemRedownload = selectionToolbar.getMenu().findItem(R.id.action_redownload);
        itemDeleteAll = selectionToolbar.getMenu().findItem(R.id.action_delete_all);
    }

    private boolean selectionToolbarOnItemClicked(@NonNull MenuItem menuItem) {
        boolean keepToolbar = false;
        switch (menuItem.getItemId()) {
            case R.id.action_share:
                shareSelectedItems();
                break;
            case R.id.action_delete:
            case R.id.action_delete_all:
                purgeSelectedItems();
                break;
            case R.id.action_archive:
                archiveSelectedItems();
                break;
            case R.id.action_open_folder:
                openItemFolder();
                break;
            case R.id.action_redownload:
                askRedownloadSelectedItemsScratch();
                keepToolbar = true;
                break;
            default:
                selectionToolbar.setVisibility(View.GONE);
                return false;
        }
        if (!keepToolbar) selectionToolbar.setVisibility(View.GONE);
        return true;
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
     * Callback for the "share item" action button
     */
    private void shareSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            Content c = Stream.of(selectedItems).findFirst().get().getContent();
            if (c != null) ContentHelper.shareContent(context, c);
        }
    }

    /**
     * Callback for the "delete item" action button
     */
    private void purgeSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        if (!selectedItems.isEmpty()) {
            List<Content> selectedContent = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().toList();
            // Remove external items if they can't be deleted
            if (!Preferences.isDeleteExternalLibrary())
                selectedContent = Stream.of(selectedContent).filterNot(c -> c.getStatus().equals(StatusContent.EXTERNAL)).toList();
            if (!selectedContent.isEmpty()) askDeleteItems(selectedContent);
        }
    }

    /**
     * Callback for the "archive item" action button
     */
    private void archiveSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            ToastUtil.toast(R.string.packaging_content);
            Content c = Stream.of(selectedItems).findFirst().get().getContent();
            if (c != null) {
                if (c.getStorageUri().isEmpty()) {
                    ToastUtil.toast(R.string.folder_undefined);
                    return;
                }
                viewModel.archiveContent(c, this::onContentArchiveSuccess);
            }
        }
    }

    /**
     * Callback for the "open containing folder" action button
     */
    private void openItemFolder() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            Content c = Stream.of(selectedItems).findFirst().get().getContent();
            if (c != null) {
                if (c.getStorageUri().isEmpty()) {
                    ToastUtil.toast(R.string.folder_undefined);
                    return;
                }

                DocumentFile folder = FileHelper.getFolderFromTreeUriString(requireContext(), c.getStorageUri());
                if (folder != null) {
                    selectExtension.deselect();
                    selectionToolbar.setVisibility(View.GONE);
                    FileHelper.openFile(requireContext(), folder);
                }
            }
        }
    }

    /**
     * Callback for the "redownload from scratch" action button
     */
    private void askRedownloadSelectedItemsScratch() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();

        int securedContent = 0;
        int externalContent = 0;
        List<Content> contents = new ArrayList<>();
        for (ContentItem ci : selectedItems) {
            Content c = ci.getContent();
            if (null == c) continue;
            if (c.getStatus().equals(StatusContent.EXTERNAL)) {
                externalContent++;
            } else if (/*c.getSite().equals(Site.FAKKU2) ||*/ c.getSite().equals(Site.EXHENTAI)) {
                securedContent++;
            } else {
                contents.add(c);
            }
            contents.add(ci.getContent());
        }

        String message = getResources().getQuantityString(R.plurals.redownload_confirm, contents.size());
        if (securedContent > 0)
            message = getResources().getQuantityString(R.plurals.redownload_secured_content, securedContent);
        else if (externalContent > 0)
            message = getResources().getQuantityString(R.plurals.redownload_secured_content, securedContent);

        // TODO make it work for secured sites (Fakku, ExHentai) -> open a browser to fetch the relevant cookies ?

        new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            redownloadContent(contents, true);
                            for (ContentItem ci : selectedItems) ci.setSelected(false);
                            selectExtension.deselect();
                            selectionToolbar.setVisibility(View.GONE);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog12, which) -> dialog12.dismiss())
                .create()
                .show();
    }

    /**
     * Callback for the success of the "archive item" action
     *
     * @param archive File containing the created archive
     */
    private void onContentArchiveSuccess(File archive) {
        Context context = getActivity();
        if (null == context) return;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(context, FileHelper.AUTHORITY, archive));
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileHelper.getExtension(archive.getName()));
        sendIntent.setType(mimeType);

        try {
            context.startActivity(sendIntent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No activity found to send %s", archive.getPath());
            ToastUtil.toast(context, R.string.error_send, Toast.LENGTH_LONG);
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param items Items to be deleted if the answer is yes
     */
    private void askDeleteItems(@NonNull final List<Content> items) {
        Context context = getActivity();
        if (null == context) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getQuantityString(R.plurals.ask_delete_multiple, items.size());
        builder.setMessage(title)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            onDeleteBooks(items);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .create().show();
    }

    private void onDeleteBooks(@NonNull final List<Content> items) {
        viewModel.deleteItems(items, this::onDeleteError);
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotRemovedException) {
            ContentNotRemovedException e = (ContentNotRemovedException) t;
            String message = (null == e.getMessage()) ? "Content removal failed" : e.getMessage();
            Snackbar snackbar = Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG);
            // If the cause if not the file not being removed, keep the item on screen, not blinking
            if (!(t instanceof FileNotRemovedException))
                viewModel.flagContentDelete(e.getContent(), false);
            snackbar.setAction("RETRY", v -> viewModel.deleteItems(Stream.of(e.getContent()).toList(), this::onDeleteError));
            snackbar.show();
        }
    }

    /**
     * Update favourite filter button appearance on the action bar
     */
    private void updateFavouriteFilter() {
        favsMenu.setIcon(favsMenu.isChecked() ? R.drawable.ic_filter_favs_on : R.drawable.ic_filter_favs_off);
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    private boolean isSearchQueryActive() {
        return (!query.isEmpty() || !metadata.isEmpty());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewModel != null) viewModel.onSaveState(outState);
        if (fastAdapter != null) fastAdapter.saveInstanceState(outState);

        // Remember current position in the sorted list
        int currentPosition = getTopItemPosition();
        if (currentPosition > 0 || -1 == topItemPosition) topItemPosition = currentPosition;

        outState.putInt(KEY_LAST_LIST_POSITION, topItemPosition);
        topItemPosition = -1;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        topItemPosition = 0;
        if (null == savedInstanceState) return;

        if (viewModel != null) viewModel.onRestoreState(savedInstanceState);
        if (fastAdapter != null) fastAdapter.withSavedInstanceState(savedInstanceState);
        // Mark last position in the list to be the one it will come back to
        topItemPosition = savedInstanceState.getInt(KEY_LAST_LIST_POSITION, 0);
    }

    /*
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onAppUpdated(AppUpdatedEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) UpdateSuccessDialogFragment.invoke(getParentFragmentManager());
    }
     */

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

            if (searchUri != null) {
                query = searchUri.getPath();
                metadata = SearchActivityBundle.Parser.parseSearchUri(searchUri);
                viewModel.search(query, metadata);
            }
        }
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener);
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private void customBackPress() {
        // If content is selected, deselect it
        if (!selectExtension.getSelectedItems().isEmpty()) {
            selectExtension.deselect();
            selectionToolbar.setVisibility(View.GONE);
            backButtonPressed = 0;
        } else if (searchMenu.isActionViewExpanded()) {
            searchMenu.collapseActionView();
        }

        // If none of the above, user is asking to leave => use double-tap
        else if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
            callback.remove();
            requireActivity().onBackPressed();

        } else {
            backButtonPressed = SystemClock.elapsedRealtime();
            ToastUtil.toast(R.string.press_back_again);

            llm.scrollToPositionWithOffset(0, 0);
        }
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(String key) {
        Timber.i("Prefs change detected : %s", key);
        if (Preferences.Key.PREF_ENDLESS_SCROLL.equals(key)) {
            initPagingMethod(Preferences.getEndlessScroll());
            viewModel.updateOrder(); // Trigger a blank search
        } else if (Preferences.Key.PREF_COLOR_THEME.equals(key)) {
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

        if (!metadata.isEmpty())
            builder.setUri(SearchActivityBundle.Builder.buildSearchUri(metadata));
        search.putExtras(builder.getBundle());

        startActivityForResult(search, 999);
        searchMenu.collapseActionView();
    }

    /**
     * Initialize the paging method of the screen
     *
     * @param isEndless True if endless mode has to be set; false if paged mode has to be set
     */
    private void initPagingMethod(boolean isEndless) {
        viewModel.setPagingMethod(isEndless);

        if (isEndless) { // Endless mode
            pager.hide();

            pagedItemAdapter = new PagedModelAdapter<>(asyncDifferConfig, i -> new ContentItem(ContentItem.ViewType.LIBRARY), c -> new ContentItem(c, null, ContentItem.ViewType.LIBRARY));
            fastAdapter = FastAdapter.with(pagedItemAdapter);
            fastAdapter.setHasStableIds(true);
            ContentItem item = new ContentItem(ContentItem.ViewType.LIBRARY);
            fastAdapter.registerItemFactory(item.getType(), item);

            itemAdapter = null;
        } else { // Paged mode
            itemAdapter = new ItemAdapter<>();
            fastAdapter = FastAdapter.with(itemAdapter);
            fastAdapter.setHasStableIds(true);
            pager.setCurrentPage(1);
            pager.show();

            pagedItemAdapter = null;
        }

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onBookClick(i, p));

        // Favourite button click listener
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                if (item.getContent() != null) onBookFavouriteClick(item.getContent());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getFavouriteButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Site button click listener
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                if (item.getContent() != null) onBookSourceClick(item.getContent());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getSiteButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Error button click listener
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                if (item.getContent() != null) onBookErrorClick(item.getContent());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getErrorButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectionListener((item, b) -> this.onSelectionChanged());
        }

        recyclerView.setAdapter(fastAdapter);
    }

    /**
     * Returns the index bounds of the list to be displayed according to the given shelf number
     * Used for paged mode only
     *
     * @param shelfNumber Number of the shelf to display
     * @param librarySize Size of the library
     * @return Min and max index of the books to display on the given page
     */
    private ImmutablePair<Integer, Integer> getShelfBound(int shelfNumber, int librarySize) {
        int minIndex = (shelfNumber - 1) * Preferences.getContentPageQuantity();
        int maxIndex = Math.min(minIndex + Preferences.getContentPageQuantity(), librarySize);
        return new ImmutablePair<>(minIndex, maxIndex);
    }

    /**
     * Loads current shelf of books to into the paged mode adapter
     * NB : A bookshelf is the portion of the collection that is displayed on screen by the paged mode
     * The width of the shelf is determined by the "Quantity per page" setting
     *
     * @param iLibrary Library to extract the shelf from
     */
    private void loadBookshelf(PagedList<Content> iLibrary) {
        if (iLibrary.isEmpty()) {
            itemAdapter.set(Collections.emptyList());
            fastAdapter.notifyDataSetChanged();
        } else {
            ImmutablePair<Integer, Integer> bounds = getShelfBound(pager.getCurrentPageNumber(), iLibrary.size());
            int minIndex = bounds.getLeft();
            int maxIndex = bounds.getRight();

            if (minIndex >= maxIndex) { // We just deleted the last item of the last page => Go back one page
                pager.setCurrentPage(pager.getCurrentPageNumber() - 1);
                loadBookshelf(iLibrary);
                return;
            }

            populateBookshelf(iLibrary, pager.getCurrentPageNumber());
        }
    }

    /**
     * Displays the current "bookshelf" (section of the list corresponding to the selected page)
     * A shelf contains as many books as the user has set in Preferences
     * <p>
     * Used in paged mode only
     *
     * @param iLibrary    Library to display books from
     * @param shelfNumber Number of the shelf to display
     */
    private void populateBookshelf(@NonNull PagedList<Content> iLibrary, int shelfNumber) {
        if (Preferences.getEndlessScroll()) return;

        ImmutablePair<Integer, Integer> bounds = getShelfBound(shelfNumber, iLibrary.size());
        int minIndex = bounds.getLeft();
        int maxIndex = bounds.getRight();

        List<ContentItem> contentItems = Stream.of(iLibrary.subList(minIndex, maxIndex)).withoutNulls().map(c -> new ContentItem(c, null, ContentItem.ViewType.LIBRARY)).toList();
        itemAdapter.set(contentItems);
        fastAdapter.notifyDataSetChanged();
    }

    /**
     * LiveData callback when a new search takes place
     *
     * @param b Unused parameter (always set to true)
     */
    private void onNewSearch(Boolean b) {
        newSearch = b;
    }

    /**
     * LiveData callback when the library changes
     * - Either because a new search has been performed
     * - Or because a book has been downloaded, deleted, updated
     *
     * @param result Current library according to active filters
     */
    private void onLibraryChanged(PagedList<Content> result) {
        Timber.i(">>Library changed ! Size=%s", result.size());

        updateTitle(result.size(), totalContentCount);

        // Update background text
        if (result.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            if (isSearchQueryActive()) emptyText.setText(R.string.search_entry_not_found);
            else emptyText.setText(R.string.downloads_empty_library);
        } else emptyText.setVisibility(View.GONE);

        // Update visibility of advanced search bar
        if (isSearchQueryActive()) {
            showSearchSortBar(true, true, false);
            if (!result.isEmpty() && searchMenu != null) searchMenu.collapseActionView();
        } else {
            searchClearButton.setVisibility(View.GONE);
        }

        // User searches a book ID
        // => Suggests searching through all sources except those where the selected book ID is already in the collection
        if (newSearch && Helper.isNumeric(query)) {
            ArrayList<Integer> siteCodes = Stream.of(result)
                    .filter(content -> query.equals(content.getUniqueSiteId()))
                    .map(Content::getSite)
                    .map(Site::getCode)
                    .collect(toCollection(ArrayList::new));

            SearchBookIdDialogFragment.invoke(requireContext(), getParentFragmentManager(), query, siteCodes);
        }

        // If the update is the result of a new search, get back on top of the list
        if (newSearch) topItemPosition = 0;

        // Update displayed books
        if (Preferences.getEndlessScroll()) {
            pagedItemAdapter.submitList(result, this::differEndCallback);
        } else {
            if (newSearch) pager.setCurrentPage(1);
            pager.setPageCount((int) Math.ceil(result.size() * 1.0 / Preferences.getContentPageQuantity()));
            loadBookshelf(result);
        }

        newSearch = false;
        library = result;
    }

    /**
     * LiveData callback when the total number of books changes (because of book download of removal)
     *
     * @param count Current book count in the whole, unfiltered library
     */
    private void onTotalContentChanged(Integer count) {
        totalContentCount = count;
        if (library != null) updateTitle(library.size(), totalContentCount);
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    private void updateTitle(long totalSelectedCount, long totalCount) {
        String title;
        if (totalSelectedCount == totalCount)
            title = totalCount + " items";
        else {
            title = getResources().getQuantityString(R.plurals.number_of_book_search_results, (int) totalSelectedCount, (int) totalSelectedCount, totalCount);
        }
        toolbar.setTitle(title);
    }

    /**
     * Callback for the book holder itself
     *
     * @param item ContentItem that has been clicked on
     */

    private boolean onBookClick(@NonNull ContentItem item, int position) {
        if (selectExtension.getSelectedItems().isEmpty()) {
            if (!invalidateNextBookClick && item.getContent() != null && !item.getContent().isBeingDeleted()) {
                topItemPosition = position;
                //ContentHelper.openHentoidViewer(requireContext(), content, viewModel.getSearchManagerBundle());
                //ContentHelper.open(requireContext(), item.getContent(), viewModel.getSearchManagerBundle());
                Single.fromCallable(() -> ContentHelper.open(requireContext(), item.getContent(), viewModel.getSearchManagerBundle()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe();
            } else invalidateNextBookClick = false;

            return true;
        } else {
            selectExtension.setSelectOnLongClick(false);
        }
        return false;
    }

    /**
     * Callback for the "source" button of the book holder
     *
     * @param content Content whose "source" button has been clicked on
     */
    private void onBookSourceClick(@NonNull Content content) {
        ContentHelper.viewContentGalleryPage(requireContext(), content);
    }

    /**
     * Callback for the "favourite" button of the book holder
     *
     * @param content Content whose "favourite" button has been clicked on
     */
    private void onBookFavouriteClick(@NonNull Content content) {
        viewModel.toggleContentFavourite(content);
    }

    /**
     * Callback for the "error" button of the book holder
     *
     * @param content Content whose "error" button has been clicked on
     */
    private void onBookErrorClick(@NonNull Content content) {
        ErrorsDialogFragment.invoke(this, content.getId());
    }

    /**
     * Add the given content back to the download queue
     *
     * @param content Content to add back to the download queue
     */
    public void redownloadContent(@NonNull final Content content) {
        List<Content> contentList = new ArrayList<>();
        contentList.add(content);
        redownloadContent(contentList, false);
    }

    private void redownloadContent(@NonNull final List<Content> contentList, boolean reparseImages) {
        StatusContent targetImageStatus = reparseImages ? StatusContent.ERROR : null;
        for (Content c : contentList) viewModel.addContentToQueue(c, targetImageStatus);

        if (Preferences.isQueueAutostart())
            ContentQueueManager.getInstance().resumeQueue(getContext());

        String message = getResources().getQuantityString(R.plurals.add_to_queue, contentList.size(), contentList.size());
        Snackbar snackbar = Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG);
        snackbar.setAction("VIEW QUEUE", v -> viewQueue());
        snackbar.show();
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        int selectedTotalCount = selectedItems.size();

        if (0 == selectedTotalCount) {
            selectionToolbar.setVisibility(View.GONE);
            selectExtension.setSelectOnLongClick(true);
            invalidateNextBookClick = true;
            new Handler().postDelayed(() -> invalidateNextBookClick = false, 200);
        } else {
            long selectedLocalCount = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().map(Content::getStatus).filter(s -> s.equals(StatusContent.DOWNLOADED)).count();
            updateSelectionToolbar(selectedTotalCount, selectedLocalCount);
            selectionToolbar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Handler for any page change
     */
    private void handleNewPage() {
        loadBookshelf(library);
        recyclerView.scrollToPosition(0);
    }

    /**
     * Navigate to the queue screen
     */
    private void viewQueue() {
        Intent intent = new Intent(requireContext(), QueueActivity.class);
        requireContext().startActivity(intent);
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    private void differEndCallback() {
        if (topItemPosition > -1) {
            int targetPos = topItemPosition;
            listRefreshDebouncer.submit(targetPos);
            topItemPosition = -1;
        }
    }

    /**
     * Callback for the end of recycler updates
     * Activated when all _displayed_ items are placed on their definitive position
     */
    private void onRecyclerUpdated(int topItemPosition) {
        int currentPosition = getTopItemPosition();
        if (currentPosition != topItemPosition)
            llm.scrollToPositionWithOffset(topItemPosition, 0); // Used to restore position after activity has been stopped and recreated
    }

    /**
     * Calculate the position of the top visible item of the book list
     *
     * @return position of the top visible item of the book list
     */
    private int getTopItemPosition() {
        return Math.max(llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition());
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
}
