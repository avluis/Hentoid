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

import java.io.File;
import java.util.List;

import me.devsaki.hentoid.HentoidApplication;
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
 * TODO: WIP
 * Builds and assigns content from db into adapter
 * for display in queue
 */
public class QueueContentAdapter extends ArrayAdapter<Content> {
    private static final String TAG = LogHelper.makeLogTag(QueueContentAdapter.class);

    private final Context context;
    private final List<Content> contents;
    private final HentoidDB db = new HentoidDB(getContext());
    private final QueueFragment fragment;

    public QueueContentAdapter(Context cxt, List<Content> contents, QueueFragment fragment) {
        super(cxt, R.layout.row_queue, contents);
        this.context = cxt;
        this.contents = contents;
        this.fragment = fragment;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ViewHolder holder;

        if (view != null) {
            holder = (ViewHolder) view.getTag();
        } else {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.row_queue, parent, false);

            holder = new ViewHolder();
            view.setTag(holder);
        }

        holder.tvTitle = (TextView) view.findViewById(R.id.tvTitle);
        holder.ivCover = (ImageView) view.findViewById(R.id.ivCover);
        holder.tvSeries = (TextView) view.findViewById(R.id.tvSeries);
        holder.tvArtist = (TextView) view.findViewById(R.id.tvArtist);
        holder.tvTags = (TextView) view.findViewById(R.id.tvTags);
        holder.tvSite = (TextView) view.findViewById(R.id.tvSite);

        final Content content = contents.get(position);

        String templateTvSeries = context.getString(R.string.tvSeries);
        String templateTvArtist = context.getString(R.string.tvArtists);
        String templateTvTags = context.getString(R.string.tvTags);

        if (content != null) {
            holder.tvSite.setText(content.getSite().getDescription());

            if (content.getTitle() == null) {
                holder.tvTitle.setText(R.string.tvTitleEmpty);
            } else {
                holder.tvTitle.setText(content.getTitle());
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

            File coverFile = AndroidHelper.getThumb(content, context);
            String image = coverFile != null ?
                    coverFile.getAbsolutePath() : content.getCoverImageUrl();

            HentoidApplication.getInstance().loadBitmap(image, holder.ivCover);

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

        return view;
    }

    private void cancel(Content content) {
        if (content.getStatus() == StatusContent.PAUSED) {
            content.setStatus(StatusContent.DOWNLOADING);
        }
        content.setStatus(StatusContent.CANCELED);
        db.updateContentStatus(content);
        fragment.update();
        if (content.getId() == contents.get(0).getId()) {
            DownloadService.paused = true;
        }
        contents.remove(content);
    }

    private void pause(Content content) {
        content.setStatus(StatusContent.PAUSED);
        // Anytime a download status is set to downloading,
        // download count goes up by one.
        int downloadCount = HentoidApplication.getDownloadCount();
        HentoidApplication.setDownloadCount(--downloadCount);

        db.updateContentStatus(content);
        fragment.update();
        if (content.getId() == contents.get(0).getId()) {
            DownloadService.paused = true;
        }
    }

    private void resume(Content content) {
        if (NetworkStatus.isOnline(context)) {
            content.setStatus(StatusContent.DOWNLOADING);
            db.updateContentStatus(content);
            fragment.update();
            if (content.getId() == contents.get(0).getId()) {
                Intent intent = new Intent(Intent.ACTION_SYNC, null, context,
                        DownloadService.class);
                context.startService(intent);
            }
        }
    }

    static class ViewHolder {
        TextView tvTitle;
        ImageView ivCover;
        TextView tvSeries;
        TextView tvArtist;
        TextView tvTags;
        TextView tvSite;
    }
}