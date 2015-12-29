package me.devsaki.hentoid;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.MenuItemCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SearchView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.components.HentoidActivity;
import me.devsaki.hentoid.components.HentoidFragment;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.ConstantsPreferences;

public class DownloadsActivity extends HentoidActivity<DownloadsActivity.DownloadsFragment> {

    private static final String TAG = DownloadsActivity.class.getName();

    @Override
    protected DownloadsFragment buildFragment() {
        return new DownloadsFragment();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_content_list, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) MenuItemCompat
                .getActionView(menu.findItem(R.id.action_search));
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(true);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
                getFragment().setQuery(s.trim());
                getFragment().searchContent();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                getFragment().setQuery(s.trim());
                getFragment().searchContent();
                return true;
            }
        });

        if (getFragment().order == 0) {
            menu.getItem(1).setVisible(false);
            menu.getItem(2).setVisible(true);
        } else {
            menu.getItem(1).setVisible(true);
            menu.getItem(2).setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_order_alphabetic) {
            getFragment().order = ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC;
            getFragment().searchContent();
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_order_by_date) {
            getFragment().order = ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE;
            getFragment().searchContent();
            invalidateOptionsMenu();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class DownloadsFragment extends HentoidFragment {
        private static String query = "";
        Toast mToast;
        private int currentPage = 1;
        private int prevPage = 0;
        private int qtyPages;
        private int order;
        private Button btnPage;
        private List<Content> contents;

        public void setQuery(String query) {
            DownloadsFragment.query = query;
            currentPage = 1;
        }

        @Override
        public void onResume() {
            super.onResume();
            searchContent();
        }

        @SuppressLint("ShowToast")
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);

            qtyPages = Integer.parseInt(getSharedPreferences()
                    .getString(ConstantsPreferences.PREF_QUANTITY_PER_PAGE_LISTS,
                            ConstantsPreferences.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));

            order = getSharedPreferences()
                    .getInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS,
                            ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE);

            // Initialize toast if needed
            if (mToast == null) {
                mToast = Toast.makeText(getActivity(), "", Toast.LENGTH_SHORT);
            }

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

            btnNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (qtyPages <= 0) {
                        mToast.setText(R.string.not_limit_per_page);
                        mToast.show();
                    } else {
                        currentPage++;
                        if (!searchContent()) {
                            btnPage.setText("" + --currentPage);
                            mToast.setText(R.string.not_next_page);
                            mToast.show();
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
                        mToast.setText(R.string.not_previous_page);
                        mToast.show();
                    } else {
                        mToast.setText(R.string.not_limit_per_page);
                        mToast.show();
                    }
                }
            });

            String settingDir = getSharedPreferences().getString(Constants.SETTINGS_FOLDER, "");

            if (settingDir.isEmpty()) {
                Intent intent = new Intent(getActivity(), SelectFolderActivity.class);
                startActivity(intent);
                getActivity().finish();
            } else searchContent();

            return rootView;
        }

        @Override
        public void onPause() {
            SharedPreferences.Editor editor = getSharedPreferences().edit();
            editor.putInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS, order).apply();
            super.onPause();
        }

        private boolean searchContent() {
            List<Content> result = getDB()
                    .selectContentByQuery(query, currentPage, qtyPages,
                            order == ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC);

            if (result != null && !result.isEmpty())
                contents = result;
            else if (contents == null)
                contents = new ArrayList<>(0);

            if (query.isEmpty()) {
                getActivity().setTitle(R.string.title_activity_downloads);
            } else {
                getActivity().setTitle(getResources()
                        .getString(R.string.title_activity_search)
                        .replace("@search", query));
            }

            if (contents == result || contents.isEmpty()) {
                ContentAdapter adapter = new ContentAdapter(getActivity(), contents);
                setListAdapter(adapter);
            }

            if (prevPage != currentPage) {
                btnPage.setText("" + currentPage);
            }
            prevPage = currentPage;

            return result != null && !result.isEmpty();
        }
    }
}
