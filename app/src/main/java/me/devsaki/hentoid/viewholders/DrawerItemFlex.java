package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.utils.DrawableUtils;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.DrawerItem;

public class DrawerItemFlex extends AbstractFlexibleItem<DrawerItemFlex.DrawerItemViewHolder> {

    private final DrawerItem item;
    private boolean flag = false;

    public DrawerItemFlex(DrawerItem item) {
        this.item = item;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
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
        Context context = holder.itemView.getContext();

        holder.setContent(item, flag);
        Drawable drawable = DrawableUtils.getSelectableBackgroundCompat(
                ContextCompat.getColor(context, R.color.drawer_item_unselected_background),
                ContextCompat.getColor(context, R.color.drawer_item_selected_background),
                ContextCompat.getColor(context, R.color.ripple));
        DrawableUtils.setBackgroundCompat(holder.itemView, drawable);
    }

    class DrawerItemViewHolder extends FlexibleViewHolder {

        private final TextView title;
        private final ImageView icon;

        DrawerItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            title = view.findViewById(R.id.drawer_item_txt);
            icon = view.findViewById(R.id.drawer_item_icon);
        }

        void setContent(DrawerItem item, boolean flag) {
            title.setText(String.format("%s%s", item.label, flag ? " *" : ""));
            icon.setImageResource(item.icon);
        }
    }
}
