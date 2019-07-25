package me.devsaki.hentoid.widget;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.IntConsumer;

public final class ScrollPositionListener extends RecyclerView.OnScrollListener {

    private final IntConsumer onPositionChangeListener;

    public ScrollPositionListener(IntConsumer onPositionChangeListener) {
        this.onPositionChangeListener = onPositionChangeListener;
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);

        LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();
        int firstVisibleItemPosition = llm.findFirstVisibleItemPosition();
        int lastCompletelyVisibleItemPosition = llm.findLastCompletelyVisibleItemPosition();
        onPositionChangeListener.accept(Math.max(firstVisibleItemPosition, lastCompletelyVisibleItemPosition));
    }
}
