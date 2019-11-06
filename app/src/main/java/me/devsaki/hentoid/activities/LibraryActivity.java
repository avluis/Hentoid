package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.fragments.library.LibraryFragment;
import me.devsaki.hentoid.util.Preferences;

public class LibraryActivity extends BaseActivity implements BaseFragment.BackInterface {

    private DrawerLayout drawerLayout;
    private BaseFragment baseFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hentoid);

        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentById(R.id.fragment_library);

        if (fragment == null) {
            fragment = new LibraryFragment();

            manager.beginTransaction()
                    .add(R.id.fragment_library, fragment)
                    .commit();
        }

        drawerLayout = findViewById(R.id.drawer_layout);

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            drawerLayout.openDrawer(GravityCompat.START);
            Preferences.setIsFirstRunProcessComplete(true);
        }

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers();
            return;
        }

        if (baseFragment == null || baseFragment.onBackPressed()) {
            // Fragment did not consume onBackPressed.
            super.onBackPressed();
        }
    }

    @Override
    public void addBackInterface(BaseFragment fragment) {
        this.baseFragment = fragment;
    }

    public void onNavigationDrawerItemClicked() {
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    public void onNavigationDrawerClicked() {
        drawerLayout.openDrawer(GravityCompat.START);
    }
}
