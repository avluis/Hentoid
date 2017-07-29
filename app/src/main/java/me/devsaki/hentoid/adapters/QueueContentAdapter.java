package me.devsaki.hentoid.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

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
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.NetworkStatus;
import timber.log.Timber;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Created by neko on 11/05/2015.
 * Builds and assigns content from db into adapter for display in queue fragment
 */
public class QueueContentAdapter extends ArrayAdapter<Content> {

    private final Context cxt;
    private final List<Content> contents;
    private final QueueFragment fragment;

    public QueueContentAdapter(Context cxt, List<Content> contents, QueueFragment fragment) {
        super(cxt, R.layout.item_queue, contents);
        this.cxt = cxt;
        this.contents = contents;
        this.fragment = fragment;
    }

    @NonNull
    @Override
    public View getView(int pos, View view, @NonNull ViewGroup parent) {
        View v = view;
        // Get the data item for this position
        final Content content = contents.get(pos);
        ViewHolder holder;
        // Check if an existing view is being reused, otherwise inflate the view
        if (v == null) {
            holder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(cxt);
            v = inflater.inflate(R.layout.item_queue, parent, false);

            holder.tvTitle = (TextView) v.findViewById(R.id.tvTitle);
            holder.ivCover = (ImageView) v.findViewById(R.id.ivCover);
            holder.tvSeries = (TextView) v.findViewById(R.id.tvSeries);
            holder.tvArtist = (TextView) v.findViewById(R.id.tvArtist);
            holder.tvTags = (TextView) v.findViewById(R.id.tvTags);
            holder.tvSite = (TextView) v.findViewById(R.id.tvSite);

            v.setTag(holder);
        } else {
            holder = (ViewHolder) v.getTag();
        }

        // Populate the data into the template view using the data object
        if (content != null) {
            populateLayout(holder, content);
            attachButtons(v, content);
            updateProgress(v, content);
        }
        // Return the completed view to render on screen
        return v;
    }

    private void populateLayout(ViewHolder holder, Content content) {
        attachTitle(holder, content);
        attachCover(holder, content);
        attachSeries(holder, content);
        attachArtist(holder, content);
        attachTags(holder, content);

        holder.tvSite.setText(content.getSite().getDescription());
    }

    private void attachTitle(ViewHolder holder, Content content) {
        if (content.getTitle() == null) {
            holder.tvTitle.setText(R.string.work_untitled);
        } else {
            holder.tvTitle.setText(content.getTitle());
            holder.tvTitle.setSelected(true);
        }
    }

    private void attachCover(ViewHolder holder, Content content) {
        RequestBuilder<Drawable> thumb = Glide.with(cxt).load(content.getCoverImageUrl());

        String coverFile = FileHelper.getThumb(cxt, content);
        holder.ivCover.layout(0, 0, 0, 0);

        RequestOptions myOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder);

        Glide.with(cxt)
                .load(coverFile)
                .apply(myOptions)
                .transition(withCrossFade())
                .thumbnail(thumb)
                .into(holder.ivCover);
    }

    private void attachSeries(ViewHolder holder, Content content) {
        String templateSeries = cxt.getString(R.string.work_series);
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
        holder.tvSeries.setText(Helper.fromHtml(templateSeries.replace("@series@", series)));

        if (seriesAttributes == null) {
            holder.tvSeries.setText(Helper.fromHtml(templateSeries.replace("@series@",
                    cxt.getResources().getString(R.string.work_untitled))));
            holder.tvSeries.setVisibility(View.VISIBLE);
        }
    }

    private void attachArtist(ViewHolder holder, Content content) {
        String templateArtist = cxt.getString(R.string.work_artist);
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
        holder.tvArtist.setText(Helper.fromHtml(templateArtist.replace("@artist@", artists)));

        if (artistAttributes == null) {
            holder.tvArtist.setText(Helper.fromHtml(templateArtist.replace("@artist@",
                    cxt.getResources().getString(R.string.work_untitled))));
            holder.tvArtist.setVisibility(View.VISIBLE);
        }
    }

    private void attachTags(ViewHolder holder, Content content) {
        String templateTags = cxt.getString(R.string.work_tags);
        String tags = "";
        List<Attribute> tagsAttributes = content.getAttributes().get(AttributeType.TAG);
        if (tagsAttributes != null) {
            for (int i = 0; i < tagsAttributes.size(); i++) {
                Attribute attribute = tagsAttributes.get(i);
                if (attribute.getName() != null) {
                    tags += templateTags.replace("@tag@", attribute.getName());
                    if (i != tagsAttributes.size() - 1) {
                        tags += ", ";
                    }
                }
            }
        }
        holder.tvTags.setText(Helper.fromHtml(tags));
    }

    private void attachButtons(View view, final Content content) {
        Button btnCancel = (Button) view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> {
            cancel(content);
            notifyDataSetChanged();
        });
        Button btnPause = (Button) view.findViewById(R.id.btnPause);
        btnPause.setOnClickListener(v -> {
            if (content.getStatus() != StatusContent.DOWNLOADING) {
                resume(content);
            } else {
                pause(content);
                notifyDataSetChanged();
            }
        });
        if (content.getStatus() != StatusContent.DOWNLOADING) {
            btnPause.setText(R.string.resume);
        }
    }

    private void updateProgress(View view, Content content) {
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

    private void cancel(Content content) {
        HentoidDB db = HentoidDB.getInstance(cxt);
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
        HentoidDB db = HentoidDB.getInstance(cxt);
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
            HentoidDB db = HentoidDB.getInstance(cxt);
            content.setStatus(StatusContent.DOWNLOADING);
            db.updateContentStatus(content);
            if (content.getId() == contents.get(0).getId()) {
                Intent intent = new Intent(Intent.ACTION_SYNC, null, cxt,
                        DownloadService.class);
                cxt.startService(intent);
            }
            fragment.update();
        } else {
            Timber.d("Not connected on resume!");
        }
    }

    private void clearDownload(Content content) {
        if (content.getStatus() == StatusContent.CANCELED) {
            FileHelper.removeContent(cxt, content);
        } else {
            Timber.d("Attempting to clear non-cancelled download: %s", content.getTitle());
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
