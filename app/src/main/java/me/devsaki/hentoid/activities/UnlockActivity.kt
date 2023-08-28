package me.devsaki.hentoid.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import dev.skomlach.biometric.compat.BiometricAuthRequest
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BiometricsHelper
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.core.HentoidApp.Companion.isUnlocked
import me.devsaki.hentoid.core.HentoidApp.Companion.setUnlocked
import me.devsaki.hentoid.core.startBiometric
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.pin.UnlockPinDialogFragment
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.ThemeHelper

/**
 * This activity asks for a 4 digit pin if it is set and then transitions to another activity
 */
class UnlockActivity : AppCompatActivity(), UnlockPinDialogFragment.Parent {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)
        if (Preferences.getAppLockPin().length != 4) {
            Preferences.setAppLockPin("")
        }
        if (0 == Settings.lockType || isUnlocked()) {
            goToNextActivity()
            return
        }
        if (savedInstanceState == null) {
            if (1 == Settings.lockType) UnlockPinDialogFragment.invoke(supportFragmentManager)
            else if (2 == Settings.lockType) {
                val bestBM = BiometricsHelper.detectBestBiometric()
                if (bestBM != null) {
                    startBiometric(
                        BiometricAuthRequest(bestBM.api, bestBM.type), false,
                        { b -> if (b) onUnlockSuccess() else finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (1 == Settings.lockType) UnlockPinDialogFragment.invoke(supportFragmentManager)
    }

    override fun onUnlockSuccess() {
        setUnlocked(true)
        goToNextActivity()
    }

    override fun onBackPressed() {
        // We don't want the back button to remove the unlock screen displayed upon app restore
        moveTaskToBack(true)
    }

    private fun goToNextActivity() {
        val parcelableExtra = intent.getParcelableExtra<Parcelable>(EXTRA_INTENT)
        val targetIntent: Intent
        if (parcelableExtra != null) targetIntent = parcelableExtra as Intent else {
            val siteCode = intent.getIntExtra(EXTRA_SITE_CODE, Site.NONE.code)
            if (siteCode == Site.NONE.code) {
                finish()
                return
            }
            val c: Class<*> = Content.getWebActivityClass(Site.searchByCode(siteCode.toLong()))
            targetIntent = Intent(getInstance(), c)
            targetIntent.action = Intent.ACTION_VIEW
        }
        startActivity(targetIntent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    companion object {
        const val EXTRA_INTENT = "intent"
        const val EXTRA_SITE_CODE = "siteCode"

        /**
         * Creates an intent that launches this activity before launching the given wrapped intent
         *
         * @param context           used for creating the return intent
         * @param destinationIntent intent that refers to the next activity
         * @return intent that launches this activity which leads to another activity referred to by
         * `destinationIntent`
         */
        fun wrapIntent(context: Context, destinationIntent: Intent): Intent {
            val intent = Intent(context, UnlockActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra(EXTRA_INTENT, destinationIntent)
            return intent
        }
    }
}