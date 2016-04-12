package me.devsaki.hentoid.activities;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.fragments.DownloadsFragment;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.DrawerMenuContents;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Handles hosting of DownloadsFragment for single screen.
 */
public class DownloadsActivity extends BaseActivity implements BaseFragment.BackInterface {
    private static final String TAG = LogHelper.makeLogTag(DownloadsActivity.class);

    private BaseFragment baseFragment;
    private Context mContext;

    @Override
    protected Fragment buildFragment() {
        return DownloadsFragment.newInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        mContext = getApplicationContext();

        LogHelper.d(TAG, "onCreate");

        initializeToolbar();
        setTitle(getToolbarTitle());

        resetDownloadCount();
    }

    private void resetDownloadCount() {
        if ((getIntent().getIntExtra(HentoidApplication.DOWNLOAD_COUNT, 0)) != 0) {
            // Reset download count
            HentoidApplication.setDownloadCount(0);
        }
    }

    @Override
    public void onBackPressed() {
        if (baseFragment == null || !baseFragment.onBackPressed()) {
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
    protected String getToolbarTitle() {
        return AndroidHelper.getActivityName(mContext, R.string.title_activity_downloads);
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