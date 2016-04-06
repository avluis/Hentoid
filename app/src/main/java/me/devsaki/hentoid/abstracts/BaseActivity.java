package me.devsaki.hentoid.abstracts;

import android.app.FragmentManager;
import android.app.ListFragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.activities.HitomiActivity;
import me.devsaki.hentoid.activities.NhentaiActivity;
import me.devsaki.hentoid.activities.PreferencesActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.TsuminoActivity;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by DevSaki on 04/06/2015.
 * Abstract activity to extend from - Implements DrawerLayout
 */
public abstract class BaseActivity<T extends ListFragment> extends AppCompatActivity {
    private static final String TAG = LogHelper.makeLogTag(BaseActivity.class);

    private static HentoidDB db;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private T fragment;

    protected static HentoidDB getDB() {
        return db;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hentoid);

        AndroidHelper.setNavBarColor(this, "#2b0202");

        db = new HentoidDB(this);

        String[] mList = getResources().getStringArray(R.array.nav_drawer_entries);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        mDrawerList.setAdapter(new ArrayAdapter<>(this,
                R.layout.drawer_list_item, mList));

        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectItem(position);
            }
        });

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close) {

            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu();
            }

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu();
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);

        fragment = buildFragment();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

        mDrawerLayout.closeDrawer(mDrawerList);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    protected abstract T buildFragment();

    public T getFragment() {
        return fragment;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(mDrawerList)) {
            mDrawerLayout.closeDrawer(mDrawerList);
        } else {
            super.onBackPressed();
        }
    }

    private void selectItem(int position) {
        Intent intent = null;

        switch (position) {
            case 0:
                intent = new Intent(this, HitomiActivity.class);
                break;
            case 1:
                intent = new Intent(this, NhentaiActivity.class);
                break;
            case 2:
                intent = new Intent(this, TsuminoActivity.class);
                break;
            case 3:
                intent = new Intent(this, DownloadsActivity.class);
                break;
            case 4:
                intent = new Intent(this, QueueActivity.class);
                break;
            case 5:
                intent = new Intent(this, PreferencesActivity.class);
                break;
            case 6:
                intent = new Intent(this, AboutActivity.class);
                break;
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        mDrawerList.setItemChecked(position, true);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(intent);
    }
}