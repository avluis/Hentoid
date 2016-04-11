package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.widget.ListView;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.fragments.DownloadsFragment;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * TODO: WIP
 */
public class DownloadsActivity extends BaseActivity {
    private static final String TAG = LogHelper.makeLogTag(DownloadsActivity.class);

    @Override
    protected DownloadsFragment buildFragment() {
        return new DownloadsFragment();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if ((getIntent().getIntExtra(HentoidApplication.DOWNLOAD_COUNT, 0)) != 0) {
            // Reset download count
            HentoidApplication.setDownloadCount(0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        ListView mDrawerList = (ListView) findViewById(R.id.drawer_list);

        if (mDrawerList != null) {
            mDrawerList.setItemChecked(3, true);
            AndroidHelper.changeEdgeEffect(this, mDrawerList, R.color.menu_item_color,
                    R.color.menu_item_active_color);
        }
    }
}