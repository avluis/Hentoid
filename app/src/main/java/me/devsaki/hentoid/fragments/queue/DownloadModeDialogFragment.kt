package me.devsaki.hentoid.fragments.queue

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogQueueChangeDlModeBinding

/**
 * Dialog to choose a new download mode
 */
class DownloadModeDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogQueueChangeDlModeBinding? = null
    private val binding get() = _binding!!

    // === VARIABLES
    private var parent: Parent? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parent = parentFragment as Parent
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = DialogQueueChangeDlModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        parent = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding.dlBtn.setOnClickListener { selectMode(Content.DownloadMode.DOWNLOAD) }
        binding.streamBtn.setOnClickListener { selectMode(Content.DownloadMode.STREAM) }
    }

    override fun onCancel(dialog: DialogInterface) {
        parent?.leaveSelectionMode()
        super.onCancel(dialog)
    }

    private fun selectMode(downloadMode: Int) {
        parent?.onNewModeSelected(downloadMode)
        dismiss()
    }


    companion object {
        fun invoke(parent: Fragment) {
            val fragment = DownloadModeDialogFragment()
            fragment.show(parent.childFragmentManager, null)
        }
    }

    interface Parent {
        fun onNewModeSelected(downloadMode: Int)
        fun leaveSelectionMode()
    }
}