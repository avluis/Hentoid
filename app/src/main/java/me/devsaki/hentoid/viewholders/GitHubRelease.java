package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Helper;

public class GitHubRelease extends AbstractFlexibleItem<GitHubRelease.ReleaseViewHolder> {

    private final String tagName;
    private final String name;
    private final String description;
    private final Date creationDate;

    public GitHubRelease(Struct releaseStruct) {
        tagName = releaseStruct.tagName.replace("v", "");
        name = releaseStruct.name;
        description = releaseStruct.body;
        creationDate = releaseStruct.creationDate;
    }

    public String getTagName() {
        return tagName;
    }

    public boolean isTagPrior(@Nonnull String tagName) {
        return getIntFromTagName(this.tagName) <= getIntFromTagName(tagName);
    }

    private static int getIntFromTagName(@Nonnull String tagName) {
        int result = 0;
        String[] parts = tagName.split("\\.");
        if (parts.length > 0) result = 10000 * Integer.parseInt(parts[0].replaceAll("[^\\d]", ""));
        if (parts.length > 1) result += 100 * Integer.parseInt(parts[1].replaceAll("[^\\d]", ""));
        if (parts.length > 2) result += Integer.parseInt(parts[2].replaceAll("[^\\d]", ""));

        return result;
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
        return tagName.hashCode();
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
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        holder.setTitle(name + " (" + dateFormat.format(creationDate) + ")");

        holder.clearContent();
        // Parse content and add lines to the description
        for (String s : description.split("\\r\\n")) {
            s = s.trim();
            if (s.startsWith("-")) holder.addListContent(s);
            else holder.addDescContent(s);
        }
    }

    public class ReleaseViewHolder extends FlexibleViewHolder {

        private final int DP_8;
        private final TextView title;
        private final LinearLayout description;

        ReleaseViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            title = view.findViewById(R.id.changelogReleaseTitle);
            description = view.findViewById(R.id.changelogReleaseDescription);

            DP_8 = Helper.dpToPixel(view.getContext(), 8);
        }

        public void setTitle(String title) {
            this.title.setText(title);
        }

        void clearContent() {
            description.removeAllViews();
        }

        void addDescContent(String text) {
            TextView tv = new TextView(description.getContext());
            tv.setText(text);
            tv.setPadding(0, DP_8, 0, 0);
            description.addView(tv);
        }

        void addListContent(String text) {
            TextView tv = new TextView(description.getContext());
            tv.setText(text);
            tv.setPadding(DP_8 * 2, DP_8, 0, 0);
            description.addView(tv);
        }
    }

    public class Struct {

        @SerializedName("tag_name")
        String tagName;

        @SerializedName("name")
        String name;

        @SerializedName("body")
        String body;

        @SerializedName("created_at")
        Date creationDate;
    }

}
