package me.devsaki.hentoid.abstracts;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.ui.CompoundAdapter;
import me.devsaki.hentoid.ui.DrawerMenuContents;
import me.devsaki.hentoid.util.ConstsPrefs;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Created by avluis on 4/11/2016.
 * Abstract activity with toolbar and navigation drawer.
 * Needs to be extended by any activity that wants to be shown as a top level activity.
 * The requirements for a subclass are:
 * calling {@link #initializeToolbar()} on onCreate, after setContentView() is called.
 * In addition, subclasses must have these layout elements:
 * - {@link android.support.v7.widget.Toolbar} with id 'toolbar'.
 * - {@link android.support.v4.widget.DrawerLayout} with id 'drawer_layout'.
 * - {@link android.widget.ListView} with id 'drawer_list'.
 */
public abstract class DrawerActivity extends BaseActivity {

    protected Fragment fragment;
    private Context cxt;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private DrawerMenuContents mDrawerMenuContents;
    private Toolbar mToolbar;
    private ActionBarDrawerToggle mDrawerToggle;
    private final FragmentManager.OnBackStackChangedListener onBackStackChangedListener =
            this::updateDrawerToggle;
    private boolean isToolbarInitialized;
    private int itemToOpen = -1;
    private int currentPos = -1;
    private boolean itemTapped;
    private DrawerLayout.DrawerListener mDrawerListener;

    /**
     * Return true if the first-app-run-activities have already been executed.
     *
     * @param context Context to be used to lookup the {@link SharedPreferences}.
     */
    private static boolean isFirstRunProcessComplete(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                ConstsPrefs.PREF_WELCOME_DONE, false);
    }

    /**
     * Mark whether this is the first time the first-app-run-processes have run.
     *
     * @param context Context to be used to edit the {@link SharedPreferences}.
     */
    private static void markFirstRunProcessComplete(final Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putBoolean(ConstsPrefs.PREF_WELCOME_DONE, true).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(getLayoutResId());
        cxt = HentoidApp.getAppContext();

        FragmentManager manager = getSupportFragmentManager();
        fragment = manager.findFragmentById(R.id.content_frame);

        if (fragment == null) {
            fragment = buildFragment();

            manager.beginTransaction()
                    .add(R.id.content_frame, fragment, getFragmentTag())
                    .commit();
        }
    }

    protected abstract Fragment buildFragment();

    protected String getToolbarTitle() {
        return Helper.getActivityName(cxt, R.string.app_name);
    }

    private String getFragmentTag() {
        if (fragment != null) {
            return fragment.getClass().getSimpleName();
        }
        return null;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!isToolbarInitialized) {
            throw new IllegalStateException(
                    "You must run super.initializeToolbar at " +
                            "the end of your onCreate method");
        }
    }

    @SuppressWarnings("SameReturnValue")
    @LayoutRes
    protected int getLayoutResId() {
        return R.layout.activity_hentoid;
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

    protected void initializeToolbar() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mToolbar == null) {
            throw new IllegalStateException(
                    "Layout is required to include a Toolbar with id " +
                            "'toolbar'");
        } else {
            setSupportActionBar(mToolbar);
            setupActionBarDrawerToggle();
            initializeNavigationDrawer();
            isToolbarInitialized = true;
        }
    }

    private void initializeNavigationDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mDrawerList = (ListView) findViewById(R.id.drawer_list);
            if (mDrawerList == null) {
                throw new IllegalStateException(
                        "A layout with a drawerLayout is required to " +
                                "include a ListView with id 'drawer_list'");
            }
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    mToolbar, R.string.drawer_open, R.string.drawer_close);
            mDrawerLayout.addDrawerListener(mDrawerListener);
            populateDrawerItems();
            updateDrawerToggle();
        }

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!isFirstRunProcessComplete(this)) {
            // first run of the app starts with the nav drawer open
            mDrawerLayout.openDrawer(GravityCompat.START);
            markFirstRunProcessComplete(this);
        }
    }

    private void setupActionBarDrawerToggle() {
        mDrawerListener = new DrawerLayout.DrawerListener() {
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

            @SuppressLint("NewApi")
            @Override
            public void onDrawerClosed(View drawerView) {
                if (mDrawerToggle != null) mDrawerToggle.onDrawerClosed(drawerView);

                int position = itemToOpen;
                if (position >= 0 && itemTapped) {
                    itemTapped = false;
                    if (Helper.isAtLeastAPI(Build.VERSION_CODES.JELLY_BEAN)) {
                        Class activityClass = mDrawerMenuContents.getActivity(position);
                        Intent intent = new Intent(DrawerActivity.this, activityClass);
                        Bundle bundle = ActivityOptions.makeCustomAnimation(
                                DrawerActivity.this, R.anim.fade_in, R.anim.fade_out).toBundle();
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent, bundle);
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    } else {
                        Class activityClass = mDrawerMenuContents.getActivity(position);
                        startActivity(new Intent(DrawerActivity.this, activityClass));
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    }
                }
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                if (mDrawerToggle != null) mDrawerToggle.onDrawerStateChanged(newState);
            }
        };
    }

    private void populateDrawerItems() {
        mDrawerMenuContents = new DrawerMenuContents(this);
        updateDrawerPosition();
        final int selectedPosition = currentPos;
        final int unselectedColor = ContextCompat.getColor(getApplicationContext(),
                R.color.drawer_item_unselected_background);
        final int selectedColor = ContextCompat.getColor(getApplicationContext(),
                R.color.drawer_item_selected_background);
        final CompoundAdapter adapter = new CompoundAdapter(this, mDrawerMenuContents.getItems(),
                R.layout.drawer_list_item,
                new String[]{DrawerMenuContents.FIELD_TITLE}, new int[]{R.id.drawer_item_title}) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                int color = unselectedColor;
                if (position == selectedPosition) {
                    color = selectedColor;
                }
                view.setBackgroundColor(color);
                return view;
            }
        };

        mDrawerList.setOnItemClickListener((parent, view, position, id) -> {
            if (position != selectedPosition) {
                mDrawerList.setItemChecked(position, true);
                itemToOpen = position;
                itemTapped = true;
            }
            mDrawerLayout.closeDrawers();
        });
        mDrawerList.setAdapter(adapter);
    }

    protected void updateDrawerPosition() {
        final int selectedPosition = mDrawerMenuContents.getPosition(this.getClass());
        updateSelected(selectedPosition);
    }

    private void updateSelected(int position) {
        currentPos = position;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Whenever the fragment back stack changes, we may need to update the
        // action bar toggle: only top level screens show the hamburger-like icon, inner
        // screens - either Activities or fragments - show the "Up" icon instead.
        getSupportFragmentManager().addOnBackStackChangedListener(onBackStackChangedListener);
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
            Timber.d("sent home");
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
            updateDrawerPosition();
            super.onBackPressed();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getSupportFragmentManager().removeOnBackStackChangedListener(onBackStackChangedListener);
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
