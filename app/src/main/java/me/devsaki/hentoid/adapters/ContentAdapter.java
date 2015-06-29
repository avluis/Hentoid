package me.devsaki.hentoid.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.MainActivity;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by neko on 11/05/2015.
 */
public class ContentAdapter extends ArrayAdapter<Content> {

    private final static String TAG = ContentAdapter.class.getName();
    private final Context context;
    private final List<Content> contents;
    private SharedPreferences sharedPreferences;
    private SimpleDateFormat sdf;

    public ContentAdapter(Context context, List<Content> contents) {
        super(context, R.layout.row_download, contents);
        this.context = context;
        this.contents = contents;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.row_download, parent, false);

        final Content content = contents.get(position);

        String templateTvSerie = context.getResources().getString(R.string.tvSeries);
        String templateTvArtist = context.getResources().getString(R.string.tvArtists);
        String templateTvTags = context.getResources().getString(R.string.tvTags);

        TextView tvTitle = (TextView) rowView.findViewById(R.id.tvTitle);
        ImageView ivCover = (ImageView) rowView.findViewById(R.id.ivCover);
        TextView tvSerie = (TextView) rowView.findViewById(R.id.tvSerie);
        TextView tvArtist = (TextView) rowView.findViewById(R.id.tvArtist);
        TextView tvTags = (TextView) rowView.findViewById(R.id.tvTags);
        TextView tvSite = (TextView) rowView.findViewById(R.id.tvSite);
        TextView tvStatus = (TextView) rowView.findViewById(R.id.tvStatus);
        TextView tvSavedDate = (TextView) rowView.findViewById(R.id.tvSavedDate);

        tvSite.setText(content.getSite().getDescription());
        tvStatus.setText(content.getStatus().getDescription());
        tvSavedDate.setText(sdf.format(new Date(content.getDownloadDate())));

        if(content.getTitle()==null)
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
                if(attribute.getName()!=null){
                    tags += templateTvTags.replace("@tag@", attribute.getName());
                    if (i != tagsAttributes.size() - 1) {
                        tags += ", ";
                    }
                }
            }
        tvTags.setText(Html.fromHtml(tags));

        final File dir = Helper.getDownloadDir(content, getContext());
        File coverFile = new File(dir, "thumb.jpg");

        ((HentoidApplication) getContext().getApplicationContext()).loadBitmap(coverFile, ivCover);

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
                deleteContent(content, dir);
            }
        });
        Button btnView = (Button) rowView.findViewById(R.id.btnViewBrowser);
        btnView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewContent(content);
            }
        });
        return rowView;
    }

    private void deleteContent(final Content content, final File dir) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
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
                                notifyDataSetChanged();
                            }
                        }).setNegativeButton(android.R.string.no, null).create().show();
    }

    private void viewContent(Content content) {
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(content.getSite()== Site.FAKKU)
            intent.putExtra(MainActivity.INTENT_URL, content.getSite().getUrl() + content.getUrl());
        else if(content.getSite()==Site.PURURIN)
            intent.putExtra(MainActivity.INTENT_URL, content.getSite().getUrl() + Constants.PURURIN_GALLERY + content.getUrl());
        intent.putExtra(MainActivity.INTENT_SITE, content.getSite().getCode());
        getContext().startActivity(intent);
    }
}
