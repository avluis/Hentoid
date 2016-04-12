package me.devsaki.hentoid.activities;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.fragments.QueueFragment;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Presents works currently in the download queue
 */
public class QueueActivity extends BaseActivity {
    private static final String TAG = LogHelper.makeLogTag(QueueActivity.class);

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

        initializeToolbar();
        setTitle(getToolbarTitle());

        getSupportFragmentManager().addOnBackStackChangedListener(
                new FragmentManager.OnBackStackChangedListener() {
                    public void onBackStackChanged() {
                        LogHelper.d(TAG, "Update UI");
                    }
                });
    }

    @Override
    protected String getToolbarTitle() {
        return AndroidHelper.getActivityName(mContext, R.string.title_activity_queue);
    }
}