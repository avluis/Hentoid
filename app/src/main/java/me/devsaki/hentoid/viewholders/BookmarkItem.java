package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.drag.IExtendedDraggable;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.ThemeHelper;

public class BookmarkItem extends AbstractItem<BookmarkItem.BookmarkViewHolder> implements IExtendedDraggable {

    private final Site site;
    private final ItemTouchHelper touchHelper;

    public BookmarkItem(Site site, ItemTouchHelper touchHelper) {
        this.site = site;
        this.touchHelper = touchHelper;
    }

    public BookmarkItem(Site site) {
        this.site = site;
        this.touchHelper = null;
    }

    public Site getSite() {
        return site;
    }


    @NotNull
    @Override
    public BookmarkItem.BookmarkViewHolder getViewHolder(@NotNull View view) {
        return new BookmarkViewHolder(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_bookmark;
    }

    @Override
    public int getType() {
        return R.id.bookmark;
    }

    @Override
    public boolean isDraggable() {
        return true;
    }

    @Nullable
    @Override
    public ItemTouchHelper getTouchHelper() {
        return touchHelper;
    }

    @Nullable
    @Override
    public View getDragView(@NotNull RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof BookmarkViewHolder)
            return ((BookmarkViewHolder) viewHolder).dragHandle;
        else return null;
    }


    static class BookmarkViewHolder extends FastAdapter.ViewHolder<BookmarkItem> implements IDraggableViewHolder {

        private final View rootView;
        private final ImageView dragHandle;
        private final TextView title;

        BookmarkViewHolder(View view) {
            super(view);
            rootView = view;
            dragHandle = view.findViewById(R.id.item_handle);
            title = view.findViewById(R.id.item_txt);
        }

        @Override
        public void bindView(@NotNull BookmarkItem item, @NotNull List<?> list) {
            DragDropUtil.bindDragHandle(this, item);
            title.setText(item.site.getDescription());
        }

        @Override
        public void unbindView(@NotNull BookmarkItem item) {
            // Nothing to do here
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
}
