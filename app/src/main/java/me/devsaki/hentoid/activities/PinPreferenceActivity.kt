package me.devsaki.hentoid.activities

import android.os.Bundle
import me.devsaki.hentoid.fragments.pin.ActivatedLockPreferenceFragment
import me.devsaki.hentoid.fragments.pin.DeactivatedLockPreferenceFragment
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings


class PinPreferenceActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val isLockOn = Settings.lockType > 0
            val fragment =
                if (isLockOn) ActivatedLockPreferenceFragment() else DeactivatedLockPreferenceFragment()
            supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commit()
        }
    }
}