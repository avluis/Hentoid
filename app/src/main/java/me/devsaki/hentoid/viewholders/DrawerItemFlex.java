package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Objects;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.Site;

import static androidx.core.view.ViewCompat.requireViewById;

public class DrawerItemFlex extends AbstractFlexibleItem<DrawerItemFlex.DrawerItemViewHolder> {

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


    public DrawerItemFlex(String label, int icon, Class<? extends AppCompatActivity> activityClass) {
        this.label = label;
        this.icon = icon;
        this.activityClass = activityClass;
    }

    public DrawerItemFlex(Site site) {
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
        holder.setContent(label, icon, flagNew, alertStatus);
    }

    class DrawerItemViewHolder extends FlexibleViewHolder {

        private final ImageView icon;
        private final ImageView alert;
        private final TextView title;

        DrawerItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            icon = requireViewById(view, R.id.drawer_item_icon);
            alert = requireViewById(view, R.id.drawer_item_alert);
            title = requireViewById(view, R.id.drawer_item_txt);
        }

        void setContent(String label, int iconRes, boolean flag, AlertStatus alertStatus) {
            icon.setImageResource(iconRes);
            if (alertStatus != AlertStatus.NONE) {
                alert.setVisibility(View.VISIBLE);
                alert.setColorFilter(ContextCompat.getColor(alert.getContext(), alertStatus.getColor()), android.graphics.PorterDuff.Mode.SRC_IN);
            } else alert.setVisibility(View.GONE);
            title.setText(String.format("%s%s", label, flag ? " *" : ""));
        }
    }
}
