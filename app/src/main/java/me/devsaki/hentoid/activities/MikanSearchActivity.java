package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.abstracts.BaseFragment.BackInterface;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.fragments.downloads.EndlessFragment;
import me.devsaki.hentoid.fragments.downloads.PagerFragment;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Created by Robb on 06/2018
 */
public class MikanSearchActivity extends BaseActivity implements BackInterface {

    private BaseFragment baseFragment;
    private Fragment fragment;

    private DownloadsFragment buildFragment() {
        if (Preferences.getEndlessScroll()) {
            Timber.d("getFragment: EndlessFragment.");
            return new EndlessFragment();
        } else {
            Timber.d("getFragment: PagerFragment.");
            return new PagerFragment();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mikan);

        FragmentManager manager = getSupportFragmentManager();
        fragment = manager.findFragmentById(R.id.content_frame);

        if (fragment == null) {
            fragment = buildFragment();
            fragment.setArguments(getCreationArguments());

            manager.beginTransaction()
                    .add(R.id.content_frame, fragment, getFragmentTag())
                    .commit();
        }
    }

    private String getFragmentTag() {
        if (fragment != null) {
            return fragment.getClass().getSimpleName();
        }
        return null;
    }

    private Bundle getCreationArguments()
    {
        Bundle result = new Bundle();
        result.putInt("mode", DownloadsFragment.MODE_MIKAN);
        return result;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (baseFragment == null || baseFragment.onBackPressed()) {
            // Fragment did not consume onBackPressed.
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                Timber.d("Permissions granted.");
                // In order to apply changes, activity/task restart is needed
                Helper.doRestart(this);
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                Timber.d("Permissions denied.");
            }
        } else {
            // Permissions cannot be set, either via policy or forced by user.
            finish();
        }
    }

    @Override
    public void addBackInterface(BaseFragment fragment) {
        this.baseFragment = fragment;
    }
}
