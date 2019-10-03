package me.devsaki.hentoid.widget;

import android.view.View;
import android.widget.ImageButton;

import androidx.recyclerview.widget.RecyclerView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.ui.CarouselDecorator;

public class LibraryPager {

    private final View.OnClickListener onPreviousListener;
    private final View.OnClickListener onNextListener;
    private final CarouselDecorator.OnPageChangeListener onPageChangeListener;

    private View pagerPanel;
    private CarouselDecorator decorator;


    public LibraryPager(View.OnClickListener onPreviousListener, View.OnClickListener onNextListener, CarouselDecorator.OnPageChangeListener onPageChangeListener) {
        this.onPreviousListener = onPreviousListener;
        this.onNextListener = onNextListener;
        this.onPageChangeListener = onPageChangeListener;
    }

    public void initUI(View rootView) {
        pagerPanel = rootView.findViewById(R.id.library_pager_group);

        RecyclerView pageCarousel = rootView.findViewById(R.id.pager_pageCarousel);
        pageCarousel.setHasFixedSize(true);

        decorator = new CarouselDecorator(rootView.getContext(), R.layout.item_pagecarousel);
        decorator.decorate(pageCarousel);
        decorator.setOnPageChangeListener(onPageChangeListener);

        ImageButton btnPrevious = rootView.findViewById(R.id.pager_btnPrevious);
        btnPrevious.setOnClickListener(onPreviousListener);
        ImageButton btnNext = rootView.findViewById(R.id.pager_btnNext);
        btnNext.setOnClickListener(onNextListener);
    }

    public void setPageCount(int pageCount) {
        decorator.setPageCount(pageCount);
    }

    public void setCurrentPage(int page) {
        decorator.setCurrentPage(page);
    }

    public void enable() {
        pagerPanel.setVisibility(View.VISIBLE);
    }

    public void disable() {
        pagerPanel.setVisibility(View.GONE);
    }
}
