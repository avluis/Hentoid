package me.devsaki.hentoid.fragments.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.databinding.DialogQueueErrorsBinding
import me.devsaki.hentoid.enums.ErrorType
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.LogHelper
import me.devsaki.hentoid.util.LogHelper.LogEntry
import me.devsaki.hentoid.util.LogHelper.LogInfo
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.file.FileHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.EnumMap

/**
 * Info dialog for download errors details
 */
class ErrorStatsDialogFragment : BaseDialogFragment<Nothing>() {
    companion object {
        const val ID = "ID"
        fun invoke(fragment: Fragment, id: Long) {
            val args = Bundle()
            args.putLong(ID, id)
            invoke(fragment, ErrorStatsDialogFragment(), args)
        }
    }

    // == UI
    private var binding: DialogQueueErrorsBinding? = null

    private var previousNbErrors = 0
    private var currentId: Long = 0


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        binding = DialogQueueErrorsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        arguments?.let {
            binding?.statsDetails?.setText(R.string.downloads_loading)
            previousNbErrors = 0
            val id = it.getLong(ID, 0)
            currentId = id
            if (id > 0) updateStats(id)
        }

        val openLogButton = ViewCompat.requireViewById<View>(rootView, R.id.open_log_btn)
        openLogButton.setOnClickListener { this.showErrorLog() }

        val shareLogButton = ViewCompat.requireViewById<View>(rootView, R.id.share_log_btn)
        shareLogButton.setOnClickListener { this.shareErrorLog() }
    }

    private fun updateStats(contentId: Long) {
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        val errors: List<ErrorRecord> = try {
            dao.selectErrorRecordByContentId(contentId)
        } finally {
            dao.cleanup()
        }
        val errorsByType: MutableMap<ErrorType, Int> = EnumMap(
            ErrorType::class.java
        )
        for (error in errors) {
            if (errorsByType.containsKey(error.type)) {
                val nbErrorsObj = errorsByType[error.type]
                var nbErrors = nbErrorsObj ?: 0
                errorsByType[error.type] = ++nbErrors
            } else {
                errorsByType[error.type] = 1
            }
        }
        val detailsStr = StringBuilder()
        errorsByType.forEach {
            detailsStr.append(resources.getString(it.key.displayName)).append(": ")
            detailsStr.append(it.value)
            detailsStr.append(System.getProperty("line.separator"))
        }
        binding?.statsDetails?.text = detailsStr.toString()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        if (event.eventType == DownloadEvent.Type.EV_COMPLETE) {
            binding?.statsDetails?.setText(R.string.download_complete)
            previousNbErrors = 0
        } else if (event.eventType == DownloadEvent.Type.EV_CANCELED) {
            binding?.statsDetails?.setText(R.string.download_cancelled)
            previousNbErrors = 0
        } else if (event.eventType == DownloadEvent.Type.EV_PROGRESS
            && event.pagesKO > previousNbErrors
        ) {
            event.content?.let {
                currentId = it.id
                previousNbErrors = event.pagesKO
                updateStats(currentId)
            }
        }
    }

    private fun createLog(): LogInfo {
        val content: Content
        val dao: CollectionDAO = ObjectBoxDAO(context)
        try {
            content = dao.selectContent(currentId)!!
        } finally {
            dao.cleanup()
        }
        val log: MutableList<LogEntry> = ArrayList()
        val errorLogInfo = LogInfo("error_log" + content.id)
        errorLogInfo.setHeaderName(resources.getString(R.string.error))
        errorLogInfo.setNoDataMessage(resources.getString(R.string.no_error_detected))
        errorLogInfo.setEntries(log)
        val errorLog: List<ErrorRecord>? = content.errorLog
        if (errorLog != null) {
            errorLogInfo.setHeader(
                resources.getString(
                    R.string.error_log_header,
                    content.title,
                    content.uniqueSiteId,
                    content.site.description,
                    errorLog.size
                )
            )
            for (e in errorLog) log.add(LogEntry(e.timestamp, e.toString()))
        }
        return errorLogInfo
    }

    private fun showErrorLog() {
        ToastHelper.toast(R.string.redownload_generating_log_file)
        val logInfo = createLog()
        val logFile = LogHelper.writeLog(requireContext(), logInfo)
        if (logFile != null) FileHelper.openFile(requireContext(), logFile)
    }

    private fun shareErrorLog() {
        val logInfo = createLog()
        val logFile = LogHelper.writeLog(requireContext(), logInfo)
        if (logFile != null) FileHelper.shareFile(
            requireContext(),
            logFile.uri,
            resources.getString(R.string.error_log_header_queue)
        )
    }
}