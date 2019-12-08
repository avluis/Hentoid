package me.devsaki.hentoid.widget;

import android.view.View;
import android.widget.ImageButton;

import androidx.recyclerview.widget.RecyclerView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.ui.CarouselDecorator;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Page navigation bar for the library screen's paged mode
 */
public class LibraryPager {

    // == COMMUNICATION
    // Callback for any page change
    private final Runnable onPageChangeListener;

    // == UI
    // Bottom panel with page controls
    private View pagerPanel;
    // Decorator for the page number carousel
    private CarouselDecorator decorator;

    // == VARIABLES
    private int currentPageNumber = 1;
    private int pageCount = 0;


    /**
     * Constructor for the pager
     *
     * @param onPageChangeListener callback to run when the current page is changed
     */
    public LibraryPager(Runnable onPageChangeListener) {
        this.onPageChangeListener = onPageChangeListener;
    }

    /**
     * Initialize the components of the pager UI
     *
     * @param rootView Root view of the library screen
     */
    public void initUI(View rootView) {
        pagerPanel = requireViewById(rootView, R.id.library_pager_group);

        RecyclerView pageCarousel = requireViewById(rootView, R.id.pager_pageCarousel);
        pageCarousel.setHasFixedSize(true);

        decorator = new CarouselDecorator(rootView.getContext(), R.layout.item_pagecarousel);
        decorator.decorate(pageCarousel);
        decorator.setOnPageChangeListener(this::pageChanged);

        ImageButton btnPrevious = requireViewById(rootView, R.id.pager_btnPrevious);
        btnPrevious.setOnClickListener(this::previousPage);
        ImageButton btnNext = requireViewById(rootView, R.id.pager_btnNext);
        btnNext.setOnClickListener(this::nextPage);
    }

    public void show() {
        pagerPanel.setVisibility(View.VISIBLE);
    }

    public void hide() {
        pagerPanel.setVisibility(View.GONE);
    }

    /**
     * Set the page count
     *
     * @param pageCount Page count (max page number) to be set
     */
    public void setPageCount(int pageCount) {
        decorator.setPageCount(pageCount);
        this.pageCount = pageCount;
    }

    /**
     * Set the current page number
     *
     * @param page Current page number to be set
     */
    public void setCurrentPage(int page) {
        decorator.setCurrentPage(page);
        currentPageNumber = page;
    }

    /**
     * Try to get to the next page if it exists
     */
    private void nextPage(View v) {
        if (currentPageNumber < pageCount) {
            currentPageNumber++;
            decorator.setCurrentPage(currentPageNumber);
            onPageChangeListener.run();
        }
    }

    /**
     * Try to get to the previous page if it exists
     */
    private void previousPage(View v) {
        if (currentPageNumber > 1) {
            currentPageNumber--;
            decorator.setCurrentPage(currentPageNumber);
            onPageChangeListener.run();
        }
    }

    /**
     * Callback for the page number carousel
     *
     * @param newPageNumber Selected page number
     */
    private void pageChanged(int newPageNumber) {
        setCurrentPage(newPageNumber);
        onPageChangeListener.run();
    }

    /**
     * Get the current page number
     *
     * @return Current page number
     */
    public int getCurrentPageNumber() {
        return currentPageNumber;
    }
}
