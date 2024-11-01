package me.devsaki.hentoid.fragments.intro

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.IntroActivity
import me.devsaki.hentoid.databinding.IncludeImportStepsBinding
import me.devsaki.hentoid.databinding.IntroSlide04Binding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.PickFolderContract
import me.devsaki.hentoid.util.PickerResult
import me.devsaki.hentoid.util.ProcessFolderResult
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.setAndScanPrimaryFolder
import me.devsaki.hentoid.util.showExistingLibraryDialog
import me.devsaki.hentoid.workers.STEP_2_BOOK_FOLDERS
import me.devsaki.hentoid.workers.STEP_3_BOOKS
import me.devsaki.hentoid.workers.STEP_4_QUEUE_FINAL
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ImportIntroFragment : Fragment(R.layout.intro_slide_04) {

    private var binding: IntroSlide04Binding? = null
    private var mergedBinding: IncludeImportStepsBinding? = null

    // True when that screen has been validated once
    private var isDone = false

    private val pickFolder = registerForActivityResult(PickFolderContract()) { res ->
        onFolderPickerResult(res.first, res.second)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = IntroSlide04Binding.inflate(inflater, container, false)
        // We need to manually bind the merged view - it won't work at runtime with the main view alone
        mergedBinding = IncludeImportStepsBinding.bind(binding!!.root)
        return binding!!.root
    }

    /**
     * Reset the screen to its initial state
     * useful when coming back from a later step to switch the selected folder
     */
    fun reset() {
        if (!isDone) return
        Settings.setStorageUri(StorageLocation.PRIMARY_1, "")

        mergedBinding?.apply {
            importStep1Button.visibility = View.VISIBLE
            importStep1Folder.text = ""
            importStep1Check.visibility = View.GONE
            importStep2.visibility = View.GONE
            importStep2Check.visibility = View.GONE
            importStep3.visibility = View.GONE
            importStep3Check.visibility = View.GONE
            importStep4.visibility = View.GONE
            importStep4Check.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        mergedBinding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mergedBinding?.apply {
            importStep1Button.setOnClickListener {
                binding?.skipBtn?.visibility = View.INVISIBLE
                pickFolder.launch(StorageLocation.PRIMARY_1)
            }
            importStep1Button.visibility = View.VISIBLE
        }

        binding?.skipBtn?.setOnClickListener { askSkip() }
    }

    override fun onResume() {
        super.onResume()
        binding?.root?.visibility =
            if (Settings.isBrowserMode) View.INVISIBLE else View.VISIBLE
    }

    private fun onFolderPickerResult(resultCode: PickerResult, treeUri: Uri?) {
        when (resultCode) {
            PickerResult.OK -> {
                if (null == treeUri) return
                binding?.apply {
                    waitTxt.visibility = View.VISIBLE
                    val animation = BlinkAnimation(750, 20)
                    waitTxt.startAnimation(animation)

                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            setAndScanPrimaryFolder(
                                requireContext(),
                                treeUri,
                                StorageLocation.PRIMARY_1,
                                true,
                                null
                            )
                        }
                        waitTxt.clearAnimation()
                        waitTxt.visibility = View.GONE
                        onScanHentoidFolderResult(result.first, result.second)
                    }
                }
            }

            PickerResult.KO_CANCELED -> {
                binding?.apply {
                    Snackbar.make(
                        root,
                        R.string.import_canceled,
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                    skipBtn.visibility = View.VISIBLE
                }
            }

            PickerResult.KO_OTHER, PickerResult.KO_NO_URI -> {
                binding?.apply {
                    Snackbar.make(
                        root,
                        R.string.import_other,
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                    skipBtn.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun onScanHentoidFolderResult(resultCode: ProcessFolderResult, rootUri: String) {
        binding?.apply {
            when (resultCode) {
                ProcessFolderResult.OK_EMPTY_FOLDER -> nextStep()
                ProcessFolderResult.OK_LIBRARY_DETECTED -> { // Import service is already launched by the Helper; nothing else to do
                    updateOnSelectFolder()
                    return
                }

                ProcessFolderResult.OK_LIBRARY_DETECTED_ASK -> {
                    updateOnSelectFolder()
                    showExistingLibraryDialog(
                        requireContext(),
                        StorageLocation.PRIMARY_1,
                        rootUri
                    ) { onCancelExistingLibraryDialog() }
                    return
                }

                ProcessFolderResult.KO_INVALID_FOLDER -> Snackbar.make(
                    root,
                    R.string.import_invalid,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ProcessFolderResult.KO_APP_FOLDER -> Snackbar.make(
                    root,
                    R.string.import_invalid,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ProcessFolderResult.KO_DOWNLOAD_FOLDER -> Snackbar.make(
                    root,
                    R.string.import_download_folder,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ProcessFolderResult.KO_CREATE_FAIL -> Snackbar.make(
                    root,
                    R.string.import_create_fail,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ProcessFolderResult.KO_ALREADY_RUNNING -> Snackbar.make(
                    root,
                    R.string.service_running,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ProcessFolderResult.KO_OTHER -> Snackbar.make(
                    root,
                    R.string.import_other,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                else -> { /* Nothing*/
                }
            }
            skipBtn.visibility = View.VISIBLE
        }
    }

    private fun updateOnSelectFolder() {
        mergedBinding?.apply {
            importStep1Button.visibility = View.INVISIBLE
            importStep1Folder.text = getFullPathFromUri(
                requireContext(),
                Uri.parse(Settings.getStorageUri(StorageLocation.PRIMARY_1))
            )
            importStep1Check.visibility = View.VISIBLE
            importStep2.visibility = View.VISIBLE
            importStep2Bar.isIndeterminate = true
        }
        binding?.skipBtn?.visibility = View.INVISIBLE
    }

    private fun onCancelExistingLibraryDialog() {
        // Revert back to initial state where only the "Select folder" button is visible
        mergedBinding?.apply {
            importStep1Button.visibility = View.VISIBLE
            importStep1Folder.text = ""
            importStep1Check.visibility = View.INVISIBLE
            importStep2.visibility = View.INVISIBLE
        }
        binding?.skipBtn?.visibility = View.VISIBLE
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        processEvent(event)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onStickyProcessEvent(event: ProcessEvent) {
        processEvent(event)
        EventBus.getDefault().removeStickyEvent(event)
    }

    private fun processEvent(event: ProcessEvent) {
        mergedBinding?.apply {
            val progressBar: ProgressBar = when (event.step) {
                STEP_2_BOOK_FOLDERS -> importStep2Bar
                STEP_3_BOOKS -> importStep3Bar
                else -> importStep4Bar
            }

            if (ProcessEvent.Type.PROGRESS == event.eventType) {
                if (event.elementsTotal > -1) {
                    progressBar.isIndeterminate = false
                    progressBar.max = event.elementsTotal
                    progressBar.progress = event.elementsOK + event.elementsKO
                } else progressBar.isIndeterminate = true
                if (STEP_3_BOOKS == event.step) {
                    importStep2Check.visibility = View.VISIBLE
                    importStep3.visibility = View.VISIBLE
                    importStep3Text.text = resources.getString(
                        R.string.refresh_step3,
                        event.elementsKO + event.elementsOK,
                        event.elementsTotal
                    )
                } else if (STEP_4_QUEUE_FINAL == event.step) {
                    importStep3Check.visibility = View.VISIBLE
                    importStep4.visibility = View.VISIBLE
                }
            } else if (ProcessEvent.Type.COMPLETE == event.eventType) {
                when (event.step) {
                    STEP_2_BOOK_FOLDERS -> {
                        importStep2Check.visibility = View.VISIBLE
                        importStep3.visibility = View.VISIBLE
                    }

                    STEP_3_BOOKS -> {
                        importStep3Text.text = resources.getString(
                            R.string.refresh_step3,
                            event.elementsTotal,
                            event.elementsTotal
                        )
                        importStep3Check.visibility = View.VISIBLE
                        importStep4.visibility = View.VISIBLE
                    }

                    STEP_4_QUEUE_FINAL -> {
                        importStep4Check.visibility = View.VISIBLE
                        if (!isDone) nextStep()
                    }
                }
            }
        }
    }

    private fun askSkip() {
        val materialDialog: AlertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.slide_04_skip_title)
            .setMessage(R.string.slide_04_skip_msg)
            .setCancelable(true)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int -> nextStep() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        materialDialog.setIcon(R.drawable.ic_warning)
        materialDialog.show()
    }

    @Synchronized
    private fun nextStep() {
        val parentActivity = requireActivity() as IntroActivity
        parentActivity.nextStep()
        binding?.skipBtn?.visibility = View.VISIBLE
        isDone = true
    }
}