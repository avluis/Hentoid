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
import me.devsaki.hentoid.database.FakkuDroidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by neko on 11/05/2015.
 */
public class ContentAdapter extends ArrayAdapter<Content> {

    private final static String TAG = ContentAdapter.class.getName();
    private final Context context;
    private final List<Content> contents;
    private SharedPreferences sharedPreferences;

    public ContentAdapter(Context context, List<Content> contents) {
        super(context, R.layout.row_download, contents);
        this.context = context;
        this.contents = contents;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
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

        if(content.getTitle()==null)
            tvTitle.setText(R.string.tvTitleEmpty);
        else
            tvTitle.setText(content.getTitle());
        String serie = null;

        serie = content.getSerie()!=null?content.getSerie().getName()!=null?content.getSerie().getName():"":"";
        tvSerie.setText(Html.fromHtml(templateTvSerie.replace("@serie@", serie)));

        String artists = "";
        if (content.getArtists() != null)
            for (int i = 0; i < content.getArtists().size(); i++) {
                Attribute attribute = content.getArtists().get(i);
                artists += attribute.getName();
                if (i != content.getArtists().size() - 1) {
                    artists += ", ";
                }
            }
        tvArtist.setText(Html.fromHtml(templateTvArtist.replace("@artist@", artists)));

        String tags = "";
        if (content.getTags() != null)
            for (int i = 0; i < content.getTags().size(); i++) {
                Attribute attribute = content.getTags().get(i);
                if(attribute.getName()!=null){
                    tags += templateTvTags.replace("@tag@", attribute.getName());
                    if (i != content.getTags().size() - 1) {
                        tags += ", ";
                    }
                }
            }
        tvTags.setText(Html.fromHtml(tags));

        final File dir = Helper.getDownloadDir(content.getFakkuId(), getContext());
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
                                FakkuDroidDB db = new FakkuDroidDB(getContext());

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
        intent.putExtra(MainActivity.INTENT_URL, Constants.FAKKU_URL + content.getUrl());

        getContext().startActivity(intent);
    }
}
