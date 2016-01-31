package me.devsaki.hentoid.adapters;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.MainActivity;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.service.DownloadManagerService;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by neko on 11/05/2015.
 */
public class ContentAdapter extends ArrayAdapter<Content> {

    private static final String TAG = ContentAdapter.class.getName();
    private final Context context;
    private final List<Content> contents;
    private final SimpleDateFormat sdf;

    @SuppressLint("SimpleDateFormat")
    public ContentAdapter(Context context, List<Content> contents) {
        super(context, R.layout.row_download, contents);
        this.context = context;
        this.contents = contents;
        sdf = new SimpleDateFormat("MM/dd/yy HH:mm");
    }

    @Override
    public View getView(int position, View convertView, final ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.row_download, parent, false);

        final Content content = contents.get(position);

        String templateTvSerie = context.getResources().getString(R.string.tvSeries);
        String templateTvArtist = context.getResources().getString(R.string.tvArtists);
        String templateTvTags = context.getResources().getString(R.string.tvTags);

        TextView tvTitle = (TextView) rowView.findViewById(R.id.tvTitle);
        ImageView ivCover = (ImageView) rowView.findViewById(R.id.ivCover);
        TextView tvSerie = (TextView) rowView.findViewById(R.id.tvSeries);
        TextView tvArtist = (TextView) rowView.findViewById(R.id.tvArtist);
        TextView tvTags = (TextView) rowView.findViewById(R.id.tvTags);
        TextView tvSite = (TextView) rowView.findViewById(R.id.tvSite);
        TextView tvStatus = (TextView) rowView.findViewById(R.id.tvStatus);
        TextView tvSavedDate = (TextView) rowView.findViewById(R.id.tvSavedDate);

        tvSite.setText(content.getSite().getDescription());
        tvStatus.setText(content.getStatus().getDescription());
        tvSavedDate.setText(sdf.format(new Date(content.getDownloadDate())));

        if (content.getTitle() == null)
            tvTitle.setText(R.string.tvTitleEmpty);
        else
            tvTitle.setText(content.getTitle());
        String series = "";
        List<Attribute> seriesAttributes = content.getAttributes().get(AttributeType.SERIE);
        if (seriesAttributes != null)
            for (int i = 0; i < seriesAttributes.size(); i++) {
                Attribute attribute = seriesAttributes.get(i);
                series += attribute.getName();
                if (i != seriesAttributes.size() - 1) {
                    series += ", ";
                }
            }
        tvSerie.setText(Html.fromHtml(templateTvSerie.replace("@serie@", series)));

        String artists = "";
        List<Attribute> artistAttributes = content.getAttributes().get(AttributeType.ARTIST);
        if (artistAttributes != null)
            for (int i = 0; i < artistAttributes.size(); i++) {
                Attribute attribute = artistAttributes.get(i);
                artists += attribute.getName();
                if (i != artistAttributes.size() - 1) {
                    artists += ", ";
                }
            }
        tvArtist.setText(Html.fromHtml(templateTvArtist.replace("@artist@", artists)));

        String tags = "";
        List<Attribute> tagsAttributes = content.getAttributes().get(AttributeType.TAG);
        if (tagsAttributes != null)
            for (int i = 0; i < tagsAttributes.size(); i++) {
                Attribute attribute = tagsAttributes.get(i);
                if (attribute.getName() != null) {
                    tags += templateTvTags.replace("@tag@", attribute.getName());
                    if (i != tagsAttributes.size() - 1) {
                        tags += ", ";
                    }
                }
            }
        tvTags.setText(Html.fromHtml(tags));

        final File dir = Helper.getDownloadDir(content, getContext());
        File coverFile = new File(dir, "thumb.jpg");

        String image = coverFile.exists()?coverFile.getAbsolutePath():content.getCoverImageUrl();

        ((HentoidApplication) getContext().getApplicationContext()).loadBitmap(image, ivCover);

        Button btnRead = (Button) rowView.findViewById(R.id.btnRead);
        btnRead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AndroidHelper.openContent(content, getContext());
            }
        });
        Button btnDelete = (Button) rowView.findViewById(R.id.btnDelete);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteContent(content, dir, (ListView) parent);
            }
        });
        Button btnDownloadAgain = (Button) rowView.findViewById(R.id.btnDownloadAgain);
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
        Button btnView = (Button) rowView.findViewById(R.id.btnViewBrowser);
        btnView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewContent(content);
            }
        });
        return rowView;
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
        String message = context.getString(R.string.download_again_dialog).replace("@error",
                numberImagesError + "").replace("@total", numberImages + "");
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                HentoidDB db = new HentoidDB(getContext());
                                content.setStatus(StatusContent.DOWNLOADING);
                                content.setDownloadDate(new Date().getTime());
                                db.updateContentStatus(content);
                                Intent intent = new Intent(Intent.ACTION_SYNC, null, context,
                                        DownloadManagerService.class);
                                context.startService(intent);

                                Toast.makeText(context, R.string.in_queue, Toast.LENGTH_SHORT).show();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(R.string.ask_delete)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                HentoidDB db = new HentoidDB(getContext());

                                try {
                                    FileUtils.deleteDirectory(dir);
                                } catch (IOException e) {
                                    Log.e(TAG, "error deleting content directory", e);
                                }

                                db.deleteContent(content);

                                Toast.makeText(getContext(),
                                        getContext().getResources().getString(R.string.deleted)
                                                .replace("@content", content.getTitle()),
                                        Toast.LENGTH_SHORT).show();
                                contents.remove(content);
                                int index = listView.getFirstVisiblePosition();
                                View v = listView.getChildAt(0);
                                int top = (v == null) ? 0 : (v.getTop() - listView.getPaddingTop());

                                notifyDataSetChanged();
                                listView.setSelectionFromTop(index, top);
                            }
                        }).setNegativeButton(android.R.string.no, null).create().show();
    }

    private void viewContent(Content content) {
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MainActivity.INTENT_URL, content.getGalleryUrl());
        intent.putExtra(MainActivity.INTENT_SITE, content.getSite().getCode());
        getContext().startActivity(intent);
    }
}