package me.devsaki.hentoid.fragments.library;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.adapters.LibraryAdapter;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewholders.LibaryItemFlex;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.views.ProgressItem;
import me.devsaki.hentoid.widget.LibraryPager;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class LibraryFragment extends BaseFragment implements FlexibleAdapter.EndlessScrollListener {

    private LibraryViewModel viewModel;
    private LibraryAdapter adapter;

    // ======== UI
    private final ProgressItem progressItem = new ProgressItem();
    private final LibraryPager pager = new LibraryPager(this::onPreviousClick, this::onNextClick, this::onPageChange);


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;


    // === SEARCH
    // Last search parameters; used to determine whether or not page number should be reset to 1
    // NB : populated by getCurrentSearchParams
    private String lastSearchParams = "";

    // Settings
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = this::onSharedPreferenceChanged;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_library, container, false);

        viewModel = ViewModelProviders.of(requireActivity()).get(LibraryViewModel.class);
        Preferences.registerPrefsChangedListener(prefsListener);

        initUI(view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.getLibrary().observe(this, this::onLibraryChanged);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewModel != null) viewModel.onSaveState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (viewModel != null) viewModel.onRestoreState(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        /*
        updatePageDisplay();
        updateFavouriteDisplay();
         */
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener);
        super.onDestroy();
    }

    private void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Timber.i("Prefs change detected : %s", key);
        switch (key) {
            case Preferences.Key.PREF_ENDLESS_SCROLL:
                initPagingMethod(Preferences.getEndlessScroll());
                break;
            default:
                // Other changes aren't handled here
        }
    }

    private void initUI(View rootView) {
        adapter = new LibraryAdapter(null, this::onSourceClick);
        adapter.addListener((FlexibleAdapter.OnItemClickListener) this::onItemClick);

        pager.initUI(rootView);

        initPagingMethod(Preferences.getEndlessScroll());

        RecyclerView recyclerView = requireViewById(rootView, R.id.library_list);
        recyclerView.setAdapter(adapter);
    }

    private void initPagingMethod(boolean isEndless) {
        if (isEndless) {
            adapter.setEndlessScrollListener(this, progressItem);
            pager.disable();
        } else {
            adapter.setEndlessScrollListener(null, progressItem);
            pager.enable();
        }
    }

    private void onLibraryChanged(ObjectBoxDAO.ContentQueryResult result) {
        if (null == result) { // No library has been loaded yet (1st run with this instance)
            viewModel.load();
        } else {
            updateTitle(result.totalSelectedContent, result.totalContent);
            pager.setPageCount((int) Math.ceil(result.totalSelectedContent * 1.0 / Preferences.getContentPageQuantity()));
            pager.setCurrentPage(result.currentPage);
            List<IFlexible> items = new ArrayList<>();
            for (Content content : result.pagedContents) {
                LibaryItemFlex holder = new LibaryItemFlex(content);
                items.add(holder);
            }
            if (0 == adapter.getItemCount()) // 1st results load
                adapter.addItems(0, items);
            else if (Preferences.getEndlessScroll()) // load more (endless mode)
                adapter.onLoadMoreComplete(items);
            else // load page (pager mode)
            {
                adapter.clear();
                adapter.addItems(0, items);
            }
        }
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    private void updateTitle(long totalSelectedCount, long totalCount) {
        Activity activity = getActivity();
        if (activity != null) { // Has to be crash-proof; sometimes there's no activity there...
            String title;
            if (totalSelectedCount == totalCount)
                title = totalCount + " items";
            else {
                Resources res = getResources();
                title = res.getQuantityString(R.plurals.number_of_book_search_results, (int)totalSelectedCount, (int)totalSelectedCount, (int)totalSelectedCount);
            }
            activity.setTitle(title);
        }
    }


    private boolean onSourceClick(Content content) {
        ContentHelper.viewContent(requireContext(), content);
        return true;
    }

    private boolean onItemClick(View view, int position) {
        // Load item
        return true;
    }

    @Override
    public boolean onBackPressed() {
        if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
            return true;
        } else {
            backButtonPressed = SystemClock.elapsedRealtime();
            Context ctx = getContext();
            if (ctx != null) ToastUtil.toast(ctx, R.string.press_back_again);
        }
        return false;
    }

    @Override
    public void noMoreLoad(int newItemsSize) {

    }

    @Override
    public void onLoadMore(int lastPosition, int currentPage) {
        Timber.d("LoadMore %s %s", lastPosition, currentPage);
        viewModel.nextPage();
    }

    private void onPreviousClick(View v) {
        // TODO test at first page
        viewModel.previousPage();
    }

    private void onNextClick(View v) {
        // TODO test at last page
        viewModel.nextPage();
    }

    private void onPageChange(int page) {
        viewModel.loadPage(page);
    }
}
