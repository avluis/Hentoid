package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.devsaki.hentoid.core.HentoidApp.Companion.setLockInstant
import me.devsaki.hentoid.core.HentoidApp.Companion.setUnlocked
import me.devsaki.hentoid.util.Settings
import timber.log.Timber
import java.time.Instant

class PowerEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (null == intent) return

        val action = intent.action
        if (!isInitialStickyBroadcast && Intent.ACTION_SCREEN_OFF == action) {
            if (Settings.lockType > 0 && Settings.lockOnAppRestore) {
                Timber.d("Set lock instant")
                setUnlocked(false)
                // Force unlock screen to appear by recording a past timestamp
                setLockInstant(Instant.now().toEpochMilli() - 120 * 1000)
            }
        }
    }
}