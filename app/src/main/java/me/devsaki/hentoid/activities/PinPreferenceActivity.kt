package me.devsaki.hentoid.activities

import android.R
import android.os.Bundle
import me.devsaki.hentoid.fragments.pin.ActivatedPinPreferenceFragment
import me.devsaki.hentoid.fragments.pin.DeactivatedPinPreferenceFragment
import me.devsaki.hentoid.util.Preferences


class PinPreferenceActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val isLockOn = Preferences.getAppLockPin().isNotEmpty()
            val fragment =
                if (isLockOn) ActivatedPinPreferenceFragment() else DeactivatedPinPreferenceFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.content, fragment)
                .commit()
        }
    }
}