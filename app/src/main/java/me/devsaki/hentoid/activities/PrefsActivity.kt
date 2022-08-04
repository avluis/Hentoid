package me.devsaki.hentoid.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.commit
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.PrefsBundle
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.preferences.PreferencesFragment
import me.devsaki.hentoid.util.file.FileHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

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

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onStop()
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
            onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onImportEventComplete(event: ProcessEvent) {
        if (ProcessEvent.EventType.COMPLETE == event.eventType
            && event.logFile != null
            && (event.processId == R.id.import_external || event.processId == R.id.import_primary)
        ) {
            val contentView = findViewById<View>(android.R.id.content)
            val snackbar =
                Snackbar.make(contentView, R.string.task_done, BaseTransientBottomBar.LENGTH_LONG)
            snackbar.setAction(R.string.read_log) { FileHelper.openFile(this, event.logFile) }
            snackbar.show()
        }
    }
}