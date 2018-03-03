package me.devsaki.hentoid.abstracts;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.adapters.ContentAdapter.ContentsWipedListener;
import me.devsaki.hentoid.database.SearchContent;
import me.devsaki.hentoid.database.SearchContent.ContentListener;
import me.devsaki.hentoid.database.domains.Content;
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
 */
public abstract class DownloadsFragment extends BaseFragment implements ContentListener,
        ContentsWipedListener, ItemSelectListener {
    protected static final int SHOW_RESULT = 3;
    private static final int SHOW_LOADING = 1;
    private static final int SHOW_BLANK = 2;
    private static final String LIST_STATE_KEY = "list_state";

    protected static String query = "";
    private final Handler searchHandler = new Handler();
    protected Context mContext;
    protected int qtyPages;
    protected int currentPage = 1;
    protected ContentAdapter mAdapter;
    protected LinearLayoutManager llm;
    protected RecyclerView mListView;
    protected List<Content> contents;
    protected List<Content> result = new ArrayList<>();
    protected SearchContent search;
    protected LinearLayout toolTip;
    protected Toolbar toolbar;
    protected boolean newContent;
    protected boolean override;
    protected boolean isLastPage;
    protected boolean isLoaded;
    private ActionMode mActionMode;
    private int mDrawerState;
    private DrawerLayout mDrawerLayout;
    private boolean shouldHide;
    private MenuItem searchMenu;
    private Parcelable mListState;
    private Button btnPage;
    private SearchView searchView;
    private SwipeRefreshLayout refreshLayout;
    private boolean orderUpdated;
    private boolean isSelected;
    private boolean selectTrigger = false;
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
    private int order;
    private long backButtonPressed;
    private String settingDir;
    private TextView loadingText;
    private TextView emptyText;
    private boolean permissionChecked;
    private ObjectAnimator animator;

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

        int qtyPages = Preferences.getContentPageQuantity();

        if (this.qtyPages != qtyPages) {
            Timber.d("qtyPages updated.");
            this.qtyPages = qtyPages;
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
        qtyPages = Preferences.getContentPageQuantity();
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
        mListView = rootView.findViewById(R.id.list);
        loadingText = rootView.findViewById(R.id.loading);
        emptyText = rootView.findViewById(R.id.empty);

        mListView.setHasFixedSize(true);
        llm = new LinearLayoutManager(mContext);
        mListView.setLayoutManager(llm);

        mAdapter = new ContentAdapter(mContext, result, this);
        mListView.setAdapter(mAdapter);

        if (mAdapter.getItemCount() == 0) {
            mListView.setVisibility(View.GONE);
            loadingText.setVisibility(View.VISIBLE);
        }

        mDrawerLayout = getActivity().findViewById(R.id.drawer_layout);
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {

            @Override
            public void onDrawerStateChanged(int newState) {
                mDrawerState = newState;
                getActivity().invalidateOptionsMenu();
            }
        });

        btnPage = rootView.findViewById(R.id.btnPage);
        toolbar = rootView.findViewById(R.id.downloads_toolbar);
        toolTip = rootView.findViewById(R.id.tooltip);
        refreshLayout = rootView.findViewById(R.id.swipe_container);
    }

    protected abstract void attachScrollListener();

    private void attachOnClickListeners(View rootView) {
        attachPrevious(rootView);
        attachNext(rootView);
        attachRefresh(rootView);

        toolTip.setOnClickListener(v -> commitRefresh());

        refreshLayout.setEnabled(false);
        refreshLayout.setOnRefreshListener(this::commitRefresh);
    }

    private void attachPrevious(View rootView) {
        ImageButton btnPrevious = rootView.findViewById(R.id.btnPrevious);
        btnPrevious.setOnClickListener(v -> {
            if (currentPage > 1 && isLoaded) {
                currentPage--;
                update();
            } else if (qtyPages > 0 && isLoaded) {
                Helper.toast(mContext, R.string.not_previous_page);
            } else {
                Timber.d("Not limit per page.");
            }
        });
    }

    private void attachNext(View rootView) {
        ImageButton btnNext = rootView.findViewById(R.id.btnNext);
        btnNext.setOnClickListener(v -> {
            if (qtyPages <= 0) {
                Timber.d("Not limit per page.");
            } else {
                if (!isLastPage && isLoaded) {
                    currentPage++;
                    update();
                } else if (isLastPage) {
                    Helper.toast(mContext, R.string.not_next_page);
                }
            }
        });
    }

    protected abstract void attachRefresh(View rootView);

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

    protected void commitRefresh() {
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
        Timber.d("Download Count: %s", HentoidApp.getDownloadCount());
        HentoidApp.setDownloadCount(0);
        NotificationManager manager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
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
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                toggleSortMenuItem(menu, false);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                toggleSortMenuItem(menu, true);

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

                return true;
            }
        });
        if (order == Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC) {
            menu.findItem(R.id.action_order_alphabetic).setVisible(false);
            menu.findItem(R.id.action_order_by_date).setVisible(true);
        } else {
            menu.findItem(R.id.action_order_alphabetic).setVisible(true);
            menu.findItem(R.id.action_order_by_date).setVisible(false);
        }
        // Save current sort order
        Preferences.setContentSortOrder(order);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(GravityCompat.START);
        shouldHide = (mDrawerState != DrawerLayout.STATE_DRAGGING &&
                mDrawerState != DrawerLayout.STATE_SETTLING && !drawerOpen);

        if (!shouldHide) {
            searchMenu.collapseActionView();
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
                order = Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC;
                update();
                getActivity().invalidateOptionsMenu();

                return true;
            case R.id.action_order_by_date:
                cleanResults();
                orderUpdated = true;
                order = Preferences.Constant.PREF_ORDER_CONTENT_BY_DATE;
                update();
                getActivity().invalidateOptionsMenu();

                return true;
            default:

                return super.onOptionsItemSelected(item);
        }
    }

    private void submitSearchQuery(String s) {
        submitSearchQuery(s, 0);
    }

    private void submitSearchQuery(final String s, long delay) {
        query = s;
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(() -> {
            setQuery(s);
            cleanResults();
            update();
        }, delay);
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
        if (toolTip.getVisibility() == View.GONE) {
            toolTip.setVisibility(View.VISIBLE);
            refreshLayout.setEnabled(true);
            newContent = true;
        } else {
            Timber.d("Tooltip visible.");
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

    private void setQuery(String query) {
        DownloadsFragment.query = query;
        currentPage = 1;
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
        isLoaded = false;
        search = new SearchContent(mContext, query, currentPage, qtyPages,
                order == Preferences.Constant.PREF_ORDER_CONTENT_BY_DATE);
        search.retrieveResults(this);
    }

    protected abstract void showToolbar(boolean show, boolean override);

    @Override
    public void onContentReady(boolean success) {
        if (success) {
            Timber.d("Content results have loaded.");
            isLoaded = true;

            if (search.getContent() == null) {
                Timber.d("Result: Nothing to match.");
                displayNoResults();
            } else {
                displayResults();
            }
        }
    }

    protected abstract void displayResults();

    protected void updatePager() {
        // TODO: Test if result.size == qtyPages (meaning; last page, exact size)
        isLastPage = result.size() < qtyPages;
        Timber.d("Results: %s", result.size());
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

    @Override
    public void onContentFailed(boolean failure) {
        if (failure) {
            Timber.d("Content results failed to load.");
            isLoaded = false;
        }
    }

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
            mActionMode = toolbar.startActionMode(mActionModeCallback);
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
        Timber.d("All items cleared!");
        displayNoResults();
        clearSelection();
        cleanResults();
    }
}
