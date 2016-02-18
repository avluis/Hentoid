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
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by neko on 11/05/2015.
 * Disclaimer: Image cover art is assumed to have .jpg extension by default
 * TODO: Add logic to determine file type and load/save appropriate file to db
 */
public class ContentQueueAdapter extends ArrayAdapter<Content> {

    private static final String TAG = ContentQueueAdapter.class.getName();
    private final Context context;
    private final List<Content> contents;

    public ContentQueueAdapter(Context context, List<Content> contents) {
        super(context, R.layout.row_downloads, contents);
        this.context = context;
        this.contents = contents;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.row_queue, parent, false);

        final Content content = contents.get(position);

        String templateTvSeries = context.getResources().getString(R.string.tvSeries);
        String templateTvArtist = context.getResources().getString(R.string.tvArtists);
        String templateTvTags = context.getResources().getString(R.string.tvTags);

        TextView tvTitle = (TextView) rowView.findViewById(R.id.tvTitle);
        ImageView ivCover = (ImageView) rowView.findViewById(R.id.ivCover);
        TextView tvSeries = (TextView) rowView.findViewById(R.id.tvSeries);
        TextView tvArtist = (TextView) rowView.findViewById(R.id.tvArtist);
        TextView tvTags = (TextView) rowView.findViewById(R.id.tvTags);
        TextView tvSite = (TextView) rowView.findViewById(R.id.tvSite);

        tvSite.setText(content.getSite().getDescription());

        tvTitle.setText(content.getTitle());
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
        tvSeries.setText(Html.fromHtml(templateTvSeries.replace("@serie@", series)));

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
        tvArtist.setText(Html.fromHtml(templateTvArtist.replace("@artist@", artists)));

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
        tvTags.setText(Html.fromHtml(tags));

        final File dir = Helper.getDownloadDir(content, getContext());
        File coverFile = new File(dir, "thumb.jpg");

        String image = coverFile.exists() ? coverFile.getAbsolutePath() : content.getCoverImageUrl();

        HentoidApplication.getInstance().loadBitmap(image, ivCover);

        Button btnCancel = (Button) rowView.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((QueueActivity) getContext()).getFragment().cancel(content);
                notifyDataSetChanged();
            }
        });
        Button btnPause = (Button) rowView.findViewById(R.id.btnPause);
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

        ProgressBar pb = (ProgressBar) rowView.findViewById(R.id.pbDownload);
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

        return rowView;
    }
}