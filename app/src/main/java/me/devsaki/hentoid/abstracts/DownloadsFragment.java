package me.devsaki.hentoid.abstracts;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.NonNull;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.database.SearchContent;
import me.devsaki.hentoid.database.SearchContent.ContentListener;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.listener.ItemClickListener.ItemSelectListener;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.ConstsPrefs;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

import static me.devsaki.hentoid.util.Helper.DURATION.LONG;

/**
 * Created by avluis on 08/27/2016.
 * Common elements for use by EndlessFragment and PagerFragment
 */
public abstract class DownloadsFragment extends BaseFragment implements ContentListener,
        DrawerListener, ItemSelectListener {
    protected static final int SHOW_LOADING = 1;
    protected static final int SHOW_BLANK = 2;
    protected static final int SHOW_RESULT = 3;
    protected static final String LIST_STATE_KEY = "list_state";

    private static final String TAG = LogHelper.makeLogTag(DownloadsFragment.class);

    protected static String query = "";
    protected final Handler searchHandler = new Handler();
    protected Context mContext;
    protected SharedPreferences prefs;
    protected int order;
    protected boolean orderUpdated;
    protected int qtyPages;
    protected int currentPage = 1;
    protected ContentAdapter mAdapter;
    protected DrawerLayout mDrawerLayout;
    protected int mDrawerState;
    protected LinearLayoutManager llm;
    protected Parcelable mListState;
    protected RecyclerView mListView;
    protected Button btnPage;
    protected List<Content> contents;
    protected List<Content> result = new ArrayList<>();
    protected SearchContent search;
    protected SearchView searchView;
    protected LinearLayout toolTip;
    protected SwipeRefreshLayout refreshLayout;
    protected Toolbar toolbar;
    protected boolean newContent;
    protected boolean override;
    protected boolean isLastPage;
    protected boolean isLoaded;
    protected boolean isSelected;
    protected boolean selectTrigger = false;
    protected ActionMode mActionMode;
    // Called when the action mode is created; startActionMode() was called
    protected final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
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
            menu.findItem(R.id.action_share).setVisible(!selectTrigger);
            menu.findItem(R.id.action_delete_sweep).setVisible(selectTrigger);

            return true;
        }

        // Called when the user selects a contextual menu item
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
    private String settingDir;
    private TextView loadingText;
    private TextView emptyText;
    private boolean permissionChecked;
    private ObjectAnimator animator;

    @Override
    public boolean onBackPressed() {
        return false;
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

    protected abstract void attachScrollListener();

    private void attachOnClickListeners(View rootView) {
        attachPrevious(rootView);
        attachNext(rootView);
        attachRefresh(rootView);

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
    }

    private void attachPrevious(View rootView) {
        ImageButton btnPrevious = (ImageButton) rootView.findViewById(R.id.btnPrevious);
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
    }

    private void attachNext(View rootView) {
        ImageButton btnNext = (ImageButton) rootView.findViewById(R.id.btnNext);
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
    }

    protected abstract void attachRefresh(View rootView);

    protected void clearQuery(int option) {
        LogHelper.d(TAG, "Clearing query with option: " + option);
        if (option == 1) {
            if (searchView != null) {
                searchView.clearFocus();
                searchView.setIconified(true);
            }
        }
        setQuery(query = "");
        update();
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

    private void initUI(View rootView) {
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
    }

    @Override
    public void onDownloadEvent(DownloadEvent event) {

    }

    // Validate permissions
    protected void checkPermissions() {
        if (Helper.permissionsCheck(getActivity(), ConstsImport.RQST_STORAGE_PERMISSION, true)) {
            queryPrefs();
            checkResults();
        } else {
            LogHelper.d(TAG, "Storage permission denied!");
            if (permissionChecked) {
                reset();
            }
            permissionChecked = true;
        }
    }

    protected void reset() {
        Helper.reset(HentoidApp.getAppContext(), getActivity());
    }

    protected void queryPrefs() {
        LogHelper.d(TAG, "Querying Prefs.");
        boolean shouldUpdate = false;

        if (settingDir.isEmpty()) {
            LogHelper.d(TAG, "Where are my files?!");
            Intent intent = new Intent(getActivity(), ImportActivity.class);
            startActivity(intent);
            getActivity().finish();
        }

        String settingDir = prefs.getString(Consts.SETTINGS_FOLDER, "");

        if (!this.settingDir.equals(settingDir)) {
            LogHelper.d(TAG, "Library directory has changed!");
            this.settingDir = settingDir;
            cleanResults();
            shouldUpdate = true;
        }

        if (FileHelper.isSAF()) {
            File storage = new File(settingDir);
            if (FileHelper.getExtSdCardFolder(storage) == null) {
                LogHelper.d(TAG, "Where are my files?!");
                Helper.toast(getActivity(),
                        "Could not find library!\nPlease check your storage device.", LONG);
                setQuery("      ");

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        getActivity().finish();
                        Runtime.getRuntime().exit(0);
                    }
                }, 3000);
            }
        }

        int qtyPages = Integer.parseInt(
                prefs.getString(
                        ConstsPrefs.PREF_QUANTITY_PER_PAGE_LISTS,
                        ConstsPrefs.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));

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

    protected abstract void checkResults();

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
            LogHelper.d(TAG, "Tooltip visible.");
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

    @Override
    public void onResume() {
        super.onResume();

        checkPermissions();

        if (mListState != null) {
            llm.onRestoreInstanceState(mListState);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        clearSelection();
    }

    protected void clearSelection() {
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
                order == ConstsPrefs.PREF_ORDER_CONTENT_BY_DATE);
        search.retrieveResults(this);
    }

    protected abstract void showToolbar(boolean show, boolean override);

    @Override
    public void onContentReady(boolean success) {
        if (success) {
            LogHelper.d(TAG, "Content results have loaded.");
            isLoaded = true;
            displayResults();
        }
    }

    protected abstract void displayResults();

    protected void updatePager() {
        // TODO: Test if result.size == qtyPages (meaning; last page, exact size)
        isLastPage = result.size() < qtyPages;
        mAdapter.enableFooter(!isLastPage);
        LogHelper.d(TAG, "Results: " + result.size());
    }

    protected void displayNoResults() {
        if (isLoaded && !("").equals(query)) {
            emptyText.setText(R.string.search_entry_not_found);
            toggleUI(SHOW_BLANK);
        } else if (isLoaded) {
            emptyText.setText(R.string.downloads_empty);
            toggleUI(SHOW_BLANK);
        } else {
            LogHelper.w(TAG, "Why are we in here?");
        }
    }

    @Override
    public void onContentFailed(boolean failure) {
        if (failure) {
            LogHelper.d(TAG, "Content results failed to load.");
            isLoaded = false;
        }
    }

    @Override
    public void onItemSelected(int selectedCount) {

    }

    @Override
    public void onItemClear(int itemCount) {

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
//        mDrawerState = newState;
//        getActivity().invalidateOptionsMenu();
    }
}
