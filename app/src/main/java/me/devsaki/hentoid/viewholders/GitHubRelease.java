package me.devsaki.hentoid.viewholders;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.services.UpdateCheckService;
import me.devsaki.hentoid.services.UpdateDownloadService;
import me.devsaki.hentoid.util.Helper;

public class GitHubRelease extends AbstractFlexibleItem<GitHubRelease.ReleaseViewHolder> {

    private final String tagName;
    private final String name;
    private final String description;
    private final Date creationDate;
    private boolean latest;

    public GitHubRelease(Struct releaseStruct) {
        tagName = releaseStruct.tagName.replace("v", "");
        name = releaseStruct.name;
        description = releaseStruct.body;
        creationDate = releaseStruct.creationDate;
    }

    public void setLatest(boolean latest) {
        this.latest = latest;
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
        if (latest && !BuildConfig.DEBUG && !BuildConfig.VERSION_NAME.equals(tagName))
            holder.enableDownload();

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
        private final ImageView downloadButton;
        private final LinearLayout description;

        ReleaseViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            title = view.findViewById(R.id.changelogReleaseTitle);
            description = view.findViewById(R.id.changelogReleaseDescription);
            downloadButton = view.findViewById(R.id.changelogReleaseDownloadButton);

            downloadButton.setOnClickListener(this::onDownloadClick);

            DP_8 = Helper.dpToPixel(view.getContext(), 8);
        }

        public void setTitle(String title) {
            this.title.setText(title);
        }

        void enableDownload() {
            downloadButton.setVisibility(View.VISIBLE);
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

        void onDownloadClick(View v) {
            // Equivalent to "check for updates" preferences menu
            if (!UpdateDownloadService.isRunning()) {
                Intent intent = UpdateCheckService.makeIntent(v.getContext(), true);
                v.getContext().startService(intent);
            }
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
