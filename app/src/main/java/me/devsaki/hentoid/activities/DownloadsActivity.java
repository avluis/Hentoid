package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.abstracts.BaseFragment.BackInterface;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.fragments.downloads.EndlessFragment;
import me.devsaki.hentoid.fragments.downloads.PagerFragment;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Created by avluis on 08/26/2016.
 * DownloadsActivity: In charge of hosting EndlessFragment & PagerFragment
 */
public class DownloadsActivity extends BaseActivity implements BackInterface {

    private DrawerLayout drawerLayout;

    private BaseFragment baseFragment;

    private Toolbar toolbar;

    private Class<? extends BaseFragment> getFragment() {
        if (Preferences.getEndlessScroll()) {
            Timber.d("getFragment: EndlessFragment.");
            return EndlessFragment.class;
        } else {
            Timber.d("getFragment: PagerFragment.");
            return PagerFragment.class;
        }
    }

    private Bundle getCreationArguments() {
        Bundle result = new Bundle();
        result.putInt("mode", DownloadsFragment.MODE_LIBRARY);
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hentoid);

        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentById(R.id.content_frame);

        if (fragment == null) {
            fragment = instantiateContentFragment();
            fragment.setArguments(getCreationArguments());
            String tag = fragment.getClass().getSimpleName();

            manager.beginTransaction()
                    .add(R.id.content_frame, fragment, tag)
                    .commit();
        }

        if (Preferences.getRecentVisibility()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        drawerLayout = findViewById(R.id.drawer_layout);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_drawer);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            drawerLayout.openDrawer(GravityCompat.START);
            Preferences.setIsFirstRunProcessComplete(true);
        }

        setTitle("");
    }

    private Fragment instantiateContentFragment() {
        if (Preferences.getEndlessScroll()) {
            Timber.d("getFragment: EndlessFragment.");
            return new EndlessFragment();
        } else {
            Timber.d("getFragment: PagerFragment.");
            return new PagerFragment();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onBackPressed() {
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
        String title = getString(R.string.title_activity_downloads) + " " + subtitle;
        super.setTitle(title);
        toolbar.setTitle(title);
    }

    @Override
    public void setTitle(int titleId) {
        super.setTitle(titleId);
        toolbar.setTitle(titleId);
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateSelectedFragment();
    }

    private void updateSelectedFragment() {
        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentById(R.id.content_frame);

        if (fragment != null) {
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
    public void addBackInterface(BaseFragment fragment) {
        this.baseFragment = fragment;
    }

    public void onNavigationDrawerItemClicked() {
        drawerLayout.closeDrawer(GravityCompat.START);
    }
}
