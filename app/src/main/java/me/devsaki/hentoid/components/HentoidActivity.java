package me.devsaki.hentoid.components;

import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;

import me.devsaki.hentoid.AboutActivity;
import me.devsaki.hentoid.DownloadManagerActivity;
import me.devsaki.hentoid.DownloadsActivity;
import me.devsaki.hentoid.PreferencesActivity;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.WebActivities.HitomiActivity;
import me.devsaki.hentoid.WebActivities.NhentaiActivity;
import me.devsaki.hentoid.WebActivities.TsuminoActivity;
import me.devsaki.hentoid.database.HentoidDB;

/**
 * Created by DevSaki on 04/06/2015.
 */
public abstract class HentoidActivity<T extends HentoidFragment> extends AppCompatActivity {
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private HentoidDB db;
    private SharedPreferences sharedPreferences;
    private T fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hentoid);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.string.drawer_open,
                R.string.drawer_close
        );

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = new HentoidDB(this);

        fragment = buildFragment();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

        mDrawerLayout.closeDrawer(GravityCompat.START);
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
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    public void ndOpenWebView(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = null;

        switch (view.getId()){
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

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @SuppressWarnings({"UnusedParameters", "unused"})
    public void ndPreferences(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(this, PreferencesActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings({"UnusedParameters", "unused"})
    public void ndDownloads(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(this, DownloadsActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings({"UnusedParameters", "unused"})
    public void ndDownloadManager(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(this, DownloadManagerActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings({"UnusedParameters", "unused"})
    public void ndAbout(View view) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

    public HentoidDB getDB() {
        return db;
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }
}