package me.devsaki.hentoid.abstracts;

import android.app.ActivityOptions;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by DevSaki on 04/06/2015.
 * Abstract activity to extend from
 * Implements DrawerLayout
 */
public abstract class BaseFragmentActivity extends BaseFragmentActivity {
    private static final String TAG = LogHelper.makeLogTag(BaseFragmentActivity.class);

    private static HentoidDB db;
    private final Handler handler = new Handler();
    private String[] mActivityList;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private Toolbar mToolbar;
    private ActionBarDrawerToggle mDrawerToggle;
    private final android.support.v4.app.FragmentManager.OnBackStackChangedListener mBackStackChangedListener =
            new android.support.v4.app.FragmentManager.OnBackStackChangedListener() {
                @Override
                public void onBackStackChanged() {
                    updateDrawerToggle();
                }
            };
    private T fragment;
    private Class[] activities;
    private int mItemToOpenWhenDrawerCloses = -1;
    private final DrawerLayout.DrawerListener mDrawerListener = new DrawerLayout.DrawerListener() {
        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            if (mDrawerToggle != null) mDrawerToggle.onDrawerSlide(drawerView, slideOffset);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            if (mDrawerToggle != null) mDrawerToggle.onDrawerOpened(drawerView);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getToolbarTitle());
            }
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            if (mDrawerToggle != null) mDrawerToggle.onDrawerClosed(drawerView);
            int position = mItemToOpenWhenDrawerCloses;

            if (position >= 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    Class activityClass = getActivity(position);
                    Intent intent = new Intent(BaseFragmentActivity.this, activityClass);
                    Bundle bundle = ActivityOptions.makeCustomAnimation(
                            BaseFragmentActivity.this, R.anim.fade_in, R.anim.fade_out).toBundle();

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent, bundle);
                } else {
                    Class activityClass = getActivity(position);
                    startActivity(new Intent(BaseFragmentActivity.this, activityClass));
                }
            }
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            if (mDrawerToggle != null) mDrawerToggle.onDrawerStateChanged(newState);
        }
    };
    private boolean isToolbarInitialized;

    protected static HentoidDB getDB() {
        return db;
    }

    protected String getToolbarTitle() {
        return AndroidHelper.getActivityName(this, R.string.app_name);
    }

    public Class getActivity(int position) {
        return activities[position];
    }

    @LayoutRes
    protected int getLayoutResId() {
        return R.layout.activity_hentoid;
    }

    protected abstract Fragment createFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        LogHelper.d(TAG, "onCreate");

        AndroidHelper.setNavBarColor(this, R.color.primary_dark);

        db = new HentoidDB(this);

        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentById(R.id.content_frame);

        if (fragment != null) {
            fragment = createFragment();
            manager.beginTransaction()
                    .add(R.id.content_frame, fragment)
                    .commit();
        }

    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        mToolbar.setTitle(title);
    }

    @Override
    public void setTitle(int titleId) {
        super.setTitle(titleId);
        mToolbar.setTitle(titleId);
    }

    protected abstract T buildFragment();

    public T getFragment() {
        return fragment;
    }

    protected void initializeToolbar() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mToolbar == null) {
            throw new IllegalStateException(
                    "Layout is required to include a Toolbar with id " +
                            "'toolbar'");
        }

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mActivityList = getResources().getStringArray(R.array.nav_drawer_entries);
        if (mDrawerLayout != null) {
            mDrawerList = (ListView) findViewById(R.id.drawer_list);
            if (mDrawerList == null) {
                throw new IllegalStateException(
                        "A layout with a drawerLayout is required to " +
                                "include a ListView with id 'drawerList'");
            }

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    mToolbar, R.string.drawer_open, R.string.drawer_close);
            mDrawerLayout.addDrawerListener(mDrawerListener);
            mDrawerLayout.setStatusBarBackgroundColor(
                    AndroidHelper.getThemeColor(this, R.attr.colorPrimary, R.color.primary));

            mDrawerList.setAdapter(new ArrayAdapter<>(this,
                    R.layout.drawer_list_item, mActivityList));

            activities = new Class[mActivityList.length];
            populateActivities(activities);

            final int selectedPosition = getPosition(this.getClass());

            mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position != selectedPosition) {
                        mItemToOpenWhenDrawerCloses = position;
                    }
                    mDrawerLayout.closeDrawers();
                }
            });
            setSupportActionBar(mToolbar);
            updateDrawerToggle();
        }
        isToolbarInitialized = true;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // If not handled by drawerToggle, home needs to be handled by returning to previous
        if (item != null && item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // If the drawer is open, back will close it
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
            return;
        }
        // Otherwise, it may return to the previous fragment stack
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        } else {
            // Lastly, it will rely on the system behavior for back
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Whenever the fragment back stack changes, we may need to update the
        // action bar toggle: only top level screens show the hamburger-like icon, inner
        // screens - either Activities or fragments - show the "Up" icon instead.
        getSupportFragmentManager().addOnBackStackChangedListener(mBackStackChangedListener);

        LogHelper.d(TAG, "onResume");
        LogHelper.d(TAG, getPosition(getClass()));
        // mDrawerList.setItemChecked(getPosition(getClass()), true);

        AndroidHelper.changeEdgeEffect(this, mDrawerList, R.color.menu_item_color,
                R.color.menu_item_active_color);
    }

    @Override
    public void onPause() {
        super.onPause();
        getSupportFragmentManager().removeOnBackStackChangedListener(mBackStackChangedListener);
    }

    @Override
    protected void onStart() {
        super.onStart();

        LogHelper.d(TAG, "onStart");

        if (!isToolbarInitialized) {
            throw new IllegalStateException(
                    "You must run super.initializeToolbar at " +
                            "the end of your onCreate method");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LogHelper.d(TAG, "onDestroy");

        handler.removeCallbacksAndMessages(null);
    }

    public int getPosition(Class activityClass) {
        for (int i = 0; i < activities.length; i++) {
            if (activities[i].equals(activityClass)) {
                return i;
            }
        }
        return -1;
    }

    private void populateActivities(Class[] activities) {
        String activity;
        Class<?> cls = null;
        for (int i = 0; i < mActivityList.length; i++) {
            activity = mActivityList[i];

            try {
                cls = Class.forName("me.devsaki.hentoid.activities." + activity + "Activity");
            } catch (ClassNotFoundException e) {
                // TODO: Log to Analytics
                LogHelper.e(TAG, "Class not found: ", e);
            }

            activities[i] = cls;
        }
    }

    private void updateDrawerToggle() {
        if (mDrawerToggle == null) {
            return;
        }
        boolean isRoot = getSupportFragmentManager().getBackStackEntryCount() == 0;
        mDrawerToggle.setDrawerIndicatorEnabled(isRoot);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(!isRoot);
            getSupportActionBar().setDisplayHomeAsUpEnabled(!isRoot);
            getSupportActionBar().setHomeButtonEnabled(!isRoot);
        }
        if (isRoot) {
            mDrawerToggle.syncState();
        }
    }
}