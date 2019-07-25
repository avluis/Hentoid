package me.devsaki.hentoid.activities;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.pin.ActivatedPinPreferenceFragment;
import me.devsaki.hentoid.fragments.pin.DeactivatedPinPreferenceFragment;
import me.devsaki.hentoid.util.Preferences;

public class PinPreferenceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_preference);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            boolean isLockOn = !Preferences.getAppLockPin().isEmpty();
            Fragment fragment = isLockOn ? new ActivatedPinPreferenceFragment() : new DeactivatedPinPreferenceFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.frame_fragment, fragment, null)
                    .commit();
        }
    }
}
