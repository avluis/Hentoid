package me.devsaki.hentoid.fragments.library;

import android.app.Activity;
import android.content.Context;
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
import java.util.Timer;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.adapters.LibraryAdapter;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewholders.LibaryItemFlex;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.views.ProgressItem;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class LibraryFragment extends BaseFragment implements FlexibleAdapter.EndlessScrollListener {

    private LibraryViewModel viewModel;
    private LibraryAdapter adapter;

    // ======== UI
    private final ProgressItem progressItem = new ProgressItem();


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;


    // === SEARCH
    protected ContentSearchManager searchManager;
    // Last search parameters; used to determine whether or not page number should be reset to 1
    // NB : populated by getCurrentSearchParams
    private String lastSearchParams = "";


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_library, container, false);

        CollectionAccessor collectionAccessor = new ObjectBoxCollectionAccessor(requireContext());
        searchManager = new ContentSearchManager(collectionAccessor);

        viewModel = ViewModelProviders.of(requireActivity()).get(LibraryViewModel.class);

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
        searchManager.saveToBundle(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            searchManager.loadFromBundle(savedInstanceState);
        }
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

        super.onDestroy();
    }


    private void initUI(View rootView) {
        adapter = new LibraryAdapter(null, this::onSourceClick);
        adapter.addListener((FlexibleAdapter.OnItemClickListener) this::onItemClick);
        if (Preferences.getEndlessScroll()) adapter.setEndlessScrollListener(this, progressItem);

        RecyclerView recyclerView = requireViewById(rootView, R.id.library_list);
        recyclerView.setAdapter(adapter);
    }

    private void onLibraryChanged(ObjectBoxCollectionAccessor.ContentQueryResult result) {
        if (null == result) { // No library has been loaded yet (1st run with this instance)
            Bundle searchParams = new Bundle();
            searchManager.saveToBundle(searchParams);
            viewModel.loadFromSearchParams(searchParams);
        } else {
            // TODO paging
            updateTitle(result.totalSelectedContent, result.totalContent);
            List<IFlexible> items = new ArrayList<>();
            for (Content content : result.pagedContents) {
                LibaryItemFlex holder = new LibaryItemFlex(content);
                items.add(holder);
            }
            if (0 == adapter.getItemCount()) // 1st results load
                adapter.addItems(0, items);
            else // load more (endless mode)
                adapter.onLoadMoreComplete(items);
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
            else title = String.format("%s/%s items", totalSelectedCount, totalCount);
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
        viewModel.loadMore();
    }
}
