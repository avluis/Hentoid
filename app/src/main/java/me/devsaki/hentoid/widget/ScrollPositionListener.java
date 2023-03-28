package me.devsaki.hentoid.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.IntConsumer;

import me.devsaki.hentoid.util.Debouncer;

public final class ScrollPositionListener extends RecyclerView.OnScrollListener {

    private boolean isScrollEnabled = true;

    private int mInitialOffsetY = -1;
    private int mTotalScrolledX = 0;
    private int mTotalScrolledY = 0;

    // Out of bounds scrolling detection
    private boolean isSettlingX = false;
    private boolean isSettlingY = false;

    private int dragStartPositionX = -1;
    private int dragStartPositionY = -1;

    private Debouncer<Integer> deltaEventDebouncer;

    private final IntConsumer onPositionChangeListener;
    private Runnable onStartOutOfBoundScroll = null;
    private Runnable onEndOutOfBoundScroll = null;


    public ScrollPositionListener(IntConsumer onPositionChangeListener) {
        this.onPositionChangeListener = onPositionChangeListener;
    }

    public void setDeltaYListener(Context context, IntConsumer deltaYListener) {
        deltaEventDebouncer = new Debouncer<>(context, 75, i -> {
            deltaYListener.accept(i - mInitialOffsetY);
            mInitialOffsetY = -1;
        });
    }

    public void setOnStartOutOfBoundScrollListener(Runnable onStartOutOfBoundScrollListener) {
        this.onStartOutOfBoundScroll = onStartOutOfBoundScrollListener;
    }

    public void setOnEndOutOfBoundScrollListener(Runnable onEndOutOfBoundScrollListener) {
        this.onEndOutOfBoundScroll = onEndOutOfBoundScrollListener;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        if (mInitialOffsetY < 0) mInitialOffsetY = mTotalScrolledY;

        mTotalScrolledY += dy;
        mTotalScrolledX += dx;

        if (deltaEventDebouncer != null) deltaEventDebouncer.submit(mTotalScrolledY);

        LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (llm != null) {
            int firstVisibleItemPosition = llm.findFirstVisibleItemPosition();
            int lastCompletelyVisibleItemPosition = llm.findLastCompletelyVisibleItemPosition();
            onPositionChangeListener.accept(Math.max(firstVisibleItemPosition, lastCompletelyVisibleItemPosition));
        }
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
        if (!isScrollEnabled) {
            recyclerView.stopScroll();
            return;
        }

        LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (llm != null) {
            if (RecyclerView.SCROLL_STATE_DRAGGING == newState) {
                dragStartPositionX = recyclerView.computeHorizontalScrollOffset();
                dragStartPositionY = recyclerView.computeVerticalScrollOffset();
                isSettlingX = false;
                isSettlingY = false;
            } else if (RecyclerView.SCROLL_STATE_SETTLING == newState) {
                // If the settling position is different from the original position, ignore that scroll
                // (e.g. snapping back to the original position after a small scroll)
                if (recyclerView.computeHorizontalScrollOffset() != dragStartPositionX)
                    isSettlingX = true;
                if (recyclerView.computeVerticalScrollOffset() != dragStartPositionY)
                    isSettlingY = true;
            } else if (RecyclerView.SCROLL_STATE_IDLE == newState) {
                // Don't do anything if we're not on a boundary
                if (!(llm.findLastVisibleItemPosition() == llm.getItemCount() - 1 || 0 == llm.findFirstVisibleItemPosition()))
                    return;

                if (null == onEndOutOfBoundScroll || null == onStartOutOfBoundScroll) return;

                int scrollDirection = 0;
                if (llm instanceof PrefetchLinearLayoutManager)
                    scrollDirection = ((PrefetchLinearLayoutManager) llm).getRawDeltaPx();

                if (recyclerView.computeHorizontalScrollOffset() == dragStartPositionX && !isSettlingX && llm.canScrollHorizontally()) {
                    if ((!llm.getReverseLayout() && scrollDirection >= 0) || (llm.getReverseLayout() && scrollDirection < 0))
                        onEndOutOfBoundScroll.run();
                    else onStartOutOfBoundScroll.run();
                }
                if (recyclerView.computeVerticalScrollOffset() == dragStartPositionY && !isSettlingY && llm.canScrollVertically()) {
                    if ((!llm.getReverseLayout() && scrollDirection >= 0) || (llm.getReverseLayout() && scrollDirection < 0))
                        onEndOutOfBoundScroll.run();
                    else onStartOutOfBoundScroll.run();
                }
            }
        }
    }

    public void disableScroll() {
        isScrollEnabled = false;
    }

    public void enableScroll() {
        isScrollEnabled = true;
    }

    public int getTotalScrolledX() {
        return mTotalScrolledX;
    }

    public int getTotalScrolledY() {
        return mTotalScrolledY;
    }
}
