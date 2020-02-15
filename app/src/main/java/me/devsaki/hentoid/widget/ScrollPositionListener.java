package me.devsaki.hentoid.widget;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.IntConsumer;

import me.devsaki.hentoid.util.Preferences;

public final class ScrollPositionListener extends RecyclerView.OnScrollListener {

    private final IntConsumer onPositionChangeListener;

    public ScrollPositionListener(IntConsumer onPositionChangeListener) {
        this.onPositionChangeListener = onPositionChangeListener;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);

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
        if (!Preferences.isViewerSwipeToTurn()) {
            recyclerView.stopScroll();
        }
    }
}
