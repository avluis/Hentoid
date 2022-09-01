package me.devsaki.hentoid.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/*
    Courtesy of https://blog.usejournal.com/improve-recyclerview-performance-ede5cec6c5bf
 */
public class PrefetchLinearLayoutManager extends LinearLayoutManager {

    private int extraLayoutSpace = -1;
    private int rawDeltaPx = 0;

    public PrefetchLinearLayoutManager(Context context) {
        super(context);
    }

    public void setExtraLayoutSpace(int extraLayoutSpace) {
        this.extraLayoutSpace = extraLayoutSpace;
    }

    // {@code extraLayoutSpace[0]} should be used for the extra space at the top/left, and
    // {@code extraLayoutSpace[1]} should be used for the extra space at the bottom/right (depending on the orientation)
    @Override
    protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
        extraLayoutSpace[0] = this.extraLayoutSpace; // Used for RTL
        extraLayoutSpace[1] = this.extraLayoutSpace; // Used for LTR & Top to bottom
    }

    /**
     * Return the "delta distance" used during the last scroll action
     *
     * @return "delta distance" used during the last scroll action
     */
    public int getRawDeltaPx() {
        return rawDeltaPx;
    }

    // Following overrides only used to capture rawDeltaPx

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (canScrollHorizontally()) rawDeltaPx = dx;
        return super.scrollHorizontallyBy(dx, recycler, state);
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (canScrollVertically()) rawDeltaPx = dy;
        return super.scrollVerticallyBy(dy, recycler, state);
    }
}
