package me.devsaki.hentoid.listener;

/**
 * Created by avluis on 04/24/2016.
 * Item Touch Listener
 */
public interface ItemTouchListener {
    boolean onItemMove(int fromPosition, int toPosition);

    void onItemDismiss(int position);
}