package me.devsaki.hentoid.activities.prefs

import android.R
import android.os.Bundle
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.fragments.pin.LockPreferenceFragment


class PreferencesPinActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.content, LockPreferenceFragment())
                .commit()
        }
    }
}