package me.devsaki.hentoid.activities.settings

import android.R
import android.os.Bundle
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.fragments.pin.LockSettingsFragment


class SettingsPinActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.content, LockSettingsFragment())
                .commit()
        }
    }
}