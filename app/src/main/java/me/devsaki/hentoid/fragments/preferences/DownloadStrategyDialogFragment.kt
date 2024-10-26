package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentActivity
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogPrefsDlStrategyBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.coerceIn

class DownloadStrategyDialogFragment : BaseDialogFragment<DownloadStrategyDialogFragment.Parent>() {
    companion object {
        fun invoke(activity: FragmentActivity) {
            invoke(activity, DownloadStrategyDialogFragment())
        }
    }

    private var binding: DialogPrefsDlStrategyBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogPrefsDlStrategyBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding?.apply {
            selector.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                if (checkedId == choiceBalance.id) {
                    description.text =
                        resources.getString(R.string.storage_strategy_balance_desc)
                    threshold.isVisible = false
                } else {
                    description.text = String.format(
                        resources.getString(R.string.storage_strategy_fallover_desc),
                        Settings.storageSwitchThresholdPc
                    )
                    threshold.isVisible = true
                }
            }
            selector.check(
                when (Settings.storageDownloadStrategy) {
                    Settings.Value.STORAGE_FILL_BALANCE_FREE -> choiceBalance.id
                    else -> choiceFallover.id
                }
            )
        }
        binding?.threshold?.editText?.apply {
            addTextChangedListener { text ->
                if (!text.isNullOrEmpty()) {
                    binding?.description?.text = String.format(
                        resources.getString(R.string.storage_strategy_fallover_desc),
                        text.toString().toInt()
                    )
                }
            }
            setText(Settings.storageSwitchThresholdPc.toString())
        }
        binding?.actionButton?.setOnClickListener { onOkClick() }
    }

    private fun onOkClick() {
        binding?.threshold?.editText?.apply {
            if (text.isEmpty()) return
            Settings.storageSwitchThresholdPc =
                coerceIn(text.toString().toFloat(), 0f, 100f).toInt()
        }

        binding?.apply {
            Settings.storageDownloadStrategy =
                when (selector.checkedButtonId) {
                    choiceBalance.id -> Settings.Value.STORAGE_FILL_BALANCE_FREE
                    else -> Settings.Value.STORAGE_FILL_FALLOVER
                }
        }

        parent?.onStrategySelected()
        dismissAllowingStateLoss()
    }

    interface Parent {
        fun onStrategySelected()
    }
}