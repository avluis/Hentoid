package me.devsaki.hentoid.activities

import android.os.Bundle
import me.devsaki.hentoid.fragments.pin.ActivatedLockPreferenceFragment


class PinPreferenceActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, ActivatedLockPreferenceFragment())
                .commit()
        }
    }
}