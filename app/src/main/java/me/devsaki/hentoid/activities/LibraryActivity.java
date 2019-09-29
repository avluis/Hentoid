package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.fragments.library.LibraryFragment;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;

public class LibraryActivity extends BaseActivity implements BaseFragment.BackInterface {

    private DrawerLayout drawerLayout;
    private LibraryViewModel viewModel;
    private BaseFragment baseFragment;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hentoid);

        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentById(R.id.content_frame);

        if (fragment == null) {
            fragment = new LibraryFragment();
            String tag = fragment.getClass().getSimpleName();

            manager.beginTransaction()
                    .add(R.id.content_frame, fragment, tag)
                    .commit();
        }

//        viewModel = ViewModelProviders.of(this).get(LibraryViewModel.class);
//        viewModel.getLibrary().observe(this, this::onLibraryChanged);

        drawerLayout = findViewById(R.id.drawer_layout);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_drawer);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            drawerLayout.openDrawer(GravityCompat.START);
            Preferences.setIsFirstRunProcessComplete(true);
        }

        setTitle("");

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    /*
    private void onLibraryChanged(List<Content> library) {
        if (null == library) { // No library has been loaded yet (1st run with this instance)
            viewModel.loadFromSearchParams(searchParams);
        }
    }
     */

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
}
