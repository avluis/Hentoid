package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.CheckBox;
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

public class SiteItem extends AbstractItem<SiteItem.SiteViewHolder> implements IExtendedDraggable {

    private final Site site;
    private final boolean showHandle;
    private final ItemTouchHelper touchHelper;

    public SiteItem(Site site, boolean selected, ItemTouchHelper touchHelper) {
        this.site = site;
        this.setSelected(selected);
        this.showHandle = true;
        this.touchHelper = touchHelper;
    }

    public SiteItem(Site site, boolean selected, boolean showHandle) {
        this.site = site;
        this.showHandle = showHandle;
        this.setSelected(selected);
        this.touchHelper = null;
    }

    public Site getSite() {
        return site;
    }


    @NotNull
    @Override
    public SiteViewHolder getViewHolder(@NotNull View view) {
        return new SiteViewHolder(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_drawer_edit;
    }

    @Override
    public int getType() {
        return R.id.drawer_edit;
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
        if (viewHolder instanceof SiteViewHolder)
            return ((SiteViewHolder) viewHolder).dragHandle;
        else return null;
    }


    static class SiteViewHolder extends FastAdapter.ViewHolder<SiteItem> implements IDraggableViewHolder {

        private final View rootView;
        private final ImageView dragHandle;
        private final ImageView icon;
        private final TextView title;
        private final CheckBox chk;

        SiteViewHolder(View view) {
            super(view);
            rootView = view;
            dragHandle = view.findViewById(R.id.drawer_item_handle);
            icon = view.findViewById(R.id.drawer_item_icon);
            title = view.findViewById(R.id.drawer_item_txt);
            chk = view.findViewById(R.id.drawer_item_chk);
        }

        @Override
        public void bindView(@NotNull SiteItem item, @NotNull List<?> list) {
            dragHandle.setVisibility(item.showHandle ? View.VISIBLE : View.GONE);
            if (item.showHandle) DragDropUtil.bindDragHandle(this, item);
            title.setText(item.site.getDescription());
            icon.setImageResource(item.site.getIco());
            chk.setChecked(item.isSelected());
            chk.setOnCheckedChangeListener((v, b) -> item.setSelected(b));
        }

        @Override
        public void unbindView(@NotNull SiteItem item) {
            chk.setOnCheckedChangeListener(null);
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
