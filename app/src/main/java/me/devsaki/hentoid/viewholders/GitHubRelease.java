package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Helper;

public class GitHubRelease extends AbstractFlexibleItem<GitHubRelease.ReleaseViewHolder> {

    private final String version;
    private final String name;
    private final String description;
    private final String apkUrl;
    private final Date creationDate;
    private boolean latest;

    public GitHubRelease(Struct releaseStruct) {
        version = releaseStruct.tagName;
        name = releaseStruct.name;
        description = releaseStruct.body;
        creationDate = releaseStruct.creationDate;
        if (releaseStruct.getApk() != null)
            apkUrl = releaseStruct.getApk().downloadUrl;
        else apkUrl = "";
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
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        holder.setTitle(name + " (" + dateFormat.format(creationDate) + ")");
        if (latest && !apkUrl.isEmpty()) holder.enableDownload(apkUrl);

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
        private final LinearLayout layout;
        private String downloadUrl = "";

        ReleaseViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            title = view.findViewById(R.id.changelogReleaseTitle);
            layout = view.findViewById(R.id.changelogReleaseLayout);
            downloadButton = view.findViewById(R.id.changelogReleaseDownloadButton);

            downloadButton.setOnClickListener(this::onDownloadClick);

            DP_8 = Helper.dpToPixel(layout.getContext(), 8);
        }

        public void setTitle(String title) {
            this.title.setText(title);
        }

        void enableDownload(String url) {
            downloadButton.setVisibility(View.VISIBLE);
            downloadUrl = url;
        }

        void addDescContent(String text) {
            TextView tv = new TextView(layout.getContext());
            tv.setText(text);
            tv.setPadding(DP_8, DP_8, 0, 0);
            layout.addView(tv);
        }

        void addListContent(String text) {
            TextView tv = new TextView(layout.getContext());
            tv.setText(text);
            tv.setPadding(DP_8 * 2, DP_8, 0, 0);
            layout.addView(tv);
        }

        void onDownloadClick(View v) {
            Helper.openUrl(layout.getContext(), downloadUrl);
        }
    }

    public class Struct {

        @SerializedName("tag_name")
        String tagName;

        @SerializedName("name")
        String name;

        @SerializedName("body")
        String body;

        @SerializedName("assets")
        List<GitHubAsset> assets;

        @SerializedName("created_at")
        Date creationDate;

        @Nullable
        GitHubAsset getApk() {
            if (assets != null)
                for (GitHubAsset asset : assets)
                    if (asset.contentType.equals("application/vnd.android.package-archive"))
                        return asset;

            return null;
        }

        class GitHubAsset {
            @SerializedName("content_type")
            String contentType;

            @SerializedName("browser_download_url")
            String downloadUrl;

            @SerializedName("size")
            long size;
        }
    }

}
