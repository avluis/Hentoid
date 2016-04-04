package me.devsaki.hentoid.adapters;

import android.content.Context;
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
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by neko on 11/05/2015.
 * Builds and assigns content from db into adapter
 * for display in queue
 */
public class ContentQueueAdapter extends ArrayAdapter<Content> {
    private static final String TAG = LogHelper.makeLogTag(ContentQueueAdapter.class);

    private final Context context;
    private final List<Content> contents;

    public ContentQueueAdapter(Context context, List<Content> contents) {
        super(context, R.layout.row_downloads, contents);
        this.context = context;
        this.contents = contents;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.row_queue, parent, false);

            viewHolder = new ViewHolder();

            viewHolder.tvTitle = (TextView) convertView.findViewById(R.id.tvTitle);
            viewHolder.ivCover = (ImageView) convertView.findViewById(R.id.ivCover);
            viewHolder.tvSeries = (TextView) convertView.findViewById(R.id.tvSeries);
            viewHolder.tvArtist = (TextView) convertView.findViewById(R.id.tvArtist);
            viewHolder.tvTags = (TextView) convertView.findViewById(R.id.tvTags);
            viewHolder.tvSite = (TextView) convertView.findViewById(R.id.tvSite);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        final Content content = contents.get(position);

        String templateTvSeries = context.getResources().getString(R.string.tvSeries);
        String templateTvArtist = context.getResources().getString(R.string.tvArtists);
        String templateTvTags = context.getResources().getString(R.string.tvTags);

        if (content != null) {
            viewHolder.tvSite.setText(content.getSite().getDescription());

            viewHolder.tvTitle.setText(content.getTitle());
            String series = "";
            List<Attribute> seriesAttributes = content.getAttributes().get(AttributeType.SERIE);
            if (seriesAttributes != null) {
                for (int i = 0; i < seriesAttributes.size(); i++) {
                    Attribute attribute = seriesAttributes.get(i);
                    series += attribute.getName();
                    if (i != seriesAttributes.size() - 1) {
                        series += ", ";
                    }
                }
            }
            viewHolder.tvSeries.setText(Html.fromHtml(templateTvSeries.replace("@series@", series)));

            // If no series found, then hide tag from list item
            if (seriesAttributes == null) {
                viewHolder.tvSeries.setVisibility(View.GONE);
            }

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
            viewHolder.tvArtist.setText(Html.fromHtml(templateTvArtist.replace("@artist@", artists)));

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
            viewHolder.tvTags.setText(Html.fromHtml(tags));

            File coverFile = AndroidHelper.getThumb(content, getContext());
            String image = coverFile != null ?
                    coverFile.getAbsolutePath() : content.getCoverImageUrl();

            HentoidApplication.getInstance().loadBitmap(image, viewHolder.ivCover);

            Button btnCancel = (Button) convertView.findViewById(R.id.btnCancel);
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((QueueActivity) getContext()).getFragment().cancel(content);
                    notifyDataSetChanged();
                }
            });
            Button btnPause = (Button) convertView.findViewById(R.id.btnPause);
            btnPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (content.getStatus() != StatusContent.DOWNLOADING) {
                        ((QueueActivity) getContext()).getFragment().resume(content);
                    } else {
                        ((QueueActivity) getContext()).getFragment().pause(content);
                        notifyDataSetChanged();
                    }
                }
            });
            if (content.getStatus() != StatusContent.DOWNLOADING) {
                btnPause.setText(R.string.resume);
            }

            ProgressBar pb = (ProgressBar) convertView.findViewById(R.id.pbDownload);
            if (content.getStatus() == StatusContent.PAUSED) {
                pb.setVisibility(View.INVISIBLE);
            } else if (content.getPercent() > 0) {
                pb.setVisibility(View.VISIBLE);
                pb.setIndeterminate(false);
                pb.setProgress((int) content.getPercent());
            } else {
                pb.setVisibility(View.VISIBLE);
                pb.setIndeterminate(true);
            }
        }

        return convertView;
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