package me.devsaki.hentoid.activities;

import android.os.Bundle;
import androidx.annotation.IdRes;
import android.view.View;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.fragments.about.ChangelogFragment;
import me.devsaki.hentoid.fragments.about.LicensesFragment;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by wightwulf1944 on 03/21/18.
 */
public class AboutActivity extends BaseActivity {

    private TextView btnChangelog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        bindTextViewLink(R.id.iv_app_logo, Consts.URL_GITHUB_WIKI);
        bindTextViewLink(R.id.tv_github, Consts.URL_GITHUB);
        bindTextViewLink(R.id.tv_discord, Consts.URL_DISCORD);
        bindTextViewLink(R.id.tv_reddit, Consts.URL_REDDIT);

        TextView tvVersionName = findViewById(R.id.tv_version_name);
        tvVersionName.setText(String.format("Hentoid ver: %s (%s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        btnChangelog = findViewById(R.id.about_changelog_button);
        btnChangelog.setOnClickListener(v -> showChangelogFragment());

        View btnLicenses = findViewById(R.id.about_licenses_button);
        btnLicenses.setOnClickListener(v -> showLicenseFragment());

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    private void bindTextViewLink(@IdRes int tvId, String url) {
        View linkableView = findViewById(tvId);
        linkableView.setOnClickListener(v -> Helper.openUrl(this, url));
    }

    private void showLicenseFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, new LicensesFragment())
                .addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
                .commit();
    }

    private void showChangelogFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, new ChangelogFragment())
                .addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
                .commit();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateEvent(UpdateEvent event) {
        if (event.hasNewVersion) btnChangelog.setText(R.string.view_changelog_flagged);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
    }
}
