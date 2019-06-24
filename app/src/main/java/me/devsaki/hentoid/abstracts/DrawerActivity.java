package me.devsaki.hentoid.abstracts;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.SelectableAdapter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.DrawerItem;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewholders.DrawerItemFlex;

import static android.support.v7.widget.DividerItemDecoration.VERTICAL;

/**
 * Created by avluis on 4/11/2016.
 * Abstract activity with toolbar and navigation drawer.
 * Needs to be extended by any activity that wants to be shown as a top level activity.
 * Subclasses must have these layout elements:
 * - {@link android.support.v4.widget.DrawerLayout} with id 'drawer_layout'.
 * - {@link android.widget.ListView} with id 'drawer_list'.
 */
public abstract class DrawerActivity extends BaseActivity {

    private DrawerLayout mDrawerLayout;
    private FlexibleAdapter<DrawerItemFlex> drawerAdapter;

    protected void initializeNavigationDrawer(Toolbar toolbar) {
        mDrawerLayout = findViewById(R.id.drawer_layout);

        toolbar.setNavigationIcon(R.drawable.ic_drawer);
        toolbar.setNavigationOnClickListener(v -> mDrawerLayout.openDrawer(GravityCompat.START));

        List<DrawerItemFlex> drawerItems = Stream.of(DrawerItem.values())
                .map(DrawerItemFlex::new)
                .toList();

        drawerAdapter = new FlexibleAdapter<>(null);
        drawerAdapter.setMode(SelectableAdapter.Mode.SINGLE);
        drawerAdapter.addListener((FlexibleAdapter.OnItemClickListener) this::onDrawerItemClick);
        drawerAdapter.addItems(0, drawerItems);
//        drawerAdapter.addSelection(DrawerItem.HOME.ordinal());

        DividerItemDecoration divider = new DividerItemDecoration(this, VERTICAL);

        Drawable d = ContextCompat.getDrawable(getBaseContext(), R.drawable.line_divider);
        if (d != null) divider.setDrawable(d);

        RecyclerView recyclerView = findViewById(R.id.drawer_list);
        recyclerView.setAdapter(drawerAdapter);
        recyclerView.addItemDecoration(divider);

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            mDrawerLayout.openDrawer(GravityCompat.START);
            Preferences.setIsFirstRunProcessComplete(true);
        }
    }

    private boolean onDrawerItemClick(View view, int position) {
        mDrawerLayout.closeDrawers();

        Class activityClass = DrawerItem.values()[position].activityClass;
        Intent intent = new Intent(this, activityClass);
        Bundle bundle = ActivityOptionsCompat
                .makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
                .toBundle();
        ContextCompat.startActivity(this, intent, bundle);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

        return true;
    }

    private void showFlagAboutItem() {
        int aboutItemPos = DrawerItem.ABOUT.ordinal();
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
