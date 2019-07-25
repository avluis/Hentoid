package me.devsaki.hentoid.abstracts;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.annimon.stream.Stream;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.activities.SearchActivity;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.collection.mikan.MikanCollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.fragments.downloads.AboutMikanDialogFragment;
import me.devsaki.hentoid.fragments.downloads.PagerFragment;
import me.devsaki.hentoid.fragments.downloads.SearchBookIdDialogFragment;
import me.devsaki.hentoid.fragments.downloads.UpdateSuccessDialogFragment;
import me.devsaki.hentoid.listener.ContentClickListener.ItemSelectListener;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toCollection;

/**
 * Created by avluis on 08/27/2016. Common elements for use by EndlessFragment and PagerFragment
 * <p>
 * todo issue: After requesting for permission, the app is reset using {@link #resetApp()} instead
 * of implementing {@link #onRequestPermissionsResult} to receive permission
 * request result
 */
public abstract class DownloadsFragment extends BaseFragment implements PagedResultListener<Content>,
        ItemSelectListener {

    // ======== CONSTANTS

    private static final int SHOW_LOADING = 1;
    private static final int SHOW_BLANK = 2;
    protected static final int SHOW_RESULT = 3;

    public static final int MODE_LIBRARY = 0;
    public static final int MODE_MIKAN = 1;


    // Save state constants
    private static final String KEY_MODE = "mode";
    private static final String KEY_PLANNED_REFRESH = "planned_refresh";


    // ======== UI ELEMENTS

    // Top tooltip appearing when a download has been completed
    private LinearLayout newContentToolTip;
    // "Search" button on top menu
    private MenuItem searchMenu;
    // "Toggle favourites" button on top menu
    private MenuItem favsMenu;
    // "Sort" button on top menu
    private MenuItem orderMenu;
    // Action view associated with search menu button
    private SearchView mainSearchView;
    // Search pane that shows up on top when using search function
    private View advancedSearchPane;
    // Layout containing the list of books
    private SwipeRefreshLayout refreshLayout;
    // List containing all books
    protected RecyclerView mListView;
    // Layout manager associated with the above list view
    private LinearLayoutManager llm;
    // Pane saying "Loading up~"
    private TextView loadingText;
    // Pane saying "Why am I empty ?"
    private TextView emptyText;
    // Bottom toolbar with page numbers
    protected LinearLayout pagerToolbar;
    // Bar with CLEAR button that appears whenever a search filter is active
    private ViewGroup filterBar;
    // Book count text on the filter bar
    private TextView filterBookCount;
    // CLEAR button on the filter bar
    private TextView filterClearButton;

    // ======== UTIL OBJECTS
    private ObjectAnimator animator;

    // ======== VARIABLES TAKEN FROM PREFERENCES / GLOBAL SETTINGS TO DETECT CHANGES
    // Books per page
    protected int booksPerPage;

    // ======== VARIABLES

    // === MISC. USAGE
    protected Context mContext;
    // Adapter in charge of book list display
    protected ContentAdapter mAdapter;
    // True if a new download is ready; used to display / hide "New Content" tooltip when scrolling
    private boolean isNewContentAvailable;
    // True if book list is being loaded; used for synchronization between threads
    protected boolean isLoading;
    // Indicates whether or not one of the books has been selected
    private boolean isSelected;
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;
    // True if bottom toolbar visibility is fixed and should not change regardless of scrolling; false if bottom toolbar visibility changes according to scrolling
    private boolean overrideBottomToolbarVisibility;
    // True if storage permissions have been checked at least once
    private boolean storagePermissionChecked = false;
    // Mode : show library or show Mikan search
    private int mode = MODE_LIBRARY;
    // Total count of book in entire selected/queried collection (Adapter is in charge of updating it)
    private long mTotalSelectedCount = -1; // -1 = uninitialized (no query done yet)
    // Total count of book in entire collection (Adapter is in charge of updating it)
    private long mTotalCount = -1; // -1 = uninitialized (no query done yet)
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;
    // A library display refresh has been planned
    private boolean plannedRefresh = false;


    // === SEARCH
    protected ContentSearchManager searchManager;
    // Last search parameters; used to determine whether or not page number should be reset to 1
    // NB : populated by getCurrentSearchParams
    private String lastSearchParams = "";

    // To be documented
    private ActionMode mActionMode;
    private boolean selectTrigger = false;


    private static int getIconFromSortOrder(int sortOrder) {
        switch (sortOrder) {
            case Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST:
                return R.drawable.ic_menu_sort_321;
            case Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_LAST:
                return R.drawable.ic_menu_sort_123;
            case Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA:
                return R.drawable.ic_menu_sort_az;
            case Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA_INVERTED:
                return R.drawable.ic_menu_sort_za;
            case Preferences.Constant.ORDER_CONTENT_LEAST_READ:
                return R.drawable.ic_menu_sort_unread;
            case Preferences.Constant.ORDER_CONTENT_MOST_READ:
                return R.drawable.ic_menu_sort_read;
            case Preferences.Constant.ORDER_CONTENT_LAST_READ:
                return R.drawable.ic_menu_sort_last_read;
            case Preferences.Constant.ORDER_CONTENT_RANDOM:
                return R.drawable.ic_menu_sort_random;
            default:
                return R.drawable.ic_error;
        }
    }


    // == METHODS

    // Called when the action mode is created; startActionMode() was called.
    private final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        // Called when action mode is first created.
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.downloads_context_menu, menu);

            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode,
        // but may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            menu.findItem(R.id.action_delete).setVisible(!selectTrigger);
            menu.findItem(R.id.action_share).setVisible(!selectTrigger);
            menu.findItem(R.id.action_archive).setVisible(!selectTrigger);
            menu.findItem(R.id.action_delete_sweep).setVisible(selectTrigger);

            return true;
        }

        // Called when the user selects a contextual menu item.
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_share:
                    mAdapter.sharedSelectedItems();
                    mode.finish();

                    return true;
                case R.id.action_delete:
                case R.id.action_delete_sweep:
                    mAdapter.purgeSelectedItems();
                    mode.finish();

                    return true;
                case R.id.action_archive:
                    mAdapter.archiveSelectedItems();
                    mode.finish();

                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode.
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            clearSelection();
            mActionMode = null;
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        // Display the "update success" dialog when an update is detected
        if (Preferences.getLastKnownAppVersionCode() > 0 &&
                Preferences.getLastKnownAppVersionCode() < BuildConfig.VERSION_CODE) {
            UpdateSuccessDialogFragment.invoke(requireFragmentManager());
            Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE);
        }

        defaultLoad();
    }

    /**
     * Check write permissions on target storage and load library
     */
    private void defaultLoad() {
        if (MODE_LIBRARY == mode) {
            if (PermissionUtil.requestExternalStoragePermission(requireActivity(), ConstsImport.RQST_STORAGE_PERMISSION)) {
                boolean shouldUpdate = queryPrefs();

                // Run a search if prefs changes detected or first run (-1 = uninitialized)
                if (shouldUpdate || -1 == mTotalSelectedCount || 0 == mAdapter.getItemCount())
                    searchLibrary();

                if (ContentQueueManager.getInstance().getDownloadCount() > 0) showReloadToolTip();
                showToolbar(true);
            } else {
                Timber.d("Storage permission denied!");
                if (storagePermissionChecked) resetApp();
                storagePermissionChecked = true;
            }
        } else if (MODE_MIKAN == mode) {
            if (-1 == mTotalSelectedCount || 0 == mAdapter.getItemCount()) searchLibrary();

            showToolbar(true);
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onImportEvent(ImportEvent event) {
        if (ImportEvent.EV_COMPLETE == event.eventType) {
            EventBus.getDefault().removeStickyEvent(event);
            plannedRefresh = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.EV_COMPLETE && !isLoading) {
            if (MODE_LIBRARY == mode) showReloadToolTip();
            else mAdapter.switchStateToDownloaded(event.content);
        }
    }

    private void openBook(Content content) {
        // TODO : plan an individual refresh of displayed books according to their new DB value (esp. read/unread)
        Bundle bundle = new Bundle();
        searchManager.saveToBundle(bundle);
        int pageOffset = 0;
        if (this instanceof PagerFragment)
            pageOffset = (searchManager.getCurrentPage() - 1) * Preferences.getContentPageQuantity();
        bundle.putInt("contentIndex", pageOffset + mAdapter.getContentPosition(content) + 1);
        FileHelper.openContent(requireContext(), content, bundle);
    }

    /**
     * Updates class variables with Hentoid user preferences
     */
    protected boolean queryPrefs() {
        Timber.d("Querying Prefs.");
        boolean shouldUpdate = false;

        if (Preferences.getRootFolderName().isEmpty()) {
            Timber.d("Where are my files?!");

            FragmentActivity activity = requireActivity();
            Intent intent = new Intent(activity, ImportActivity.class);
            startActivity(intent);
            activity.finish();
        }

        if (plannedRefresh && mTotalCount > -1) {
            Timber.d("A library display refresh has been planned");
            shouldUpdate = true;
            plannedRefresh = false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkStorage();
        }

        int settingsBooksPerPage = Preferences.getContentPageQuantity();

        if (this.booksPerPage != settingsBooksPerPage) {
            Timber.d("booksPerPage updated.");
            this.booksPerPage = settingsBooksPerPage;
            setQuery("");
            shouldUpdate = true;
        }

        return shouldUpdate;
    }

    private void checkStorage() {
        if (FileHelper.isSAF()) {
            File storage = new File(Preferences.getRootFolderName());
            if (FileHelper.getExtSdCardFolder(storage) == null) {
                Timber.d("Where are my files?!");
                ToastUtil.toast(requireActivity(),
                        "Could not find library!\nPlease check your storage device.", Toast.LENGTH_LONG);
                setQuery("      ");

                new Handler().postDelayed(() -> {
                    FragmentActivity activity = requireActivity();
                    activity.finish();
                    Runtime.getRuntime().exit(0);
                }, 3000);
            }
            checkSDHealth();
        }
    }

    private void checkSDHealth() {
        if (!FileHelper.isWritable(new File(Preferences.getRootFolderName()))) {
            ToastUtil.toast(R.string.sd_access_error);
            new AlertDialog.Builder(requireActivity())
                    .setMessage(R.string.sd_access_fatal_error)
                    .setTitle("Error!")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    /**
     * Reset the app (to get write permissions)
     */
    private void resetApp() {
        Helper.reset(HentoidApp.getAppContext(), requireActivity());
    }

    @Override
    public void onPause() {
        super.onPause();

        clearSelection();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_MODE, mode);
        outState.putBoolean(KEY_PLANNED_REFRESH, plannedRefresh);
        searchManager.saveToBundle(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);

        if (state != null) {
            mode = state.getInt(KEY_MODE);
            plannedRefresh = state.getBoolean(KEY_PLANNED_REFRESH, false);
            searchManager.loadFromBundle(state);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);

        mContext = getContext();
        booksPerPage = Preferences.getContentPageQuantity();
    }

    @Override
    public void onDestroy() {
        searchManager.dispose();
        mAdapter.dispose();
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        if (this.getArguments() != null) mode = this.getArguments().getInt("mode");
        CollectionAccessor collectionAccessor = (MODE_LIBRARY == mode) ? new ObjectBoxCollectionAccessor(mContext) : new MikanCollectionAccessor(mContext);
        searchManager = new ContentSearchManager(collectionAccessor);

        View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);

        initUI(rootView, collectionAccessor);
        attachScrollListener();
        attachOnClickListeners(rootView);

        return rootView;
    }

    protected void initUI(View rootView, CollectionAccessor accessor) {
        loadingText = rootView.findViewById(R.id.loading);
        emptyText = rootView.findViewById(R.id.empty);
        emptyText.setText((MODE_LIBRARY == mode) ? R.string.downloads_empty_library : R.string.downloads_empty_mikan);

        llm = new LinearLayoutManager(mContext);

        mAdapter = new ContentAdapter.Builder()
                .setContext(mContext)
                .setCollectionAccessor(accessor)
                .setDisplayMode(mode)
                .setSortComparator(Content.getComparator())
                .setItemSelectListener(this)
                .setOnContentRemovedListener(this::onContentRemoved)
                .setOpenBookAction(this::openBook)
                .build();

        // Main view
        mListView = rootView.findViewById(R.id.list);
        mListView.setHasFixedSize(true);
        mListView.setLayoutManager(llm);
        mListView.setAdapter(mAdapter);
        mListView.setVisibility(View.GONE);

        loadingText.setVisibility(View.VISIBLE);


        pagerToolbar = rootView.findViewById(R.id.downloads_toolbar);
        newContentToolTip = rootView.findViewById(R.id.tooltip);
        refreshLayout = rootView.findViewById(R.id.swipe_container);

        filterBar = rootView.findViewById(R.id.filter_bar);
        filterBookCount = rootView.findViewById(R.id.filter_book_count);
        filterClearButton = rootView.findViewById(R.id.filter_clear);

        advancedSearchPane = rootView.findViewById(R.id.advanced_search);
        advancedSearchPane.setOnClickListener(v -> onAdvancedSearchClick());
    }

    protected void attachScrollListener() {
        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@Nonnull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // Show toolbar:
                if (!overrideBottomToolbarVisibility && mAdapter.getItemCount() > 0) {
                    // At top of list
                    int firstVisibleItemPos = llm.findFirstVisibleItemPosition();
                    View topView = llm.findViewByPosition(firstVisibleItemPos);
                    if (topView != null && topView.getTop() == 0 && firstVisibleItemPos == 0) {
                        showToolbar(true);
                        if (isNewContentAvailable) {
                            newContentToolTip.setVisibility(View.VISIBLE);
                        }
                    }

                    // Last item in list
                    if (llm.findLastVisibleItemPosition() == mAdapter.getItemCount() - 1) {
                        showToolbar(true);
                        if (isNewContentAvailable) {
                            newContentToolTip.setVisibility(View.VISIBLE);
                        }
                    } else {
                        // When scrolling up
                        if (dy < -10) {
                            showToolbar(true);
                            if (isNewContentAvailable) {
                                newContentToolTip.setVisibility(View.VISIBLE);
                            }
                            // When scrolling down
                        } else if (dy > 100) {
                            showToolbar(false);
                            if (isNewContentAvailable) {
                                newContentToolTip.setVisibility(View.GONE);
                            }
                        }
                    }
                }
            }
        });
    }

    protected void attachOnClickListeners(View rootView) {
        newContentToolTip.setOnClickListener(v -> commitRefresh());

        filterClearButton.setOnClickListener(v -> {
            setQuery("");
            mainSearchView.setQuery("", false);
            searchManager.clearSelectedSearchTags();
            filterBar.setVisibility(View.GONE);
            searchLibrary();
        });

        refreshLayout.setEnabled(false);
        refreshLayout.setOnRefreshListener(this::commitRefresh);
    }

    @Override
    public boolean onBackPressed() {
        // If content is selected, deselect it
        if (isSelected) {
            clearSelection();
            backButtonPressed = 0;

            return false;
        }

        // If none of the above, user is asking to leave => use double-tap
        if (MODE_MIKAN == mode || backButtonPressed + 2000 > System.currentTimeMillis()) {
            return true;
        } else {
            backButtonPressed = System.currentTimeMillis();
            ToastUtil.toast(mContext, R.string.press_back_again);

            if (llm != null) {
                llm.scrollToPositionWithOffset(0, 0);
            }
        }

        return false;
    }


    /**
     * Clear search query and hide the search view if asked so
     */
    private void clearQuery() {
        setQuery("");
        searchLibrary();
    }

    /**
     * Refresh the whole screen - Called by pressing the "New Content" button that appear on new
     * downloads - Called by scrolling up when being on top of the list ("force reload" command)
     */
    private void commitRefresh() {
        newContentToolTip.setVisibility(View.GONE);
        refreshLayout.setRefreshing(false);
        refreshLayout.setEnabled(false);
        isNewContentAvailable = false;
        searchLibrary();
        resetCount();
    }

    /**
     * Reset the download count (used to properly display the number of downloads in Notifications)
     */
    private void resetCount() {
        Timber.d("Download Count: %s", ContentQueueManager.getInstance().getDownloadCount());
        ContentQueueManager.getInstance().setDownloadCount(0);

        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(0);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.downloads_menu, menu);

        MenuItem aboutMikanMenu = menu.findItem(R.id.action_about_mikan);
        aboutMikanMenu.setVisible(MODE_MIKAN == mode);
        if (MODE_MIKAN == mode) {
            aboutMikanMenu.setOnMenuItemClickListener(item -> {
                AboutMikanDialogFragment.show(getFragmentManager());
                return true;
            });
        }

        orderMenu = menu.findItem(R.id.action_order);
        orderMenu.setVisible(MODE_LIBRARY == mode);

        searchMenu = menu.findItem(R.id.action_search);
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                setSearchPaneVisibility(true);

                // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                if (!searchManager.getQuery().isEmpty())
                    // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                    new Handler().postDelayed(() -> {
                        invalidateNextQueryTextChange = true;
                        mainSearchView.setQuery(searchManager.getQuery(), false);
                    }, 100);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                setSearchPaneVisibility(false);
                return true;
            }
        });

        favsMenu = menu.findItem(R.id.action_favourites);
        favsMenu.setVisible(MODE_LIBRARY == mode);
        updateFavouriteFilter();
        favsMenu.setOnMenuItemClickListener(item -> {
            toggleFavouriteFilter();
            return true;
        });

        mainSearchView = (SearchView) searchMenu.getActionView();
        mainSearchView.setIconifiedByDefault(true);
        mainSearchView.setQueryHint(getString(R.string.search_hint));
        // Change display when text query is typed
        mainSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                submitContentSearchQuery(s);
                mainSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
                    clearQuery();
                }

                return true;
            }
        });

        // Sets the starting book sort icon according to the current sort order
        orderMenu.setIcon(getIconFromSortOrder(Preferences.getContentSortOrder()));
    }

    private void onAdvancedSearchClick() {
        Intent search = new Intent(this.getContext(), SearchActivity.class);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder();

        builder.setMode(mode);
        if (!searchManager.getTags().isEmpty())
            builder.setUri(SearchActivityBundle.Builder.buildSearchUri(searchManager.getTags()));
        search.putExtras(builder.getBundle());

        startActivityForResult(search, 999);
        searchMenu.collapseActionView();
    }

    /**
     * Callback method used when a sort method is selected in the sort drop-down menu => Updates the
     * UI according to the chosen sort method
     *
     * @param item MenuItem that has been selected
     * @return true if the order has been successfully processed
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int contentSortOrder;

        switch (item.getItemId()) {
            case R.id.action_order_AZ:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA;
                break;
            case R.id.action_order_321:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST;
                break;
            case R.id.action_order_ZA:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA_INVERTED;
                break;
            case R.id.action_order_123:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_LAST;
                break;
            case R.id.action_order_least_read:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_LEAST_READ;
                break;
            case R.id.action_order_most_read:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_MOST_READ;
                break;
            case R.id.action_order_last_read:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_LAST_READ;
                break;
            case R.id.action_order_random:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_RANDOM;
                RandomSeedSingleton.getInstance().renewSeed();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        mAdapter.setSortComparator(Content.getComparator());
        orderMenu.setIcon(getIconFromSortOrder(contentSortOrder));
        Preferences.setContentSortOrder(contentSortOrder);
        searchLibrary();

        return true;
    }

    /**
     * Toggles the visibility of the search pane
     *
     * @param visible True if search pane has to become visible; false if not
     */
    private void setSearchPaneVisibility(boolean visible) {
        advancedSearchPane.setVisibility(visible ? View.VISIBLE : View.GONE);
        invalidateNextQueryTextChange = true;
    }

    /**
     * Toggles favourite filter on a book and updates the UI accordingly
     */
    private void toggleFavouriteFilter() {
        searchManager.setFilterFavourites(!searchManager.isFilterFavourites());
        updateFavouriteFilter();
        searchLibrary();
    }

    /**
     * Update favourite filter button appearance (icon and color) on a book
     */
    private void updateFavouriteFilter() {
        favsMenu.setIcon(searchManager.isFilterFavourites() ? R.drawable.ic_fav_full : R.drawable.ic_fav_empty);
    }

    private void submitContentSearchQuery(final String s) {
        searchManager.clearSelectedSearchTags(); // If user searches in main toolbar, universal search takes over advanced search
        setQuery(s);
        searchLibrary();
    }

    private void showReloadToolTip() {
        if (newContentToolTip.getVisibility() == View.GONE) {
            newContentToolTip.setVisibility(View.VISIBLE);
            refreshLayout.setEnabled(true);
            isNewContentAvailable = true;
        }
    }

    private void setQuery(String query) {
        searchManager.setQuery(query);
        searchManager.setCurrentPage(1);
    }

    private void clearSelection() {
        if (mAdapter != null) {
            if (mListView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                mAdapter.clearSelections();
                selectTrigger = false;
                showToolbar(true);
            }
            isSelected = false;
        }
    }

    protected void toggleUI(int mode) {
        switch (mode) {
            case SHOW_LOADING:
                mListView.setVisibility(View.GONE);
                emptyText.setVisibility(View.GONE);
                loadingText.setVisibility(View.VISIBLE);
                startLoadingTextAnimation();
                break;
            case SHOW_BLANK:
                mListView.setVisibility(View.GONE);
                emptyText.setVisibility(View.VISIBLE);
                loadingText.setVisibility(View.GONE);
                showToolbar(false);
                break;
            case SHOW_RESULT:
                mListView.setVisibility(View.VISIBLE);
                emptyText.setVisibility(View.GONE);
                loadingText.setVisibility(View.GONE);
                showToolbar(true);
                break;
            default:
                stopLoadingTextAnimation();
                loadingText.setVisibility(View.GONE);
                break;
        }
    }

    private void startLoadingTextAnimation() {
        final int POWER_LEVEL = 9000;

        Drawable[] compoundDrawables = loadingText.getCompoundDrawables();
        for (Drawable drawable : compoundDrawables) {
            if (drawable == null) {
                continue;
            }

            animator = ObjectAnimator.ofInt(drawable, "level", 0, POWER_LEVEL);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.start();
        }
    }

    private void stopLoadingTextAnimation() {
        Drawable[] compoundDrawables = loadingText.getCompoundDrawables();
        for (Drawable drawable : compoundDrawables) {
            if (drawable == null) {
                continue;
            }
            animator.cancel();
        }
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    private boolean isSearchQueryActive() {
        return (!searchManager.getQuery().isEmpty() || !searchManager.getTags().isEmpty());
    }

    /**
     * Create a "thumbprint" unique to the combination of current search parameters
     *
     * @return Search parameters thumbprint
     */
    private String getCurrentSearchParams() {
        return (mode == MODE_LIBRARY ? "L" : "M") +
                "|" + searchManager.getQuery() +
                "|" + SearchActivityBundle.Builder.buildSearchUri(searchManager.getTags()) +
                "|" + booksPerPage +
                "|" + searchManager.getContentSortOrder() +
                "|" + searchManager.isFilterFavourites();
    }

    protected abstract boolean forceSearchFromPageOne();

    protected void searchLibrary() {
        searchLibrary(true);
    }

    /**
     * Loads the library applying current search parameters
     *
     * @param showLoadingPanel True if loading panel has to appear while search is running
     */
    protected void searchLibrary(boolean showLoadingPanel) {
        isLoading = true;
        searchManager.setContentSortOrder(Preferences.getContentSortOrder());

        if (showLoadingPanel) toggleUI(SHOW_LOADING);

        // Searches start from page 1 if they are new or if the fragment implementation forces it
        String currentSearchParams = getCurrentSearchParams();
        if (!currentSearchParams.equals(lastSearchParams) || forceSearchFromPageOne()) {
            searchManager.setCurrentPage(1);
            mListView.scrollToPosition(0);
        }
        lastSearchParams = currentSearchParams;

        searchManager.searchLibraryForContent(booksPerPage, this);
    }

    protected abstract void showToolbar(boolean show);

    protected abstract void displayResults(List<Content> results, long totalSelectedContent);

    /**
     * Indicates if current page is the last page of the library
     *
     * @return true if last page has been reached
     */
    protected boolean isLastPage() {
        return (searchManager.getCurrentPage() * booksPerPage >= mTotalSelectedCount);
    }

    private void displayNoResults() {
        if (!searchManager.getQuery().isEmpty()) {
            emptyText.setText(R.string.search_entry_not_found);
        } else {
            emptyText.setText((MODE_LIBRARY == mode) ? R.string.downloads_empty_library : R.string.downloads_empty_mikan);
        }
        toggleUI(SHOW_BLANK);
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    private void updateTitle() {
        if (MODE_LIBRARY == mode) {
            Activity activity = getActivity();
            if (activity != null) { // Has to be crash-proof; sometimes there's no activity there...
                String title;
                if (mTotalSelectedCount == mTotalCount)
                    title = "(" + mTotalCount + ")";
                else title = "(" + mTotalSelectedCount + "/" + mTotalCount + ")";
                activity.setTitle(title);
            }
        }
    }

    /*
    PagedResultListener implementation
     */
    @Override
    public void onPagedResultReady(List<Content> results, long totalSelectedContent, long totalContent) {
        Timber.d("Content results have loaded : %s results; %s total selected count, %s total count", results.size(), totalSelectedContent, totalContent);
        isLoading = false;

        if (isSearchQueryActive()) {
            // Disable "New content" popup
            if (isNewContentAvailable) {
                newContentToolTip.setVisibility(View.GONE);
                isNewContentAvailable = false;
            }

            Resources res = getResources();
            String textRes = res.getQuantityString(R.plurals.downloads_filter_book_count_plural, (int)totalSelectedContent, (int)totalSelectedContent);

            filterBookCount.setText(textRes);
            filterBar.setVisibility(View.VISIBLE);
            if (totalSelectedContent > 0 && searchMenu != null) searchMenu.collapseActionView();
        } else {
            filterBar.setVisibility(View.GONE);
        }

        // User searches a book ID
        // => Suggests searching through all sources except those where the selected book ID is already in the collection
        if (Helper.isNumeric(searchManager.getQuery())) {
            ArrayList<Integer> siteCodes = Stream.of(results)
                    .filter(content -> searchManager.getQuery().equals(content.getUniqueSiteId()))
                    .map(Content::getSite)
                    .map(Site::getCode)
                    .collect(toCollection(ArrayList::new));

            SearchBookIdDialogFragment.invoke(requireFragmentManager(), searchManager.getQuery(), siteCodes);
        }

        if (0 == totalSelectedContent) {
            displayNoResults();
        } else {
            displayResults(results, totalSelectedContent);
        }

        mTotalSelectedCount = totalSelectedContent;
        mTotalCount = totalContent;

        updateTitle();
    }

    @Override
    public void onPagedResultFailed(Content content, String message) {
        Timber.w(message);
        isLoading = false;

        Snackbar.make(mListView, message, Snackbar.LENGTH_LONG)
                .setAction("RETRY", v -> searchLibrary())
                .show();
        toggleUI(SHOW_BLANK);
    }

    /*
    ItemSelectListener implementation
     */
    @Override
    public void onItemSelected(int selectedCount) {
        isSelected = true;
        showToolbar(false);
        overrideBottomToolbarVisibility = true;

        if (selectedCount == 1) {
            mAdapter.notifyDataSetChanged();
        }

        if (selectedCount >= 2) {
            selectTrigger = true;
        }

        if (mActionMode == null) {
            mActionMode = pagerToolbar.startActionMode(mActionModeCallback);
        }

        if (mActionMode != null) {
            mActionMode.invalidate();
            mActionMode.setTitle(
                    selectedCount + (selectedCount > 1 ? " items selected" : " item selected"));
        }
    }

    @Override
    public void onItemClear(int itemCount) {
        if (mActionMode != null) {
            if (itemCount >= 1) {
                mActionMode.setTitle(
                        itemCount + (itemCount > 1 ? " items selected" : " item selected"));
            } else {
                selectTrigger = false;
                mActionMode.invalidate();
                mActionMode.setTitle("");
            }
        }

        if (itemCount == 1 && selectTrigger) {
            selectTrigger = false;

            if (mActionMode != null) {
                mActionMode.invalidate();
            }
        }

        if (itemCount < 1) {
            clearSelection();
            showToolbar(true);
            overrideBottomToolbarVisibility = false;

            if (mActionMode != null) {
                mActionMode.finish();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 999
                && resultCode == Activity.RESULT_OK
                && data != null && data.getExtras() != null) {
            Uri searchUri = new SearchActivityBundle.Parser(data.getExtras()).getUri();

            if (searchUri != null) {
                setQuery(searchUri.getPath());
                searchManager.setTags(SearchActivityBundle.Parser.parseSearchUri(searchUri));
                searchLibrary();
            }
        }
    }

    /**
     * Triggers when one or more items have been removed from the list
     *
     * @param i Number of items removed from the list
     */
    private void onContentRemoved(int i) {
        mTotalSelectedCount = mTotalSelectedCount - i;
        mTotalCount = mTotalCount - i;

        if (0 == mTotalCount) searchManager.setCurrentPage(1);

        if (0 == mTotalSelectedCount) {
            displayNoResults();
            clearSelection();
        }

        updateTitle();
    }
}
