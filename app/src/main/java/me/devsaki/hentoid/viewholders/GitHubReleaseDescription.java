package me.devsaki.hentoid.viewholders;

import androidx.annotation.IntDef;
import android.view.View;
import android.widget.TextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Helper;

public class GitHubReleaseDescription extends AbstractFlexibleItem<GitHubReleaseDescription.ReleaseDescriptionViewHolder> {

    @IntDef({Type.DESCRIPTION, Type.LIST_ITEM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
        int DESCRIPTION = 0;
        int LIST_ITEM = 1;
    }

    private final String text;
    private final @Type
    int type;

    public GitHubReleaseDescription(String text, @Type int type) {
        this.text = text;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GitHubReleaseDescription) {
            GitHubReleaseDescription inItem = (GitHubReleaseDescription) o;
            return this.text.equals(inItem.text);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_text;
    }

    @Override
    public ReleaseDescriptionViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ReleaseDescriptionViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, ReleaseDescriptionViewHolder holder, int position, List<Object> payloads) {
        if (type == Type.DESCRIPTION) holder.setDescContent(text);
        else if (type == Type.LIST_ITEM) holder.setListContent(text);
    }

    class ReleaseDescriptionViewHolder extends FlexibleViewHolder {

        private final int DP_8;
        private final TextView title;

        ReleaseDescriptionViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            title = view.findViewById(R.id.drawer_item_txt);
            DP_8 = Helper.dpToPixel(view.getContext(), 8);
        }

        void setDescContent(String text) {
            title.setText(text);
            title.setPadding(0, DP_8, 0, 0);
        }

        void setListContent(String text) {
            title.setText(text);
            title.setPadding(DP_8 * 2, DP_8, 0, 0);
        }
    }
}
