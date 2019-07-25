package me.devsaki.hentoid.fragments.downloads;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ImageButton;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.ui.CarouselDecorator;
import me.devsaki.hentoid.util.ToastUtil;
import timber.log.Timber;

/**
 * Created by avluis on 08/26/2016.
 * Presents the list of downloaded works to the user in a classic pager.
 */
public class PagerFragment extends DownloadsFragment {

    // Button containing the page number on Paged view
    private CarouselDecorator pager;


    @Override
    protected void initUI(View rootView, CollectionAccessor accessor) {
        super.initUI(rootView, accessor);

        RecyclerView pageCarousel = rootView.findViewById(R.id.pager);
        pageCarousel.setHasFixedSize(true);

        pager = new CarouselDecorator(mContext, R.layout.item_pagecarousel);
        pager.decorate(pageCarousel);
    }

    @Override
    protected void attachOnClickListeners(View rootView) {
        super.attachOnClickListeners(rootView);
        attachPrevious(rootView);
        attachNext(rootView);
        attachPageSelector();
    }

    @Override
    protected boolean forceSearchFromPageOne() {
        return false;
    }

    private void attachPrevious(View rootView) {
        ImageButton btnPrevious = rootView.findViewById(R.id.btnPrevious);
        btnPrevious.setOnClickListener(v -> {
            if (searchManager.getCurrentPage() > 1 && !isLoading) {
                searchManager.decreaseCurrentPage();
                pager.setCurrentPage(searchManager.getCurrentPage()); // Cleaner when displayed on bottom bar _before_ the update starts
                searchLibrary();
            } else if (booksPerPage > 0 && !isLoading) {
                ToastUtil.toast(mContext, R.string.not_previous_page);
            } else {
                Timber.d("No limit per page.");
            }
        });
    }

    private void attachNext(View rootView) {
        ImageButton btnNext = rootView.findViewById(R.id.btnNext);
        btnNext.setOnClickListener(v -> {
            if (booksPerPage <= 0) {
                Timber.d("No limit per page.");
            } else {
                if (!isLastPage() && !isLoading) {
                    searchManager.increaseCurrentPage();
                    pager.setCurrentPage(searchManager.getCurrentPage()); // Cleaner when displayed on bottom bar _before_ the update starts
                    searchLibrary();
                } else if (isLastPage()) {
                    ToastUtil.toast(mContext, R.string.not_next_page);
                }
            }
        });
    }

    private void attachPageSelector() {
        pager.setOnPageChangeListener(this::onPageChange);
    }

    private void onPageChange(int page) {
        if (page != searchManager.getCurrentPage()) {
            searchManager.setCurrentPage(page);
            searchLibrary();
        }
    }

    @Override
    protected void showToolbar(boolean show) {
        pagerToolbar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void displayResults(List<Content> results, long totalSelectedContent) {
        mAdapter.replaceAll(results);
        toggleUI(SHOW_RESULT);

        pager.setPageCount((int) Math.ceil(totalSelectedContent * 1.0 / booksPerPage));
        pager.setCurrentPage(searchManager.getCurrentPage());
        mListView.scrollToPosition(0);
    }
}
