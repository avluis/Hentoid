package me.devsaki.hentoid.activities

import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.commit
import me.devsaki.hentoid.activities.bundles.PrefsBundle
import me.devsaki.hentoid.fragments.preferences.PreferencesFragment

class PrefsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var rootKey: String? = null
        when {
            isViewerPrefs() -> rootKey = "viewer"
            isBrowserPrefs() -> rootKey = "browser"
            isDownloaderPrefs() -> rootKey = "downloader"
            isStoragePrefs() -> rootKey = "storage"
        }
        val fragment = PreferencesFragment.newInstance(rootKey)

        supportFragmentManager.commit {
            replace(android.R.id.content, fragment)
        }
    }

    private fun isViewerPrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = PrefsBundle(intent.extras!!)
            parser.isViewerPrefs
        } else false
    }

    private fun isBrowserPrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = PrefsBundle(intent.extras!!)
            parser.isBrowserPrefs
        } else false
    }

    private fun isDownloaderPrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = PrefsBundle(intent.extras!!)
            parser.isDownloaderPrefs
        } else false
    }

    private fun isStoragePrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = PrefsBundle(intent.extras!!)
            parser.isStoragePrefs
        } else false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}