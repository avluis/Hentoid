package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.abstracts.BaseFragment.BackInterface;
import me.devsaki.hentoid.fragments.queue.QueueFragment;
import me.devsaki.hentoid.util.Preferences;

/**
 * Handles hosting of QueueFragment for a single screen.
 */
public class QueueActivity extends BaseActivity implements BackInterface {

    private BaseFragment baseFragment;
    private Fragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager manager = getSupportFragmentManager();
        if (fragment == null) {
            fragment = new QueueFragment();

            manager.beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    public void onBackPressed() {
        if (baseFragment == null || baseFragment.onBackPressed()) {
            // Fragment did not consume onBackPressed.
            super.onBackPressed();
        }
    }

    @Override
    public void addBackInterface(BaseFragment fragment) {
        this.baseFragment = fragment;
    }
}
