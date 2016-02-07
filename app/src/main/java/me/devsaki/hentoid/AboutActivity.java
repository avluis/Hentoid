package me.devsaki.hentoid;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatCheckedTextView;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatSpinner;
import android.text.Html;
import android.text.Spanned;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

/**
 * Created by avluis on 8/22/15.
 * Presents an About Screen for the user to inquire more about the app.
 */
public class AboutActivity extends AppCompatActivity {
    private String verName = "Hentoid ver: ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setTitle(R.string.title_activity_about);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final Intent intent = new Intent(Intent.ACTION_VIEW);

        Spanned spGitHub = Html.fromHtml(getString(R.string.about_github));
        TextView tvGitHub = (TextView) findViewById(R.id.tv_github);
        final String urlGitHub = getString(R.string.about_github_url);
        tvGitHub.setText(spGitHub);
        tvGitHub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intent.setData(Uri.parse(urlGitHub));
                startActivity(intent);
            }
        });

        Spanned spCommunity = Html.fromHtml(getString(R.string.about_community));
        TextView tvCommunity = (TextView) findViewById(R.id.tv_community);
        final String urlCommunity = getString(R.string.about_community_url);
        tvCommunity.setText(spCommunity);
        tvCommunity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intent.setData(Uri.parse(urlCommunity));
                startActivity(intent);
            }
        });

        Spanned spBlog = Html.fromHtml(getString(R.string.about_blog));
        TextView tvBlog = (TextView) findViewById(R.id.tv_blog);
        final String urlBlog = getString(R.string.about_blog_url);
        tvBlog.setText(spBlog);
        tvBlog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intent.setData(Uri.parse(urlBlog));
                startActivity(intent);
            }
        });

        Spanned spAbout = Html.fromHtml(getString(R.string.about));
        TextView tvAbout = (TextView) findViewById(R.id.tv_about);
        tvAbout.setText(spAbout);

        getVersionInfo();

        TextView tvVersionName = (TextView) findViewById(R.id.tv_version_name);
        tvVersionName.setText(verName);

        Spanned spAboutNotes = Html.fromHtml(getString(R.string.about_notes));
        TextView tvAboutNotes = (TextView) findViewById(R.id.tv_about_notes);
        tvAboutNotes.setText(spAboutNotes);
    }

    private void getVersionInfo() {
        PackageInfo packageInfo;

        try {
            packageInfo = getApplicationContext()
                    .getPackageManager()
                    .getPackageInfo(
                            getApplicationContext().getPackageName(), 0);
            verName += packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            verName += "Unknown";
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public View onCreateView(String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        // Allow super to try and create a view first
        final View result = super.onCreateView(name, context, attrs);
        if (result != null) {
            return result;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // If we're running pre-L, we need to 'inject' our tint aware Views in place of the
            // standard framework versions
            switch (name) {
                case "EditText":
                    return new AppCompatEditText(this, attrs);
                case "Spinner":
                    return new AppCompatSpinner(this, attrs);
                case "CheckBox":
                    return new AppCompatCheckBox(this, attrs);
                case "RadioButton":
                    return new AppCompatRadioButton(this, attrs);
                case "CheckedTextView":
                    return new AppCompatCheckedTextView(this, attrs);
            }
        }

        return null;
    }
}