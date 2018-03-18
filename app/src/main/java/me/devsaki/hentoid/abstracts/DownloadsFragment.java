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
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.adapters.ContentAdapter.ContentsWipedListener;
import me.devsaki.hentoid.database.SearchContent;
import me.devsaki.hentoid.database.SearchContent.ContentListener;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.listener.ItemClickListener.ItemSelectListener;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.DURATION.LONG;

/**
 * Created by avluis on 08/27/2016.
 * Common elements for use by EndlessFragment and PagerFragment
 * TODO: Dismiss 'new content' tooltip upon search
 * TODO: Fix empty 2nd page when books count = booksPerPage
 */
public abstract class DownloadsFragment extends BaseFragment implements ContentListener,
        ContentsWipedListener, ItemSelectListener {

    // ======== CONSTANTS

    protected static final int SHOW_DEFAULT = 0;
    protected static final int SHOW_LOADING = 1;
    protected static final int SHOW_BLANK = 2;
    protected static final int SHOW_RESULT = 3;

    protected static final int TAGFILTER_ACTIVE = 0;
    protected static final int TAGFILTER_SELECTED = 1;
    protected static final int TAGFILTER_INACTIVE = 3;

    private static final String LIST_STATE_KEY = "list_state";


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
    private SearchView searchView;
    // Search pane that shows up on top when using search function
    protected View searchPane;
    // Tag mosaic at the bottom of the search pane
    private ViewGroup tagMosaic;
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
    protected Toolbar pagerToolbar;
    // Button containing the page number on Paged view
    private Button btnPage;


    // == VARIABLES TAKEN FROM SETTINGS
    // Books per page
    protected int booksPerPage;
    // Hentoid directory
    private String settingDir;


    // == VARIABLES

    protected Context mContext;
    // Expression typed in the search bar
    protected String query = "";
    private final Handler searchHandler = new Handler();
    protected int currentPage = 1;
    protected ContentAdapter mAdapter;
    // True if a new download is ready; used to display / hide "New Content" tooltip when scrolling
    protected boolean isNewContentAvailable;
    protected boolean override;
    // True if current page is last page (EndlessView : a "page" is a group of books in the list; last page means there is nothing left to load)
    protected boolean isLastPage;
    // True if book list has finished loading; used for synchronization between threads
    protected boolean isLoaded;
    // True if search mode is active
    protected boolean isSearchMode = false;
    private ActionMode mActionMode;
    private int mDrawerState;
    private boolean shouldHide;
    private Parcelable mListState;
    // Active tag filters
    private Map<String, Integer> tagFilters;
    // Active site filters
    private List<Integer> siteFilters;
    // Indicates whether or not one of the books has been selected
    private boolean isSelected;
    private boolean selectTrigger = false;
    // Books sort order
    private int order;
    // True if sort order has been updated
    private boolean orderUpdated;
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;
    private boolean permissionChecked;
    private ObjectAnimator animator;

    // States for search bar buttons
    private boolean filterByTitle = true;
    private boolean filterByArtist = true;
    private boolean filterByTag = false;


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

        checkPermissions();

        if (mListState != null) {
            llm.onRestoreInstanceState(mListState);
        }
    }

    // Validate permissions
    private void checkPermissions() {
        if (Helper.permissionsCheck(getActivity(), ConstsImport.RQST_STORAGE_PERMISSION, true)) {
            queryPrefs();
            checkResults();
        } else {
            Timber.d("Storage permission denied!");
            if (permissionChecked) {
                reset();
            }
            permissionChecked = true;
        }
    }

    protected void queryPrefs() {
        Timber.d("Querying Prefs.");
        boolean shouldUpdate = false;

        if (settingDir.isEmpty()) {
            Timber.d("Where are my files?!");
            Intent intent = new Intent(getActivity(), ImportActivity.class);
            startActivity(intent);
            getActivity().finish();
        }

        String settingDir = Preferences.getRootFolderName();

        if (!this.settingDir.equals(settingDir)) {
            Timber.d("Library directory has changed!");
            this.settingDir = settingDir;
            cleanResults();
            shouldUpdate = true;
        }

        if (Helper.isAtLeastAPI(Build.VERSION_CODES.LOLLIPOP)) {
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

        if (shouldUpdate) {
            update();
        }
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
                    getActivity().finish();
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

    protected abstract void checkResults();

    private void reset() {
        Helper.reset(HentoidApp.getAppContext(), getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();

        clearSelection();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mListState = llm.onSaveInstanceState();
        outState.putParcelable(LIST_STATE_KEY, mListState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle state) {
        super.onViewStateRestored(state);

        if (state != null) {
            mListState = state.getParcelable(LIST_STATE_KEY);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle state) {
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

        initUI(rootView);
        attachScrollListener();
        attachOnClickListeners(rootView);

        return rootView;
    }

    private void initUI(View rootView) {
        loadingText = rootView.findViewById(R.id.loading);
        emptyText = rootView.findViewById(R.id.empty);

        // Main view
        mListView = rootView.findViewById(R.id.list);
        mListView.setHasFixedSize(true);
        llm = new LinearLayoutManager(mContext);
        mListView.setLayoutManager(llm);

        Comparator<Content> comparator;
        switch(order)
        {
            case Preferences.Constant.PREF_ORDER_CONTENT_BY_DATE:
                comparator = Content.DLDATE_COMPARATOR;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_BY_DATE_INVERTED:
                comparator = Content.DLDATE_INV_COMPARATOR;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC:
                comparator = Content.TITLE_ALPHA_COMPARATOR;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC_INVERTED:
                comparator = Content.TITLE_ALPHA_INV_COMPARATOR;
                break;
            default :
                comparator = Content.QUERY_ORDER_COMPARATOR;
        }

        mAdapter = new ContentAdapter(mContext, this, comparator);
        mListView.setAdapter(mAdapter);

        if (mAdapter.getItemCount() == 0) {
            mListView.setVisibility(View.GONE);
            loadingText.setVisibility(View.VISIBLE);
        }

        // Drawer
        mDrawerLayout = getActivity().findViewById(R.id.drawer_layout);
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {

            @Override
            public void onDrawerStateChanged(int newState) {
                mDrawerState = newState;
                getActivity().invalidateOptionsMenu();
            }
        });

        btnPage = rootView.findViewById(R.id.btnPage);
        pagerToolbar = rootView.findViewById(R.id.downloads_toolbar);
        newContentToolTip = rootView.findViewById(R.id.tooltip);
        refreshLayout = rootView.findViewById(R.id.swipe_container);

        // Tag filter
        tagMosaic = rootView.findViewById(R.id.filter_tags);
        searchPane = rootView.findViewById(R.id.tag_filter_view);

        tagFilters = new HashMap<>();
        // Init site filters; all on by default
        siteFilters = new ArrayList<>();
        siteFilters.add(Site.NHENTAI.getCode());
        siteFilters.add(Site.HITOMI.getCode());
        siteFilters.add(Site.ASMHENTAI.getCode());
        siteFilters.add(Site.ASMHENTAI_COMICS.getCode());
        siteFilters.add(Site.HENTAICAFE.getCode());
        siteFilters.add(Site.PURURIN.getCode());
        siteFilters.add(Site.TSUMINO.getCode());
    }

    protected void attachScrollListener() {
        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // Show toolbar:
                if (!override && mAdapter.getItemCount() > 0) {
                    // At top of list
                    if (llm.findViewByPosition(llm.findFirstVisibleItemPosition())
                            .getTop() == 0 && llm.findFirstVisibleItemPosition() == 0) {
                        showToolbar(true, false);
                        if (isNewContentAvailable) {
                            newContentToolTip.setVisibility(View.VISIBLE);
                        }
                    }

                    // Last item in list
                    if (llm.findLastVisibleItemPosition() == mAdapter.getItemCount() - 1) {
                        showToolbar(true, false);
                        if (isNewContentAvailable) {
                            newContentToolTip.setVisibility(View.VISIBLE);
                        }
                    } else {
                        // When scrolling up
                        if (dy < -10) {
                            showToolbar(true, false);
                            if (isNewContentAvailable) {
                                newContentToolTip.setVisibility(View.VISIBLE);
                            }
                            // When scrolling down
                        } else if (dy > 100) {
                            showToolbar(false, false);
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
        if (searchView != null && option == 1) {
            searchView.clearFocus();
            searchView.setIconified(true);
        }
        setQuery(query = "");
        update();
    }

    /**
     * Called by pressing the "New Content" button that appear on new downloads
     */
    protected void commitRefresh() {
        newContentToolTip.setVisibility(View.GONE);
        refreshLayout.setRefreshing(false);
        refreshLayout.setEnabled(false);
        isNewContentAvailable = false;
        if (filterByTag) updateTagMosaic();
        cleanResults();
        update();
        resetCount();
    }

    private void resetCount() {
        Timber.d("Download Count: %s", HentoidApp.getDownloadCount());
        HentoidApp.setDownloadCount(0);

        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(0);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        Double percent = event.percent;
        if (percent >= 0) {
            Timber.d("Download Progress: %s", percent);
        } else if (isLoaded) {
            showReloadToolTip();
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_content_list, menu);

        // Associate searchable configuration with the SearchView
        final SearchManager searchManager = (SearchManager)
                mContext.getSystemService(Context.SEARCH_SERVICE);

        searchMenu = menu.findItem(R.id.action_search);
        orderMenu = menu.findItem(R.id.action_order);
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                setSearchPaneVisibility(true);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                setSearchPaneVisibility(false);

                if (!("").equals(query)) {
                    query = "";
                    submitSearchQuery(query, 300);
                }

                return true;
            }
        });

        searchView = (SearchView) searchMenu.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(
                getActivity().getComponentName()));
        searchView.setIconifiedByDefault(true);
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                submitSearchQuery(s);
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (shouldHide && (!s.isEmpty())) {
                    submitSearchQuery(s, 1000);
                }

                if (shouldHide && orderUpdated) {
                    clearQuery(0);
                    orderUpdated = false;
                }

                if (!shouldHide && (!s.isEmpty())) {
                    clearQuery(1);
                }

                if (s.isEmpty() && filterByTag)
                {
                    for (String tag : tagFilters.keySet())
                    {
                        Button btn = tagMosaic.findViewWithTag(tag);
                        if (btn != null) btn.setVisibility(View.VISIBLE);
                    }
                }

                return true;
            }
        });

        // == SEARCH PANE

        // Attaches listeners to website filters
        final View nHentaiButton = getActivity().findViewById(R.id.filter_nhentai);
        nHentaiButton.setOnClickListener(v -> selectSiteFilter(nHentaiButton, Site.NHENTAI.getCode()));

        final View hitomiButton = getActivity().findViewById(R.id.filter_hitomi);
        hitomiButton.setOnClickListener(v -> selectSiteFilter(hitomiButton, Site.HITOMI.getCode()));

        final View hentaiCafeButton = getActivity().findViewById(R.id.filter_hentaicafe);
        hentaiCafeButton.setOnClickListener(v -> selectSiteFilter(hentaiCafeButton, Site.HENTAICAFE.getCode()));

        final View asmButton = getActivity().findViewById(R.id.filter_asm);
        asmButton.setOnClickListener(v -> selectSiteFilter(asmButton, Site.ASMHENTAI.getCode()));

        final View asmComicsButton = getActivity().findViewById(R.id.filter_asmcomics);
        asmComicsButton.setOnClickListener(v -> selectSiteFilter(asmComicsButton, Site.ASMHENTAI_COMICS.getCode()));

        final View tsminoButton = getActivity().findViewById(R.id.filter_tsumino);
        tsminoButton.setOnClickListener(v -> selectSiteFilter(tsminoButton, Site.TSUMINO.getCode()));

        final View pururinButton = getActivity().findViewById(R.id.filter_pururin);
        pururinButton.setOnClickListener(v -> selectSiteFilter(pururinButton, Site.PURURIN.getCode()));

        // Attach listeners to category filter buttons
        getActivity().findViewById(R.id.search_filter_title).setOnClickListener(v -> selectFieldFilter(!filterByTitle, filterByArtist, filterByTag));
        getActivity().findViewById(R.id.search_filter_artist).setOnClickListener(v -> selectFieldFilter(filterByTitle, !filterByArtist, filterByTag));
        getActivity().findViewById(R.id.search_filter_tag).setOnClickListener(v -> selectFieldFilter(filterByTitle, filterByArtist, !filterByTag));

        // == BOOKS SORT

        // Sets the right starting icon according to the starting sort order
        switch(order)
        {
            case Preferences.Constant.PREF_ORDER_CONTENT_BY_DATE:
                orderMenu.setIcon(R.drawable.ic_menu_sort_321);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_BY_DATE_INVERTED:
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
            default :
                // Nothing
        }
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean result = false;

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
                order = Preferences.Constant.PREF_ORDER_CONTENT_BY_DATE;
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
                order = Preferences.Constant.PREF_ORDER_CONTENT_BY_DATE_INVERTED;
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
     * Toggles the use of tag filter. This has an effect on :
     *   - visibility of the sheet with the tag filter
     */
    private void setSearchPaneVisibility(boolean visible)
    {
        if(visible) {
            if (getQuery().length() > 0) clearQuery(1); // Clears any previously active query (search bar)
            searchPane.setVisibility(View.VISIBLE);
        }
        else {
            searchPane.setVisibility(View.GONE);
        }
        isSearchMode = visible;
    }

    private void selectSiteFilter(View button, int siteCode)
    {
        ImageButton imgButton = (ImageButton)button;

        if (siteFilters.contains(siteCode))
        {
            siteFilters.remove(Integer.valueOf(siteCode));
            imgButton.setColorFilter(Color.BLACK);
        } else {
            siteFilters.add(Integer.valueOf(siteCode));
            imgButton.clearColorFilter();
        }
        if (filterByTag) updateTagMosaic();

        searchContent();
    }

    private void selectFieldFilter(boolean filterByTitle, boolean filterByArtist, boolean filterByTag)
    {
        if (filterByTag && !this.filterByTag)
        {
            this.filterByTitle = false;
            ((ToggleButton)getActivity().findViewById(R.id.search_filter_title)).setChecked(false);
            this.filterByArtist = false;
            ((ToggleButton)getActivity().findViewById(R.id.search_filter_artist)).setChecked(false);

            // Enable tag mosaic
            ViewGroup.LayoutParams params = searchPane.getLayoutParams();
            params.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, getResources().getDisplayMetrics());
            searchPane.setLayoutParams(params);

            updateTagMosaic();
            tagMosaic.setVisibility(View.VISIBLE);
            this.filterByTag = true;
            setQuery("");
            searchView.setQuery("", false);
            searchContent();
        }
        else if (this.filterByTag && (!filterByTag || filterByTitle || filterByArtist)) {

            // Get back to the default filter = title
            if (!filterByTag && !this.filterByTitle && !this.filterByArtist) {
                this.filterByTitle = true;
                ((ToggleButton) getActivity().findViewById(R.id.search_filter_title)).setChecked(true);
            } else {
                this.filterByTitle = filterByTitle;
                this.filterByArtist = filterByArtist;
            }
            ((ToggleButton) getActivity().findViewById(R.id.search_filter_tag)).setChecked(false);

            // Disable tag mosaic
            ViewGroup.LayoutParams params = searchPane.getLayoutParams();
            params.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());;
            searchPane.setLayoutParams(params);

            tagMosaic.setVisibility(View.GONE);
            this.filterByTag = false;
            tagFilters.clear();
            setQuery("");
            searchView.setQuery("", false);
            searchContent();
        }
        else
        {
            this.filterByTitle = filterByTitle;
            this.filterByArtist = filterByArtist;
        }
    }

    /**
     * Updates the displayed tags in the tag mosaic, according to :
     *   - owned books and their tags
     *   - selected site filters
     */
    private void updateTagMosaic() { updateTagMosaic(true); }
    private void updateTagMosaic(boolean removeNotFound)
    {
        List<String> selectedTags = new ArrayList<>();
        for (String key : tagFilters.keySet()) {
            if (TAGFILTER_SELECTED == tagFilters.get(key)) selectedTags.add(key);
        }
        List<Pair<String,Integer>> tags = getDB().selectAllAttributesByUsage(AttributeType.TAG.getCode(), selectedTags, siteFilters);

        // Removes all tag buttons
        if (removeNotFound)
        {
            tagMosaic.removeAllViews();

            // Set all buttons to be removed
            for(String key : tagFilters.keySet())
            {
                tagFilters.put(key, tagFilters.get(key) + 10);
            }

            for(Pair<String,Integer> val : tags)
            {
                if (!tagFilters.containsKey(val.first)) tagFilters.put(val.first, TAGFILTER_ACTIVE); // Brand new tag
                else if (tagFilters.get(val.first) > 9) tagFilters.put(val.first, tagFilters.get(val.first) - 10); // Reuse of previous tag

                addTagButton(val.first, val.second);
            }

            // Purge unused filter entries
            Set<String> keySet = new HashSet<>();
            keySet.addAll(tagFilters.keySet());
            for (String key : keySet) {
                if (tagFilters.get(key) > 9) tagFilters.remove(key);
            }
        } else {
            List<String> availableTags = new ArrayList<>();
            for(Pair<String,Integer> val : tags)
            {
                availableTags.add(val.first);
            }

            for (String key : tagFilters.keySet()) {
                Button b = tagMosaic.findViewWithTag(key);

                if (availableTags.contains(key)) {
                    if (TAGFILTER_INACTIVE == tagFilters.get(key)) {
                        tagFilters.put(key, TAGFILTER_ACTIVE);
                        colorButton(b, TAGFILTER_ACTIVE);
                    }
                }
                else {
                    tagFilters.put(key, TAGFILTER_INACTIVE);
                    colorButton(b, TAGFILTER_INACTIVE);
                }
            }
        }
    }

    /**
     * Adds a tag filter button in the tag filter bottom sheet.
     * The button displays "label (count)"
     *
     * @param label Label to display on the button to add
     * @param count Count to display on the button to add
     */
    private void addTagButton(String label, Integer count)
    {
        Button button = new Button(mContext);
        button.setText(label + "("+count+")");
        button.setBackgroundResource(R.drawable.btn_buttonshape);
        button.setMinHeight(0);
        button.setMinimumHeight(0);

        int tagState = TAGFILTER_ACTIVE;
        if (tagFilters.containsKey(label)) tagState = tagFilters.get(label);
        colorButton(button, tagState);

        button.setOnClickListener( v -> selectTagFilter(button, label) );
        button.setTag(label);

        tagMosaic.addView(button);
    }

    private void colorButton(Button b, int tagState)
    {
        GradientDrawable grad = (GradientDrawable)b.getBackground();
        int color = Color.WHITE;
        if (TAGFILTER_SELECTED == tagState) {
            color = Color.RED;
        }
        else if (TAGFILTER_INACTIVE == tagState) {
            color = Color.DKGRAY;
        }
        b.setTextColor(color);
        grad.setStroke(3, color);
    }

    /**
     * Updates visible books of the collection according to the selected tag filters
     * This method is fired every time a tag filter button is pressed
     *
     * @param b Pressed button
     * @param tag Tag represented by the pressed button
     */
    private void selectTagFilter(Button b, String tag)
    {
        GradientDrawable grad = (GradientDrawable)b.getBackground();
        boolean doSearch = true;

        if (tagFilters.containsKey(tag)) {

            switch (tagFilters.get(tag)) {
                case TAGFILTER_ACTIVE:
                    b.setTextColor(Color.RED);
                    grad.setStroke(3, Color.RED);
                    tagFilters.put(tag, TAGFILTER_SELECTED);
                    break;
                case TAGFILTER_SELECTED:
                    b.setTextColor(Color.WHITE);
                    grad.setStroke(3, Color.WHITE);
                    tagFilters.put(tag, TAGFILTER_ACTIVE);
                    break;
                default:
                    // Inactive button
                    doSearch = false;
            }

            // Update filtered books
            if (doSearch) {
                searchContent();
                Handler handler = new Handler();
                handler.post(() -> { updateTagMosaic(false); });
            }
        }
        else
        {
            Timber.d("Tag %s absent from tagFilter", tag);
        }
    }


    private void submitSearchQuery(String s) {
        submitSearchQuery(s, 0);
    }

    private void submitSearchQuery(final String s, long delay) {
        if (!filterByTag) { // Search actual books based on query
            query = s;
            searchHandler.removeCallbacksAndMessages(null);
            searchHandler.postDelayed(() -> {
                setQuery(s);
                cleanResults();
                update();
            }, delay);
        } else { // Filter tag mosaic based on query
            for (String tag : tagFilters.keySet())
            {
                Button btn = tagMosaic.findViewWithTag(tag);
                if (btn != null) {
                    if (tag.contains(s)) {
                        btn.setVisibility(View.VISIBLE);
                    } else {
                        btn.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    protected void checkContent(boolean clear) {
        if (clear) {
            resetCount();
        } else {
            if (HentoidApp.getDownloadCount() > 0) {
                if (isLoaded) {
                    showReloadToolTip();
                }
            } else {
                setCurrentPage();
                showToolbar(true, false);
            }
        }
    }

    private void showReloadToolTip() {
        if (newContentToolTip.getVisibility() == View.GONE) {
            newContentToolTip.setVisibility(View.VISIBLE);
            refreshLayout.setEnabled(true);
            isNewContentAvailable = true;
        } else {
            Timber.d("Tooltip visible.");
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
        return query == null?"":query;
    }

    private void clearSelection() {
        if (mAdapter != null) {
            if (mListView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                mAdapter.clearSelections();
                selectTrigger = false;
                showToolbar(true, false);
            }
            isSelected = false;
        }
    }

    protected void update() {
        toggleUI(SHOW_LOADING);
        searchContent();
        setCurrentPage();
    }

    private void setCurrentPage() {
        btnPage.setText(String.valueOf(currentPage));
    }

    protected void toggleUI(int mode) {
        switch (mode) {
            case SHOW_LOADING:
                mListView.setVisibility(View.GONE);
                emptyText.setVisibility(View.GONE);
                loadingText.setVisibility(View.VISIBLE);
                showToolbar(false, false);
                startAnimation();
                break;
            case SHOW_BLANK:
                mListView.setVisibility(View.GONE);
                emptyText.setVisibility(View.VISIBLE);
                loadingText.setVisibility(View.GONE);
                showToolbar(false, false);
                break;
            case SHOW_RESULT:
                mListView.setVisibility(View.VISIBLE);
                emptyText.setVisibility(View.GONE);
                loadingText.setVisibility(View.GONE);
                showToolbar(true, false);
                break;
            default:
                stopAnimation();
                loadingText.setVisibility(View.GONE);
                break;
        }
    }

    private void startAnimation() {
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

    private void stopAnimation() {
        Drawable[] compoundDrawables = loadingText.getCompoundDrawables();
        for (Drawable drawable : compoundDrawables) {
            if (drawable == null) {
                continue;
            }
            animator.cancel();
        }
    }

    protected void searchContent() {
        List<String> selectedTags = new ArrayList<>();
        String query = this.getQuery();

        // Populate tag filter if tag filtering is active
        if (filterByTag)
        {
            for (String key : tagFilters.keySet()) {
                if (TAGFILTER_SELECTED == tagFilters.get(key)) selectedTags.add(key);
            }
            // Tag filter is incompatible with search by keyword
            query = "";
        }

        isLoaded = false;
        SearchContent search = new SearchContent(mContext, this);
        search.retrieveResults(filterByTitle?query:"", filterByArtist?query:"", currentPage, booksPerPage, selectedTags, siteFilters, order);
    }

    protected abstract void showToolbar(boolean show, boolean override);

    protected abstract void displayResults(List<Content> results);

    protected void updatePager() {
        // TODO: Test if result.size == booksPerPage (meaning; last page, exact size)
        isLastPage = mAdapter.getItemCount() < booksPerPage;
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
    public void onContentReady(boolean success, List<Content> results) {
        if (success) {
            Timber.d("Content results have loaded : %s results", results.size());
            isLoaded = true;

            // Display new results
            displayResults(results);
        }
    }

    @Override
    public void onContentFailed(boolean failure) {
        if (failure) {
            Timber.d("Content results failed to load.");
            isLoaded = false;
        }
    }

    /*
    ItemSelectListener implementation
     */
    @Override
    public void onItemSelected(int selectedCount) {
        isSelected = true;
        showToolbar(false, true);

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
            showToolbar(true, false);

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
