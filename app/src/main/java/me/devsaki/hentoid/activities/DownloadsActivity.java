package me.devsaki.hentoid.activities;

import android.app.ListFragment;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.view.MenuItemCompat;
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

    private static Menu searchMenu;
    private static SearchView searchView;
    private final Handler searchHandler = new Handler();
    private long backButtonPressed;

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
                if (s.equals("")) {
                    clearSearchQuery();
                } else {
                    submitSearchQuery(s, 1000);
                }

                return true;
            }
        });

        if (DownloadsFragment.order == 0) {
            menu.getItem(1).setVisible(false);
            menu.getItem(2).setVisible(true);
        } else {
            menu.getItem(1).setVisible(true);
            menu.getItem(2).setVisible(false);
        }

        return true;
    }

    // Clear search query onBackPressed
    // Double-Back (press back twice) to exit (after clearing searchView)
    @Override
    public void onBackPressed() {
        if (backButtonPressed + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            AndroidHelper.singleToast(
                    getApplicationContext(), getString(R.string.press_back_again),
                    Toast.LENGTH_SHORT);
        }
        backButtonPressed = System.currentTimeMillis();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_order_alphabetic:
                clearQuery();
                DownloadsFragment.order = ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC;
                invalidateOptionsMenu();

                return true;
            case R.id.action_order_by_date:
                clearQuery();
                DownloadsFragment.order = ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE;
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
        private static int order;
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
            // Save current sort order
            SharedPreferences.Editor editor = HentoidApplication.getAppPreferences().edit();
            editor.putInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS, order).apply();

            // Get & save current list position
            ListView list = getListView();
            index = list.getFirstVisiblePosition();
            View view = list.getChildAt(0);
            top = (view == null) ? 0 : (view.getTop() - list.getPaddingTop());

            super.onPause();
        }

        private void queryPrefs() {
            SharedPreferences preferences = HentoidApplication.getAppPreferences();
            String settingDir = preferences.getString(Constants.SETTINGS_FOLDER, "");

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
            }

            order = preferences.getInt(
                    ConstantsPreferences.PREF_ORDER_CONTENT_LISTS,
                    ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE);
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

        // TODO: Rewrite with non-blocking code
        private boolean searchContent() {
            List<Content> result = getDB()
                    .selectContentByQuery(query, currentPage, qtyPages,
                            order == ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC);

            if (query.isEmpty()) {
                getActivity().setTitle(R.string.title_activity_downloads);
            } else {
                getActivity().setTitle(getResources()
                        .getString(R.string.title_activity_search)
                        .replace("@search", query));
            }
            if (result != null && !result.isEmpty()) {
                contents = result;
            } else if (contents == null) {
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