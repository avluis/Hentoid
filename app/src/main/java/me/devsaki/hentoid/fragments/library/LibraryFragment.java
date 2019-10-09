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
import androidx.paging.PagedList;
import androidx.recyclerview.widget.RecyclerView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.adapters.ContentAdapter2;
import me.devsaki.hentoid.adapters.PagedContentAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.widget.LibraryPager;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class LibraryFragment extends BaseFragment /*implements FlexibleAdapter.EndlessScrollListener*/ {

    private LibraryViewModel viewModel;
    private final PagedContentAdapter endlessAdapter = new PagedContentAdapter(this::onSourceClick);
    private final ContentAdapter2 pagerAdapter = new ContentAdapter2(this::onSourceClick);
    private PagedList<Content> library;

    // ======== UI
    private final LibraryPager pager = new LibraryPager(this::onPreviousClick, this::onNextClick, this::onPageChange);
    private RecyclerView recyclerView;


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

        viewModel.getLibraryPaged().observe(this, this::onPagedLibraryChanged);
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
        pager.initUI(rootView);

        recyclerView = requireViewById(rootView, R.id.library_list);

        initPagingMethod(Preferences.getEndlessScroll());
    }

    private void initPagingMethod(boolean isEndless) {
        if (isEndless) {
            pager.disable();
            recyclerView.setAdapter(endlessAdapter);
            if (library != null) endlessAdapter.submitList(library);
        } else {
            pager.enable();
            recyclerView.setAdapter(pagerAdapter);
            if (library != null) loadPagerAdapter(library);
        }
    }

    private void loadPagerAdapter(PagedList<Content> library) {
        int minIndex = (pager.getCurrentPageNumber() - 1) * Preferences.getContentPageQuantity();
        int maxIndex = Math.min(minIndex + Preferences.getContentPageQuantity(), library.size() - 1);
        pagerAdapter.setShelf(library.subList(minIndex, maxIndex));
        pagerAdapter.notifyDataSetChanged();
    }

    private void onPagedLibraryChanged(PagedList<Content> result) {
        Timber.d(">>Size=%s", result.size());
        updateTitle(result.size(), result.size()); // TODO total size = size of unfiltered content

        pager.setPageCount((int) Math.ceil(result.size() * 1.0 / Preferences.getContentPageQuantity()));

        if (Preferences.getEndlessScroll()) endlessAdapter.submitList(result);
        else loadPagerAdapter(result);

        library = result;
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
                title = res.getQuantityString(R.plurals.number_of_book_search_results, (int) totalSelectedCount, (int) totalSelectedCount, (int) totalSelectedCount);
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

    private void onPreviousClick(View v) {
        pager.previousPage();
        handleNewPage();
    }

    private void onNextClick(View v) {
        pager.nextPage();
        handleNewPage();
    }

    private void onPageChange(int page) {
        pager.setCurrentPage(page);
        handleNewPage();
    }

    private void handleNewPage() {
        int page = pager.getCurrentPageNumber();
        pager.setCurrentPage(page); // TODO - handle this transparently...
        loadPagerAdapter(library);
    }
}
