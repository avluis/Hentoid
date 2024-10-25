package me.devsaki.hentoid.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.AppStartup.appKilled
import me.devsaki.hentoid.core.AppStartup.initApp
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.getRandomInt
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Displays a Splash while starting up.
 * <p>
 * Nothing but a splash/activity selection should be defined here.
 */
class SplashActivity : BaseActivity() {
    private lateinit var mainPb: ProgressBar
    private lateinit var secondaryPb: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        appKilled = false
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        //ThemeHelper.applyTheme(this); <-- this won't help; the starting activity is shown with the default theme, aka Light
        mainPb = findViewById(R.id.progress_main)
        secondaryPb = findViewById(R.id.progress_secondary)
        val quote = findViewById<TextView>(R.id.quote)
        val quotes = resources.getStringArray(R.array.splash_quotes)
        val random = getRandomInt(quotes.size)
        quote.text = quotes[random]
        Timber.d("Splash / Init")
        initApp(
            this,
            { progress: Float -> displayMainProgress(progress) },
            { progress: Float -> displaySecondaryProgress(progress) }) { followStartupFlow() }
    }

    private fun displayMainProgress(progress: Float) {
        mainPb.progress = (progress * 100).roundToInt()
    }

    private fun displaySecondaryProgress(progress: Float) {
        secondaryPb.progress = (progress * 100).roundToInt()
    }

    private fun followStartupFlow() {
        mainPb.visibility = View.GONE
        secondaryPb.visibility = View.GONE
        Timber.d("Splash / Startup flow initiated")
        if (Settings.isFirstRun) { // Go to intro wizard if it's a first run
            goToActivity(Intent(this, IntroActivity::class.java))
        } else { // Go to the library screen
            goToLibraryActivity()
        }
    }

    /**
     * Close splash screen and go to the given activity
     *
     * @param intent Intent to launch through a new activity
     */
    @Suppress("DEPRECATION")
    private fun goToActivity(intent: Intent) {
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)
        } else {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        finish()
    }

    /**
     * Go to the library screen
     */
    private fun goToLibraryActivity() {
        Timber.d("Splash / Launch library")
        var intent = Intent(this, LibraryActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent = UnlockActivity.wrapIntent(this, intent)
        goToActivity(intent)
    }
}