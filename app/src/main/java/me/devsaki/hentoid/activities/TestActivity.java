package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.abstracts.DrawerActivity;
import me.devsaki.hentoid.fragments.TestFragment1;
import me.devsaki.hentoid.fragments.TestFragment2;
import me.devsaki.hentoid.ui.DrawerMenuContents;
import me.devsaki.hentoid.util.ConstsPrefs;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 08/26/2016.
 * Test Activity: In charge of hosting TestFragment1 & TestFragment2
 * in accordance to Shared Prefs Setting Key: PREF_ENDLESS_SCROLL
 */
public class TestActivity extends DrawerActivity implements BaseFragment.BackInterface {
    private static final String TAG = LogHelper.makeLogTag(TestActivity.class);

    private BaseFragment baseFragment;
    private Context cxt;

    @Override
    protected Fragment buildFragment() {
        try {
            return getFragment().newInstance();
        } catch (InstantiationException e) {
            LogHelper.e(TAG, "Error: Could not access constructor: ", e);
        } catch (IllegalAccessException e) {
            LogHelper.e(TAG, "Error: Field or method is not accessible: ", e);
        }
        return null;
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

        updateDrawerPosition();
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

    private Class<? extends BaseFragment> getFragment() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        boolean endlessScroll = prefs.getBoolean(
                ConstsPrefs.PREF_ENDLESS_SCROLL, ConstsPrefs.PREF_ENDLESS_SCROLL_DEFAULT);

        if (endlessScroll) {
            return TestFragment1.class;
        } else {
            return TestFragment2.class;
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
    public void setSelectedFragment(BaseFragment baseFragment) {
        this.baseFragment = baseFragment;
    }
}
