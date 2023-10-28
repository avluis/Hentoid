package me.devsaki.hentoid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.PluralsRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogProgressBinding
import me.devsaki.hentoid.events.ProcessEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ProgressDialogFragment : DialogFragment() {
    private var binding: DialogProgressBinding? = null
    private var dialogTitle: String? = null

    @PluralsRes
    private var progressUnit = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = arguments
        requireNotNull(bundle) { "No arguments found" }
        dialogTitle = bundle.getString(TITLE, "")
        progressUnit = bundle.getInt(PROGRESS_UNIT, -1)
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogProgressBinding.inflate(inflater, container, false)
        isCancelable = false
        return binding!!.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        binding?.apply {
            title.text = dialogTitle
            bar.isIndeterminate = true
        }
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
        binding = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        if (event.processId != R.id.generic_progress) return
        binding?.apply {
            bar.max = event.elementsTotal
            bar.isIndeterminate = false
            val nbProcessed = event.elementsOK + event.elementsKO
            if (ProcessEvent.Type.PROGRESS == event.eventType) {
                progress.text = getString(
                    R.string.generic_progress,
                    event.elementsOK + event.elementsKO,
                    event.elementsTotal,
                    resources.getQuantityString(
                        progressUnit,
                        event.elementsOK + event.elementsKO
                    )
                )
                bar.progress = nbProcessed
            }
            if (ProcessEvent.Type.COMPLETE == event.eventType || nbProcessed == event.elementsTotal) {
                dismissAllowingStateLoss()
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        if (event.processId != R.id.generic_progress) return
        binding?.apply {
            bar.max = event.elementsTotal
            bar.isIndeterminate = false
        }
        EventBus.getDefault().removeStickyEvent(event)
        if (ProcessEvent.Type.COMPLETE == event.eventType) dismissAllowingStateLoss()
    }

    companion object {
        const val TITLE = "title"
        const val PROGRESS_UNIT = "progressUnit"

        fun invoke(
            fragmentManager: FragmentManager,
            title: String,
            @PluralsRes progressUnit: Int
        ): DialogFragment {
            val fragment = ProgressDialogFragment()
            val args = Bundle()
            args.putString(TITLE, title)
            args.putInt(PROGRESS_UNIT, progressUnit)
            fragment.arguments = args
            fragment.show(fragmentManager, null)
            return fragment
        }
    }
}