package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.queue.QueueFragment;
import me.devsaki.hentoid.util.Preferences;

/**
 * Handles hosting of QueueFragment for a single screen.
 */
public class QueueActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentById(R.id.fragment_queue);
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
}
