package me.devsaki.hentoid.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.commit
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.PrefsActivityBundle
import me.devsaki.hentoid.events.ImportEvent
import me.devsaki.hentoid.fragments.PreferenceFragment
import me.devsaki.hentoid.util.FileHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class PrefsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var rootKey: String? = null
        if (isViewerPrefs()) rootKey = "viewer"
        val fragment = PreferenceFragment.newInstance(rootKey)

        supportFragmentManager.commit {
            replace(android.R.id.content, fragment)
        }

        EventBus.getDefault().register(this)
    }

    private fun isViewerPrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = PrefsActivityBundle.Parser(intent.extras!!)
            parser.isViewerPrefs
        } else false
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onImportEventComplete(event: ImportEvent) {
        if (ImportEvent.EV_COMPLETE == event.eventType && event.logFile != null) {
            val contentView = findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(contentView, R.string.task_done, BaseTransientBottomBar.LENGTH_LONG)
            snackbar.setAction("READ LOG") { FileHelper.openFile(this, event.logFile) }
            snackbar.show()
        }
    }
}