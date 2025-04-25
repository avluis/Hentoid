package me.devsaki.hentoid.fragments.tools

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.DuplicateDetectorActivity
import me.devsaki.hentoid.core.isFinishing
import me.devsaki.hentoid.databinding.IncludeDuplicateControlsBinding
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
import me.devsaki.hentoid.workers.STEP_COVER_INDEX
import me.devsaki.hentoid.workers.STEP_DUPLICATES
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class DuplicateMainTopPanel(activity: DuplicateDetectorActivity) : DefaultLifecycleObserver {

    // Core UI
    private lateinit var binding: IncludeDuplicateControlsBinding
    private lateinit var menuView: View
    private lateinit var menuWindow: PopupWindow

    // Core vars
    private lateinit var lifecycleOwner: LifecycleOwner
    private var isShowing: Boolean = false

    // Custom vars
    private val viewModel: DuplicateViewModel


    init {
        setLifecycleOwnerFromContext(activity)
        initFrame(activity)
        initUI(activity)

        val vmFactory = ViewModelFactory(activity.application)
        viewModel = ViewModelProvider(activity, vmFactory)[DuplicateViewModel::class.java]
    }

    private fun setLifecycleOwnerFromContext(context: Context) {
        if (context is LifecycleOwner) {
            setLifecycleOwner(context as LifecycleOwner)
        }
    }

    private fun setLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
        this.lifecycleOwner = lifecycleOwner
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dismiss()
        lifecycleOwner.lifecycle.removeObserver(this)
    }

    private fun initFrame(activity: DuplicateDetectorActivity) {
        binding = IncludeDuplicateControlsBinding.inflate(
            LayoutInflater.from(activity),
            activity.window.decorView as ViewGroup,
            false
        )
        menuView = binding.root
        menuWindow = PopupWindow(
            menuView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        // Outside click auto-dismisses the menu
        menuWindow.isFocusable = true
        menuWindow.setOnDismissListener { dismiss() }
    }

    fun showAsDropDown(anchor: View) {
        if (!isShowing
            && anchor.isAttachedToWindow
            && !anchor.context.isFinishing()
        ) {
            updateUI(anchor.context)
            isShowing = true
            menuWindow.showAsDropDown(anchor)
        } else {
            dismiss()
        }
    }

    fun isVisible(): Boolean {
        return isShowing
    }

    fun dismiss() {
        if (isShowing) {
            menuWindow.dismiss()
            isShowing = false
        }
    }

    private fun initUI(context: Context) {
        binding.scanFab.setOnClickListener {
            this.onScanClick()
        }
        binding.stopFab.setOnClickListener {
            this.onStopClick()
        }

        binding.useTitle.setOnCheckedChangeListener { _, _ -> onMainCriteriaChanged() }
        binding.useCover.setOnCheckedChangeListener { _, _ -> onMainCriteriaChanged() }

        binding.useTitle.isChecked = Settings.duplicateUseTitle
        binding.useCover.isChecked = Settings.duplicateUseCover
        binding.useArtist.isChecked = Settings.duplicateUseArtist
        binding.useSameLanguage.isChecked = Settings.duplicateUseSameLanguage
        binding.ignoreChapters.isChecked = Settings.duplicateIgnoreChapters
        binding.useSensitivity.index = Settings.duplicateSensitivity
        updateUI(context)
    }

    private fun updateUI(context: Context) {
        if (DuplicateDetectorWorker.isRunning(context)) {
            binding.scanFab.visibility = View.INVISIBLE
            binding.stopFab.visibility = View.VISIBLE
            // TODO simplify that
            val coverControlsVisibility =
                if (binding.useCover.isChecked) View.VISIBLE else View.GONE
            binding.indexPicturesTxt.visibility = coverControlsVisibility
            binding.indexPicturesPb.visibility = coverControlsVisibility
            binding.indexPicturesPbTxt.visibility = View.GONE
            binding.detectBooksTxt.visibility = View.VISIBLE
            binding.detectBooksPb.visibility = View.VISIBLE
            binding.detectBooksPbTxt.visibility = View.GONE
        } else {
            binding.scanFab.visibility = View.VISIBLE
            binding.stopFab.visibility = View.INVISIBLE
            binding.indexPicturesTxt.visibility = View.GONE
            binding.indexPicturesPb.visibility = View.GONE
            binding.indexPicturesPbTxt.visibility = View.GONE
            binding.detectBooksTxt.visibility = View.GONE
            binding.detectBooksPb.visibility = View.GONE
            binding.detectBooksPbTxt.visibility = View.GONE
        }
    }

    private fun onScanClick() {
        Settings.duplicateUseTitle = binding.useTitle.isChecked
        Settings.duplicateUseCover = binding.useCover.isChecked
        Settings.duplicateUseArtist = binding.useArtist.isChecked
        Settings.duplicateUseSameLanguage = binding.useSameLanguage.isChecked
        Settings.duplicateIgnoreChapters = binding.ignoreChapters.isChecked
        Settings.duplicateSensitivity = binding.useSensitivity.index

        activateScanUi()

        viewModel.setFirstUse(false)
        viewModel.scanForDuplicates(
            binding.useTitle.isChecked,
            binding.useCover.isChecked,
            binding.useArtist.isChecked,
            binding.useSameLanguage.isChecked,
            binding.ignoreChapters.isChecked,
            binding.useSensitivity.index
        )
    }

    private fun activateScanUi() {
        binding.scanFab.visibility = View.INVISIBLE
        binding.stopFab.visibility = View.VISIBLE

        binding.useTitle.isEnabled = false
        binding.useCover.isEnabled = false
        binding.useArtist.isEnabled = false
        binding.useSameLanguage.isEnabled = false
        binding.ignoreChapters.isEnabled = false
        binding.useSensitivity.isEnabled = false

        val coverControlsVisibility =
            if (binding.useCover.isChecked) View.VISIBLE else View.GONE
        binding.indexPicturesTxt.visibility = coverControlsVisibility
        binding.indexPicturesPb.progress = 0
        binding.indexPicturesPb.visibility = coverControlsVisibility
        binding.detectBooksTxt.visibility = View.VISIBLE
        binding.detectBooksPb.progress = 0
        binding.detectBooksPb.visibility = View.VISIBLE
    }

    private fun disableScanUi() {
        binding.scanFab.visibility = View.VISIBLE
        binding.stopFab.visibility = View.INVISIBLE

        binding.useTitle.isEnabled = true
        binding.useCover.isEnabled = true
        binding.useArtist.isEnabled = true
        binding.useSameLanguage.isEnabled = true
        binding.ignoreChapters.isEnabled = true
        binding.useSensitivity.isEnabled = true

        binding.indexPicturesTxt.visibility = View.GONE
        binding.indexPicturesPb.visibility = View.GONE
        binding.indexPicturesPbTxt.visibility = View.GONE
        binding.detectBooksTxt.visibility = View.GONE
        binding.detectBooksPb.visibility = View.GONE
        binding.detectBooksPbTxt.visibility = View.GONE
    }

    private fun onMainCriteriaChanged() {
        binding.scanFab.isEnabled =
            (binding.useTitle.isChecked || binding.useCover.isChecked)
    }

    private fun onStopClick() {
        WorkManager.getInstance(binding.stopFab.context)
            .cancelUniqueWork(R.id.duplicate_detector_service.toString())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        if (event.processId != R.id.duplicate_index && event.processId != R.id.duplicate_detect) return

        val progressBar: ProgressBar =
            if (STEP_COVER_INDEX == event.step) binding.indexPicturesPb else binding.detectBooksPb
        val progressBarTxt: TextView =
            if (STEP_COVER_INDEX == event.step) binding.indexPicturesPbTxt else binding.detectBooksPbTxt

        if (STEP_COVER_INDEX == event.step) {
            if (null == binding.detectBooksPbTxt.animation) {
                binding.detectBooksPbTxt.startAnimation(BlinkAnimation(750, 20))
                binding.detectBooksPbTxt.text =
                    binding.detectBooksPbTxt.context.resources.getText(R.string.duplicate_wait_index)
                binding.detectBooksPbTxt.visibility = View.VISIBLE
            }
        } else {
            binding.detectBooksPbTxt.clearAnimation()
        }

        progressBar.max = event.elementsTotal
        progressBar.progress = event.elementsOK + event.elementsKO
        progressBarTxt.text = String.format("%d / %d", progressBar.progress, progressBar.max)
        progressBarTxt.visibility = View.VISIBLE

        if (ProcessEvent.Type.COMPLETE == event.eventType && STEP_DUPLICATES == event.step) {
            disableScanUi()
        } else if (binding.scanFab.visibility == View.VISIBLE && DuplicateDetectorWorker.isRunning(
                binding.scanFab.context
            )
        ) {
            activateScanUi()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        if (event.processId != R.id.duplicate_index && event.processId != R.id.duplicate_detect) return

        EventBus.getDefault().removeStickyEvent(event)

        if (ProcessEvent.Type.COMPLETE == event.eventType && STEP_DUPLICATES == event.step) {
            disableScanUi()
        } else if (binding.scanFab.visibility == View.VISIBLE && DuplicateDetectorWorker.isRunning(
                binding.scanFab.context
            )
        ) {
            activateScanUi()
        }
    }

    fun onServiceDestroyedEvent() {
        disableScanUi()
    }
}