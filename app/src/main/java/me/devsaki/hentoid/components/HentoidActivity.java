package me.devsaki.hentoid.components;

import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;

import me.devsaki.hentoid.AboutActivity;
import me.devsaki.hentoid.DownloadManagerActivity;
import me.devsaki.hentoid.DownloadsActivity;
import me.devsaki.hentoid.MainActivity;
import me.devsaki.hentoid.PreferencesActivity;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.updater.UpdateCheck;

/**
 * Created by DevSaki on 04/06/2015.
 */
public abstract class HentoidActivity<T extends HentoidFragment> extends AppCompatActivity {
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private HentoidDB db;
    private SharedPreferences sharedPreferences;
    private T fragment;
    private static final String updateURL = "https://raw.githubusercontent.com/csaki/Hentoid/master/update.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hentoid);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = new HentoidDB(this);

        fragment = buildFragment();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();
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

    public void ndFakkuWb(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.INTENT_SITE, Site.FAKKU.getCode());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void ndPururinWb(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.INTENT_SITE, Site.PURURIN.getCode());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void ndHitomiWb(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.INTENT_SITE, Site.HITOMI.getCode());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void ndPreferences(View view) {
        Intent intent = new Intent(this, PreferencesActivity.class);
        startActivity(intent);
    }

    public void ndCheckUpdates(View view) {
        UpdateCheck.getInstance().checkForUpdate(getApplicationContext(), updateURL, false, new UpdateCheck.UpdateCheckCallback() {
            @Override
            public void noUpdateAvailable() {
                System.out.println("No Update Available~");
            }

            @Override
            public void onUpdateAvailable() {
                System.out.println("Update Available!");
            }
        });
    }

    public void ndDownloads(View view) {
        Intent intent = new Intent(this, DownloadsActivity.class);
        startActivity(intent);
    }

    public void ndDownloadManager(View view) {
        Intent intent = new Intent(this, DownloadManagerActivity.class);
        startActivity(intent);
    }

    public void ndAbout(View view) {
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
