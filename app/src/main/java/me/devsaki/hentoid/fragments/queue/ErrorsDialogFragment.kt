package me.devsaki.hentoid.fragments.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.databinding.DialogLibraryErrorsBinding
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.LogEntry
import me.devsaki.hentoid.util.LogInfo
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.writeLog

/**
 * Info dialog for download errors details
 */
class ErrorsDialogFragment : BaseDialogFragment<ErrorsDialogFragment.Parent>() {

    companion object {
        const val ID = "ID"

        fun invoke(parentFragment: Fragment, id: Long) {
            val args = Bundle()
            args.putLong(ID, id)
            invoke(parentFragment, ErrorsDialogFragment(), args)
        }
    }

    private var binding: DialogLibraryErrorsBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        binding = DialogLibraryErrorsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        val id = requireArguments().getLong(ID, 0)
        require(0L != id) { "No ID found" }

        val content: Content
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            content = dao.selectContent(id)!!
        } finally {
            dao.cleanup()
        }

        updateStats(content)

        binding?.apply {
            redownloadBtn.setOnClickListener { redownload(content) }
            openLogBtn.setOnClickListener { showErrorLog(content) }
            shareLogBtn.setOnClickListener { shareErrorLog(content) }
        }
    }

    private fun updateStats(content: Content) {
        var images = 0
        var imgErrors = 0
        val context = context ?: return
        content.imageFiles?.let {
            images = it.size - 1 // Don't count the cover
            for (imgFile in it) if (imgFile.status == StatusContent.ERROR) imgErrors++
            if (0 == images) {
                images = content.qtyPages
                imgErrors = images
            }
        }
        binding?.let {
            it.redownloadDetail.text = context.getString(
                R.string.redownload_dialog_message, images, images - imgErrors, imgErrors
            )
            content.errorLog?.let { log ->
                if (!log.isEmpty()) {
                    val firstError = log[0]
                    var message = context.getString(
                        R.string.redownload_first_error,
                        context.getString(firstError.type.displayName)
                    )
                    if (firstError.description.isNotEmpty()) message += String.format(
                        " - %s", firstError.description
                    )
                    it.redownloadDetailFirstError.text = message
                    it.redownloadDetailFirstError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun createLog(content: Content): LogInfo {
        val log: MutableList<LogEntry> = ArrayList()
        val errorLogInfo = LogInfo("error_log" + content.id)
        errorLogInfo.setHeaderName("Error")
        errorLogInfo.setNoDataMessage("No error detected.")
        errorLogInfo.setEntries(log)
        val errorLog: List<ErrorRecord>? = content.errorLog
        if (errorLog != null) {
            errorLogInfo.setHeader("Error log for " + content.title + " [" + content.uniqueSiteId + "@" + content.site.description + "] : " + errorLog.size + " errors")
            for (e in errorLog) log.add(LogEntry(e.timestamp, e.toString()))
        }
        return errorLogInfo
    }

    private fun showErrorLog(content: Content) {
        toast(R.string.redownload_generating_log_file)
        val logInfo = createLog(content)
        val logFile = requireContext().writeLog(logInfo)
        if (logFile != null) FileHelper.openFile(requireContext(), logFile)
    }

    private fun shareErrorLog(content: Content) {
        val logInfo = createLog(content)
        val logFile = requireContext().writeLog(logInfo)
        if (logFile != null) FileHelper.shareFile(
            requireContext(), logFile.uri, "Error log for book ID " + content.uniqueSiteId
        )
    }

    private fun redownload(content: Content) {
        parent?.redownloadContent(content)
        dismiss()
    }

    interface Parent {
        fun redownloadContent(content: Content)
    }
}