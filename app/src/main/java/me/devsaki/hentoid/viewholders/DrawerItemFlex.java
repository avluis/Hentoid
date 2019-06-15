package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.DrawerItem;

public class DrawerItemFlex extends AbstractFlexibleItem<DrawerItemFlex.DrawerItemViewHolder> {

    private final DrawerItem item;

    public DrawerItemFlex(DrawerItem item) {
        this.item = item;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DrawerItemFlex) {
            DrawerItemFlex inItem = (DrawerItemFlex) o;
            return this.item.equals(inItem.item);
        }
        return false;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_drawer;
    }

    @Override
    public DrawerItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new DrawerItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, DrawerItemViewHolder holder, int position, List<Object> payloads) {
        holder.setContent(item);
    }

    class DrawerItemViewHolder extends FlexibleViewHolder {

        private final TextView title;
        private final ImageView icon;

        DrawerItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            title = view.findViewById(R.id.drawer_item_txt);
            icon = view.findViewById(R.id.drawer_item_icon);
        }

        void setContent(DrawerItem item) {
            title.setText(item.label);
            icon.setImageResource(item.icon);
        }
    }
}
