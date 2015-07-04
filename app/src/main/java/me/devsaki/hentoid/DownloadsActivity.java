package me.devsaki.hentoid;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_content_list, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView =
                (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(true);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
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
            SharedPreferences.Editor editor = getSharedPreferences().edit();
            editor.putInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS, ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC).apply();
            getFragment().searchContent();
            return true;
        } else if (id == R.id.action_order_by_date) {
            SharedPreferences.Editor editor = getSharedPreferences().edit();
            editor.putInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS, ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE).apply();
            getFragment().searchContent();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class DownloadsFragment extends HentoidFragment {
        private static String query = "";
        private int currentPage = 1;
        private List<Content> contents;

        public void setQuery(String query) {
            DownloadsFragment.query = query;
            currentPage = 1;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_downloads, container, false);
            btnPage = (Button) rootView.findViewById(R.id.btnPage);

            ImageButton btnRefresh = (ImageButton) rootView.findViewById(R.id.btnRefresh);
            btnRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchContent();
                }
            });
            ImageButton btnNext = (ImageButton) rootView.findViewById(R.id.btnNext);
            btnNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int qtyPages = Integer.parseInt(getSharedPreferences().getString(ConstantsPreferences.PREF_QUANTITY_PER_PAGE_LISTS, ConstantsPreferences.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));
                    if (qtyPages <= 0) {
                        Toast.makeText(getActivity(), R.string.not_limit_per_page, Toast.LENGTH_SHORT).show();
                    } else {
                        currentPage++;
                        searchContent();
                    }
                }
            });
            ImageButton btnPrevious = (ImageButton) rootView.findViewById(R.id.btnPrevious);
            btnPrevious.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int qtyPages = Integer.parseInt(getSharedPreferences().getString(ConstantsPreferences.PREF_QUANTITY_PER_PAGE_LISTS, ConstantsPreferences.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));
                    if (qtyPages <= 0) {
                        Toast.makeText(getActivity(), R.string.not_limit_per_page, Toast.LENGTH_SHORT).show();
                    } else {
                        if (currentPage > 1) {
                            currentPage--;
                            searchContent();
                        } else {
                            Toast.makeText(getActivity(), R.string.not_previous_page, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
            String settingDir = getSharedPreferences().getString(Constants.SETTINGS_FOLDER, "");
            boolean showMessageSupport = true;
            if (settingDir.isEmpty()) {
                Intent intent = new Intent(getActivity(), SelectFolderActivity.class);
                startActivity(intent);
                getActivity().finish();
            } else {
                searchContent();
            }
            try {
                PackageInfo pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                showMessageSupport = getSharedPreferences().getBoolean(ConstantsPreferences.SHOW_MESSAGE_SUPPORT + pInfo.versionCode, true);

            } catch (Exception ex) {
            }
            if (showMessageSupport) {
                Intent intent = new Intent(getActivity(), MessageSupportActivity.class);
                startActivity(intent);
            }
            return rootView;
        }

        private void searchContent() {
            int order = getSharedPreferences().getInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS, ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE);
            int qtyPages = Integer.parseInt(getSharedPreferences().getString(ConstantsPreferences.PREF_QUANTITY_PER_PAGE_LISTS, ConstantsPreferences.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));
            if (qtyPages < 0) {
                qtyPages = ConstantsPreferences.PREF_QUANTITY_PER_PAGE_DEFAULT;
            }
            contents = getDB().selectContentByQuery(query, currentPage, qtyPages, order == ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC);
            if (contents == null) {
                contents = new ArrayList<>();
            }
            if (query.isEmpty())
                getActivity().setTitle(R.string.title_activity_downloads);
            else
                getActivity().setTitle(getResources().getString(R.string.title_activity_search).replace("@search", query));

            ContentAdapter adapter = new ContentAdapter(getActivity(), contents);
            setListAdapter(adapter);


            btnPage.setText("" + currentPage);
        }

        private Button btnPage;
    }
}
