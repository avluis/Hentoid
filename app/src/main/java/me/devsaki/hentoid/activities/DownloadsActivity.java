package me.devsaki.hentoid.activities;

import android.app.ListFragment;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.view.GravityCompat;
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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.ConstantsPreferences;

/**
 * Presents the list of downloaded works to the user.
 */
public class DownloadsActivity extends BaseActivity<DownloadsActivity.DownloadsFragment> {
    private static final String TAG = DownloadsActivity.class.getName();
    static SharedPreferences preferences;
    static String settingDir;
    private static int order;
    private static boolean orderUpdated;
    private static Menu searchMenu;
    private static SearchView searchView;
    private static DrawerLayout mDrawerLayout;
    private final Handler searchHandler = new Handler();
    private long backButtonPressed;

    // DO NOT use this in onCreateOptionsMenu
    private static void clearQuery() {
        if (searchView.isShown()) {
            searchView.setIconified(true);
            searchMenu.findItem(R.id.action_search).collapseActionView();
        }
        searchView.setQuery("", false);
    }

    private void clearSearchQuery() {
        getFragment().setQuery("");
        getFragment().searchContent();
        searchView.clearFocus();
    }

    private void submitSearchQuery(String s) {
        submitSearchQuery(s, 0);
    }

    private void submitSearchQuery(final String s, long delay) {
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                getFragment().setQuery(s.trim());
                getFragment().searchContent();
            }
        }, delay);
    }

    @Override
    protected DownloadsFragment buildFragment() {
        return new DownloadsFragment();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = HentoidApplication.getAppPreferences();
        settingDir = preferences.getString(Constants.SETTINGS_FOLDER, "");

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            clearQuery();
            menu.findItem(R.id.action_search).setVisible(false);

            if (order == 0) {
                menu.findItem(R.id.action_order_by_date).setVisible(false);
            } else {
                menu.findItem(R.id.action_order_alphabetic).setVisible(false);
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_content_list, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        searchMenu = menu;
        searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
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
                if ((mDrawerLayout.isDrawerOpen(GravityCompat.START)) && (!s.isEmpty())) {
                    clearSearchQuery();
                } else if ((s.equals("")) && (orderUpdated)) {
                    clearSearchQuery();
                    orderUpdated = false;
                } else {
                    submitSearchQuery(s, 1000);
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
            orderUpdated = true;
        } else {
            menu.findItem(R.id.action_order_alphabetic).setVisible(true);
            menu.findItem(R.id.action_order_by_date).setVisible(false);

            // Save current sort order
            editor.putInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS, order).apply();
            orderUpdated = true;
        }

        return true;
    }

    // Close nav drawer if open
    // Clear search query onBackPressed
    // Double-Back (press back twice) to exit (after clearing searchView)
    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else if (backButtonPressed + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            AndroidHelper.singleToast(
                    getApplicationContext(), getString(R.string.press_back_again),
                    Toast.LENGTH_SHORT);
        }
        backButtonPressed = System.currentTimeMillis();
        clearSearchQuery();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_order_alphabetic:
                clearQuery();
                order = ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC;
                invalidateOptionsMenu();

                return true;
            case R.id.action_order_by_date:
                clearQuery();
                order = ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE;
                invalidateOptionsMenu();

                return true;
            default:

                return super.onOptionsItemSelected(item);
        }
    }

    public static class DownloadsFragment extends ListFragment {
        private static String query = "";
        private static int currentPage = 1;
        private static int qtyPages;
        private static int index = -1;
        private static int top;
        private int prevPage;
        private Button btnPage;
        private Context mContext;
        private List<Content> contents;

        public void setQuery(String query) {
            DownloadsFragment.query = query;
            currentPage = 1;
        }

        @Override
        public void onResume() {
            super.onResume();

            queryPrefs();
            searchContent();

            // Retrieve list position
            ListView list = getListView();
            list.setSelectionFromTop(index, top);
        }

        @Override
        public void onPause() {
            // Get & save current list position
            ListView list = getListView();
            index = list.getFirstVisiblePosition();
            View view = list.getChildAt(0);
            top = (view == null) ? 0 : (view.getTop() - list.getPaddingTop());

            super.onPause();
        }

        private void queryPrefs() {
            if (settingDir.isEmpty()) {
                Intent intent = new Intent(getActivity(), SelectFolderActivity.class);
                startActivity(intent);
                getActivity().finish();
            }

            int newQtyPages = Integer.parseInt(preferences.getString(
                    ConstantsPreferences.PREF_QUANTITY_PER_PAGE_LISTS,
                    ConstantsPreferences.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));

            if (qtyPages != newQtyPages) {
                setQuery("");
                qtyPages = newQtyPages;

                order = preferences.getInt(
                        ConstantsPreferences.PREF_ORDER_CONTENT_LISTS,
                        ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE);
            }
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);

            mContext = getActivity().getApplicationContext();

            btnPage = (Button) rootView.findViewById(R.id.btnPage);
            ImageButton btnRefresh = (ImageButton) rootView.findViewById(R.id.btnRefresh);
            ImageButton btnNext = (ImageButton) rootView.findViewById(R.id.btnNext);
            ImageButton btnPrevious = (ImageButton) rootView.findViewById(R.id.btnPrevious);

            btnRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchContent();
                }
            });

            btnRefresh.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (currentPage != 1) {
                        setQuery("");
                        searchContent();
                        AndroidHelper.singleToast(
                                mContext, getString(R.string.on_first_page), Toast.LENGTH_SHORT);

                        return true;
                    } else {
                        searchContent();

                        return true;
                    }
                }
            });

            btnNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (qtyPages <= 0) {
                        AndroidHelper.singleToast(
                                mContext, getString(R.string.not_limit_per_page),
                                Toast.LENGTH_SHORT);
                    } else {
                        currentPage++;
                        if (!searchContent()) {
                            btnPage.setText(String.valueOf(--currentPage));
                            AndroidHelper.singleToast(
                                    mContext, getString(R.string.not_next_page),
                                    Toast.LENGTH_SHORT);
                        }
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
                        AndroidHelper.singleToast(
                                mContext, getString(R.string.not_previous_page),
                                Toast.LENGTH_SHORT);
                    } else {
                        AndroidHelper.singleToast(
                                mContext, getString(R.string.not_limit_per_page),
                                Toast.LENGTH_SHORT);
                    }
                }
            });

            return rootView;
        }

        // TODO: Rewrite with non-blocking code - AsyncTask could be a good replacement
        private boolean searchContent() {
            List<Content> result = getDB()
                    .selectContentByQuery(query, currentPage, qtyPages,
                            order == ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC);

            if ((mDrawerLayout.isDrawerOpen(GravityCompat.START)) || (query.isEmpty())) {
                getActivity().setTitle(R.string.title_activity_downloads);
            } else {
                getActivity().setTitle(getResources()
                        .getString(R.string.title_activity_search)
                        .replace("@search", query));
            }
            if (result != null && !result.isEmpty()) {
                contents = result;
            } else if (contents == null) { // TODO: Possible entry point for no content match?
                contents = new ArrayList<>(0);
            }
            if (contents == result || contents.isEmpty()) {
                ContentAdapter adapter = new ContentAdapter(getActivity(), contents);
                setListAdapter(adapter);
            }
            if (prevPage != currentPage) {
                btnPage.setText(String.valueOf(currentPage));
            }
            prevPage = currentPage;

            return result != null && !result.isEmpty();
        }
    }
}