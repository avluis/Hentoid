package me.devsaki.hentoid.activities

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.AppStartup
import me.devsaki.hentoid.core.HentoidApp.Companion.getLockInstant
import me.devsaki.hentoid.core.HentoidApp.Companion.isUnlocked
import me.devsaki.hentoid.core.HentoidApp.Companion.setUnlocked
import me.devsaki.hentoid.core.convertLocaleToEnglish
import me.devsaki.hentoid.database.domains.Achievement
import me.devsaki.hentoid.events.AchievementEvent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.lockType
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.toast
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.time.Instant

abstract class BaseActivity : AppCompatActivity {

    constructor() : super()

    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force a proper restart if app has been killed
        if (AppStartup.appKilled) {
            val intent = Intent(application, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            application.startActivity(intent)
            this.finish()
        }
        // Change locale if set manually
        this.convertLocaleToEnglish()
        applyTheme()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onRestart() {
        // If locked and PIN enabled, display the PIN
        if (!isUnlocked() && lockType > 0 && Settings.lockOnAppRestore) {
            // Evaluate if any set delay has passed; if so, the app gets locked
            val lockDelaySec = when (Settings.lockTimer) {
                Settings.Value.LOCK_TIMER_10S -> 10
                Settings.Value.LOCK_TIMER_30S -> 30
                Settings.Value.LOCK_TIMER_1M -> 60
                Settings.Value.LOCK_TIMER_2M -> 120
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    open fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.ALL || event.type != CommunicationEvent.Type.BROADCAST || event.message.isEmpty()) return
        // Make sure current activity is active (=eligible to display that event)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        toast(event.message)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN_ORDERED)
    open fun onAchievementEvent(event: AchievementEvent) {
        // Make sure current activity is active (=eligible to display that event)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        // TODO handle display of multiples achievements if many triggered at once
        EventBus.getDefault().removeStickyEvent(event)
        val achievement = AchievementsManager.masterdata[event.achievementId] ?: return
        val powerMenu = PowerMenu.Builder(this).addItem(
            PowerMenuItem(
                resources.getString(achievement.title), false, achievement.icon
            )
        ).setAnimation(MenuAnimation.SHOWUP_BOTTOM_RIGHT).setMenuRadius(10f)
            .setLifecycleOwner(this).setBackgroundAlpha(0f)
            .setTextColor(ContextCompat.getColor(this, R.color.white_opacity_87))
            .setTextTypeface(Typeface.DEFAULT)
            .setMenuColor(ContextCompat.getColor(this, R.color.dark_gray))
            .setTextSize(dimensAsDp(this, R.dimen.text_subtitle_1))
            .setWidth(resources.getDimension(R.dimen.popup_menu_width).toInt()).setAutoDismiss(true)
            .build()

        powerMenu.setIconColor(
            ContextCompat.getColor(this, Achievement.colorFromType(achievement.type))
        )
        val root: ViewGroup = findViewById(android.R.id.content)
        powerMenu.showAtLocation(root.rootView, (Gravity.BOTTOM or Gravity.RIGHT), 0, 0)

        // Dismiss after 3s
        Debouncer<Int>(
            this.lifecycleScope, 3000
        ) {
            powerMenu.dismiss()
        }.submit(1)
    }
}