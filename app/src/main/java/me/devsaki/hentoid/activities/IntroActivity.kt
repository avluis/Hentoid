package me.devsaki.hentoid.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.appintro.AppIntro2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.intro.EndIntroFragment
import me.devsaki.hentoid.fragments.intro.ImportIntroFragment
import me.devsaki.hentoid.fragments.intro.PermissionIntroFragment
import me.devsaki.hentoid.fragments.intro.SourcesIntroFragment
import me.devsaki.hentoid.fragments.intro.ThemeIntroFragment
import me.devsaki.hentoid.fragments.intro.WelcomeIntroFragment
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.applyTheme

/**
 * Welcome (Intro Slide) Activity
 * Presents required permissions, then calls the proper activity to:
 * Set storage directory and library import
 */
class IntroActivity : AppIntro2() {
    private var autoEndHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addSlide(WelcomeIntroFragment())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) addSlide(PermissionIntroFragment())
        addSlide(ImportIntroFragment())
        addSlide(ThemeIntroFragment())
        addSlide(SourcesIntroFragment())
        addSlide(EndIntroFragment())
        setTitle(R.string.app_name)
        isWizardMode = true // Replaces skip button with back button
        isSystemBackButtonLocked = true
        isIndicatorEnabled = true
        setSwipeLock(true)

        // Set default color theme, in case user skips the slide
        Preferences.setColorTheme(Preferences.Default.COLOR_THEME)
        backgroundDrawable = ContextCompat.getDrawable(this, R.drawable.bg_pin_dialog)
    }

    override fun onSlideChanged(oldFragment: Fragment?, newFragment: Fragment?) {
        super.onSlideChanged(oldFragment, newFragment)
        if (oldFragment is SourcesIntroFragment) setSourcePrefs(oldFragment.getSelection())

        setSwipeLock(true) // Is set to false in AppIntroBase.onRequestPermissionsResult :facepalm:

        if (newFragment is ImportIntroFragment) {
            // Skip Import fragment when in browser mode
            if (Preferences.isBrowserMode()) {
                if (oldFragment is PermissionIntroFragment) {
                    lifecycleScope.launch {
                        delay(75)
                        goToNextSlide(false)
                    }
                } else {
                    if (oldFragment is ThemeIntroFragment) {
                        lifecycleScope.launch {
                            delay(75)
                            goToPreviousSlide()
                        }
                    }
                }
            } else {
                // Reset folder selection
                newFragment.reset()
                isButtonsEnabled = false
            }
        } else {
            isButtonsEnabled = true
        }

        // Auto-validate the last screen after 2 seconds of inactivity
        if (newFragment is EndIntroFragment) {
            autoEndHandler = Handler(Looper.getMainLooper())
            autoEndHandler?.postDelayed({ onDonePressed(newFragment) }, 2000)
        } else { // Stop auto-validate if user goes back
            autoEndHandler?.removeCallbacksAndMessages(null)
        }
    }

    fun nextStep() {
        goToNextSlide(false)
    }

    fun setThemePrefs(pref: Int) {
        Preferences.setColorTheme(pref)
        applyTheme()
        goToNextSlide(false)
    }

    private fun setSourcePrefs(sources: List<Site>) {
        Settings.activeSites = sources
    }

    // Validation of the final step of the wizard
    @Suppress("DEPRECATION")
    override fun onDonePressed(currentFragment: Fragment?) {
        autoEndHandler!!.removeCallbacksAndMessages(null)
        Preferences.setIsFirstRun(false)
        // Need to do that to avoid a useless reloading of the library screen upon loading prefs for the first time
        Settings.libraryDisplay = Settings.Value.LIBRARY_DISPLAY_DEFAULT

        // Load library screen
        val intent = Intent(this, LibraryActivity::class.java)
        intent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TOP
                or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)
        } else {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        finish()
    }
}