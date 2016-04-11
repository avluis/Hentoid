package me.devsaki.hentoid.fragments;

import android.Manifest;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.activities.IntroSlideActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.ConstantsPreferences;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/10/2016.
 * TODO: WIP
 * Presents the list of downloaded works to the user.
 */
public class DownloadsFragment extends BaseFragment {
    private final static String TAG = LogHelper.makeLogTag(DownloadsFragment.class);

    private final static int STORAGE_PERMISSION_REQUEST = 1;
    private static String query = "";
    private static int currentPage = 1;
    private static int qtyPages;
    private static int index = -1;
    private static int top;
    private static SharedPreferences prefs;
    private static String settingDir;
    private static int order;
    private static boolean orderUpdated;
    private final Handler searchHandler = new Handler();
    private int prevPage;
    private TextView emptyText;
    private Button btnPage;
    private ListView mListView;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private int mDrawerState;
    private MenuItem searchMenu;
    private SearchView searchView;
    private boolean shouldHide;

    private void setQuery(String query) {
        DownloadsFragment.query = query;
        currentPage = 1;
    }

    // Validate permissions
    private void checkPermissions() {
        if (AndroidHelper.permissionsCheck(getActivity(),
                STORAGE_PERMISSION_REQUEST)) {
            LogHelper.d(TAG, "Storage permission allowed!");
            queryPrefs();
            searchContent();
        } else {
            LogHelper.d(TAG, "Storage permission denied!");
            reset();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_order_alphabetic:
                orderUpdated = true;
                order = ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC;
                getActivity().invalidateOptionsMenu();

                return true;
            case R.id.action_order_by_date:
                orderUpdated = true;
                order = ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE;
                getActivity().invalidateOptionsMenu();

                return true;
            default:

                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                checkPermissions();
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    reset();
                } else {
                    getActivity().finish();
                }
            }
        }
    }

    // TODO: This could be relaxed - we could try another permission request
    private void reset() {
        AndroidHelper.commitFirstRun(true);
        Intent intent = new Intent(getActivity(), IntroSlideActivity.class);
        startActivity(intent);
        getActivity().finish();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
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
                getActivity().getSystemService(Context.SEARCH_SERVICE);

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

    private void clearQuery(int option) {
        if (option == 1) {
            searchView.clearFocus();
            searchView.setIconified(true);
        }
        query = "";
        setQuery(query);
        searchContent();
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
                searchContent();
            }
        }, delay);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermissions();

        // Retrieve list position
        // ListView list = getListView();
        mListView.setSelectionFromTop(index, top);
    }

    @Override
    public void onPause() {
        // Get & save current list position
        // ListView list = getListView();
        index = mListView.getFirstVisiblePosition();
        View view = mListView.getChildAt(0);
        top = (view == null) ? 0 : (view.getTop() - mListView.getPaddingTop());

        super.onPause();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        prefs = HentoidApplication.getAppPreferences();
        settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
        order = prefs.getInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS,
                ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC);

        mDrawerLayout = (DrawerLayout) getActivity().findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) getActivity().findViewById(R.id.drawer_list);

        mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
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
        });
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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);

        mListView = (ListView) rootView.findViewById(android.R.id.list);

        btnPage = (Button) rootView.findViewById(R.id.btnPage);
        emptyText = (TextView) rootView.findViewById(android.R.id.empty);
        ImageButton btnRefresh = (ImageButton) rootView.findViewById(R.id.btnRefresh);
        ImageButton btnNext = (ImageButton) rootView.findViewById(R.id.btnNext);
        ImageButton btnPrevious = (ImageButton) rootView.findViewById(R.id.btnPrevious);

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchContent();
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (qtyPages <= 0) {
//                        AndroidHelper.sSnack(container, R.string.not_limit_per_page,
//                                Snackbar.LENGTH_SHORT);
                } else {
                    currentPage++;
                    if (!searchContent()) {
                        btnPage.setText(String.valueOf(--currentPage));
//                            AndroidHelper.sSnack(container, R.string.not_next_page,
//                                    Snackbar.LENGTH_SHORT);
                        searchContent();
                    }
                }
            }
        });

        btnRefresh.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (currentPage != 1) {
                    setQuery("");
                    searchContent();
//                    AndroidHelper.sSnack(container, R.string.on_first_page,
//                            Snackbar.LENGTH_SHORT);

                    return true;
                } else {
                    searchContent();

                    return true;
                }
            }
        });

        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage > 1) {
                    currentPage--;
                    searchContent();
                } else if (qtyPages > 0) {
//                        AndroidHelper.sSnack(container, R.string.not_previous_page,
//                                Snackbar.LENGTH_SHORT);
                } else {
//                        AndroidHelper.sSnack(container, R.string.not_limit_per_page,
//                                Snackbar.LENGTH_SHORT);
                }
            }
        });

        return rootView;
    }

    // TODO: Rewrite with non-blocking code - AsyncTask could be a good replacement
    private boolean searchContent() {
        List<Content> contents;
        List<Content> result = BaseFragment.getDB()
                .selectContentByQuery(query, currentPage, qtyPages,
                        order == ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE);

        if (isAdded()) {
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
                ContentAdapter adapter = new ContentAdapter(getActivity(), contents);
                mListView.setAdapter(adapter);
            }
            if (prevPage != currentPage) {
                btnPage.setText(String.valueOf(currentPage));
            }
            prevPage = currentPage;

        }

        return result != null && !result.isEmpty();
    }
}