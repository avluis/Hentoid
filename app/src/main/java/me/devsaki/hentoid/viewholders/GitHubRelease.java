package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.model.GitHubReleases;
import me.devsaki.hentoid.util.Helper;

public class GitHubRelease extends AbstractFlexibleItem<GitHubRelease.ReleaseViewHolder> {

    private final String version;
    private final String name;
    private final String description;

    public GitHubRelease(GitHubReleases.GitHubRelease releaseStruct) {
        version = releaseStruct.getTagName();
        name = releaseStruct.getName();
        description = releaseStruct.getBody();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GitHubRelease) {
            GitHubRelease inItem = (GitHubRelease) o;
            return (this.hashCode() == inItem.hashCode());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_changelog;
    }

    @Override
    public ReleaseViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ReleaseViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, ReleaseViewHolder holder, int position, List<Object> payloads) {
        holder.setTitle(name);

        // Parse content and add lines to the description
        for (String s : description.split("\\r\\n")) {
            s = s.trim();
            if (s.startsWith("_")) holder.addCategoryContent(s);
            else if (s.startsWith("*")) holder.addListContent(s);
            else holder.addDescContent(s);
        }
    }

    public class ReleaseViewHolder extends FlexibleViewHolder {

        private final int DP_8;
        TextView title;
        private LinearLayout layout;

        ReleaseViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            title = view.findViewById(R.id.changelogReleaseTitle);
            layout = view.findViewById(R.id.changelogReleaseLayout);

            DP_8 = Helper.dpToPixel(layout.getContext(), 8);
        }

        public void setTitle(String title) {
            this.title.setText(title);
        }

        void addDescContent(String text) {
            TextView tv = new TextView(layout.getContext());
            tv.setText(text);
            tv.setPadding(DP_8, DP_8, 0, 0);
            layout.addView(tv);
        }

        void addCategoryContent(String text) {
            addDescContent(text);
        }

        void addListContent(String text) {
            TextView tv = new TextView(layout.getContext());
            tv.setText(text);
            tv.setPadding(DP_8 * 2, DP_8, 0, 0);
            layout.addView(tv);
        }
    }
}
