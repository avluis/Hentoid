package me.devsaki.hentoid.fragments.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogReaderDeleteBinding
import me.devsaki.hentoid.util.Preferences

const val KEY_DELETE_PAGE_ALLOWED = "delete_page_allowed"

class ReaderDeleteDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogReaderDeleteBinding? = null
    private val binding get() = _binding!!

    // === VARIABLES
    private var parent: Parent? = null
    private var isDeletePageAllowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        isDeletePageAllowed = requireArguments().getBoolean(KEY_DELETE_PAGE_ALLOWED, false)
        parent = parentFragment as Parent?
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = DialogReaderDeleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        parent = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding.bookPrefsDeleteSpin.apply {
            setIsFocusable(true)
            lifecycleOwner = requireActivity()
            setItems(R.array.page_delete_choices)
            selectItemByIndex(0)
        }

        if (!isDeletePageAllowed) binding.deleteModePage.isEnabled = false

        binding.actionButton.setOnClickListener {
            if (!binding.deleteModePage.isChecked && !binding.deleteModeBook.isChecked) return@setOnClickListener
            Preferences.setReaderDeleteAskMode(binding.bookPrefsDeleteSpin.selectedIndex)
            Preferences.setReaderDeleteTarget(if (binding.deleteModePage.isChecked) Preferences.Constant.VIEWER_DELETE_TARGET_PAGE else Preferences.Constant.VIEWER_DELETE_TARGET_BOOK)
            parent?.onDeleteElement(binding.deleteModePage.isChecked)
            dismiss()
        }
    }

    companion object {
        fun invoke(parent: Fragment, isDeletePageAllowed: Boolean) {
            val fragment = ReaderDeleteDialogFragment()

            val args = Bundle()
            args.putBoolean(KEY_DELETE_PAGE_ALLOWED, isDeletePageAllowed)
            fragment.arguments = args

            fragment.show(parent.childFragmentManager, null)
        }
    }

    interface Parent {
        fun onDeleteElement(onDeletePage: Boolean)
    }
}