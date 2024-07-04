package me.devsaki.hentoid.fragments.tools

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.databinding.DialogQueueDownloadsImportBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.events.ServiceDestroyedEvent
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel
import me.devsaki.hentoid.util.PickFileContract
import me.devsaki.hentoid.util.PickerResult
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.widget.AddQueueMenu
import me.devsaki.hentoid.workers.DownloadsImportWorker
import me.devsaki.hentoid.workers.data.DownloadsImportData
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.io.InputStreamReader

/**
 * Dialog for the downloads list import feature
 */
class DownloadsImportDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        fun invoke(fragment: Fragment) {
            invoke(fragment, DownloadsImportDialogFragment())
        }

        fun readFile(context: Context, file: DocumentFile): List<String> {
            var lines: List<String>
            getInputStream(context, file).use { inputStream ->
                InputStreamReader(inputStream).use {
                    lines = it.readLines()
                }
            }
            return lines
                .map { s -> s.trim().lowercase() }
                .filterNot { s -> s.isEmpty() }
                .filter { s ->
                    isNumeric(s) ||
                            (s.startsWith("http")
                                    && Site.searchByUrl(s) != Site.NONE
                                    )
                }
        }
    }


    private var binding: DialogQueueDownloadsImportBinding? = null

    private var isServiceGracefulClose = false


    private val pickFile = registerForActivityResult(PickFileContract()) { result ->
        onFilePickerResult(result.first, result.second)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogQueueDownloadsImportBinding.inflate(inflater, container, false)
        EventBus.getDefault().register(this)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        binding?.importSelectFileBtn?.setOnClickListener { pickFile.launch(0) }
    }

    private fun onFilePickerResult(resultCode: PickerResult, uri: Uri) {
        binding?.apply {
            when (resultCode) {
                PickerResult.OK -> {
                    // File selected
                    val doc = DocumentFile.fromSingleUri(requireContext(), uri) ?: return
                    importSelectFileBtn.visibility = View.GONE
                    checkFile(doc)
                }

                PickerResult.KO_CANCELED -> Snackbar.make(
                    root,
                    R.string.import_canceled,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                PickerResult.KO_NO_URI -> Snackbar.make(
                    root,
                    R.string.import_invalid,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                PickerResult.KO_OTHER -> Snackbar.make(
                    root,
                    R.string.import_other,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun checkFile(file: DocumentFile) {
        binding?.apply {
            // Display indeterminate progress bar while file is being deserialized
            importProgressText.setText(R.string.checking_file)
            importProgressBar.isIndeterminate = true
            importProgressText.visibility = View.VISIBLE
            importProgressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                var errorFileName = ""
                val result = withContext(Dispatchers.IO) {
                    try {
                        return@withContext readFile(requireContext(), file)
                    } catch (e: Exception) {
                        Timber.w(e)
                        errorFileName = file.name ?: ""
                    }
                    return@withContext emptyList<String>()
                }
                coroutineScope {
                    if (errorFileName.isEmpty()) onFileRead(result, file)
                    else {
                        importProgressText.text = resources.getString(
                            R.string.import_file_invalid,
                            errorFileName
                        )
                        importProgressBar.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    private fun onFileRead(
        downloadsList: List<String>,
        jsonFile: DocumentFile
    ) {
        binding?.apply {
            importProgressText.visibility = View.GONE
            importProgressBar.visibility = View.GONE
            if (downloadsList.isEmpty()) {
                importFileInvalidText.text =
                    resources.getString(R.string.import_file_invalid, jsonFile.name)
                importFileInvalidText.visibility = View.VISIBLE
            } else {
                importSelectFileBtn.visibility = View.GONE
                importFileInvalidText.visibility = View.GONE
                importFileValidText.text = resources.getQuantityString(
                    R.plurals.import_downloads_found,
                    downloadsList.size,
                    downloadsList.size
                )
                importFileValidText.visibility = View.VISIBLE
                importRunBtn.visibility = View.VISIBLE
                importRunBtn.isEnabled = true
                importRunBtn.setOnClickListener {
                    askRun(jsonFile.uri)
                }
            }
        }
    }

    private fun askRun(fileUri: Uri) {
        val queuePosition = Preferences.getQueueNewDownloadPosition()
        if (queuePosition == Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            binding?.let { bdg ->
                AddQueueMenu.show(
                    requireContext(),
                    bdg.root,
                    this
                ) { position, _ ->
                    runImport(
                        fileUri,
                        if (0 == position) Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_TOP else Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM
                    )
                }
            }
        } else {
            runImport(fileUri, queuePosition)
        }
    }

    private fun runImport(
        fileUri: Uri,
        queuePosition: Int
    ) {
        isCancelable = false
        binding?.apply {
            importRunBtn.visibility = View.GONE
            importStreamed.isEnabled = false
            val builder = DownloadsImportData.Builder()
            builder.setFileUri(fileUri)
            builder.setQueuePosition(queuePosition)
            builder.setImportAsStreamed(importStreamed.isChecked)
            ImportNotificationChannel.init(requireContext())
            importProgressText.setText(R.string.starting_import)
            importProgressBar.isIndeterminate = true
            importProgressText.visibility = View.VISIBLE
            importProgressBar.visibility = View.VISIBLE
            val workManager = WorkManager.getInstance(requireContext())
            workManager.enqueueUniqueWork(
                R.id.downloads_import_service.toString(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(DownloadsImportWorker::class.java)
                    .setInputData(builder.data)
                    .addTag(WORK_CLOSEABLE).build()
            )
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onImportEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_downloads || isServiceGracefulClose) return
        if (ProcessEvent.Type.PROGRESS == event.eventType) {
            val progress = event.elementsOK + event.elementsKO
            val itemTxt = resources.getQuantityString(R.plurals.item, progress)
            binding?.apply {
                importProgressText.text =
                    resources.getString(
                        R.string.generic_progress,
                        progress,
                        event.elementsTotal,
                        itemTxt
                    )
                importProgressBar.max = event.elementsTotal
                importProgressBar.progress = progress
                importProgressBar.isIndeterminate = false
            }
        } else if (ProcessEvent.Type.COMPLETE == event.eventType) {
            isServiceGracefulClose = true
            binding?.apply {
                importProgressBar.progress = event.elementsTotal
                importProgressText.text = resources.getQuantityString(
                    R.plurals.import_result,
                    event.elementsOK,
                    event.elementsOK,
                    event.elementsTotal
                )
            }
            // Dismiss after 3s, for the user to be able to see the ending message
            Handler(Looper.getMainLooper()).postDelayed({ dismissAllowingStateLoss() }, 2500)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onImportStickyEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_downloads || isServiceGracefulClose) return
        if (ProcessEvent.Type.COMPLETE == event.eventType) {
            EventBus.getDefault().removeStickyEvent(event)
            isServiceGracefulClose = true
            binding?.apply {
                importProgressBar.progress = event.elementsTotal
                importProgressText.text = resources.getQuantityString(
                    R.plurals.import_result,
                    event.elementsOK,
                    event.elementsOK,
                    event.elementsTotal
                )
            }
            // Dismiss after 3s, for the user to be able to see the ending message
            Handler(Looper.getMainLooper()).postDelayed({ dismissAllowingStateLoss() }, 2500)
        }
    }

    /**
     * Service destroyed event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceDestroyed(event: ServiceDestroyedEvent) {
        if (event.service != R.id.downloads_import_service) return
        if (!isServiceGracefulClose) {
            binding?.let { bdg ->
                Snackbar.make(
                    bdg.root,
                    R.string.import_unexpected,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
            Handler(Looper.getMainLooper()).postDelayed({ dismissAllowingStateLoss() }, 3000)
        }
    }
}