package me.devsaki.hentoid.fragments;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageButton;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.ui.CarouselDecorator;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Created by avluis on 08/26/2016.
 * Presents the list of downloaded works to the user in a classic pager.
 */
public class PagerFragment extends DownloadsFragment {

    // Button containing the page number on Paged view
    private CarouselDecorator pager;


    @Override
    protected void initUI(View rootView) {
        super.initUI(rootView);

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

    private void attachPrevious(View rootView) {
        ImageButton btnPrevious = rootView.findViewById(R.id.btnPrevious);
        btnPrevious.setOnClickListener(v -> {
            if (currentPage > 1 && isLoaded) {
                currentPage--;
                update();
            } else if (booksPerPage > 0 && isLoaded) {
                Helper.toast(mContext, R.string.not_previous_page);
            } else {
                Timber.d("Not limit per page.");
            }
        });
    }

    private void attachNext(View rootView) {
        ImageButton btnNext = rootView.findViewById(R.id.btnNext);
        btnNext.setOnClickListener(v -> {
            if (booksPerPage <= 0) {
                Timber.d("Not limit per page.");
            } else {
                if (!isLastPage() && isLoaded) {
                    currentPage++;
                    update();
                } else if (isLastPage()) {
                    Helper.toast(mContext, R.string.not_next_page);
                }
            }
        });
    }

    private void attachPageSelector() {
        pager.setPageCount((int)Math.ceil(mAdapter.getTotalCount()*1.0/booksPerPage));
        pager.setCurrentPage(currentPage);
        pager.setOnPageChangeListener(this::onPageChange);
    }

    private void onPageChange(int page) {
        currentPage = page;
        update();
    }

    @Override
    protected void checkResults() {
        if (0 == mAdapter.getItemCount()) {
            if (!isLoaded) update();
            checkContent(true);
        } else {
            if (isLoaded) update();
            checkContent(false);
            mAdapter.setContentsWipedListener(this);
        }

        if (!query.isEmpty()) {
            Timber.d("Saved Query: %s", query);
            if (isLoaded) update();
        }
    }


    @Override
    protected void showToolbar(boolean show, boolean override) {
        this.override = override;

        if (show) {
            pagerToolbar.setVisibility(View.VISIBLE);
        } else {
            pagerToolbar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void displayResults(List<Content> results) {
        if (0 == results.size()) {
            Timber.d("Result: Nothing to match.");
            displayNoResults();
        } else {
            toggleUI(SHOW_DEFAULT);

            mAdapter.replaceAll(results);

            toggleUI(SHOW_RESULT);
        }
    }

    /**
     * Updates the page number on the bottom toolbar
     */
    @Override
    protected void setCurrentPage() {
        pager.setCurrentPage(currentPage);
    }
}
