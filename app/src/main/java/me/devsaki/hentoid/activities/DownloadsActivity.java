package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.WindowManager;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.abstracts.BaseFragment.BackInterface;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.abstracts.DrawerActivity;
import me.devsaki.hentoid.fragments.EndlessFragment;
import me.devsaki.hentoid.fragments.PagerFragment;
import me.devsaki.hentoid.ui.DrawerMenuContents;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Created by avluis on 08/26/2016.
 * DownloadsActivity: In charge of hosting EndlessFragment & PagerFragment
 */
public class DownloadsActivity extends DrawerActivity implements BackInterface {

    private BaseFragment baseFragment;
    private Context context;

    @Override
    protected Class<? extends BaseFragment> getFragment() {
        if (Preferences.getEndlessScroll()) {
            Timber.d("getFragment: EndlessFragment.");
            return EndlessFragment.class;
        } else {
            Timber.d("getFragment: PagerFragment.");
            return PagerFragment.class;
        }
    }

    @Override
    protected Bundle getCreationArguments()
    {
        Bundle result = new Bundle();
        result.putInt("mode", DownloadsFragment.MODE_LIBRARY);
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Preferences.getRecentVisibility()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        setContentView(mainLayout);

        context = HentoidApp.getAppContext();
        initializeToolbar();
        setTitle(getToolbarTitle());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers();
            return;
        }

        if (baseFragment == null || baseFragment.onBackPressed()) {
            // Fragment did not consume onBackPressed.
            super.onBackPressed();
        }
    }

    @Override
    public void setTitle(CharSequence subtitle) {
        super.setTitle(getToolbarTitle() + " " + subtitle);
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateSelectedFragment();
        updateDrawerPosition();
    }

    private void updateSelectedFragment() {
        FragmentManager manager = getSupportFragmentManager();
        fragment = manager.findFragmentById(R.id.content_frame);

        if (fragment != null) {
            /*
            Fragment selectedFragment = buildFragment();
            String selectedFragmentTag = selectedFragment.getClass().getSimpleName();
            */
            String selectedFragmentTag = getFragment().getSimpleName();

            if (!selectedFragmentTag.equals(fragment.getTag())) {
                Helper.doRestart(this);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                Timber.d("Permissions granted.");
                // In order to apply changes, activity/task restart is needed
                Helper.doRestart(this);
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                Timber.d("Permissions denied.");
            }
        } else {
            // Permissions cannot be set, either via policy or forced by user.
            finish();
        }
    }

    @Override
    protected String getToolbarTitle() {
        return Helper.getActivityName(context, R.string.title_activity_downloads);
    }

    @Override
    public void addBackInterface(BaseFragment fragment) {
        this.baseFragment = fragment;
    }
}
