package me.devsaki.hentoid.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.fragments.DownloadsFragment;
import me.devsaki.hentoid.services.DownloadService;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by neko on 11/05/2015.
 * Builds and assigns content from db into adapter
 * for display in downloads
 */
public class ContentAdapter extends ArrayAdapter<Content> {
    private static final String TAG = LogHelper.makeLogTag(ContentAdapter.class);

    private final Context cxt;
    private final List<Content> contents;
    private final HentoidDB db = new HentoidDB(getContext());
    private final SimpleDateFormat sdf;
    private final DownloadsFragment fragment;

    public ContentAdapter(Context cxt, List<Content> contents, DownloadsFragment fragment) {
        super(cxt, R.layout.row_downloads, contents);
        this.cxt = cxt;
        this.contents = contents;
        this.fragment = fragment;
        sdf = new SimpleDateFormat("MM/dd/yy HH:mm", Locale.US);
    }

    @Override
    public View getView(int position, View view, final ViewGroup parent) {
        // Get the data item for this position
        final Content content = contents.get(position);
        ViewHolder holder;
        // Check if an existing view is being reused, otherwise inflate the view
        if (view == null) {
            holder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(cxt);
            view = inflater.inflate(R.layout.row_downloads, parent, false);

            holder.tvTitle = (TextView) view.findViewById(R.id.tvTitle);
            holder.ivCover = (ImageView) view.findViewById(R.id.ivCover);
            holder.tvSeries = (TextView) view.findViewById(R.id.tvSeries);
            holder.tvArtist = (TextView) view.findViewById(R.id.tvArtist);
            holder.tvTags = (TextView) view.findViewById(R.id.tvTags);
            holder.tvSite = (TextView) view.findViewById(R.id.tvSite);
            holder.tvStatus = (TextView) view.findViewById(R.id.tvStatus);
            holder.tvSavedDate = (TextView) view.findViewById(R.id.tvSavedDate);

            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        String templateTvSeries = cxt.getResources().getString(R.string.tvSeries);
        String templateTvArtist = cxt.getResources().getString(R.string.tvArtists);
        String templateTvTags = cxt.getResources().getString(R.string.tvTags);
        // Populate the data into the template view using the data object
        if (content != null) {
            holder.tvSite.setText(content.getSite().getDescription());
            holder.tvStatus.setText(content.getStatus().getDescription());
            holder.tvSavedDate.setText(sdf.format(new Date(content.getDownloadDate())));

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

            final File dir = AndroidHelper.getDownloadDir(content, cxt);

            File coverFile = AndroidHelper.getThumb(content, cxt);
            String image = coverFile != null ?
                    coverFile.getAbsolutePath() : content.getCoverImageUrl();

            HentoidApplication.getInstance().loadBitmap(image, holder.ivCover);

            Button btnRead = (Button) view.findViewById(R.id.btnRead);
            btnRead.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AndroidHelper.openContent(content, cxt);
                }
            });
            Button btnDelete = (Button) view.findViewById(R.id.btnDelete);
            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteContent(content, dir, (ListView) parent);
                }
            });
            Button btnDownloadAgain = (Button) view.findViewById(R.id.btnDownloadAgain);
            if (content.getStatus() == StatusContent.ERROR) {
                btnDownloadAgain.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAgain(content, (ListView) parent);
                    }
                });
                btnDownloadAgain.setVisibility(View.VISIBLE);
            } else {
                btnDownloadAgain.setVisibility(View.GONE);
            }
            Button btnView = (Button) view.findViewById(R.id.btnViewBrowser);
            btnView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    viewContent(content);
                }
            });
        }
        // Return the completed view to render on screen
        return view;
    }

    private void downloadAgain(final Content content, final ListView listView) {
        int numberImages;
        int numberImagesError = 0;

        numberImages = content.getImageFiles().size();
        for (ImageFile img : content.getImageFiles()) {
            if (img.getStatus() == StatusContent.ERROR) {
                numberImagesError++;
            }
        }
        String message = cxt.getString(R.string.download_again_dialog).replace("@error",
                numberImagesError + "").replace("@total", numberImages + "");
        AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
        builder.setMessage(message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                content.setStatus(StatusContent.DOWNLOADING)
                                        .setDownloadDate(new Date().getTime());
                                db.updateContentStatus(content);
                                Intent intent = new Intent(Intent.ACTION_SYNC, null, cxt,
                                        DownloadService.class);
                                cxt.startService(intent);

                                AndroidHelper.toast(cxt, R.string.in_queue);
                                contents.remove(content);
                                int index = listView.getFirstVisiblePosition();
                                View v = listView.getChildAt(0);
                                int top = (v == null) ? 0 : (v.getTop() - listView.getPaddingTop());

                                notifyDataSetChanged();
                                listView.setSelectionFromTop(index, top);
                            }
                        }).setNegativeButton(android.R.string.no, null).create().show();
    }

    private void deleteContent(final Content content, final File dir, final ListView listView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
        builder.setMessage(R.string.ask_delete)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                try {
                                    FileUtils.deleteDirectory(dir);
                                } catch (IOException e) {
                                    LogHelper.e(TAG, "Error deleting content directory: ", e);
                                }

                                db.deleteContent(content);

                                AndroidHelper.toast(cxt,
                                        cxt.getResources().getString(R.string.deleted)
                                                .replace("@content", content.getTitle()));
                                contents.remove(content);
                                int index = listView.getFirstVisiblePosition();
                                View v = listView.getChildAt(0);
                                int top = (v == null) ? 0 : (v.getTop() - listView.getPaddingTop());

                                notifyDataSetChanged();
                                listView.setSelectionFromTop(index, top);
                                fragment.update();
                            }
                        }).setNegativeButton(android.R.string.no, null).create().show();
    }

    private void viewContent(Content content) {
        Intent intent = new Intent(cxt, content.getWebActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Constants.INTENT_URL, content.getGalleryUrl());
        cxt.startActivity(intent);
    }

    // View lookup cache
    private static class ViewHolder {
        TextView tvTitle;
        ImageView ivCover;
        TextView tvSeries;
        TextView tvArtist;
        TextView tvTags;
        TextView tvSite;
        TextView tvStatus;
        TextView tvSavedDate;
    }
}