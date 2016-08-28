package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.abstracts.DrawerActivity;
import me.devsaki.hentoid.fragments.EndlessFragment;
import me.devsaki.hentoid.fragments.PagerFragment;
import me.devsaki.hentoid.ui.DrawerMenuContents;
import me.devsaki.hentoid.util.ConstsPrefs;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 08/26/2016.
 * DownloadsActivity: In charge of hosting EndlessFragment & PagerFragment
 * in accordance to Shared Prefs Setting Key: PREF_ENDLESS_SCROLL
 */
public class DownloadsActivity extends DrawerActivity implements BaseFragment.BackInterface {
    private static final String TAG = LogHelper.makeLogTag(DownloadsActivity.class);

    private BaseFragment baseFragment;
    private Class<? extends BaseFragment> selectedFragment;
    private Context cxt;

    @Override
    protected Fragment buildFragment() {
        setFragment();
        try {
            return selectedFragment.newInstance();
        } catch (InstantiationException e) {
            LogHelper.e(TAG, "Error: Could not access constructor: ", e);
        } catch (IllegalAccessException e) {
            LogHelper.e(TAG, "Error: Field or method is not accessible: ", e);
        }
        return null;
    }

    private void setFragment() {
        if (getEndlessPref()) {
            selectedFragment = EndlessFragment.class;
        } else {
            selectedFragment = PagerFragment.class;
        }
    }

    private boolean getEndlessPref() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        return prefs.getBoolean(
                ConstsPrefs.PREF_ENDLESS_SCROLL, ConstsPrefs.PREF_ENDLESS_SCROLL_DEFAULT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        cxt = HentoidApp.getAppContext();
        initializeToolbar();
        setTitle(getToolbarTitle());

        LogHelper.d(TAG, "onCreate");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (baseFragment == null || baseFragment.onBackPressed()) {
            // Fragment did not consume onBackPressed.
            super.onBackPressed();
        }
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
            LogHelper.d(TAG, "Fragment Tag: " + fragment.getTag());
            LogHelper.d(TAG, "Selected Fragment: " + buildFragment().getClass().getSimpleName());

            if (!buildFragment().getClass().getSimpleName().equals(fragment.getTag())) {
                manager.beginTransaction().replace(
                        R.id.content_frame, buildFragment(),
                        buildFragment().getClass().getSimpleName())
                        .commit();
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
                LogHelper.d(TAG, "Permissions granted.");
                // In order to apply changes, activity/task restart is needed
                Helper.doRestart(this);
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                LogHelper.d(TAG, "Permissions denied.");
            }
        } else {
            // Permissions cannot be set, either via policy or forced by user.
            finish();
        }
    }

    @Override
    protected String getToolbarTitle() {
        return Helper.getActivityName(cxt, R.string.title_activity_downloads);
    }

    @Override
    protected void updateDrawerPosition() {
        DrawerMenuContents mDrawerMenuContents = new DrawerMenuContents(this);
        mDrawerMenuContents.getPosition(this.getClass());
        super.updateDrawerPosition();
    }

    @Override
    public void addBackInterface(BaseFragment fragment) {
        this.baseFragment = fragment;
    }
}
