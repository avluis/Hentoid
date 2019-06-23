package me.devsaki.hentoid.abstracts;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.SelectableAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.enums.DrawerItem;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewholders.DrawerItemFlex;
import timber.log.Timber;

/**
 * Created by avluis on 4/11/2016.
 * Abstract activity with toolbar and navigation drawer.
 * Needs to be extended by any activity that wants to be shown as a top level activity.
 * Subclasses must have these layout elements:
 * - {@link android.support.v4.widget.DrawerLayout} with id 'drawer_layout'.
 * - {@link android.widget.ListView} with id 'drawer_list'.
 */
public abstract class DrawerActivity extends BaseActivity implements DrawerLayout.DrawerListener {

    private DrawerLayout mDrawerLayout;
    private FlexibleAdapter<DrawerItemFlex> drawerAdapter;
    private ActionBarDrawerToggle mDrawerToggle;
    private int itemToOpen = -1;
    private int currentPos = -1;
    private boolean itemTapped;

    protected final String getToolbarTitle() {
        return getString(R.string.title_activity_downloads);
    }

    protected void initializeNavigationDrawer(Toolbar toolbar) {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        RecyclerView recyclerView = findViewById(R.id.drawer_list);

        drawerAdapter = new FlexibleAdapter<>(null);
        drawerAdapter.setMode(SelectableAdapter.Mode.SINGLE);
        recyclerView.setAdapter(drawerAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));

        DividerItemDecoration divider = new
                DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        Drawable d = ContextCompat.getDrawable(getBaseContext(), R.drawable.line_divider);
        if (d != null) divider.setDrawable(d);
        recyclerView.addItemDecoration(divider);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                toolbar,
                R.string.drawer_open,
                R.string.drawer_close
        );
        mDrawerLayout.addDrawerListener(this);
        populateDrawerItems();
        updateDrawerToggle();

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            mDrawerLayout.openDrawer(GravityCompat.START);
            Preferences.setIsFirstRunProcessComplete(true);
        }
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
        if (mDrawerToggle != null) mDrawerToggle.onDrawerSlide(drawerView, slideOffset);
    }

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {
        if (mDrawerToggle != null) mDrawerToggle.onDrawerOpened(drawerView);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getToolbarTitle());
        }
    }

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {
        if (mDrawerToggle != null) mDrawerToggle.onDrawerClosed(drawerView);

        if (itemToOpen >= 0 && itemTapped) {
            itemTapped = false;
            currentPos = itemToOpen;
            Class activityClass = DrawerItem.getActivity(itemToOpen);
            Intent intent = new Intent(this, activityClass);
            Bundle bundle = ActivityOptionsCompat
                    .makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
                    .toBundle();
            ContextCompat.startActivity(this, intent, bundle);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        if (mDrawerToggle != null) mDrawerToggle.onDrawerStateChanged(newState);
    }

    private boolean onDrawerItemClick(View view, int position) {
        if (position != currentPos) {
            itemToOpen = position;
            itemTapped = true;
            drawerAdapter.addSelection(position);
            mDrawerLayout.closeDrawers();
            return true;
        }
        return false;
    }

    private void populateDrawerItems() {
        for (DrawerItem item : DrawerItem.values()) drawerAdapter.addItem(new DrawerItemFlex(item));

        FlexibleAdapter.OnItemClickListener clickListenerAdapter = this::onDrawerItemClick;
        drawerAdapter.addListener(clickListenerAdapter);

        updateDrawerPosition();
//        drawerAdapter.toggleSelection(DrawerItem.getPosition(this.getClass())); // Init-time
    }

    protected void updateDrawerPosition() {
        final int selectedPosition = DrawerItem.getPosition(this.getClass());
        updateSelected(selectedPosition);
    }

    private void updateSelected(int position) {
        if (currentPos != position) {
            drawerAdapter.toggleSelection(position);
            drawerAdapter.notifyItemChanged(currentPos);
            drawerAdapter.notifyItemChanged(position);
            currentPos = position;
        }
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
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateDrawerToggle);
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
        // Pass the event to {@link android.support.v7.app.ActionBarDrawerToggle}, if it returns
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
        getSupportFragmentManager().removeOnBackStackChangedListener(this::updateDrawerToggle);
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

    private void showFlagAboutItem()
    {
        int aboutItemPos = DrawerItem.getPosition(AboutActivity.class);
        DrawerItemFlex item = drawerAdapter.getItem(aboutItemPos);
        if (item != null) {
            item.setFlag(true);
            drawerAdapter.notifyItemChanged(aboutItemPos);
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateEvent(UpdateEvent event) {
        if (event.hasNewVersion) showFlagAboutItem();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
    }
}
