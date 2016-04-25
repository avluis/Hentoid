package me.devsaki.hentoid.util;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;

import me.devsaki.hentoid.listener.ItemTouchListener;

/**
 * Created by avluis on 04/25/2016.
 * Overrides and implements ItemTouchHelper.Callback
 */
public class SimpleItemTouchHelper extends ItemTouchHelper.Callback {

    private final ItemTouchListener itemTouchListener;

    public SimpleItemTouchHelper(ItemTouchListener listener) {
        itemTouchListener = listener;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return true;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                          RecyclerView.ViewHolder target) {
        itemTouchListener.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        itemTouchListener.onItemDismiss(viewHolder.getAdapterPosition());
    }
}