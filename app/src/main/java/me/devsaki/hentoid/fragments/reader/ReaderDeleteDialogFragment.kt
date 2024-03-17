package me.devsaki.hentoid.fragments.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.databinding.DialogReaderDeleteBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Preferences

class ReaderDeleteDialogFragment : BaseDialogFragment<ReaderDeleteDialogFragment.Parent>() {

    companion object {
        const val KEY_DELETE_PAGE_ALLOWED = "delete_page_allowed"

        fun invoke(parent: Fragment, isDeletePageAllowed: Boolean) {
            val args = Bundle()
            args.putBoolean(KEY_DELETE_PAGE_ALLOWED, isDeletePageAllowed)
            invoke(parent, ReaderDeleteDialogFragment(), args)
        }
    }


    // UI
    private var binding: DialogReaderDeleteBinding? = null

    // === VARIABLES
    private var isDeletePageAllowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        isDeletePageAllowed = requireArguments().getBoolean(KEY_DELETE_PAGE_ALLOWED, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogReaderDeleteBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding?.apply {
            deleteWhat.index = 0

            if (!isDeletePageAllowed) deleteModePage.isEnabled = false

            actionButton.setOnClickListener {
                if (!deleteModePage.isChecked && !deleteModeBook.isChecked) return@setOnClickListener
                Preferences.setReaderDeleteAskMode(deleteWhat.index)
                Preferences.setReaderDeleteTarget(if (deleteModePage.isChecked) Preferences.Constant.VIEWER_DELETE_TARGET_PAGE else Preferences.Constant.VIEWER_DELETE_TARGET_BOOK)
                parent?.onDeleteElement(deleteModePage.isChecked)
                dismiss()
            }
        }
    }

    interface Parent {
        fun onDeleteElement(onDeletePage: Boolean)
    }
}