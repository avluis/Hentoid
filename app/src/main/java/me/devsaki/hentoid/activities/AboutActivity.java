package me.devsaki.hentoid.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by avluis on 8/22/15.
 * Presents an About Screen for the user to inquire more about the app.
 */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);
        setTitle(R.string.title_activity_about);

        final Intent intent = new Intent(Intent.ACTION_VIEW);
        populateLinks(intent);
        attachBuildNotes();
        attachLicenseNotes();
    }

    private void populateLinks(final Intent intent) {
        Spanned spGitHub = Html.fromHtml(getString(R.string.about_github));
        TextView tvGitHub = (TextView) findViewById(R.id.tv_github);
        final String urlGitHub = getString(R.string.about_github_url);
        if (tvGitHub != null) {
            tvGitHub.setText(spGitHub);
            tvGitHub.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    intent.setData(Uri.parse(urlGitHub));
                    startActivity(intent);
                }
            });
        }

        Spanned spCommunity = Html.fromHtml(getString(R.string.about_community));
        TextView tvCommunity = (TextView) findViewById(R.id.tv_community);
        final String urlCommunity = getString(R.string.about_community_url);
        if (tvCommunity != null) {
            tvCommunity.setText(spCommunity);
            tvCommunity.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    intent.setData(Uri.parse(urlCommunity));
                    startActivity(intent);
                }
            });
        }

        Spanned spDiscord = Html.fromHtml(getString(R.string.about_discord));
        TextView tvDiscord = (TextView) findViewById(R.id.tv_discord);
        final String urlBlog = getString(R.string.about_discord_url);
        if (tvDiscord != null) {
            tvDiscord.setText(spDiscord);
            tvDiscord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    intent.setData(Uri.parse(urlBlog));
                    startActivity(intent);
                }
            });
        }

        Spanned spReddit = Html.fromHtml(getString(R.string.about_reddit));
        TextView tvReddit = (TextView) findViewById(R.id.tv_reddit);
        final String urlReddit = getString(R.string.about_reddit_url);
        if (tvReddit != null) {
            tvReddit.setText(spReddit);
            tvReddit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    intent.setData(Uri.parse(urlReddit));
                    startActivity(intent);
                }
            });
        }
    }

    private void attachBuildNotes() {
        Spanned spAbout = Html.fromHtml(getString(R.string.about));
        TextView tvAbout = (TextView) findViewById(R.id.tv_about);
        if (tvAbout != null) {
            tvAbout.setText(spAbout);
        }

        String verName = "Hentoid ver: ";
        try {
            verName += Helper.getAppVersionInfo(this);
        } catch (PackageManager.NameNotFoundException e) {
            verName += "Unknown";
        }

        TextView tvVersionName = (TextView) findViewById(R.id.tv_version_name);
        if (tvVersionName != null) {
            tvVersionName.setText(verName);
        }

        Spanned spAboutNotes = Html.fromHtml(getString(R.string.about_notes));
        TextView tvAboutNotes = (TextView) findViewById(R.id.tv_about_notes);
        if (tvAboutNotes != null) {
            tvAboutNotes.setText(spAboutNotes);
        }
    }

    private void attachLicenseNotes() {
        Button btnLicenses = (Button) findViewById(R.id.btn_about_licenses);

        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Licenses");

        WebView wv = new WebView(this);
        wv.loadUrl("file:///android_asset/licenses.html");
        wv.setInitialScale(95);

        alert.setView(wv);
        alert.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        btnLicenses.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.show();
            }
        });
    }
}
