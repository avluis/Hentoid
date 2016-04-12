package me.devsaki.hentoid.activities;

import android.content.Context;
import android.os.Bundle;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.fragments.QueueFragment;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.DrawerMenuContents;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Handles hosting of QueueFragment for a single screen.
 */
public class QueueActivity extends BaseActivity implements BaseFragment.BackInterface {
    private static final String TAG = LogHelper.makeLogTag(QueueActivity.class);

    private BaseFragment baseFragment;
    private Context mContext;

    @Override
    protected QueueFragment buildFragment() {
        return QueueFragment.newInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        mContext = getApplicationContext();

        LogHelper.d(TAG, "onCreate");

        initializeToolbar();
        setTitle(getToolbarTitle());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDrawerPosition();
    }

    @Override
    public void onBackPressed() {
        if (baseFragment == null || !baseFragment.onBackPressed()) {
            // Fragment did not consume onBackPressed.
            super.onBackPressed();
        }
    }

    @Override
    protected String getToolbarTitle() {
        return AndroidHelper.getActivityName(mContext, R.string.title_activity_queue);
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