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
import me.devsaki.hentoid.util.file.openFile
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ToolsActivity : BaseActivity() {
    enum class MassOperation {
        DELETE, STREAM
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.commit {
            replace(android.R.id.content, ToolsFragment())
        }
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
        // Replace the default "preferences" toolbar title
        supportActionBar?.title = getText(R.string.tools_title)
    }

    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onImportEventComplete(event: ProcessEvent) {
        if (ProcessEvent.Type.COMPLETE == event.eventType
            && (event.processId == R.id.import_external || event.processId == R.id.import_primary)
        ) {
            event.logFile?.let { logFile ->
                val contentView = findViewById<View>(android.R.id.content)
                val snackbar =
                    Snackbar.make(
                        contentView,
                        R.string.task_done,
                        BaseTransientBottomBar.LENGTH_LONG
                    )
                snackbar.setAction(R.string.read_log) { openFile(this, logFile) }
                snackbar.show()
            }
        }
    }
}