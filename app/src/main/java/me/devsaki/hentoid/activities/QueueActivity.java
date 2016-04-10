package me.devsaki.hentoid.activities;

import android.widget.ListView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.fragments.QueueFragment;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Presents the list of works currently downloading to the user.
 */
public class QueueActivity extends BaseActivity {
    private static final String TAG = LogHelper.makeLogTag(QueueActivity.class);

    @Override
    protected QueueFragment buildFragment() {
        return new QueueFragment();
    }


    @Override
    protected void onResume() {
        super.onResume();

        ListView mDrawerList = (ListView) findViewById(R.id.drawer_list);

        if (mDrawerList != null) {
            mDrawerList.setItemChecked(4, true);
            AndroidHelper.changeEdgeEffect(this, mDrawerList, R.color.menu_item_color,
                    R.color.menu_item_active_color);
        }
    }
}