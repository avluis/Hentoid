package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;

public class LibraryActivity extends BaseActivity {

    private DrawerLayout drawerLayout;

    private OnBackPressedCallback callback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hentoid);
        drawerLayout = findViewById(R.id.drawer_layout);

        callback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                closeNavigationDrawer();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            openNavigationDrawer();
            Preferences.setIsFirstRunProcessComplete(true);
        }

        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        new ViewModelProvider(this, vmFactory).get(LibraryViewModel.class);

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    public void closeNavigationDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
        callback.setEnabled(false);
    }

    public void openNavigationDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
        callback.setEnabled(true);
    }
}
