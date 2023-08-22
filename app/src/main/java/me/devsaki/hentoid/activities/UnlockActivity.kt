package me.devsaki.hentoid.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.core.HentoidApp.Companion.isUnlocked
import me.devsaki.hentoid.core.HentoidApp.Companion.setUnlocked
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.pin.UnlockPinDialogFragment
import me.devsaki.hentoid.util.Preferences
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
        if (Preferences.getAppLockPin().isEmpty() || isUnlocked()) {
            goToNextActivity()
            return
        }
        if (savedInstanceState == null) {
            UnlockPinDialogFragment.invoke(supportFragmentManager)
        }
    }

    override fun onResume() {
        super.onResume()
        UnlockPinDialogFragment.invoke(supportFragmentManager)
    }

    override fun onPinSuccess() {
        setUnlocked(true)
        goToNextActivity()
    }

    @Deprecated("Deprecated in Java")
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

        /**
         * Creates an intent that launches this activity before launching the given site's
         * wrapped web activity intent
         *
         *
         * NB : Specific implementation mandatory to create shortcuts because shortcut intents bundles
         * are `PersistableBundle`s that cannot only store basic values (no Intent objects)
         *
         * @param context used for creating the return intent
         * @param site    Site whose web activity to launch after the PIN is unlocked
         * @return intent that launches this activity which leads to the `site`'s web activity
         */
        fun wrapIntent(context: Context, site: Site): Intent {
            val intent = Intent(context, UnlockActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra(EXTRA_SITE_CODE, site.code)
            return intent
        }
    }
}