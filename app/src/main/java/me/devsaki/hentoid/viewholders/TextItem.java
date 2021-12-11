package me.devsaki.hentoid.viewholders;

import static androidx.core.view.ViewCompat.requireViewById;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.Consumer;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.drag.IExtendedDraggable;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.listeners.TouchEventHook;
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.ThemeHelper;

public class TextItem<T> extends AbstractItem<TextItem.TextViewHolder<T>> implements IExtendedDraggable {

    private final String text;
    private final T tag;
    private final boolean centered;
    private final boolean draggable;
    private final boolean reformatCase;
    private final boolean isHighlighted;
    private final ItemTouchHelper touchHelper;


    public TextItem(String text, T tag, boolean centered) {
        this.text = text;
        this.tag = tag;
        this.centered = centered;
        this.draggable = false;
        this.touchHelper = null;
        this.reformatCase = true;
        this.isHighlighted = false;
    }

    public TextItem(String text, T tag, boolean draggable, boolean reformatCase, boolean isHighlighted, ItemTouchHelper touchHelper) {
        this.text = text;
        this.tag = tag;
        this.centered = false;
        this.draggable = draggable;
        this.touchHelper = touchHelper;
        this.reformatCase = reformatCase;
        this.isHighlighted = isHighlighted;
    }

    @Nullable
    @Override
    public T getTag() {
        return tag;
    }

    public String getText() {
        return text;
    }

    private String getDisplayText() {
        return reformatCase ? StringHelper.capitalizeString(text) : text;
    }

    @Override
    public boolean isDraggable() {
        return draggable;
    }

    @Nullable
    @Override
    public ItemTouchHelper getTouchHelper() {
        return touchHelper;
    }

    @NotNull
    @Override
    public TextItem.TextViewHolder<T> getViewHolder(@NotNull View view) {
        return new TextViewHolder<>(view);
    }

    @Nullable
    @Override
    public View getDragView(@NotNull RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof TextViewHolder)
            return ((TextViewHolder<?>) viewHolder).dragHandle;
        else return null;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_text;
    }

    @Override
    public int getType() {
        return R.id.text;
    }

    static class TextViewHolder<T> extends FastAdapter.ViewHolder<TextItem<T>> implements IDraggableViewHolder {

        private final View rootView;
        private final TextView title;
        private final ImageView checkedIndicator;
        private final ImageView dragHandle;

        TextViewHolder(View view) {
            super(view);
            rootView = view;
            checkedIndicator = view.findViewById(R.id.checked_indicator);
            dragHandle = view.findViewById(R.id.item_handle);
            title = requireViewById(view, R.id.item_txt);
            int color = ThemeHelper.getColor(view.getContext(), R.color.secondary_light);
            view.setBackground(FastAdapterUIUtils.getSelectablePressedBackground(view.getContext(), FastAdapterUIUtils.adjustAlpha(color, 100), 50, true));
        }


        @Override
        public void bindView(@NotNull TextItem<T> item, @NotNull List<?> list) {
            if (item.draggable) {
                dragHandle.setVisibility(View.VISIBLE);
                DragDropUtil.bindDragHandle(this, item);
            }

            if (item.isSelected()) checkedIndicator.setVisibility(View.VISIBLE);
            else checkedIndicator.setVisibility(View.GONE);

            title.setText(item.getDisplayText());
            if (item.centered) title.setGravity(Gravity.CENTER);

            if (item.isHighlighted) title.setTypeface(null, Typeface.BOLD);
            else title.setTypeface(null, Typeface.NORMAL);
        }

        @Override
        public void unbindView(@NotNull TextItem<T> item) {
            // No specific behaviour to implement
        }

        @Override
        public void onDragged() {
            rootView.setBackgroundColor(ThemeHelper.getColor(rootView.getContext(), R.color.white_opacity_25));
        }

        @Override
        public void onDropped() {
            rootView.setBackgroundColor(ThemeHelper.getColor(rootView.getContext(), R.color.transparent));
        }
    }

    public static class DragHandlerTouchEvent<T> extends TouchEventHook<TextItem<T>> {

        private final Consumer<Integer> action;

        public DragHandlerTouchEvent(@NonNull Consumer<Integer> action) {
            this.action = action;
        }

        @Nullable
        @Override
        public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
            return (viewHolder instanceof TextViewHolder<?>) ? ((TextViewHolder<?>) viewHolder).dragHandle : null;
        }

        @Override
        public boolean onTouch(@NonNull View view, @NonNull MotionEvent motionEvent, int position, @NonNull FastAdapter<TextItem<T>> fastAdapter, @NonNull TextItem<T> item) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                action.accept(position);
                return true;
            }
            return false;
        }
    }
}
