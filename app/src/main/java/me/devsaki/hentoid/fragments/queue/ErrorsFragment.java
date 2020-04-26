package me.devsaki.hentoid.fragments.queue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.paged.PagedModelAdapter;
import com.pluscubed.recyclerfastscroll.RecyclerFastScroller;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewmodels.QueueViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 04/2020
 * Presents the list of downloads with errors
 */
public class ErrorsFragment extends Fragment {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // COMMUNICATION
    // Viewmodel
    private QueueViewModel viewModel;

    private TextView mEmptyText;    // "No errors" message panel

    // Used to keep scroll position when moving items
    // https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
    private int topItemPosition = -1;
//    private int offsetTop = 0;

    // Used to start processing when the recyclerView has finished updating
//    private final Debouncer<Integer> listRefreshDebouncer = new Debouncer<>(75, this::onRecyclerUpdated);


    /**
     * Diff calculation rules for list items
     * <p>
     * Created once and for all to be used by FastAdapter in endless mode (=using Android PagedList)
     */
    private final AsyncDifferConfig<Content> asyncDifferConfig = new AsyncDifferConfig.Builder<>(new DiffUtil.ItemCallback<Content>() {
        @Override
        public boolean areItemsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return oldItem.getUrl().equalsIgnoreCase(newItem.getUrl())
                    && oldItem.getSite().equals(newItem.getSite())
                    && oldItem.getLastReadDate() == newItem.getLastReadDate()
                    && oldItem.isBeingFavourited() == newItem.isBeingFavourited()
                    && oldItem.isBeingDeleted() == newItem.isBeingDeleted()
                    && oldItem.isFavourite() == newItem.isFavourite();
        }
    }).build();

    private final PagedModelAdapter<Content, ContentItem> itemAdapter = new PagedModelAdapter<>(asyncDifferConfig, i -> new ContentItem(ContentItem.ViewType.ERRORS), c -> new ContentItem(c, ContentItem.ViewType.ERRORS));


    @Override
    public void onDestroy() {
        compositeDisposable.clear();
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // UI ELEMENTS
        View rootView = inflater.inflate(R.layout.fragment_queue_errors, container, false);

        mEmptyText = requireViewById(rootView, R.id.errors_empty_txt);

        // Book list container
        RecyclerView recyclerView = requireViewById(rootView, R.id.queue_list);

        FastAdapter<ContentItem> fastAdapter = FastAdapter.with(itemAdapter);
        fastAdapter.setHasStableIds(true);
        ContentItem item = new ContentItem(ContentItem.ViewType.ERRORS);
        fastAdapter.registerItemFactory(item.getType(), item);
        recyclerView.setAdapter(fastAdapter);

//        LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();

        // Fast scroller
        RecyclerFastScroller fastScroller = requireViewById(rootView, R.id.queue_list_fastscroller);
        fastScroller.attachRecyclerView(recyclerView);

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onBookClick(i));

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(QueueViewModel.class);
        viewModel.getErrorsPaged().observe(getViewLifecycleOwner(), this::onErrorsChanged);
    }

    private void onErrorsChanged(PagedList<Content> result) {
        Timber.i(">>Errors changed ! Size=%s", result.size());

        // Update list visibility
        mEmptyText.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);

        // Update displayed books
        itemAdapter.submitList(result/*, this::differEndCallback*/);
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    /*
    private void differEndCallback() {
        if (topItemPosition >= 0) {
            int targetPos = topItemPosition;
            listRefreshDebouncer.submit(targetPos);
            topItemPosition = -1;
        }
    }

     */

    /**
     * Callback for the end of recycler updates
     * Activated when all _displayed_ items are placed on their definitive position
     */
        /*

    private void onRecyclerUpdated(int topItemPosition) {
        llm.scrollToPositionWithOffset(topItemPosition, offsetTop); // Used to restore position after activity has been stopped and recreated
    }

         */
    private boolean onBookClick(ContentItem i) {
        Content c = i.getContent();
        if (c != null) {
            ContentHelper.openHentoidViewer(requireContext(), c, null);
            return true;
        } else return false;
    }
}
