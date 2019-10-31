package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Objects;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

public class DrawerItemFlex extends AbstractFlexibleItem<DrawerItemFlex.DrawerItemViewHolder> {

    private final String label;
    private final int icon;
    private final Class<? extends AppCompatActivity> activityClass;

    private boolean flag = false;

    public DrawerItemFlex(String label, int icon, Class<? extends AppCompatActivity> activityClass) {
        this.label = label;
        this.icon = icon;
        this.activityClass = activityClass;
    }

    public DrawerItemFlex(Site site) {
        this.label = site.getDescription().toUpperCase();
        this.icon = site.getIco();
        this.activityClass = Content.getWebActivityClass(site);
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public Class<? extends AppCompatActivity> getActivityClass() {
        return activityClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DrawerItemFlex that = (DrawerItemFlex) o;
        return icon == that.icon &&
                Objects.equals(label, that.label) &&
                Objects.equals(activityClass, that.activityClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, icon, activityClass);
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
        holder.setContent(label, icon, flag);
    }

    class DrawerItemViewHolder extends FlexibleViewHolder {

        private final TextView title;
        private final ImageView icon;

        DrawerItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            title = view.findViewById(R.id.drawer_item_txt);
            icon = view.findViewById(R.id.drawer_item_icon);
        }

        void setContent(String label, int iconRes, boolean flag) {
            title.setText(String.format("%s%s", label, flag ? " *" : ""));
            icon.setImageResource(iconRes);
        }
    }
}
