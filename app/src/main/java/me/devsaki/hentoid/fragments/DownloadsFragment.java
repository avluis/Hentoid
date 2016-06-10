package me.devsaki.hentoid.fragments;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
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

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.activities.IntroActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.adapters.ContentAdapter.ContentsWipedListener;
import me.devsaki.hentoid.adapters.ContentAdapter.EndlessScrollListener;
import me.devsaki.hentoid.database.SearchContent;
import me.devsaki.hentoid.database.SearchContent.ContentListener;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.listener.DownloadEvent;
import me.devsaki.hentoid.listener.ItemClickListener.ItemSelectListener;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.ConstsPrefs;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of downloaded works to the user.
 * <p/>
 * TODO: Allow user to (instantly) scroll to top when on last item/page (endless scroll)
 * TODO: Consider showing load progress after last item (endless scroll)
 */
public class DownloadsFragment extends BaseFragment implements ContentListener,
        ContentsWipedListener, DrawerListener, EndlessScrollListener, ItemSelectListener {
    private static final String TAG = LogHelper.makeLogTag(DownloadsFragment.class);

    private static final int SHOW_LOADING = 1;
    private static final int SHOW_BLANK = 2;
    private static final int SHOW_RESULT = 3;
    private static final String LIST_STATE_KEY = "list_state";
    private static String query = "";
    private final Handler searchHandler = new Handler();
    private int currentPage = 1;
    private int qtyPages;
    private ActionMode mActionMode;
    private SharedPreferences prefs;
    private String settingDir;
    private int order;
    private boolean orderUpdated;
    private boolean endlessScroll;
    private TextView loadingText;
    private TextView emptyText;
    private Toolbar toolbar;
    private LinearLayout toolTip;
    private SwipeRefreshLayout refreshLayout;
    private boolean newContent;
    private boolean override;
    private Button btnPage;
    private RecyclerView mListView;
    private ContentAdapter mAdapter;
    private List<Content> contents;
    private List<Content> result = new ArrayList<>();
    private Context mContext;
    private MenuItem searchMenu;
    private SearchView searchView;
    private long backButtonPressed;
    private DrawerLayout mDrawerLayout;
    private int mDrawerState;
    private boolean shouldHide;
    private SearchContent search;
    private LinearLayoutManager llm;
    private Parcelable mListState;
    private boolean permissionChecked;
    private boolean isLastPage;
    private boolean isLoaded;
    private boolean isSelected;
    private boolean selectTrigger = false;
    // Called when the action mode is created; startActionMode() was called
    private final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.menu_context_menu, menu);

            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            menu.findItem(R.id.action_delete).setVisible(!selectTrigger);
            menu.findItem(R.id.action_delete_sweep).setVisible(selectTrigger);

            return true;
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_delete:
                    mAdapter.purgeSelectedItems();
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

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            clearSelection();
            mActionMode = null;
        }
    };
    private ObjectAnimator animator;

    public static DownloadsFragment newInstance() {
        return new DownloadsFragment();
    }

    // Validate permissions
    private void checkPermissions() {
        if (Helper.permissionsCheck(getActivity(), ConstsImport.RQST_STORAGE_PERMISSION)) {
            queryPrefs();
        } else {
            LogHelper.d(TAG, "Storage permission denied!");
            if (permissionChecked) {
                reset();
            }
            permissionChecked = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                LogHelper.d(TAG, "Permissions granted.");
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                permissionChecked = true;
            }
        } else {
            // Permissions cannot be set, either via policy or forced by user.
            getActivity().finish();
        }
    }

    private void reset() {
        // We have asked for permissions, but still denied.
        Helper.toast(R.string.reset);
        Helper.commitFirstRun(true);
        Intent intent = new Intent(getActivity(), IntroActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        getActivity().finish();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_content_list, menu);

        // Associate searchable configuration with the SearchView
        final SearchManager searchManager = (SearchManager)
                mContext.getSystemService(Context.SEARCH_SERVICE);

        searchMenu = menu.findItem(R.id.action_search);
        MenuItemCompat.setOnActionExpandListener(searchMenu,
                new MenuItemCompat.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        toggleSortMenuItem(menu, false);

                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        toggleSortMenuItem(menu, true);

                        if (!query.equals("")) {
                            query = "";
                            submitSearchQuery(query, 300);
                        }

                        return true;
                    }
                });
        searchView = (SearchView) MenuItemCompat.getActionView(searchMenu);
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

                return true;
            }
        });
        SharedPreferences.Editor editor = HentoidApp.getSharedPrefs().edit();
        if (order == 0) {
            menu.findItem(R.id.action_order_alphabetic).setVisible(false);
            menu.findItem(R.id.action_order_by_date).setVisible(true);
        } else {
            menu.findItem(R.id.action_order_alphabetic).setVisible(true);
            menu.findItem(R.id.action_order_by_date).setVisible(false);
        }
        // Save current sort order
        editor.putInt(ConstsPrefs.PREF_ORDER_CONTENT_LISTS, order).apply();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(GravityCompat.START);
        shouldHide = (mDrawerState != DrawerLayout.STATE_DRAGGING &&
                mDrawerState != DrawerLayout.STATE_SETTLING && !drawerOpen);

        if (!shouldHide) {
            MenuItemCompat.collapseActionView(searchMenu);
            menu.findItem(R.id.action_search).setVisible(false);

            toggleSortMenuItem(menu, false);
        }
    }

    private void toggleSortMenuItem(Menu menu, boolean show) {
        if (order == 0) {
            menu.findItem(R.id.action_order_by_date).setVisible(show);
        } else {
            menu.findItem(R.id.action_order_alphabetic).setVisible(show);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_order_alphabetic:
                cleanResults();
                orderUpdated = true;
                order = ConstsPrefs.PREF_ORDER_CONTENT_ALPHABETIC;
                update();
                getActivity().invalidateOptionsMenu();

                return true;
            case R.id.action_order_by_date:
                cleanResults();
                orderUpdated = true;
                order = ConstsPrefs.PREF_ORDER_CONTENT_BY_DATE;
                update();
                getActivity().invalidateOptionsMenu();

                return true;
            default:

                return super.onOptionsItemSelected(item);
        }
    }

    private void cleanResults() {
        if (contents != null) {
            contents.clear();
            contents = null;
        }

        if (result != null) {
            result.clear();
            result = null;
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        currentPage = 1;
    }

    private void clearQuery(int option) {
        LogHelper.d(TAG, "Clearing query with option: " + option);
        if (option == 1) {
            searchView.clearFocus();
            searchView.setIconified(true);
        }
        setQuery(query = "");
        update();
    }

    private void submitSearchQuery(String s) {
        submitSearchQuery(s, 0);
    }

    private void submitSearchQuery(final String s, long delay) {
        query = s;
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                setQuery(s);
                cleanResults();
                update();
            }
        }, delay);
    }

    private void queryPrefs() {
        LogHelper.d(TAG, "Querying Prefs.");
        boolean shouldUpdate = false;

        if (settingDir.isEmpty()) {
            LogHelper.d(TAG, "Where are my files?!");
            Intent intent = new Intent(getActivity(), ImportActivity.class);
            startActivity(intent);
            getActivity().finish();
        }

        boolean endlessScroll = prefs.getBoolean(
                ConstsPrefs.PREF_ENDLESS_SCROLL, ConstsPrefs.PREF_ENDLESS_SCROLL_DEFAULT);

        if (this.endlessScroll != endlessScroll) {
            this.endlessScroll = endlessScroll;
            cleanResults();
            shouldUpdate = true;
        }
        LogHelper.d(TAG, "Endless Scrolling Enabled: " + this.endlessScroll);

        int qtyPages = Integer.parseInt(
                prefs.getString(
                        ConstsPrefs.PREF_QUANTITY_PER_PAGE_LISTS,
                        ConstsPrefs.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));

        if (this.endlessScroll) {
            qtyPages = ConstsPrefs.PREF_QUANTITY_PER_PAGE_DEFAULT;
        }

        if (this.qtyPages != qtyPages) {
            LogHelper.d(TAG, "qtyPages updated.");
            this.qtyPages = qtyPages;
            setQuery("");
            shouldUpdate = true;
        }

        int order = prefs.getInt(
                ConstsPrefs.PREF_ORDER_CONTENT_LISTS, ConstsPrefs.PREF_ORDER_CONTENT_ALPHABETIC);

        if (this.order != order) {
            LogHelper.d(TAG, "order updated.");
            orderUpdated = true;
            this.order = order;
        }

        if (shouldUpdate) {
            update();
        }
    }

    private void setQuery(String query) {
        DownloadsFragment.query = query;
        currentPage = 1;
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPermissions();

        if (mListState != null) {
            llm.onRestoreInstanceState(mListState);
        }

        checkResults();
    }

    @Override
    public void onPause() {
        super.onPause();

        clearSelection();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        Double percent = event.percent;
        if (percent >= 0) {
            LogHelper.d(TAG, "Download Progress: " + percent);
        } else if (isLoaded) {
            showReloadToolTip();
        }
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

        prefs = HentoidApp.getSharedPrefs();

        settingDir = prefs.getString(Consts.SETTINGS_FOLDER, "");

        order = prefs.getInt(
                ConstsPrefs.PREF_ORDER_CONTENT_LISTS, ConstsPrefs.PREF_ORDER_CONTENT_ALPHABETIC);

        qtyPages = Integer.parseInt(
                prefs.getString(
                        ConstsPrefs.PREF_QUANTITY_PER_PAGE_LISTS,
                        ConstsPrefs.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));

        endlessScroll = prefs.getBoolean(
                ConstsPrefs.PREF_ENDLESS_SCROLL, ConstsPrefs.PREF_ENDLESS_SCROLL_DEFAULT);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);

        mListView = (RecyclerView) rootView.findViewById(R.id.list);
        loadingText = (TextView) rootView.findViewById(R.id.loading);
        emptyText = (TextView) rootView.findViewById(R.id.empty);

        mListView.setHasFixedSize(true);
        llm = new LinearLayoutManager(mContext);
        mListView.setLayoutManager(llm);

        mAdapter = new ContentAdapter(mContext, result, this);
        mListView.setAdapter(mAdapter);

        if (mAdapter.getItemCount() == 0) {
            mListView.setVisibility(View.GONE);
            loadingText.setVisibility(View.VISIBLE);
        }

        mDrawerLayout = (DrawerLayout) getActivity().findViewById(R.id.drawer_layout);
        mDrawerLayout.addDrawerListener(this);

        btnPage = (Button) rootView.findViewById(R.id.btnPage);
        toolbar = (Toolbar) rootView.findViewById(R.id.downloads_toolbar);
        toolTip = (LinearLayout) rootView.findViewById(R.id.tooltip);
        refreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_container);

        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!override) {
                    // Show toolbar:
                    if (result != null && result.size() > 0) {
                        // At top of list
                        if (llm.findViewByPosition(llm.findFirstVisibleItemPosition())
                                .getTop() == 0 && llm.findFirstVisibleItemPosition() == 0) {
                            showToolbar(true, false);
                            if (newContent) {
                                toolTip.setVisibility(View.VISIBLE);
                            }
                        }

                        // Last item in list
                        if (llm.findLastVisibleItemPosition() == result.size() - 1) {
                            showToolbar(true, false);
                            if (newContent && !endlessScroll) {
                                toolTip.setVisibility(View.VISIBLE);
                            }
                        } else {
                            // When scrolling up
                            if (dy < -10) {
                                showToolbar(true, false);
                                if (newContent) {
                                    toolTip.setVisibility(View.VISIBLE);
                                }
                                // When scrolling down
                            } else if (dy > 100) {
                                showToolbar(false, false);
                                if (newContent) {
                                    toolTip.setVisibility(View.GONE);
                                }
                            }
                        }
                    }
                }
            }
        });

        ImageButton btnPrevious = (ImageButton) rootView.findViewById(R.id.btnPrevious);
        ImageButton btnNext = (ImageButton) rootView.findViewById(R.id.btnNext);
        ImageButton btnRefresh = (ImageButton) rootView.findViewById(R.id.btnRefresh);

        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage > 1 && isLoaded) {
                    currentPage--;
                    update();
                } else if (qtyPages > 0 && isLoaded) {
                    Helper.toast(mContext, R.string.not_previous_page);
                } else {
                    LogHelper.d(TAG, R.string.not_limit_per_page);
                }
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (qtyPages <= 0) {
                    LogHelper.d(TAG, R.string.not_limit_per_page);
                } else {
                    if (!isLastPage && isLoaded) {
                        currentPage++;
                        update();
                    } else if (isLastPage) {
                        Helper.toast(mContext, R.string.not_next_page);
                    }
                }
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLoaded) {
                    update();
                }
            }
        });

        btnRefresh.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!endlessScroll) {
                    if (currentPage != 1 && isLoaded) {
                        Helper.toast(mContext, R.string.moving_to_first_page);
                        clearQuery(1);

                        return true;
                    } else if (currentPage == 1 && isLoaded) {
                        Helper.toast(mContext, R.string.on_first_page);
                        update();

                        return true;
                    }
                }

                return false;
            }
        });

        toolTip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commitRefresh();
            }
        });

        refreshLayout.setEnabled(false);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                commitRefresh();
            }
        });

        return rootView;
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
        }

        if (!query.isEmpty()) {
            clearQuery(1);
        }

        return false;
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

    private void checkResults() {
        if (endlessScroll) {
            if (contents != null) {
                LogHelper.d(TAG, "Contents are not null.");
            } else if (isLoaded && result != null) {
                LogHelper.d(TAG, "Result is not null.");
                result.clear();
            } else {
                LogHelper.d(TAG, "Contents are null.");
            }
            mAdapter.setEndlessScrollListener(this);
        }
        if (result != null) {
            LogHelper.d(TAG, "Result is not null.");
            LogHelper.d(TAG, "Are results loaded? " + isLoaded);
            if (result.isEmpty() && !isLoaded) {
                LogHelper.d(TAG, "Result is empty!");
                update();
            }
            checkContent(false);
            mAdapter.setContentsWipedListener(this);
        } else {
            LogHelper.d(TAG, "Result is null.");

            update();
            checkContent(true);
        }
    }

    private void checkContent(boolean clear) {
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
        if (toolTip.getVisibility() == View.GONE) {
            toolTip.setVisibility(View.VISIBLE);
            refreshLayout.setEnabled(true);
            newContent = true;
        } else {
            LogHelper.d(TAG, "Tooltip visible.");
        }
    }

    private void commitRefresh() {
        toolTip.setVisibility(View.GONE);
        refreshLayout.setRefreshing(false);
        refreshLayout.setEnabled(false);
        newContent = false;
        mAdapter.updateContentList();
        cleanResults();
        update();
        resetCount();
    }

    private void resetCount() {
        LogHelper.d(TAG, "Download Count: " + HentoidApp.getDownloadCount());
        HentoidApp.setDownloadCount(0);
        NotificationManager manager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        manager.cancel(0);
    }

    private void update() {
        toggleUI(SHOW_LOADING);
        searchContent();
        setCurrentPage();
    }

    private void toggleUI(int mode) {
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

    private void searchContent() {
        isLoaded = false;
        search = new SearchContent(mContext, query, currentPage, qtyPages,
                order == ConstsPrefs.PREF_ORDER_CONTENT_BY_DATE);
        search.retrieveResults(this);
    }

    private void setCurrentPage() {
        btnPage.setText(String.valueOf(currentPage));
    }

    private void displayResults() {
        clearSelection();
        toggleUI(0);
        result = search.getContent();

        if (query.isEmpty()) {
            if (result != null && !result.isEmpty()) {
                if (endlessScroll) {
                    if (contents == null) {
                        contents = result;
                        mAdapter.setContentList(contents);
                        mListView.setAdapter(mAdapter);
                    } else {
                        int curSize = mAdapter.getItemCount();
                        contents.addAll(result);
                        mAdapter.notifyItemRangeInserted(curSize, contents.size() - 1);
                    }
                } else {
                    List<Content> singleResult = result;
                    mAdapter.setContentList(singleResult);
                    mListView.setAdapter(mAdapter);
                }
                toggleUI(SHOW_RESULT);
                updatePager();
            } else {
                LogHelper.d(TAG, "Result: Nothing to match.");
                displayNoResults();
            }
        } else {
            LogHelper.d(TAG, "Query: " + query);
            if (result != null && !result.isEmpty()) {
                LogHelper.d(TAG, "Result: Match.");

                List<Content> searchResults = result;
                mAdapter.setContentList(searchResults);
                mListView.setAdapter(mAdapter);

                toggleUI(SHOW_RESULT);
                showToolbar(true, true);
                updatePager();
            } else {
                LogHelper.d(TAG, "Result: Nothing to match.");
                displayNoResults();
            }
        }
    }

    private void updatePager() {
        // TODO: if result.size == qtyPages (meaning; last page, exact size)
        isLastPage = result.size() < qtyPages;
        LogHelper.d(TAG, "Results: " + result.size());
    }

    private void displayNoResults() {
        if (!query.equals("") && isLoaded) {
            emptyText.setText(R.string.search_entry_not_found);
            toggleUI(SHOW_BLANK);
        } else if (isLoaded) {
            emptyText.setText(R.string.downloads_empty);
            toggleUI(SHOW_BLANK);
        } else {
            LogHelper.w(TAG, "Why are we in here?");
        }
    }

    private void showToolbar(boolean show, boolean override) {
        this.override = override;

        if (override) {
            if (show) {
                toolbar.setVisibility(View.VISIBLE);
            } else {
                toolbar.setVisibility(View.GONE);
            }
        } else {
            if (endlessScroll) {
                toolbar.setVisibility(View.GONE);
            } else if (show) {
                toolbar.setVisibility(View.VISIBLE);
            } else {
                toolbar.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onContentReady(boolean success) {
        if (success) {
            LogHelper.d(TAG, "Content results have loaded.");
            isLoaded = true;
            displayResults();
        }
    }

    @Override
    public void onContentFailed(boolean failure) {
        if (failure) {
            // TODO: Log to Analytics
            LogHelper.d(TAG, "Content results failed to load.");
            isLoaded = false;
        }
    }

    @Override
    public void onItemSelected(int selectedCount, int position) {
        isSelected = true;
        showToolbar(false, true);

        if (selectedCount == 1) {
            mAdapter.notifyDataSetChanged();
        }

        if (selectedCount >= 2) {
            selectTrigger = true;
        }

        if (mActionMode == null) {
            mActionMode = toolbar.startActionMode(mActionModeCallback);
        }

        if (mActionMode != null) {
            mActionMode.invalidate();
            mActionMode.setTitle(
                    selectedCount + (selectedCount > 1 ? " items selected" : " item selected"));
        }
    }

    @Override
    public void onItemClear(int itemCount, int position) {
        if (mActionMode != null) {
            if (itemCount >= 1) {
                mActionMode.setTitle(
                        itemCount + (itemCount > 1 ? " items selected" : " item selected"));
            } else {
                selectTrigger = false;
                mActionMode.invalidate();
                mActionMode.setTitle("");
                mAdapter.notifyDataSetChanged();
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

    @Override
    public void onContentsWiped() {
        LogHelper.d(TAG, "All items cleared!");
        update();
    }

    @Override
    public void onLoadMore(int position) {
        if (endlessScroll && query.isEmpty()) {
            LogHelper.d(TAG, "Load more data now~");
            if (!isLastPage) {
                currentPage++;
                searchContent();
            }
        }

        LogHelper.d(TAG, "Endless Scrolling disabled.");
    }

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {
        // We don't care about this event.
    }

    @Override
    public void onDrawerOpened(View drawerView) {
        // We don't care about this event.
    }

    @Override
    public void onDrawerClosed(View drawerView) {
        // We don't care about this event.
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        mDrawerState = newState;
        getActivity().invalidateOptionsMenu();
    }
}