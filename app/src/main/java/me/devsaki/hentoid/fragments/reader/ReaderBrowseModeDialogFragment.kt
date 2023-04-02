package me.devsaki.hentoid.fragments.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.databinding.DialogReaderBrowseModeChooserBinding
import me.devsaki.hentoid.util.Preferences

class ReaderBrowseModeDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogReaderBrowseModeChooserBinding? = null
    private val binding get() = _binding!!

    // === VARIABLES
    private var parent: Parent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parent = parentFragment as Parent?
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = DialogReaderBrowseModeChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        parent = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding.chooseHorizontalLtr.setOnClickListener {
            chooseBrowseMode(Preferences.Constant.VIEWER_BROWSE_LTR)
        }

        binding.chooseHorizontalRtl.setOnClickListener {
            chooseBrowseMode(Preferences.Constant.VIEWER_BROWSE_RTL)
        }

        binding.chooseVertical.setOnClickListener {
            chooseBrowseMode(Preferences.Constant.VIEWER_BROWSE_TTB)
        }
    }

    private fun chooseBrowseMode(browseMode: Int) {
        Preferences.setReaderBrowseMode(browseMode)
        parent?.onBrowseModeChange()
        dismiss()
    }

    companion object {
        fun invoke(parent: Fragment) {
            val fragment = ReaderBrowseModeDialogFragment()
            fragment.show(parent.childFragmentManager, null)
        }
    }

    interface Parent {
        fun onBrowseModeChange()
    }
}