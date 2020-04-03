package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;

public class SiteItem extends AbstractItem<SiteItem.SiteViewHolder> {

    private final Site site;
    private boolean showHandle = true;

    public SiteItem(Site site) {
        this.site = site;
    }

    public SiteItem(Site site, boolean selected) {
        this.site = site;
        this.setSelected(selected);
    }

    public SiteItem(Site site, boolean selected, boolean showHandle) {
        this.site = site;
        this.showHandle = showHandle;
        this.setSelected(selected);
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


    static class SiteViewHolder extends FastAdapter.ViewHolder<SiteItem> {

        private final ImageView dragHandle;
        private final ImageView icon;
        private final TextView title;
        private final CheckBox chk;

        SiteViewHolder(View view) {
            super(view);
            dragHandle = view.findViewById(R.id.drawer_item_handle);

            icon = view.findViewById(R.id.drawer_item_icon);
            title = view.findViewById(R.id.drawer_item_txt);
            chk = view.findViewById(R.id.drawer_item_chk);
        }

        @Override
        public void bindView(@NotNull SiteItem item, @NotNull List<?> list) {
            dragHandle.setVisibility(item.showHandle ? View.VISIBLE : View.GONE);
            title.setText(item.site.getDescription());
            icon.setImageResource(item.site.getIco());
            chk.setChecked(item.isSelected());
            chk.setOnCheckedChangeListener((v, b) -> item.setSelected(b));
        }

        @Override
        public void unbindView(@NotNull SiteItem item) {
            // No specific behaviour to implement
        }
    }
}
