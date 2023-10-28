package me.devsaki.hentoid.activities

import android.content.Intent
import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import me.devsaki.hentoid.core.HentoidApp.Companion.getLockInstant
import me.devsaki.hentoid.core.HentoidApp.Companion.isUnlocked
import me.devsaki.hentoid.core.HentoidApp.Companion.setUnlocked
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.util.LocaleHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings.lockType
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.ToastHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.time.Instant

abstract class BaseActivity : AppCompatActivity {

    constructor() : super()

    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Change locale if set manually
        LocaleHelper.convertLocaleToEnglish(this)
        ThemeHelper.applyTheme(this)
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onRestart() {
        // If locked and PIN enabled, display the PIN
        if (!isUnlocked() && lockType > 0 && Preferences.isLockOnAppRestore()) {
            // Evaluate if any set delay has passed; if so, the app gets locked
            val lockDelaySec = when (Preferences.getLockTimer()) {
                Preferences.Constant.LOCK_TIMER_10S -> 10
                Preferences.Constant.LOCK_TIMER_30S -> 30
                Preferences.Constant.LOCK_TIMER_1M -> 60
                Preferences.Constant.LOCK_TIMER_2M -> 120
                else -> 0
            }
            if ((Instant.now().toEpochMilli() - getLockInstant()) / 1000 > lockDelaySec) {
                val intent = Intent(this, UnlockActivity::class.java)
                startActivity(intent)
            } else {
                setUnlocked(true) // Auto-unlock when we're back to the app under the delay
            }
        }
        super.onRestart()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    open fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.RC_ALL || event.type != CommunicationEvent.EV_BROADCAST || event.message.isEmpty()) return
        // Make sure current activity is active (=eligible to display that toast)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        ToastHelper.toast(event.message)
    }
}