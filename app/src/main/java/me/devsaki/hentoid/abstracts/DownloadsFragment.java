package me.devsaki.hentoid.abstracts;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
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

import com.google.android.flexbox.FlexboxLayout;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.adapters.ContentAdapter.ContentRemovedListener;
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
        ContentRemovedListener, ItemSelectListener, AttributeListener {

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
    private static final String SELECTED_TAGS = "selected_tags";
    private static final String FILTER_FAVOURITES = "filter_favs";
    private static final String CURRENT_PAGE = "current_page";
    private static final String QUERY = "query";
    private static final String MODE = "mode";


    // ======== UI ELEMENTS

    // Top tooltip appearing when a download has been completed
    protected LinearLayout newContentToolTip;
    // Left drawer
    private DrawerLayout mDrawerLayout;
    // "Search" button on top menu
    private MenuItem searchMenu;
    // "Toggle favourites" button on top menu
    private MenuItem favsMenu;
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
    // Panel that displays the "waiting for metadata info" visuals
    private View tagWaitPanel;
    // Image that displays current metadata type title (e.g. "Character search")
    private TextView tagWaitTitle;
    // Image that displays current metadata type icon (e.g. face icon for character)
    private ImageView tagWaitImage;
    // Image that displays metadata search message (e.g. loading up / too many results / no result)
    private TextView tagWaitMessage;

    // ======== UTIL OBJECTS
    private ObjectAnimator animator;
    // Handler for text searches; needs to be there to be cancelable upon new key press
    private final Handler searchHandler = new Handler();

    // ======== VARIABLES TAKEN FROM PREFERENCES / GLOBAL SETTINGS TO DETECT CHANGES
    // Books per page
    protected int booksPerPage;
    // Books sort order
    private int bookSortOrder;
    // Attributes sort order
    private int attributesSortOrder;

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
    // True if book sort order has been updated
    private boolean bookSortOrderUpdated;
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
    // Total count of book in entire selected/queried collection (Adapter is in charge of updating it)
    private int mTotalSelectedCount = -1; // -1 = uninitialized (no query done yet)
    // Total count of book in entire collection (Adapter is in charge of updating it)
    private int mTotalCount = -1; // -1 = uninitialized (no query done yet)



    // === SEARCH
    // Favourite filter active
    private boolean filterFavourites = false;
    // Expression typed in the search bar
    protected String query = "";
    // Currently selected tab
    private AttributeType selectedTab = AttributeType.TAG;
    // Current search tags
    private List<Attribute> selectedSearchTags = new ArrayList<>();


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
                if (shouldUpdate || -1 == mTotalSelectedCount) update(); // If prefs changes detected or first run (-1 = uninitialized)
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

        if (Preferences.getRootFolderName().isEmpty()) {
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

        if (mTotalCount > -1 && mTotalCount != getDB().countAllContent() ) {
            Timber.d("Library has been refreshed!");
            showReloadToolTip();
            /*
            cleanResults();
            shouldUpdate = true;
            */
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

        int bookOrder = Preferences.getContentSortOrder();
        if (this.bookSortOrder != bookOrder) {
            Timber.d("book sort order updated.");
            bookSortOrderUpdated = true;
            this.bookSortOrder = bookOrder;
        }

        int attrOrder = Preferences.getAttributesSortOrder();
        if (this.attributesSortOrder != attrOrder) {
            Timber.d("attribute sort order updated.");

            // Force the update of currently displayed attribute list, if displayed
            if (attrSelector != null) {
                ImageButton selectedMetadataTab = attrSelector.findViewWithTag(selectedTab);
                if (selectedMetadataTab != null) selectAttrButton(selectedMetadataTab);
            }

            this.attributesSortOrder = attrOrder;
        }

        return shouldUpdate;
    }

    private void checkStorage() {
        if (FileHelper.isSAF()) {
            File storage = new File(Preferences.getRootFolderName());
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
        File file = new File(Preferences.getRootFolderName(), "test");
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
        outState.putBoolean(FILTER_FAVOURITES, filterFavourites);
        outState.putString(QUERY, query);
        outState.putInt(CURRENT_PAGE, currentPage);
        outState.putInt(MODE, mode);

        ArrayList<Integer> selectedTagIds = new ArrayList<>();
        for (Attribute a : selectedSearchTags) selectedTagIds.add(a.getId());
        outState.putIntegerArrayList(SELECTED_TAGS, selectedTagIds);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);

        if (state != null) {
            mListState = state.getParcelable(LIST_STATE_KEY);
            filterFavourites = state.getBoolean(FILTER_FAVOURITES, false);
            query = state.getString(QUERY, "");
            currentPage = state.getInt(CURRENT_PAGE);
            mode = state.getInt(MODE);

            List<Integer> selectedTagIds = state.getIntegerArrayList(SELECTED_TAGS);
            if (selectedTagIds != null) {
                for (Integer i : selectedTagIds) {
                    Attribute a = getDB().selectAttributeById(i);
                    selectedSearchTags.add(a);
                    searchTags.addView(createTagSuggestionButton(a, true));
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
        bookSortOrder = Preferences.getContentSortOrder();
        booksPerPage = Preferences.getContentPageQuantity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);

        if (this.getArguments() != null) mode = this.getArguments().getInt("mode");
        if (MODE_LIBRARY == mode) collectionAccessor = new DatabaseAccessor(mContext); else collectionAccessor = new MikanAccessor(mContext);

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

        if (MODE_MIKAN == mode) bookSortOrder = Preferences.Constant.PREF_ORDER_CONTENT_LAST_UL_DATE_FIRST;

        Comparator<Content> comparator;
        switch (bookSortOrder) {
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

    /**
     * Reset the download count (used to properly display the number of downloads in Notifications)
     */
    private void resetCount() {
        Timber.d("Download Count: %s", ContentQueueManager.getInstance().getDownloadCount());
        ContentQueueManager.getInstance().setDownloadCount(0);

        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(0);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.EV_COMPLETE && isLoaded) {
            if (MODE_LIBRARY == mode) showReloadToolTip();
            else mAdapter.switchStateToDownloaded(event.content);
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

        favsMenu = menu.findItem(R.id.action_favourites);
        favsMenu.setVisible(MODE_LIBRARY == mode);
        updateFavouriteFilter();
        favsMenu.setOnMenuItemClickListener(item -> {
            toggleFavouriteFilter();
            return true;
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

                if (shouldHide && bookSortOrderUpdated) {
                    clearQuery(0);
                    bookSortOrderUpdated = false;
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
        attrSelector.removeAllViews();
        attrSelector.addView(createAttributeSectionButton(AttributeType.LANGUAGE));
        attrSelector.addView(createAttributeSectionButton(AttributeType.ARTIST)); // TODO circle in the same tag
        attrSelector.addView(createAttributeSectionButton(AttributeType.TAG));
        attrSelector.addView(createAttributeSectionButton(AttributeType.CHARACTER));
        attrSelector.addView(createAttributeSectionButton(AttributeType.SERIE));
        if(MODE_LIBRARY == mode) attrSelector.addView(createAttributeSectionButton(AttributeType.SOURCE));

        tagWaitPanel = activity.findViewById(R.id.tag_wait_panel);
        tagWaitPanel.setVisibility(View.GONE);
        tagWaitImage = activity.findViewById(R.id.tag_wait_image);
        tagWaitMessage = activity.findViewById(R.id.tag_wait_description);
        tagWaitTitle = activity.findViewById(R.id.tag_wait_title);

        SearchView tagSearchView = activity.findViewById(R.id.tag_filter);
        if (searchManager != null) {
            tagSearchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        }
        tagSearchView.setIconifiedByDefault(false);
        tagSearchView.setQueryHint("Search " + selectedTab.name().toLowerCase());
        tagSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {

                if (MODE_MIKAN == mode && selectedTab.equals(AttributeType.TAG) && IllegalTags.isIllegal(s))
                {
                    Helper.toast(mContext, R.string.masterdata_illegal_tag, Helper.DURATION.LONG);
                } else if (!s.isEmpty()) {
                    submitAttributeSearchQuery(selectedTab, s);
                }
                tagSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (MODE_MIKAN == mode && selectedTab.equals(AttributeType.TAG) && IllegalTags.isIllegal(s))
                {
                    Helper.toast(mContext, R.string.masterdata_illegal_tag, Helper.DURATION.LONG);
                    searchHandler.removeCallbacksAndMessages(null);
                } else if (shouldHide && (!s.isEmpty())) {
                    submitAttributeSearchQuery(selectedTab, s, 1000);
                }

                return true;
            }
        });



        // == BOOKS SORT

        // Sets the right starting icon according to the starting sort order
        switch (bookSortOrder) {
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

    // TODO documentation of new methods
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
        tagSearchView.setVisibility(View.VISIBLE);
        tagSearchView.setQuery("", false);
        tagSearchView.setQueryHint("Search " + selectedTab.name().toLowerCase());
        // Remove previous tag suggestions
        attributeMosaic.removeAllViews();
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
                bookSortOrderUpdated = true;
                bookSortOrder = Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC;
                mAdapter.setComparator(Content.TITLE_ALPHA_COMPARATOR);
                orderMenu.setIcon(R.drawable.ic_menu_sort_alpha);
                update();

                result = true;
                break;
            case R.id.action_order_321:
                cleanResults();
                bookSortOrderUpdated = true;
                bookSortOrder = Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_FIRST;
                mAdapter.setComparator(Content.DLDATE_COMPARATOR);
                orderMenu.setIcon(R.drawable.ic_menu_sort_321);
                update();

                result = true;
                break;
            case R.id.action_order_ZA:
                cleanResults();
                bookSortOrderUpdated = true;
                bookSortOrder = Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC_INVERTED;
                mAdapter.setComparator(Content.TITLE_ALPHA_INV_COMPARATOR);
                orderMenu.setIcon(R.drawable.ic_menu_sort_za);
                update();

                result = true;
                break;
            case R.id.action_order_123:
                cleanResults();
                bookSortOrderUpdated = true;
                bookSortOrder = Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_LAST;
                mAdapter.setComparator(Content.DLDATE_INV_COMPARATOR);
                orderMenu.setIcon(R.drawable.ic_menu_sort_by_date);
                update();

                result = true;
                break;
            case R.id.action_order_random:
                cleanResults();
                bookSortOrderUpdated = true;
                bookSortOrder = Preferences.Constant.PREF_ORDER_CONTENT_RANDOM;
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
        Preferences.setContentSortOrder(bookSortOrder);

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
    }

    /**
     * Toggles favourite filter on a book and updates the UI accordingly
     */
    private void toggleFavouriteFilter() {
        filterFavourites = !filterFavourites;
        updateFavouriteFilter();
        searchLibrary();
        updateAttributeMosaic();
    }

    /**
     * Update favourite filter button appearance (icon and color) on a book
     */
    private void updateFavouriteFilter() {
        favsMenu.setIcon(filterFavourites?R.drawable.ic_fav_full:R.drawable.ic_fav_empty);
    }

    private Button createTagSuggestionButton(Attribute attribute, boolean isSelected) {
        Button button = new Button(mContext);
        button.setText(MessageFormat.format("{0}({1})", attribute.getName(), attribute.getCount()));
        button.setBackgroundResource(R.drawable.btn_attribute_selector);
        button.setMinHeight(0);
        button.setMinimumHeight(0);

        colorButton(button, TAGFILTER_ACTIVE);

        button.setTag(attribute);
        button.setId(Math.abs(attribute.getId()));

        if (isSelected) button.setOnClickListener(v -> selectSearchTag(button));
        else button.setOnClickListener(v -> selectTagSuggestion(button));

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

    private void selectTagSuggestion(Button b) {
        Attribute a = (Attribute)b.getTag();

        // Add new tag to the selection
        if (!selectedSearchTags.contains(a)) {
            searchTags.addView(createTagSuggestionButton(a, true));
            colorButton(b, TAGFILTER_SELECTED);
            selectedSearchTags.add(a);
        } else { // Remove selected tag
            searchTags.removeView(searchTags.findViewById(Math.abs(a.getId())));
            colorButton(b, TAGFILTER_ACTIVE);
            selectedSearchTags.remove(a);
        }

        // Launch book search according to new attribute selection
        searchLibrary();
        // Update attribute mosaic buttons state according to available metadata
        updateAttributeMosaic();
    }

    private void selectSearchTag(Button b) {
        Attribute a = (Attribute)b.getTag();
        selectedSearchTags.remove(a);
        searchTags.removeView(b);

        // If displayed, change color of the corresponding button in tag suggestions
        Button tagButton = attributeMosaic.findViewById(Math.abs(a.getId()));
        if (tagButton != null) colorButton(tagButton, TAGFILTER_ACTIVE);

        // Launch book search according to new attribute selection
        searchLibrary();
        // Update attribute mosaic buttons state according to available metadata
        updateAttributeMosaic();
    }

    private void updateAttributeMosaic()
    {
        // Refresh attributes list according to selected attributes (library mode only)
        if (MODE_LIBRARY == mode)
        {
            List<Attribute> searchTags = new ArrayList<>();
            List<Integer> searchSites = new ArrayList<>();

            for (Attribute attr : selectedSearchTags)
            {
                if (attr.getType().equals(AttributeType.SOURCE)) searchSites.add(attr.getId());
                else searchTags.add(attr);
            }

            // TODO run DB transaction on a dedicated thread
            List<Attribute> availableAttrs;
            if (selectedTab.equals(AttributeType.SOURCE))
            {
                availableAttrs = getDB().selectAvailableSources();
            } else {
                availableAttrs = getDB().selectAvailableAttributes(selectedTab.getCode(), searchTags, searchSites, filterFavourites);
            }

            // Refresh displayed tag buttons
            boolean found, selected;
            String label = "";
            for (int i=0; i<attributeMosaic.getChildCount(); i++)
            {
                Button button = (Button)attributeMosaic.getChildAt(i);
                Attribute displayedAttr = (Attribute)button.getTag();
                if (displayedAttr != null)
                {
                    found = false;
                    for (Attribute attr : availableAttrs)
                        if (attr.getId().equals(displayedAttr.getId()))
                        {
                            found = true;
                            label = attr.getName() + " ("+attr.getCount()+")";
                            break;
                        }
                    if (!found) label = displayedAttr.getName() + " (0)";

                    selected = false;
                    for (Attribute attr : selectedSearchTags)
                        if (attr.getId().equals(displayedAttr.getId()))
                        {
                            selected = true;
                            break;
                        }
                    colorButton(button, selected?TAGFILTER_SELECTED:found?TAGFILTER_ACTIVE:TAGFILTER_INACTIVE);
                    button.setText(label);
                }
            }
        }
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
    private boolean isSearchMode()
    {
        return ( (query != null && query.length() > 0) || selectedSearchTags.size() > 0);
    }

    protected void searchLibrary() {
        String query = this.getQuery();
        isLoaded = false;

        if (MODE_MIKAN == mode) toggleUI(SHOW_LOADING);

        if (isSearchMode()) collectionAccessor.searchBooks(query, selectedSearchTags, currentPage, booksPerPage, bookSortOrder, filterFavourites, this);
        else collectionAccessor.getRecentBooks(Site.HITOMI, Language.ANY, currentPage, booksPerPage, bookSortOrder, filterFavourites, this);
    }

    protected void searchMasterData(AttributeType a, final String s) {
        tagWaitImage.setImageResource(a.getIcon());
        tagWaitTitle.setText(String.format("%s search", Helper.capitalizeString(a.name())) );
        tagWaitMessage.setText(R.string.downloads_loading);

        // Set blinking animation
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(750);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        tagWaitMessage.startAnimation(anim);

        tagWaitPanel.setVisibility(View.VISIBLE);
        collectionAccessor.getAttributeMasterData(a, s, this);
    }

    protected abstract void showToolbar(boolean show);

    protected abstract void displayResults(List<Content> results, int totalSelectedContent);

    protected boolean isLastPage() {
        return (currentPage * booksPerPage >= mTotalSelectedCount);
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

    private void updateTitle()
    {
        Activity activity = getActivity();
        if (null != activity) {
            if (mTotalSelectedCount == mTotalCount) activity.setTitle("(" + mTotalCount + ")");
            else activity.setTitle("(" + mTotalSelectedCount + "/" + mTotalCount + ")");
        }
    }

    /*
    ContentListener implementation
     */
    @Override
    public void onContentReady(List<Content> results, int totalSelectedContent, int totalContent) {
        Timber.d("Content results have loaded : %s results; %s total selected count, %s total count", results.size(), totalSelectedContent, totalContent);
        isLoaded = true;

        if (isSearchMode() && isNewContentAvailable)
        {
            newContentToolTip.setVisibility(View.GONE);
            isNewContentAvailable = false;
        }

        // Display new results
        displayResults(results, totalSelectedContent);

        mTotalSelectedCount = totalSelectedContent;
        mTotalCount = totalContent;

        updateTitle();
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

        tagWaitMessage.clearAnimation();

        if (0 == totalContent) {
            tagWaitMessage.setText(R.string.masterdata_no_result);
        } else if (totalContent > MAX_ATTRIBUTES_DISPLAYED) {
            SearchView tagSearchView = searchPane.findViewById(R.id.tag_filter);
            String searchQuery = tagSearchView.getQuery().toString();

            String errMsg = (0 == searchQuery.length())? mContext.getString(R.string.masterdata_too_many_results_noquery):mContext.getString(R.string.masterdata_too_many_results_query);
            tagWaitMessage.setText(errMsg.replace("%1",searchQuery));
        } else {
            // Sort items according to prefs
            Comparator<Attribute> comparator;
            switch (attributesSortOrder) {
                case Preferences.Constant.PREF_ORDER_ATTRIBUTES_ALPHABETIC:
                    comparator = Attribute.NAME_COMPARATOR;
                    break;
                default:
                    comparator = Attribute.COUNT_COMPARATOR;
            }
            Attribute[] attrs = results.toArray(new Attribute[results.size()]); // Well, yes, since results.sort(comparator) requires API 24...
            Arrays.sort(attrs, comparator);

            // Display buttons
            for (Attribute attr : attrs) {
                View button = createTagSuggestionButton(attr, false);
                attributeMosaic.addView(button);
                FlexboxLayout.LayoutParams lp = (FlexboxLayout.LayoutParams) button.getLayoutParams();
                lp.setFlexGrow(1);
                button.setLayoutParams(lp);
            }

            // Update attribute mosaic buttons state according to available metadata
            updateAttributeMosaic();
            tagWaitPanel.setVisibility(View.GONE);
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
    ContentRemovedListener implementation
     */
    @Override
    public void onAllContentRemoved() {
        Timber.d("All items cleared!");
        mTotalSelectedCount = 0;
        mTotalCount = 0;
        currentPage = 1;

        displayNoResults();
        clearSelection();
        updateTitle();
    }

    @Override
    public void onContentRemoved(int i) {
        mTotalSelectedCount = mTotalSelectedCount - i;
        mTotalCount = mTotalCount - i;
        updateTitle();
    }
}
