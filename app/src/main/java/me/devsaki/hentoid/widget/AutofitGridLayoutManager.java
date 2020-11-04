package me.devsaki.hentoid.widget;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// https://stackoverflow.com/a/30256880/8374722
public class AutofitGridLayoutManager extends GridLayoutManager {
    private int columnWidthPx;
    private boolean isColumnWidthChanged = true;
    private int lastWidth;
    private int lastHeight;

    public AutofitGridLayoutManager(@NonNull final Context context, final int columnWidthPx) {
        /* Initially set spanCount to 1, will be changed automatically later. */
        super(context, 1);
        setColumnWidth(checkedColumnWidth(context, columnWidthPx));
    }

    public AutofitGridLayoutManager(
            @NonNull final Context context,
            final int columnWidth,
            final int orientation,
            final boolean reverseLayout) {

        /* Initially set spanCount to 1, will be changed automatically later. */
        super(context, 1, orientation, reverseLayout);
        setColumnWidth(checkedColumnWidth(context, columnWidth));
    }

    private int checkedColumnWidth(@NonNull final Context context, final int columnWidthPx) {
        if (columnWidthPx <= 0) {
            /* Set default columnWidth value (48dp here). It is better to move this constant
            to static constant on top, but we need context to convert it to dp, so can't really
            do so. */
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48,
                    context.getResources().getDisplayMetrics());
        } else return columnWidthPx;
    }

    public void setColumnWidth(final int newColumnWidthPx) {
        if (newColumnWidthPx > 0 && newColumnWidthPx != columnWidthPx) {
            columnWidthPx = newColumnWidthPx;
            isColumnWidthChanged = true;
        }
    }

    @Override
    public void onLayoutChildren(@NonNull final RecyclerView.Recycler recycler, @NonNull final RecyclerView.State state) {
        final int widthPx = getWidth();
        final int heightPx = getHeight();
        if (columnWidthPx > 0 && widthPx > 0 && heightPx > 0 && (isColumnWidthChanged || lastWidth != widthPx || lastHeight != heightPx)) {
            final int totalSpacePx;
            if (getOrientation() == VERTICAL) {
                totalSpacePx = widthPx - getPaddingRight() - getPaddingLeft();
            } else {
                totalSpacePx = heightPx - getPaddingTop() - getPaddingBottom();
            }
            final int spanCount = Math.max(1, totalSpacePx / columnWidthPx);
            setSpanCount(spanCount);
            isColumnWidthChanged = false;
        }
        lastWidth = widthPx;
        lastHeight = heightPx;
        super.onLayoutChildren(recycler, state);
    }
}
