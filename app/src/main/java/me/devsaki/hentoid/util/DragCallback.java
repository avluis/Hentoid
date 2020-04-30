package me.devsaki.hentoid.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.Consumer;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;

public class DragCallback extends SimpleDragCallback {

    private final Consumer<RecyclerView.ViewHolder> onStartDrag;

    public DragCallback(int directions, ItemTouchCallback touchCallback, @NonNull Consumer<RecyclerView.ViewHolder> onStartDrag) {
        super(directions, touchCallback);
        this.onStartDrag = onStartDrag;
    }

    public DragCallback(int directions, @NonNull Consumer<RecyclerView.ViewHolder> onStartDrag) {
        super(directions);
        this.onStartDrag = onStartDrag;
    }

    @Override
    public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (ItemTouchHelper.ACTION_STATE_DRAG == actionState) onStartDrag.accept(viewHolder);
    }
}
