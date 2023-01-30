package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import me.devsaki.hentoid.databinding.DialogPrefsDlStrategyBinding
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

        binding.selector.check(
            when (Preferences.getStorageFillMethod()) {
                Preferences.Constant.STORAGE_FILL_BALANCE_OCCUPIED -> binding.choiceBalance.id
                else -> binding.choiceFallover.id
            }
        )
        // TODO description changes
        // TODO display threshold
        binding.actionButton.setOnClickListener { onOkClick() }
    }

    private fun onOkClick() {
        // TODO save threshold
        Preferences.setStorageFillMethod(
            when (binding.selector.checkedButtonId) {
                binding.choiceBalance.id -> Preferences.Constant.STORAGE_FILL_BALANCE_OCCUPIED
                else -> Preferences.Constant.STORAGE_FILL_FALLOVER
            }
        )

        parent?.onStrategySelected()
        dismiss()
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