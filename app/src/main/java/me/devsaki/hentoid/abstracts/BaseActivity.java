package me.devsaki.hentoid.abstracts;

import android.app.FragmentManager;
import android.app.ListFragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;

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

/**
 * Created by DevSaki on 04/06/2015.
 * Abstract activity to extend from - Implements DrawerLayout
 */
public abstract class BaseActivity<T extends ListFragment> extends AppCompatActivity {
    private static HentoidDB db;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private T fragment;

    public static HentoidDB getDB() {
        return db;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hentoid);

        AndroidHelper.setNavBarColor(this, "#2b0202");

        db = new HentoidDB(this);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
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

        mDrawerLayout.closeDrawer(GravityCompat.START);

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
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    public void ndOpenWebView(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = null;

        switch (view.getId()) {
            case R.id.ndHitomiWbButton:
                intent = new Intent(this, HitomiActivity.class);
                break;
            case R.id.ndNhentaiWbButton:
                intent = new Intent(this, NhentaiActivity.class);
                break;
            case R.id.ndTsuminoWbButton:
                intent = new Intent(this, TsuminoActivity.class);
                break;
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        startActivity(intent);
    }

    @SuppressWarnings({"UnusedParameters", "unused"})
    public void ndDownloads(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(this, DownloadsActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings({"UnusedParameters", "unused"})
    public void ndQueue(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(this, QueueActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings({"UnusedParameters", "unused"})
    public void ndPreferences(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(this, PreferencesActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings({"UnusedParameters", "unused"})
    public void ndAbout(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }
}