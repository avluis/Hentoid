package me.devsaki.hentoid.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.databinding.DialogSettingsTimeRangeBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.disabledStr
import me.devsaki.hentoid.util.formatIntAsStr
import me.devsaki.hentoid.workers.ContentDownloadWorker
import nl.joery.timerangepicker.TimeRangePicker
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit


private const val WORK_TAG = "download_scheduled"

class TimeRangeDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        fun invoke(parentFragment: Fragment) {
            invoke(parentFragment, TimeRangeDialogFragment())
        }
    }

    private var binding: DialogSettingsTimeRangeBinding? = null
    private var timeStart = 0
    private var timeEnd = 0
    private var timeStr = ""


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogSettingsTimeRangeBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        timeStart = Settings.downloadScheduleStart
        timeEnd = Settings.downloadScheduleEnd

        binding?.apply {
            picker.startTimeMinutes = timeStart
            picker.endTimeMinutes = timeEnd

            picker.setOnTimeChangeListener(object : TimeRangePicker.OnTimeChangeListener {
                override fun onStartTimeChange(startTime: TimeRangePicker.Time) {
                    timeStart = startTime.totalMinutes
                    refreshText()
                }

                override fun onEndTimeChange(endTime: TimeRangePicker.Time) {
                    timeEnd = endTime.totalMinutes
                    refreshText()
                }

                override fun onDurationChange(duration: TimeRangePicker.TimeDuration) {
                    // Not useful for the app
                }
            })
            refreshText()

            // Bottom buttons
            actionButton.setOnClickListener { onActionClick() }
            cancelButton.setOnClickListener { onCancelClick() }
        }
    }

    private fun refreshText() {
        val tStart = TimeRangePicker.Time(timeStart)
        val tEnd = TimeRangePicker.Time(timeEnd)
        val startH = formatIntAsStr(tStart.hour, 2)
        val startM = formatIntAsStr(tStart.minute, 2)
        val endH = formatIntAsStr(tEnd.hour, 2)
        val endM = formatIntAsStr(tEnd.minute, 2)
        timeStr = "$startH:$startM - $endH:$endM"
        binding?.rangeTxt?.text = timeStr
    }

    private fun onCancelClick() {
        Settings.downloadScheduleStart = 0
        Settings.downloadScheduleEnd = 0
        Settings.downloadScheduleSummary = disabledStr

        val workManager = WorkManager.getInstance(requireActivity())
        workManager.cancelAllWorkByTag(WORK_TAG)

        dismissAllowingStateLoss()
    }

    private fun onActionClick() {
        Settings.downloadScheduleStart = timeStart
        Settings.downloadScheduleEnd = timeEnd
        Settings.downloadScheduleSummary = timeStr

        // Schedule downloader
        val now = LocalTime.now()
        val scheduledTime = LocalTime.ofSecondOfDay(timeStart * 60L)
        val delay = if (now < scheduledTime) {
            now.until(scheduledTime, ChronoUnit.MINUTES)
        } else {
            24 * 60 - scheduledTime.until(now, ChronoUnit.MINUTES)
        }

        val workRequest = PeriodicWorkRequest.Builder(
            ContentDownloadWorker::class.java,
            24,
            TimeUnit.HOURS,
            PeriodicWorkRequest.Companion.MIN_PERIODIC_FLEX_MILLIS,
            TimeUnit.MILLISECONDS
        )
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .addTag(WORK_CLOSEABLE)
            .build()

        val workManager = WorkManager.getInstance(requireActivity())
        workManager.cancelAllWorkByTag(WORK_TAG)
        workManager.enqueueUniquePeriodicWork(
            R.id.download_service.toString(),
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        dismissAllowingStateLoss()
    }
}