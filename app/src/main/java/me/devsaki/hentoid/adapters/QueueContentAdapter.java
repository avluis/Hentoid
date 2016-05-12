package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.fragments.QueueFragment;
import me.devsaki.hentoid.services.DownloadService;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Created by neko on 11/05/2015.
 * Builds and assigns content from db into adapter for display in queue fragment
 */
public class QueueContentAdapter extends ArrayAdapter<Content> {
    private static final String TAG = LogHelper.makeLogTag(QueueContentAdapter.class);

    private final Context cxt;
    private final List<Content> contents;
    private final HentoidDB db = HentoidDB.getInstance(getContext());
    private final QueueFragment fragment;

    public QueueContentAdapter(Context cxt, List<Content> contents, QueueFragment fragment) {
        super(cxt, R.layout.row_queue, contents);
        this.cxt = cxt;
        this.contents = contents;
        this.fragment = fragment;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        // Get the data item for this position
        final Content content = contents.get(position);
        ViewHolder holder;
        // Check if an existing view is being reused, otherwise inflate the view
        if (view == null) {
            holder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(cxt);
            view = inflater.inflate(R.layout.row_queue, parent, false);

            holder.tvTitle = (TextView) view.findViewById(R.id.tvTitle);
            holder.ivCover = (ImageView) view.findViewById(R.id.ivCover);
            holder.tvSeries = (TextView) view.findViewById(R.id.tvSeries);
            holder.tvArtist = (TextView) view.findViewById(R.id.tvArtist);
            holder.tvTags = (TextView) view.findViewById(R.id.tvTags);
            holder.tvSite = (TextView) view.findViewById(R.id.tvSite);

            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        String templateTvSeries = cxt.getString(R.string.tvSeries);
        String templateTvArtist = cxt.getString(R.string.tvArtists);
        String templateTvTags = cxt.getString(R.string.tvTags);
        // Populate the data into the template view using the data object
        if (content != null) {
            holder.tvSite.setText(content.getSite().getDescription());

            if (content.getTitle() == null) {
                holder.tvTitle.setText(R.string.tvEmpty);
            } else {
                holder.tvTitle.setText(content.getTitle());
                holder.tvTitle.setSelected(true);
            }

            String series = "";
            List<Attribute> seriesAttributes = content.getAttributes().get(AttributeType.SERIE);
            if (seriesAttributes == null) {
                holder.tvSeries.setVisibility(View.GONE);
            } else {
                for (int i = 0; i < seriesAttributes.size(); i++) {
                    Attribute attribute = seriesAttributes.get(i);
                    series += attribute.getName();
                    if (i != seriesAttributes.size() - 1) {
                        series += ", ";
                    }
                }
                holder.tvSeries.setVisibility(View.VISIBLE);
            }
            holder.tvSeries.setText(Html.fromHtml(templateTvSeries.replace("@series@", series)));

            String artists = "";
            List<Attribute> artistAttributes = content.getAttributes().get(AttributeType.ARTIST);
            if (artistAttributes != null) {
                for (int i = 0; i < artistAttributes.size(); i++) {
                    Attribute attribute = artistAttributes.get(i);
                    artists += attribute.getName();
                    if (i != artistAttributes.size() - 1) {
                        artists += ", ";
                    }
                }
            }
            holder.tvArtist.setText(Html.fromHtml(templateTvArtist.replace("@artist@", artists)));

            if (seriesAttributes == null && artistAttributes == null) {
                holder.tvSeries.setText(R.string.tvEmpty);
                holder.tvSeries.setVisibility(View.VISIBLE);
            }

            String tags = "";
            List<Attribute> tagsAttributes = content.getAttributes().get(AttributeType.TAG);
            if (tagsAttributes != null) {
                for (int i = 0; i < tagsAttributes.size(); i++) {
                    Attribute attribute = tagsAttributes.get(i);
                    if (attribute.getName() != null) {
                        tags += templateTvTags.replace("@tag@", attribute.getName());
                        if (i != tagsAttributes.size() - 1) {
                            tags += ", ";
                        }
                    }
                }
            }
            holder.tvTags.setText(Html.fromHtml(tags));

            File coverFile = AndroidHelper.getThumb(cxt, content);
            String image = coverFile != null ?
                    coverFile.getAbsolutePath() : content.getCoverImageUrl();

            HentoidApp.getInstance().loadBitmap(image, holder.ivCover);

            Button btnCancel = (Button) view.findViewById(R.id.btnCancel);
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancel(content);
                    notifyDataSetChanged();
                }
            });
            Button btnPause = (Button) view.findViewById(R.id.btnPause);
            btnPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (content.getStatus() != StatusContent.DOWNLOADING) {
                        resume(content);
                    } else {
                        pause(content);
                        notifyDataSetChanged();
                    }
                }
            });
            if (content.getStatus() != StatusContent.DOWNLOADING) {
                btnPause.setText(R.string.resume);
            }

            ProgressBar pb = (ProgressBar) view.findViewById(R.id.pbDownload);
            if (content.getStatus() != StatusContent.PAUSED) {
                pb.setVisibility(View.VISIBLE);
                if (content.getPercent() > 0) {
                    pb.setIndeterminate(false);
                    pb.setProgress((int) content.getPercent());
                } else {
                    pb.setIndeterminate(true);
                }
            } else {
                pb.setVisibility(View.INVISIBLE);
            }
        }
        // Return the completed view to render on screen
        return view;
    }

    private void cancel(Content content) {
        // Quick hack as workaround if download is paused
        if (content.getStatus() == StatusContent.PAUSED) {
            resume(content);
        }
        content.setStatus(StatusContent.CANCELED);
        db.updateContentStatus(content);
        if (content.getId() == contents.get(0).getId()) {
            DownloadService.paused = true;
        }
        contents.remove(content);
        fragment.update();
        clearDownload(content);
    }

    private void pause(Content content) {
        content.setStatus(StatusContent.PAUSED);
        // Anytime a download status is set to downloading,
        // download count goes up by one.
        int downloadCount = HentoidApp.getDownloadCount();
        HentoidApp.setDownloadCount(--downloadCount);

        db.updateContentStatus(content);
        if (content.getId() == contents.get(0).getId()) {
            DownloadService.paused = true;
        }
        fragment.update();
    }

    private void resume(Content content) {
        if (NetworkStatus.isOnline(cxt)) {
            content.setStatus(StatusContent.DOWNLOADING);
            db.updateContentStatus(content);
            if (content.getId() == contents.get(0).getId()) {
                Intent intent = new Intent(Intent.ACTION_SYNC, null, cxt,
                        DownloadService.class);
                cxt.startService(intent);
            }
            fragment.update();
        }
    }

    private void clearDownload(Content content) {
        if (content.getStatus() == StatusContent.CANCELED) {
            File dir = AndroidHelper.getContentDownloadDir(cxt, content);

            // This loves to fail
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
                LogHelper.e(TAG, "Error deleting content directory: ", e);
            }

            // Run this as well
            // Log will state if directory was deleted (deleteDirectory failed)
            // or if it was not (deleteDirectory success)
            AndroidHelper.deleteDir(dir);
        }
    }

    // View lookup cache
    private static class ViewHolder {
        TextView tvTitle;
        ImageView ivCover;
        TextView tvSeries;
        TextView tvArtist;
        TextView tvTags;
        TextView tvSite;
    }
}