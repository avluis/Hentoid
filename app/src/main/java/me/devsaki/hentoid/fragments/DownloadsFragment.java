package me.devsaki.hentoid.fragments;

import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.activities.IntroSlideActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.database.SearchContent;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.listener.ItemClickListener;
import me.devsaki.hentoid.listener.ItemLongClickListener;
import me.devsaki.hentoid.services.DownloadService;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.ConstantsImport;
import me.devsaki.hentoid.util.ConstantsPreferences;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of downloaded works to the user.
 * <p/>
 * TODO: Add additional UI elements to CardView.
 * TODO: Retain list when paused
 * {@link #searchContent()}
 */
public class DownloadsFragment extends BaseFragment implements DrawerLayout.DrawerListener,
        ItemClickListener, ItemLongClickListener {
    private final static String TAG = LogHelper.makeLogTag(DownloadsFragment.class);

    private final static int REQUEST_STORAGE_PERMISSION = ConstantsImport.REQUEST_STORAGE_PERMISSION;
    private final static String LIST_STATE_KEY = "list_state";
    private static String query = "";
    private static int currentPage = 1;
    private static int qtyPages;
    private static SharedPreferences prefs;
    private static String settingDir;
    private static int order;
    private static boolean orderUpdated;
    private final Handler searchHandler = new Handler();
    private TextView emptyText;
    private Button btnPage;
    private RecyclerView mListView;
    private ContentAdapter mAdapter;
    private List<Content> result;
    private Context mContext;
    private MenuItem searchMenu;
    private SearchView searchView;
    private long backButtonPressed;
    private DrawerLayout mDrawerLayout;
    private int mDrawerState;
    private boolean shouldHide;
    private SearchContent search;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                double percent = bundle.getDouble(DownloadService.INTENT_PERCENT_BROADCAST);
                if (percent >= 0) {
                    LogHelper.d(TAG, "Download Progress: " + percent);
                } else {
                    update();
                }
            }
        }
    };
    private LinearLayoutManager mLayoutManager;
    private Parcelable mListState;
    private boolean permissionChecked;
    private boolean isLastPage;

    public static DownloadsFragment newInstance() {
        return new DownloadsFragment();
    }

    // Validate permissions
    private void checkPermissions() {
        if (AndroidHelper.permissionsCheck(getActivity(),
                REQUEST_STORAGE_PERMISSION)) {
            LogHelper.d(TAG, "Storage permission allowed!");
            queryPrefs();
            update();
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
        AndroidHelper.toast(R.string.reset);
        AndroidHelper.commitFirstRun(true);
        Intent intent = new Intent(getActivity(), IntroSlideActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        getActivity().finish();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(GravityCompat.START);
        shouldHide = (mDrawerState != DrawerLayout.STATE_DRAGGING &&
                mDrawerState != DrawerLayout.STATE_SETTLING && !drawerOpen);

        if (!shouldHide) {
            MenuItemCompat.collapseActionView(searchMenu);
            menu.findItem(R.id.action_search).setVisible(false);

            if (order == 0) {
                menu.findItem(R.id.action_order_by_date).setVisible(false);
            } else {
                menu.findItem(R.id.action_order_alphabetic).setVisible(false);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_content_list, menu);

        // Associate searchable configuration with the SearchView
        final SearchManager searchManager = (SearchManager)
                mContext.getSystemService(Context.SEARCH_SERVICE);

        searchMenu = menu.findItem(R.id.action_search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchMenu);
        MenuItemCompat.setOnActionExpandListener(this.searchMenu,
                new MenuItemCompat.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        if (order == 0) {
                            menu.findItem(R.id.action_order_by_date).setVisible(false);
                        } else {
                            menu.findItem(R.id.action_order_alphabetic).setVisible(false);
                        }

                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        if (order == 0) {
                            menu.findItem(R.id.action_order_by_date).setVisible(true);
                        } else {
                            menu.findItem(R.id.action_order_alphabetic).setVisible(true);
                        }

                        if (!query.equals("")) {
                            query = "";
                            submitSearchQuery(query, 300);
                        }

                        return true;
                    }
                });
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

                if (shouldHide && (orderUpdated)) {
                    clearQuery(0);
                    orderUpdated = false;
                }

                if (!shouldHide && (!s.isEmpty())) {
                    clearQuery(1);
                }

                return true;
            }
        });
        SharedPreferences.Editor editor = HentoidApplication.getAppPreferences().edit();
        if (order == 0) {
            menu.findItem(R.id.action_order_alphabetic).setVisible(false);
            menu.findItem(R.id.action_order_by_date).setVisible(true);

            // Save current sort order
            editor.putInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS, order).apply();
        } else {
            menu.findItem(R.id.action_order_alphabetic).setVisible(true);
            menu.findItem(R.id.action_order_by_date).setVisible(false);

            // Save current sort order
            editor.putInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS, order).apply();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_order_alphabetic:
                orderUpdated = true;
                order = ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC;
                getActivity().invalidateOptionsMenu();
                update();

                return true;
            case R.id.action_order_by_date:
                orderUpdated = true;
                order = ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE;
                getActivity().invalidateOptionsMenu();
                update();

                return true;
            default:

                return super.onOptionsItemSelected(item);
        }
    }

    private void clearQuery(int option) {
        if (option == 1) {
            searchView.clearFocus();
            searchView.setIconified(true);
        }
        query = "";
        setQuery(query);
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
                update();
            }
        }, delay);
    }

    private void queryPrefs() {
        if (settingDir.isEmpty()) {
            Intent intent = new Intent(getActivity(), ImportActivity.class);
            startActivity(intent);
            getActivity().finish();
        }

        int newQtyPages = Integer.parseInt(prefs.getString(
                ConstantsPreferences.PREF_QUANTITY_PER_PAGE_LISTS,
                ConstantsPreferences.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));

        int trackOrder = prefs.getInt(
                ConstantsPreferences.PREF_ORDER_CONTENT_LISTS,
                ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC);

        if (qtyPages != newQtyPages) {
            setQuery("");
            qtyPages = newQtyPages;
        }

        if (order != trackOrder) {
            orderUpdated = true;
            order = trackOrder;
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

        getContext().registerReceiver(receiver, new IntentFilter(
                DownloadService.DOWNLOAD_NOTIFICATION));

        if (mListState != null) {
            mLayoutManager.onRestoreInstanceState(mListState);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        getContext().unregisterReceiver(receiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mListState = mLayoutManager.onSaveInstanceState();
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
        if (BuildConfig.DEBUG) {
            // StrictMode to assist with refactoring
            /** {@link StrictMode.ThreadPolicy},
             * {@link StrictMode.ThreadPolicy.Builder#detectDiskReads()},
             * {@link StrictMode.ThreadPolicy.Builder#detectDiskWrites()},
             * {@link StrictMode.ThreadPolicy.Builder#detectNetwork()}
             */
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    //        .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
            /** {@link StrictMode.VmPolicy},
             * {@link StrictMode.VmPolicy.Builder#detectLeakedSqlLiteObjects()},
             * {@link StrictMode.VmPolicy.Builder#detectLeakedClosableObjects()}
             */
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    //        .penaltyDeath()
                    .build());
        }
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);

        mContext = getContext();

        prefs = HentoidApplication.getAppPreferences();
        settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
        order = prefs.getInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS,
                ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);

        mListView = (RecyclerView) rootView.findViewById(R.id.list);
        emptyText = (TextView) rootView.findViewById(R.id.empty);

        mLayoutManager = new LinearLayoutManager(mContext);
        mListView.setLayoutManager(mLayoutManager);

        mAdapter = new ContentAdapter(mContext, result);
        mListView.setAdapter(mAdapter);

        this.mAdapter.setOnItemClickListener(this);
        this.mAdapter.setOnItemLongClickListener(this);

        mDrawerLayout = (DrawerLayout) getActivity().findViewById(R.id.drawer_layout);
        mDrawerLayout.addDrawerListener(this);

        btnPage = (Button) rootView.findViewById(R.id.btnPage);

        ImageButton btnPrevious = (ImageButton) rootView.findViewById(R.id.btnPrevious);
        ImageButton btnNext = (ImageButton) rootView.findViewById(R.id.btnNext);
        ImageButton btnRefresh = (ImageButton) rootView.findViewById(R.id.btnRefresh);

        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage > 1) {
                    currentPage--;
                    update();
                } else if (qtyPages > 0) {
                    AndroidHelper.toast(mContext, R.string.not_previous_page);
                } else {
                    AndroidHelper.toast(mContext, R.string.not_limit_per_page);
                }
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (qtyPages <= 0) {
                    AndroidHelper.toast(mContext, R.string.not_limit_per_page);
                } else {
                    if (isLastPage) {
                        AndroidHelper.toast(mContext, R.string.not_next_page);
                    } else {
                        currentPage++;
                        update();
                    }
                }
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                update();
            }
        });

        btnRefresh.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (currentPage != 1) {
                    setQuery("");
                    update();
                    AndroidHelper.toast(mContext, R.string.on_first_page);

                    return true;
                } else {
                    update();

                    return true;
                }
            }
        });

        return rootView;
    }

    @Override
    public boolean onBackPressed() {
        // If the drawer is open, back will close it
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
            return false;
        }
        if (backButtonPressed + 2000 > System.currentTimeMillis()) {
            AndroidHelper.toast(mContext, "Exiting RecyclerView");
            return true;
        } else {
            backButtonPressed = System.currentTimeMillis();
            AndroidHelper.toast(mContext, R.string.press_back_again);
        }
        clearQuery(1);
        return false;
    }

    public void update() {
        searchContent();
    }

    private void searchContent() {
        search = new SearchContent(mContext, this, query, currentPage, qtyPages,
                order == ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE);
    }

    private void displayResults() {
        List<Content> contents;
        result = search.getContent();

        if (result != null && !result.isEmpty()) {
            contents = result;
            emptyText.setVisibility(View.GONE);
            LogHelper.d(TAG, "Content: Match.");
        } else {
            contents = new ArrayList<>(0);
            LogHelper.d(TAG, "Content: No match.");
            if (!query.equals("")) {
                emptyText.setText(R.string.search_entry_not_found);
                emptyText.setVisibility(View.VISIBLE);
            } else {
                emptyText.setText(R.string.downloads_empty);
                emptyText.setVisibility(View.VISIBLE);
            }
        }

        if (contents == result || contents.isEmpty()) {
            mAdapter.setContentList(result);
            mListView.setAdapter(mAdapter);
            LogHelper.d(TAG, "Adapter set.");

            LogHelper.d(TAG, mAdapter.getItemCount());

            if (mAdapter.getItemCount() < qtyPages) {
                isLastPage = true;
                LogHelper.d(TAG, "On the last page.");
            } else {
                isLastPage = false;
                LogHelper.d(TAG, "Not on the last page.");
            }
        }

        btnPage.setText(String.valueOf(currentPage));
    }

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {
    }

    @Override
    public void onDrawerOpened(View drawerView) {
    }

    @Override
    public void onDrawerClosed(View drawerView) {
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        mDrawerState = newState;
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onContentReady(boolean ready) {
        LogHelper.d(TAG, "Content ready? " + ready);
        displayResults();
    }

    @Override
    public void onItemClick(View view, int position) {
        AndroidHelper.toast(mContext, result.get(position).getTitle() + " clicked.");

        AndroidHelper.openContent(result.get(position), mContext);
    }

    @Override
    public void onItemLongClick(View view, int position) {
        AndroidHelper.toast(mContext, result.get(position).getTitle() + " long clicked.");

        Intent intent = new Intent(mContext, result.get(position).getWebActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Constants.INTENT_URL, result.get(position).getGalleryUrl());
        mContext.startActivity(intent);
    }
}