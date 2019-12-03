package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.Site;

import static androidx.core.view.ViewCompat.requireViewById;

public class DrawerItem extends AbstractItem<DrawerItem.DrawerViewHolder> {

    // Label of the item
    private final String label;
    // Icon of the item
    private final int icon;
    // Flag to indicate new content
    private boolean flagNew = false;
    // Flag to indicate an alert
    private AlertStatus alertStatus = AlertStatus.NONE;

    // Activity class to launch when clicking on the item
    private final Class<? extends AppCompatActivity> activityClass;
    // Corresponding site, if any
    private Site site;

    public DrawerItem(String label, int icon, Class<? extends AppCompatActivity> activityClass) {
        this.label = label;
        this.icon = icon;
        this.activityClass = activityClass;
    }

    public DrawerItem(Site site) {
        this.label = site.getDescription().toUpperCase();
        this.icon = site.getIco();
        this.activityClass = Content.getWebActivityClass(site);
        this.site = site;
    }

    public void setFlagNew(boolean flagNew) {
        this.flagNew = flagNew;
    }

    public void setAlertStatus(AlertStatus status) {
        this.alertStatus = status;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    @Nullable
    public Site getSite() {
        return site;
    }

    public Class<? extends AppCompatActivity> getActivityClass() {
        return activityClass;
    }


    @NotNull
    @Override
    public DrawerItem.DrawerViewHolder getViewHolder(@NotNull View view) {
        return new DrawerViewHolder(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_drawer;
    }

    @Override
    public int getType() {
        return R.id.drawer;
    }


    static class DrawerViewHolder extends FastAdapter.ViewHolder<DrawerItem> {

        private final ImageView icon;
        private final ImageView alert;
        private final TextView title;

        DrawerViewHolder(View view) {
            super(view);
            icon = requireViewById(view, R.id.drawer_item_icon);
            alert = requireViewById(view, R.id.drawer_item_alert);
            title = requireViewById(view, R.id.drawer_item_txt);
        }


        @Override
        public void bindView(@NotNull DrawerItem item, @NotNull List<Object> list) {
            icon.setImageResource(item.icon);
            if (item.alertStatus != AlertStatus.NONE) {
                alert.setVisibility(View.VISIBLE);
                alert.setColorFilter(ContextCompat.getColor(alert.getContext(), item.alertStatus.getColor()), android.graphics.PorterDuff.Mode.SRC_IN);
            } else alert.setVisibility(View.GONE);
            title.setText(String.format("%s%s", item.label, item.flagNew ? " *" : ""));
        }

        @Override
        public void unbindView(@NotNull DrawerItem item) {

        }
    }
}
