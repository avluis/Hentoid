package me.devsaki.hentoid.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.LayoutInflater;
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
 */
public class DownloadsFragment extends BaseFragment {
    private final static String TAG = LogHelper.makeLogTag(DownloadsFragment.class);

    private final static int STORAGE_PERMISSION_REQUEST = 1;
    private static String query = "";
    private static int currentPage = 1;
    private static int qtyPages;
    private static int index = -1;
    private static int top;
    private int prevPage;
    private TextView emptyText;
    private Button btnPage;
    private List<Content> contents;
    private ListView mListView;
    private static SharedPreferences prefs;
    private static String settingDir;
    private static int order;

    public void setQuery(String query) {
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

        prefs = HentoidApplication.getAppPreferences();
        settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
        order = prefs.getInt(ConstantsPreferences.PREF_ORDER_CONTENT_LISTS,
                ConstantsPreferences.PREF_ORDER_CONTENT_ALPHABETIC);
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
            // TODO: Subscribe to this or move out
            // orderUpdated = true;
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
        List<Content> result = BaseFragment.getDB()
                .selectContentByQuery(query, currentPage, qtyPages,
                        order == ConstantsPreferences.PREF_ORDER_CONTENT_BY_DATE);
        if (isAdded()) {
            if (result != null && !result.isEmpty()) {
                contents = result;
                LogHelper.d(TAG, "Content: Match.");
            } else {
                contents = new ArrayList<>(0);
                LogHelper.d(TAG, "Content: No match.");
                if (!query.equals("")) {
                    emptyText.setText(R.string.search_entry_not_found);
                } else {
                    emptyText.setText(R.string.downloads_empty);
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

        } else {
            contents = null;
            setQuery("");
        }

        return result != null && !result.isEmpty();
    }
}