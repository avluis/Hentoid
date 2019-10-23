package me.devsaki.hentoid.widget;

import android.view.View;
import android.widget.ImageButton;

import androidx.recyclerview.widget.RecyclerView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.ui.CarouselDecorator;

public class LibraryPager {

    private final Runnable onPageChangeListener;

    private View pagerPanel;
    private CarouselDecorator decorator;

    private int currentPageNumber = 1;
    private int pageCount = 0;


    public LibraryPager(Runnable onPageChangeListener) {
        this.onPageChangeListener = onPageChangeListener;
    }

    public void initUI(View rootView) {
        pagerPanel = rootView.findViewById(R.id.library_pager_group);

        RecyclerView pageCarousel = rootView.findViewById(R.id.pager_pageCarousel);
        pageCarousel.setHasFixedSize(true);

        decorator = new CarouselDecorator(rootView.getContext(), R.layout.item_pagecarousel);
        decorator.decorate(pageCarousel);
        decorator.setOnPageChangeListener(this::pageChanged);

        ImageButton btnPrevious = rootView.findViewById(R.id.pager_btnPrevious);
        btnPrevious.setOnClickListener(this::previousPage);
        ImageButton btnNext = rootView.findViewById(R.id.pager_btnNext);
        btnNext.setOnClickListener(this::nextPage);
    }

    public void enable() {
        pagerPanel.setVisibility(View.VISIBLE);
    }

    public void disable() {
        pagerPanel.setVisibility(View.GONE);
    }

    public void setPageCount(int pageCount) {
        decorator.setPageCount(pageCount);
        this.pageCount = pageCount;
    }

    public void setCurrentPage(int page) {
        decorator.setCurrentPage(page);
        currentPageNumber = page;
    }

    private void nextPage(View v) {
        if (currentPageNumber < pageCount) {
            currentPageNumber++;
            decorator.setCurrentPage(currentPageNumber);
            onPageChangeListener.run();
        }
    }

    private void previousPage(View v) {
        if (currentPageNumber > 1) {
            currentPageNumber--;
            decorator.setCurrentPage(currentPageNumber);
            onPageChangeListener.run();
        }
    }

    private void pageChanged(int newPageNumber) {
        setCurrentPage(newPageNumber);
        onPageChangeListener.run();
    }

    public int getCurrentPageNumber() {
        return currentPageNumber;
    }

    public int getPageCount() {
        return pageCount;
    }
}
