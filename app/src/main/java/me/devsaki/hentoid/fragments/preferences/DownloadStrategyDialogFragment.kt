package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogPrefsDlStrategyBinding
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences

class DownloadStrategyDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogPrefsDlStrategyBinding? = null
    private val binding get() = _binding!!

    // === VARIABLES
    private var parent: Parent? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parent = activity as Parent
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = DialogPrefsDlStrategyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        parent = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding.selector.apply {
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                if (checkedId == binding.choiceBalance.id) {
                    binding.description.text =
                        resources.getString(R.string.storage_strategy_balance_desc)
                    binding.threshold.isVisible = false
                } else {
                    binding.description.text = String.format(
                        resources.getString(R.string.storage_strategy_fallover_desc),
                        Preferences.getStorageSwitchThresholdPc()
                    )
                    binding.threshold.isVisible = true
                }
            }
            check(
                when (Preferences.getStorageDownloadStrategy()) {
                    Preferences.Constant.STORAGE_FILL_BALANCE_FREE -> binding.choiceBalance.id
                    else -> binding.choiceFallover.id
                }
            )
        }
        binding.threshold.editText?.apply {
            addTextChangedListener { text ->
                binding.description.text = String.format(
                    resources.getString(R.string.storage_strategy_fallover_desc),
                    text.toString().toInt()
                )
            }
            setText(Preferences.getStorageSwitchThresholdPc().toString())
        }
        binding.actionButton.setOnClickListener { onOkClick() }
    }

    private fun onOkClick() {
        Preferences.setStorageDownloadStrategy(
            when (binding.selector.checkedButtonId) {
                binding.choiceBalance.id -> Preferences.Constant.STORAGE_FILL_BALANCE_FREE
                else -> Preferences.Constant.STORAGE_FILL_FALLOVER
            }
        )
        binding.threshold.editText?.apply {
            Preferences.setStorageSwitchThresholdPc(
                Helper.coerceIn(text.toString().toFloat(), 0f, 100f)
                    .toInt()
            )
        }

        parent?.onStrategySelected()
        dismissAllowingStateLoss()
    }


    companion object {
        fun invoke(fragmentManager: FragmentManager) {
            val fragment = DownloadStrategyDialogFragment()
            fragment.show(fragmentManager, null)
        }
    }

    interface Parent {
        fun onStrategySelected()
    }
}