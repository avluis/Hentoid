package me.devsaki.hentoid.fragments.intro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.include_import_steps.*
import kotlinx.android.synthetic.main.intro_slide_04.*
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.IntroActivity
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.FileHelper
import me.devsaki.hentoid.util.ImportHelper
import me.devsaki.hentoid.util.Preferences
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

// TODO: 6/23/2018 implement ISlidePolicy to force user to select a storage option
class ImportIntroFragment : Fragment(R.layout.intro_slide_04) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        import_step1_button.setOnClickListener { ImportHelper.openFolderPicker(this, false) }
        import_step1_button.visibility = View.VISIBLE
    }

    // Callback from the directory chooser
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        @ImportHelper.Result val result = ImportHelper.processPickerResult(activity as Activity, requestCode, resultCode, data)
        when (result) {
            ImportHelper.Result.OK_EMPTY_FOLDER -> nextStep()
            ImportHelper.Result.OK_LIBRARY_DETECTED -> updateOnSelectFolder() // Import service is already launched by the Helper; nothing else to do
            ImportHelper.Result.OK_LIBRARY_DETECTED_ASK -> {
                updateOnSelectFolder()
                ImportHelper.showExistingLibraryDialog(requireContext()) { onCancelExistingLibraryDialog() }
            }
            ImportHelper.Result.CANCELED -> Snackbar.make(main, R.string.import_canceled, BaseTransientBottomBar.LENGTH_LONG).show()
            ImportHelper.Result.INVALID_FOLDER -> Snackbar.make(main, R.string.import_invalid, BaseTransientBottomBar.LENGTH_LONG).show()
            ImportHelper.Result.CREATE_FAIL -> Snackbar.make(main, R.string.import_create_fail, BaseTransientBottomBar.LENGTH_LONG).show()
            ImportHelper.Result.OTHER -> Snackbar.make(main, R.string.import_other, BaseTransientBottomBar.LENGTH_LONG).show()
        }
    }

    private fun updateOnSelectFolder() {
        import_step1_button.visibility = View.INVISIBLE
        import_step1_folder.text = FileHelper.getFullPathFromTreeUri(requireContext(), Uri.parse(Preferences.getStorageUri()), true)
        import_step1_check.visibility = View.VISIBLE
        import_step2.visibility = View.VISIBLE
        import_step2_bar.isIndeterminate = true
    }

    private fun onCancelExistingLibraryDialog() {
        // Revert back to initial state where only the "Select folder" button is visible
        import_step1_button.visibility = View.VISIBLE
        import_step1_folder.text = ""
        import_step1_check.visibility = View.INVISIBLE
        import_step2.visibility = View.INVISIBLE
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMigrationEvent(event: ProcessEvent) {
        val progressBar: ProgressBar = if (2 == event.step) import_step2_bar else import_step3_bar
        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            if (event.elementsTotal > -1) {
                progressBar.isIndeterminate = false
                progressBar.max = event.elementsTotal
                progressBar.progress = event.elementsOK + event.elementsKO
            } else progressBar.isIndeterminate = true
            if (3 == event.step) {
                import_step2_check.visibility = View.VISIBLE
                import_step3.visibility = View.VISIBLE
                import_step3_text.text = resources.getString(R.string.api29_migration_step3, event.elementsKO + event.elementsOK, event.elementsTotal)
            } else if (4 == event.step) {
                import_step3_check.visibility = View.VISIBLE
                import_step4.visibility = View.VISIBLE
            }
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
            if (2 == event.step) {
                import_step2_check.visibility = View.VISIBLE
                import_step3.visibility = View.VISIBLE
            } else if (3 == event.step) {
                import_step3_text.text = resources.getString(R.string.api29_migration_step3, event.elementsTotal, event.elementsTotal)
                import_step3_check.visibility = View.VISIBLE
                import_step4.visibility = View.VISIBLE
            } else if (4 == event.step) {
                import_step4_check.visibility = View.VISIBLE
                nextStep()
            }
        }
    }

    private fun nextStep() {
        val parentActivity = context as IntroActivity
        parentActivity.nextStep()
    }
}