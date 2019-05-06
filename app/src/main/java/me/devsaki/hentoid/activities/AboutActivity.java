package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by wightwulf1944 on 03/21/18.
 */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        bindTextViewLink(R.id.iv_app_logo, Consts.URL_GITHUB_WIKI);
        bindTextViewLink(R.id.tv_github, Consts.URL_GITHUB);
        bindTextViewLink(R.id.tv_discord, Consts.URL_DISCORD);
        bindTextViewLink(R.id.tv_reddit, Consts.URL_REDDIT);

        TextView tvVersionName = findViewById(R.id.tv_version_name);
        tvVersionName.setText(String.format("Hentoid ver: %s", BuildConfig.VERSION_NAME));

        WebView webView = new WebView(this);
        webView.loadUrl("file:///android_asset/licenses.html");
        webView.setInitialScale(95);

        AlertDialog licensesDialog = new AlertDialog.Builder(this)
                .setTitle("Licenses")
                .setView(webView)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        // TODO: dialog should not show large content or a no-op button
        // replace with activity instead
        Button btnLicenses = findViewById(R.id.btn_about_licenses);
        btnLicenses.setOnClickListener(view -> licensesDialog.show());
    }

    private void bindTextViewLink(@IdRes int tvId, String url) {
        View linkableView = findViewById(tvId);
        linkableView.setOnClickListener(v -> Helper.openUrl(this, url));
    }
}
