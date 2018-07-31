package me.devsaki.hentoid.abstracts;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.adapters.ContentAdapter.ContentsWipedListener;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.collection.mikan.MikanAccessor;
import me.devsaki.hentoid.database.DatabaseAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ItemClickListener.ItemSelectListener;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.IllegalTags;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.DURATION.LONG;

/**
 * Created by avluis on 08/27/2016.
 * Common elements for use by EndlessFragment and PagerFragment
 */
public abstract class DownloadsFragment extends BaseFragment implements ContentListener,
        ContentsWipedListener, ItemSelectListener, AttributeListener {

    // ======== CONSTANTS

    protected static final int SHOW_LOADING = 1;
    protected static final int SHOW_BLANK = 2;
    protected static final int SHOW_RESULT = 3;

    protected static final int TAGFILTER_ACTIVE = 0;
    protected static final int TAGFILTER_SELECTED = 1;
    protected static final int TAGFILTER_INACTIVE = 3;

    public final static int MODE_LIBRARY = 0;
    public final static int MODE_MIKAN = 1;

    protected static final int MAX_ATTRIBUTES_DISPLAYED = 40;


    // Save state constants
    private static final String LIST_STATE_KEY = "list_state";
    private static final String IS_SEARCH_MODE = "is_search_mode";
    private static final String TAG_FILTERS_KEYS = "tag_filters_keys";
    private static final String TAG_FILTERS_VALUES = "tag_filters_values";
    private static final String FILTER_FAVOURITES = "filter_favs";
    private static final String QUERY = "query";


    // ======== UI ELEMENTS

    // Top tooltip appearing when a download has been completed
    protected LinearLayout newContentToolTip;
    // Left drawer
    private DrawerLayout mDrawerLayout;
    // "Search" button on top menu
    private MenuItem searchMenu;
    // "Sort" button on top menu
    private MenuItem orderMenu;
    // Action view associated with search menu button
    private SearchView mainSearchView;
    // Search pane that shows up on top when using search function
    protected View searchPane;
    // Container where selected attributed are displayed
    private ViewGroup searchTags;
    // Container where all available attributes are loaded
    private ViewGroup attributeMosaic;
    // Layout containing the list of books
    private SwipeRefreshLayout refreshLayout;
    // List containing all books
    protected RecyclerView mListView;
    // Layout manager associated with the above list view
    protected LinearLayoutManager llm;
    // Pane saying "Loading up~"
    private TextView loadingText;
    // Pane saying "Why am I empty ?"
    private TextView emptyText;
    // Bottom toolbar with page numbers
    protected LinearLayout pagerToolbar;
    // Bar containing attribute selectors
    private LinearLayout attrSelector;
    // TODO
    private View tagWaitPanel;
    private ImageView tagWaitImage;
    private TextView tagWaitDescription;
    private TextView tagWaitTitle;

    // ======== UTIL OBJECTS
    private ObjectAnimator animator;
    // Handler for text searches; needs to be there to be cancelable upon new key press
    private final Handler searchHandler = new Handler();

    // ======== VARIABLES TAKEN FROM SETTINGS / PREFERENCES
    // Books per page
    protected int booksPerPage;
    // Hentoid directory
    private String settingDir;
    // Books sort order
    private int order;


    // ======== VARIABLES

    // === MISC. USAGE
    protected Context mContext;
    // Current state of left drawer (see constants in DrawerLayout class)
    private int mDrawerState;
    // Current page of collection view (NB : In EndlessFragment, a "page" is a group of loaded books. Last page is reached when scrolling reaches the very end of the book list)
    protected int currentPage = 1;
    // Adapter in charge of book list display
    protected ContentAdapter mAdapter;
    // True if a new download is ready; used to display / hide "New Content" tooltip when scrolling
    protected boolean isNewContentAvailable;
    // True if book list has finished loading; used for synchronization between threads
    protected boolean isLoaded;
    // Indicates whether or not one of the books has been selected
    private boolean isSelected;
    // True if sort order has been updated
    private boolean orderUpdated;
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;
    // True if bottom toolbar visibility is fixed and should not change regardless of scrolling; false if bottom toolbar visibility changes according to scrolling
    protected boolean overrideBottomToolbarVisibility;
    // True if storage permissions have been checked at least once
    private boolean storagePermissionChecked = false;
    // Mode : show library or show Mikan search
    private int mode = MODE_LIBRARY;
    // Collection accessor (DB or external, depending on mode)
    private CollectionAccessor collectionAccessor;


    // === SEARCH
    // True if search mode is active
    private boolean isSearchMode = false;
    // Active tag filters
    @Deprecated
    private final Map<String, Integer> tagFilters = new HashMap<>();
    // Favourite filter active
    private boolean filterFavourites = false;
    // Expression typed in the search bar
    protected String query = "";
    // True if search results need to replace displayed books (set before calling a search to be used during results display)
    protected boolean isSearchReplaceResults;

    // Currently selected tab
    private AttributeType selectedTab = AttributeType.TAG;
    // Current search tags
    private List<Attribute> currentSearchTags = new ArrayList<>();


    // To be documented
    private ActionMode mActionMode;
    private boolean shouldHide;
    private Parcelable mListState;
    private boolean selectTrigger = false;


    // == METHODS

    // Called when the action mode is created; startActionMode() was called.
    private final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        // Called when action mode is first created.
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.menu_context_menu, menu);

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
                    mAdapter.purgeSelectedItems();
                    mode.finish();

                    return true;
                case R.id.action_archive:
                    mAdapter.archiveSelectedItems();
                    mode.finish();

                    return true;
                case R.id.action_delete_sweep:
                    mAdapter.purgeSelectedItems();
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

        defaultLoad();

        if (mListState != null) {
            llm.onRestoreInstanceState(mListState);
        }
    }

    /**
     * Check write permissions on target storage and load library
     */
    private void defaultLoad() {

        if (MODE_LIBRARY == mode) {
            if (Helper.permissionsCheck(getActivity(), ConstsImport.RQST_STORAGE_PERMISSION, true)) {
                boolean shouldUpdate = queryPrefs();
                if (shouldUpdate || -1 == mAdapter.getTotalCount()) update();
                if (ContentQueueManager.getInstance().getDownloadCount() > 0) showReloadToolTip();
                showToolbar(true);
            } else {
                Timber.d("Storage permission denied!");
                if (storagePermissionChecked) {
                    resetApp();
                }
                storagePermissionChecked = true;
            }
        } else if (MODE_MIKAN == mode) {
            if (-1 == mAdapter.getTotalCount()) update();
            showToolbar(true);
        }
    }

    /**
     * Updates class variables with Hentoid user preferences
     */
    protected boolean queryPrefs() {
        Timber.d("Querying Prefs.");
        boolean shouldUpdate = false;

        if (settingDir.isEmpty()) {
            Timber.d("Where are my files?!");

            FragmentActivity activity = getActivity();
            if (null == activity) {
                Timber.e("Activity unreachable !");
                return false;
            }
            Intent intent = new Intent(activity, ImportActivity.class);
            startActivity(intent);
            activity.finish();
        }

        String settingDir = Preferences.getRootFolderName();

        if (!this.settingDir.equals(settingDir)) {
            Timber.d("Library directory has changed!");
            this.settingDir = settingDir;
            cleanResults();
            shouldUpdate = true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkStorage();
        }

        int booksPerPage = Preferences.getContentPageQuantity();

        if (this.booksPerPage != booksPerPage) {
            Timber.d("booksPerPage updated.");
            this.booksPerPage = booksPerPage;
            setQuery("");
            shouldUpdate = true;
        }

        int order = Preferences.getContentSortOrder();

        if (this.order != order) {
            Timber.d("order updated.");
            orderUpdated = true;
            this.order = order;
        }

        return shouldUpdate;
    }

    private void checkStorage() {
        if (FileHelper.isSAF()) {
            File storage = new File(settingDir);
            if (FileHelper.getExtSdCardFolder(storage) == null) {
                Timber.d("Where are my files?!");
                Helper.toast(getActivity(),
                        "Could not find library!\nPlease check your storage device.", LONG);
                setQuery("      ");

                Handler handler = new Handler();
                handler.postDelayed(() -> {
                    FragmentActivity activity = getActivity();
                    if (null == activity) {
                        Timber.e("Activity unreachable !");
                        return;
                    }
                    activity.finish();
                    Runtime.getRuntime().exit(0);
                }, 3000);
            }
            checkSDHealth();
        }
    }

    private void checkSDHealth() {
        File file = new File(settingDir, "test");
        OutputStream output = null;
        try {
            output = FileHelper.getOutputStream(file);
            // build
            byte[] bytes = "test".getBytes();
            // write
            output.write(bytes);
            FileHelper.sync(output);
            output.flush();
        } catch (NullPointerException npe) {
            Timber.e(npe, "Invalid Stream");
            Helper.toast(R.string.sd_access_error);
            new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.sd_access_fatal_error)
                    .setTitle("Error!")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (IOException e) {
            Timber.e(e, "IOException while checking SD Health");
        } finally {
            // finished
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            if (file.exists()) {
                Timber.d("Test file removed: %s", FileHelper.removeFile(file));
            }
        }
    }

    /**
     * Reset the app (to get write permissions)
     */
    private void resetApp() {
        Helper.reset(HentoidApp.getAppContext(), getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();

        clearSelection();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        mListState = llm.onSaveInstanceState();
        outState.putParcelable(LIST_STATE_KEY, mListState);
        outState.putBoolean(IS_SEARCH_MODE, isSearchMode);
        outState.putBoolean(FILTER_FAVOURITES, filterFavourites);
        outState.putString(QUERY, query);

        // Save tag filters (key set on one variable; value set on the other)
        outState.putStringArrayList(TAG_FILTERS_KEYS, new ArrayList<>());
        outState.putIntegerArrayList(TAG_FILTERS_VALUES, new ArrayList<>(tagFilters.values()));
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);

        if (state != null) {
            mListState = state.getParcelable(LIST_STATE_KEY);
            isSearchMode = state.getBoolean(IS_SEARCH_MODE, false);
            filterFavourites = state.getBoolean(FILTER_FAVOURITES, false);
            query = state.getString(QUERY, "");

            // Restore tag filters (key set on one variable; value set on the other)
            List<String> tagKeys = state.getStringArrayList(TAG_FILTERS_KEYS);
            List<Integer> tagValues = state.getIntegerArrayList(TAG_FILTERS_VALUES);
            if (tagKeys != null && tagValues != null) {
                for (int i = 0; i < tagKeys.size(); i++) {
                    tagFilters.put(tagKeys.get(i), tagValues.get(i));
                }
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        super.onViewCreated(view, state);

        if (mListState != null) {
            mListState = state.getParcelable(LIST_STATE_KEY);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);

        mContext = getContext();
        settingDir = Preferences.getRootFolderName();
        order = Preferences.getContentSortOrder();
        booksPerPage = Preferences.getContentPageQuantity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);

        if (this.getArguments() != null) mode = this.getArguments().getInt("mode");
        if (MODE_LIBRARY == mode) collectionAccessor = new DatabaseAccessor(mContext); else collectionAccessor = new MikanAccessor();

        initUI(rootView);
        attachScrollListener();
        attachOnClickListeners(rootView);

        return rootView;
    }

    protected void initUI(View rootView) {
        loadingText = rootView.findViewById(R.id.loading);
        emptyText = rootView.findViewById(R.id.empty);

        // Main view
        mListView = rootView.findViewById(R.id.list);
        mListView.setHasFixedSize(true);
        llm = new LinearLayoutManager(mContext);
        mListView.setLayoutManager(llm);

        if (MODE_MIKAN == mode) order = Preferences.Constant.PREF_ORDER_CONTENT_LAST_UL_DATE_FIRST;

        Comparator<Content> comparator;
        switch (order) {
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_FIRST:
                comparator = Content.DLDATE_COMPARATOR;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_LAST:
                comparator = Content.DLDATE_INV_COMPARATOR;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC:
                comparator = Content.TITLE_ALPHA_COMPARATOR;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC_INVERTED:
                comparator = Content.TITLE_ALPHA_INV_COMPARATOR;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_UL_DATE_FIRST:
                comparator = Content.ULDATE_COMPARATOR;
                break;
            default:
                comparator = Content.QUERY_ORDER_COMPARATOR;
        }

        mAdapter = new ContentAdapter(mContext, this, comparator, collectionAccessor,  mode);
        mAdapter.setContentsWipedListener(this);
        mListView.setAdapter(mAdapter);

        if (mAdapter.getItemCount() == 0) {
            mListView.setVisibility(View.GONE);
            loadingText.setVisibility(View.VISIBLE);
        }

        // Drawer
        FragmentActivity activity = getActivity();
        if (null == activity) {
            Timber.e("Activity unreachable !");
            return;
        }

        mDrawerLayout = activity.findViewById(R.id.drawer_layout);
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {

            @Override
            public void onDrawerStateChanged(int newState) {
                mDrawerState = newState;
                activity.invalidateOptionsMenu();
            }
        });

        pagerToolbar = rootView.findViewById(R.id.downloads_toolbar);
        newContentToolTip = rootView.findViewById(R.id.tooltip);
        refreshLayout = rootView.findViewById(R.id.swipe_container);

        searchPane = rootView.findViewById(R.id.tag_filter_view);
        attributeMosaic = rootView.findViewById(R.id.tag_suggestion);
        searchTags = rootView.findViewById(R.id.search_tags);
    }

    protected void attachScrollListener() {
        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
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

        refreshLayout.setEnabled(false);
        refreshLayout.setOnRefreshListener(this::commitRefresh);
    }

    @Override
    public boolean onBackPressed() {
        // If the drawer is open, back will close it
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
            backButtonPressed = 0;

            return false;
        }

        if (isSelected) {
            clearSelection();
            backButtonPressed = 0;

            return false;
        }

        if (backButtonPressed + 2000 > System.currentTimeMillis()) {
            return true;
        } else {
            backButtonPressed = System.currentTimeMillis();
            Helper.toast(mContext, R.string.press_back_again);

            if (llm != null) {
                llm.scrollToPositionWithOffset(0, 0);
            }
        }

        if (!query.isEmpty()) {
            clearQuery(1);
        }

        return false;
    }

    protected void clearQuery(int option) {
        Timber.d("Clearing query with option: %s", option);
        if (mainSearchView != null && option == 1) {
            mainSearchView.clearFocus();
            mainSearchView.setIconified(true);
        }
        setQuery(query = "");
        update();
    }

    /**
     * Refresh the whole screen
     *  - Called by pressing the "New Content" button that appear on new downloads
     *  - Called by scrolling up when being on top of the list ("force reload" command)
     */
    protected void commitRefresh() {
        newContentToolTip.setVisibility(View.GONE);
        refreshLayout.setRefreshing(false);
        refreshLayout.setEnabled(false);
        isNewContentAvailable = false;
        cleanResults();
        update();
        resetCount();
    }

    private void resetCount() {
        Timber.d("Download Count: %s", ContentQueueManager.getInstance().getDownloadCount());
        ContentQueueManager.getInstance().setDownloadCount(0);

        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(0);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.EV_COMPLETE && isLoaded && MODE_LIBRARY == mode) {
            showReloadToolTip();
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_content_list, menu);

        // Associate searchable configuration with the SearchView
        final SearchManager searchManager = (SearchManager)
                mContext.getSystemService(Context.SEARCH_SERVICE);

        MenuItem aboutMikanMenu = menu.findItem(R.id.action_about_mikan);
        aboutMikanMenu.setVisible(MODE_MIKAN == mode);
        if (MODE_MIKAN == mode) {
            aboutMikanMenu.setOnMenuItemClickListener(item -> {
                WebView webView = new WebView(mContext);
                webView.loadUrl("file:///android_asset/about_mikan.html");
                webView.setInitialScale(95);

                android.support.v7.app.AlertDialog mikanDialog = new android.support.v7.app.AlertDialog.Builder(mContext)
                        .setTitle("About Mikan Search")
                        .setView(webView)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();

                mikanDialog.show();

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
                mainSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                setSearchPaneVisibility(false);
/* New behaviour
                if (!("").equals(query)) {
                    query = "";
                    submitSearchQuery(query, 300);
                }
*/
                return true;
            }
        });

        FragmentActivity activity = getActivity();
        if (null == activity) {
            Timber.e("Activity unreachable !");
            return;
        }

        mainSearchView = (SearchView) searchMenu.getActionView();
        if (searchManager != null) {
            mainSearchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        }
        mainSearchView.setIconifiedByDefault(true);
        mainSearchView.setQueryHint(getString(R.string.search_hint));
        mainSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                submitContentSearchQuery(s);
                mainSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (shouldHide && (!s.isEmpty())) {
                    submitContentSearchQuery(s, 1000);
                }

                if (shouldHide && orderUpdated) {
                    clearQuery(0);
                    orderUpdated = false;
                }

                if (!shouldHide && (!s.isEmpty())) {
                    clearQuery(1);
                }

                return true;
            }
        });

        // == SEARCH PANE

        // Create category buttons
        attrSelector = activity.findViewById(R.id.search_tabs);
        attrSelector.addView(createAttributeSectionButton(AttributeType.LANGUAGE));
        attrSelector.addView(createAttributeSectionButton(AttributeType.ARTIST)); // TODO circle in the same tag
        attrSelector.addView(createAttributeSectionButton(AttributeType.TAG));
        attrSelector.addView(createAttributeSectionButton(AttributeType.CHARACTER));
        attrSelector.addView(createAttributeSectionButton(AttributeType.SERIE));
        if(MODE_LIBRARY == mode) attrSelector.addView(createAttributeSectionButton(AttributeType.SOURCE));

        tagWaitPanel = activity.findViewById(R.id.tag_wait_panel);
        tagWaitPanel.setVisibility(View.GONE);
        tagWaitImage = activity.findViewById(R.id.tag_wait_image);
        tagWaitDescription = activity.findViewById(R.id.tag_wait_description);
        tagWaitTitle = activity.findViewById(R.id.tag_wait_title);

/*
        // Attaches listener to favourite filters
        final ImageButton favouriteButton = activity.findViewById(R.id.filter_favs);
        updateFavouriteFilter(favouriteButton);
        favouriteButton.setOnClickListener(v -> toggleFavouriteFilter(favouriteButton));
*/

        SearchView tagSearchView = activity.findViewById(R.id.tag_filter);
        if (searchManager != null) {
            tagSearchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        }
        tagSearchView.setIconifiedByDefault(false);
        tagSearchView.setQueryHint("Search " + selectedTab.name());
        tagSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {

                if (selectedTab.equals(AttributeType.TAG) && IllegalTags.isIllegal(s))
                {
                    Helper.toast(mContext.getString(R.string.masterdata_illegal_tag));
                } else if (!s.isEmpty()) {
                    submitAttributeSearchQuery(selectedTab, s);
                }
                tagSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (selectedTab.equals(AttributeType.TAG) && IllegalTags.isIllegal(s))
                {
                    Helper.toast(mContext.getString(R.string.masterdata_illegal_tag));
                    searchHandler.removeCallbacksAndMessages(null);
                } else if (shouldHide && (!s.isEmpty())) {
                    submitAttributeSearchQuery(selectedTab, s, 1000);
                }

                return true;
            }
        });



        // == BOOKS SORT

        // Sets the right starting icon according to the starting sort order
        switch (order) {
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_FIRST:
                orderMenu.setIcon(R.drawable.ic_menu_sort_321);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_LAST:
                orderMenu.setIcon(R.drawable.ic_menu_sort_by_date);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC:
                orderMenu.setIcon(R.drawable.ic_menu_sort_alpha);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC_INVERTED:
                orderMenu.setIcon(R.drawable.ic_menu_sort_za);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_RANDOM:
                orderMenu.setIcon(R.drawable.ic_menu_sort_random);
                break;
            default:
                // Nothing
        }
    }

    private ImageButton createAttributeSectionButton(AttributeType attr)
    {
        ImageButton button = new ImageButton(mContext);
        button.setBackgroundResource(R.drawable.btn_attribute_section_off);
        button.setImageResource(attr.getIcon());

        button.setClickable(true);
        button.setFocusable(true);

        button.setOnClickListener(v -> selectAttrButton(button));
        button.setTag(attr);

        return button;
    }

    private void selectAttrButton(ImageButton button)
    {
        selectedTab = (AttributeType)button.getTag();
        // Reset color of every tab
        for (View v : attrSelector.getTouchables()) v.setBackgroundResource(R.drawable.btn_attribute_section_off);
        // Set color of selected tab
        button.setBackgroundResource(R.drawable.btn_attribute_section_on);
        // Set hint on search bar
        SearchView tagSearchView = searchPane.findViewById(R.id.tag_filter);
        tagSearchView.setQueryHint("Search " + selectedTab.name().toLowerCase());
        // Run search
        searchMasterData(selectedTab, "");
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(GravityCompat.START);
        shouldHide = (mDrawerState != DrawerLayout.STATE_DRAGGING &&
                mDrawerState != DrawerLayout.STATE_SETTLING && !drawerOpen);

        if (!shouldHide) {
            searchMenu.collapseActionView();
            menu.findItem(R.id.action_search).setVisible(false);
        }
    }

    /**
     * Callback method used when a sort method is selected in the sort drop-down menu
     * => Updates the UI according to the chosen sort method
     *
     * @param item MenuItem that has been selected
     * @return true if the order has been successfuly processed
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean result;

        switch (item.getItemId()) {
            case R.id.action_order_AZ:
                cleanResults();
                orderUpdated = true;
                order = Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC;
                mAdapter.setComparator(Content.TITLE_ALPHA_COMPARATOR);
                orderMenu.setIcon(R.drawable.ic_menu_sort_alpha);
                update();

                result = true;
                break;
            case R.id.action_order_321:
                cleanResults();
                orderUpdated = true;
                order = Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_FIRST;
                mAdapter.setComparator(Content.DLDATE_COMPARATOR);
                orderMenu.setIcon(R.drawable.ic_menu_sort_321);
                update();

                result = true;
                break;
            case R.id.action_order_ZA:
                cleanResults();
                orderUpdated = true;
                order = Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC_INVERTED;
                mAdapter.setComparator(Content.TITLE_ALPHA_INV_COMPARATOR);
                orderMenu.setIcon(R.drawable.ic_menu_sort_za);
                update();

                result = true;
                break;
            case R.id.action_order_123:
                cleanResults();
                orderUpdated = true;
                order = Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_LAST;
                mAdapter.setComparator(Content.DLDATE_INV_COMPARATOR);
                orderMenu.setIcon(R.drawable.ic_menu_sort_by_date);
                update();

                result = true;
                break;
            case R.id.action_order_random:
                cleanResults();
                orderUpdated = true;
                order = Preferences.Constant.PREF_ORDER_CONTENT_RANDOM;
                mAdapter.setComparator(Content.QUERY_ORDER_COMPARATOR);
                RandomSeedSingleton.getInstance().renewSeed();
                orderMenu.setIcon(R.drawable.ic_menu_sort_random);
                update();

                result = true;
                break;
            default:

                result = super.onOptionsItemSelected(item);
        }
        // Save current sort order
        Preferences.setContentSortOrder(order);

        return result;
    }

    /**
     * Toggles the visibility of the search pane
     *
     * @param visible True if search pane has to become visible; false if not
     */
    private void setSearchPaneVisibility(boolean visible) {
        if (visible) {
            if (getQuery().length() > 0)
                clearQuery(1); // Clears any previously active query (search bar)
            searchPane.setVisibility(View.VISIBLE);
        } else {
            searchPane.setVisibility(View.GONE);
        }
        isSearchMode = visible;
    }

    /**
     * Toggles favourite filter on a book and updates the UI accordingly
     *
     * @param button Filter button that has been pressed
     */
    private void toggleFavouriteFilter(ImageButton button) {
        filterFavourites = !filterFavourites;

        updateFavouriteFilter(button);

        searchLibrary();
    }

    /**
     * Update favourite filter button appearance (icon and color) on a book
     *
     * @param button Button to update
     */
    private void updateFavouriteFilter(ImageButton button) {
        if (filterFavourites) {
            button.setImageResource(R.drawable.ic_fav_full);
            button.clearColorFilter();
        } else {
            button.setImageResource(R.drawable.ic_fav_empty);
            button.setColorFilter(Color.BLACK);
        }
    }

    private Button createTagSuggestionButton(Attribute attribute, boolean isSelected) {
        Button button = new Button(mContext);
        button.setText(MessageFormat.format("{0}({1})", attribute.getName(), attribute.getCount()));
        button.setBackgroundResource(R.drawable.btn_attribute_selector);
        button.setMinHeight(0);
        button.setMinimumHeight(0);

        // TODO handle tag state
/*
        if (tagFilters.containsKey(label)) tagState = tagFilters.get(label);
        colorButton(button, tagState);
*/
        // TEMP
        colorButton(button, TAGFILTER_ACTIVE);
        // TEMP

        button.setTag(attribute);
        /*if (!isSelected)*/ button.setId(attribute.getId());

        if (isSelected) button.setOnClickListener(v -> removeTagSuggestion(button));
        else button.setOnClickListener(v -> addTagSuggestion(button));

        return button;
    }

    /**
     * Applies to the edges and text of the given Button the color corresponding to the given state
     *
     * @param b        Button to be updated
     * @param tagState Tag state whose color has to be applied
     */
    private void colorButton(Button b, int tagState) {
        GradientDrawable grad = (GradientDrawable) b.getBackground();
        int color = Color.WHITE;
        if (TAGFILTER_SELECTED == tagState) {
            color = Color.RED;
        } else if (TAGFILTER_INACTIVE == tagState) {
            color = Color.DKGRAY;
        }
        b.setTextColor(color);
        grad.setStroke(3, color);
    }

    private void addTagSuggestion(Button b) {
        Attribute a = (Attribute)b.getTag();

        if (!currentSearchTags.contains(a)) {
            searchTags.addView(createTagSuggestionButton(a, true));
            colorButton(b, TAGFILTER_SELECTED);
            currentSearchTags.add(a);

            // Launch book search
            searchLibrary();
        } else {
            searchTags.removeView(searchTags.findViewById(a.getId()));
            colorButton(b, TAGFILTER_ACTIVE);
            currentSearchTags.remove(a);

            // Launch book search
            searchLibrary();
        }
    }

    private void removeTagSuggestion(Button b) {
        Attribute a = (Attribute)b.getTag();
        currentSearchTags.remove(a);
        searchTags.removeView(b);

        // If displayed, change color of the corresponding button in tag suggestions
        Button tagButton = attributeMosaic.findViewById(a.getId());
        if (tagButton != null) colorButton(tagButton, TAGFILTER_ACTIVE);

        // Launch book search
        searchLibrary();
    }

    private void submitContentSearchQuery(String s) {
        submitContentSearchQuery(s, 0);
    }

    private void submitContentSearchQuery(final String s, long delay) {
        query = s;
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(() -> {
            setQuery(s);
            cleanResults();
            update();
        }, delay);
    }

    private void submitAttributeSearchQuery(AttributeType a, String s) {
        submitAttributeSearchQuery(a, s, 0);
    }

    private void submitAttributeSearchQuery(AttributeType a, final String s, long delay) {
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(() -> searchMasterData(a, s), delay);
    }

    private void showReloadToolTip() {
        if (newContentToolTip.getVisibility() == View.GONE) {
            newContentToolTip.setVisibility(View.VISIBLE);
            refreshLayout.setEnabled(true);
            isNewContentAvailable = true;
        }
    }

    private void cleanResults() {
        if (mAdapter != null) {
            mAdapter.removeAll();
        }
        currentPage = 1;
    }

    private void setQuery(String query) {
        this.query = query;
        currentPage = 1;
    }

    /**
     * Returns the current value of the query typed in the search toolbar; empty string if no query typed
     *
     * @return Current value of the query typed in the search toolbar; empty string if no query typed
     */
    private String getQuery() {
        return query == null ? "" : query;
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

    /**
     * Update screen with book of current page
     */
    protected void update() {
        toggleUI(SHOW_LOADING);
        searchLibrary();
    }

    protected void toggleUI(int mode) {
        switch (mode) {
            case SHOW_LOADING:
                mListView.setVisibility(View.GONE);
                emptyText.setVisibility(View.GONE);
                loadingText.setVisibility(View.VISIBLE);
                //showToolbar(false);
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
     * Runs a new search in the DB according to active filters
     */
    protected void searchLibrary() {
        searchLibrary(isSearchMode);
    }

    protected void searchLibrary(boolean searchMode) {
        String query = this.getQuery();
        isSearchReplaceResults = searchMode;
        isLoaded = false;

        if (searchMode) collectionAccessor.searchBooks(query, currentSearchTags, booksPerPage, order, filterFavourites, this);
        else collectionAccessor.getRecentBooks(Site.HITOMI, Language.ANY, currentPage, booksPerPage, order, filterFavourites, this);
    }

    protected void searchMasterData(AttributeType a, final String s) {
        tagWaitImage.setImageResource(a.getIcon());
        tagWaitTitle.setText(String.format("%s search", Helper.capitalizeString(a.name())) );
        tagWaitDescription.setText(R.string.downloads_loading);

        // Set blinking animation
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(750);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        tagWaitDescription.startAnimation(anim);

        tagWaitPanel.setVisibility(View.VISIBLE);
        collectionAccessor.getAttributeMasterData(a, s, this);
    }

    protected abstract void showToolbar(boolean show);

    protected abstract void displayResults(List<Content> results, int totalContent);

    protected boolean isLastPage() {
        return (currentPage * booksPerPage >= mAdapter.getTotalCount());
    }

    protected void displayNoResults() {
        if (isLoaded && !("").equals(query)) {
            emptyText.setText(R.string.search_entry_not_found);
            toggleUI(SHOW_BLANK);
        } else if (isLoaded) {
            emptyText.setText(R.string.downloads_empty);
            toggleUI(SHOW_BLANK);
        } else {
            Timber.w("Why are we in here?");
        }
    }

    /*
    ContentListener implementation
     */
    @Override
    public void onContentReady(List<Content> results, int totalContent) {
        Timber.d("Content results have loaded : %s results; %s total count", results.size(), totalContent);
        isLoaded = true;

        if (isSearchReplaceResults && isNewContentAvailable)
        {
            newContentToolTip.setVisibility(View.GONE);
            isNewContentAvailable = false;
        }

        // Display new results
        displayResults(results, totalContent);

        mAdapter.setTotalCount(totalContent);
    }

    @Override
    public void onContentFailed() {
        Timber.w("Content results failed to load.");
        Helper.toast("Content results failed to load.");
        isLoaded = false;
    }

    /*
    AttributeListener implementation
     */
    @Override
    public void onAttributesReady(List<Attribute> results, int totalContent) {
        attributeMosaic.removeAllViews();

        tagWaitDescription.clearAnimation();

        // TODO handle display Alpha vs. display by count
        if (0 == totalContent) {
            tagWaitDescription.setText(R.string.masterdata_no_result);
        }
        else if (totalContent <= MAX_ATTRIBUTES_DISPLAYED) {
            for (Attribute attr : results) {
                attributeMosaic.addView(createTagSuggestionButton(attr, false));
            }
            tagWaitPanel.setVisibility(View.GONE);
        } else {
            SearchView tagSearchView = searchPane.findViewById(R.id.tag_filter);
            String searchQuery = tagSearchView.getQuery().toString();

            String errMsg = (0 == searchQuery.length())? mContext.getString(R.string.masterdata_too_many_results_noquery):mContext.getString(R.string.masterdata_too_many_results_query);
            tagWaitDescription.setText(errMsg.replace("%1",searchQuery));
        }
    }

    @Override
    public void onAttributesFailed() {
        Timber.w("Attributes failed to load.");
        Helper.toast("Attributes failed to load.");
        tagWaitPanel.setVisibility(View.GONE);
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

    /*
    ContentsWipedListener implementation
     */
    @Override
    public void onContentsWiped() {
        Timber.d("All items cleared!");
        displayNoResults();
        clearSelection();
        cleanResults();
    }
}
