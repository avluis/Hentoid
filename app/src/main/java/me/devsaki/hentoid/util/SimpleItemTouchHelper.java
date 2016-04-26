package me.devsaki.hentoid.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.listener.ItemTouchListener;
import me.devsaki.hentoid.listener.ItemTouchViewListener;

/**
 * Created by avluis on 04/25/2016.
 * Overrides and implements ItemTouchHelper.Callback
 */
public class SimpleItemTouchHelper extends ItemTouchHelper.Callback {

    private final ItemTouchListener itemTouchListener;
    private Drawable background;
    private Drawable clearDrawable;
    private int clearDrawableMargin;

    public SimpleItemTouchHelper(Context context, ItemTouchListener listener) {
        itemTouchListener = listener;
        background = new ColorDrawable(ContextCompat.getColor(context, R.color.primary));
        clearDrawable = ContextCompat.getDrawable(context, R.drawable.ic_delete_forever);
        clearDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        clearDrawableMargin = (int) context.getResources().getDimension(R.dimen.ic_clear_margin);
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return true;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.START);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                          RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        itemTouchListener.onItemDismiss(viewHolder.getAdapterPosition());
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder instanceof ItemTouchViewListener) {
                ItemTouchViewListener itemView = (ItemTouchViewListener) viewHolder;
                itemView.onItemSelected();
            }
        }

        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof ItemTouchViewListener) {
            ItemTouchViewListener itemView = (ItemTouchViewListener) viewHolder;
            itemView.onItemClear();
        }

        super.clearView(recyclerView, viewHolder);
    }

    @Override
    public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;

        if (viewHolder.getAdapterPosition() == -1) {
            return;
        }

        background.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(),
                itemView.getBottom());

        int itemWeight = itemView.getBottom() - itemView.getTop();
        int intrinsicWidth = clearDrawable.getIntrinsicWidth();
        int intrinsicHeight = clearDrawable.getIntrinsicWidth();

        int drawLeft = itemView.getRight() - clearDrawableMargin - intrinsicWidth;
        int drawRight = itemView.getRight() - clearDrawableMargin;
        int drawTop = itemView.getTop() + (itemWeight - intrinsicHeight) / 2;
        int drawBottom = drawTop + intrinsicHeight;
        clearDrawable.setBounds(drawLeft, drawTop, drawRight, drawBottom);

        if (isCurrentlyActive && actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            background.draw(c);
            clearDrawable.draw(c);
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}