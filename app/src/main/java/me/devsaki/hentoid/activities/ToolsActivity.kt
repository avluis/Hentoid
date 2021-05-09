package me.devsaki.hentoid.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.commit
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import me.devsaki.hentoid.R
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.tools.ToolsFragment
import me.devsaki.hentoid.util.FileHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ToolsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.commit {
            replace(android.R.id.content, ToolsFragment.newInstance())
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        // Replace the default "preferences" toolbar title
        supportActionBar?.title = getText(R.string.tools_title)
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
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
        if (ProcessEvent.EventType.COMPLETE == event.eventType && event.logFile != null) {
            val contentView = findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(contentView, R.string.task_done, BaseTransientBottomBar.LENGTH_LONG)
            snackbar.setAction("READ LOG") { FileHelper.openFile(this, event.logFile) }
            snackbar.show()
        }
    }
}