package me.devsaki.hentoid.fragments.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.databinding.DialogMetaRenameBinding

/**
 * Dialog to rename an attribute
 */
const val KEY_ID = "id"

class MetaRenameDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogMetaRenameBinding? = null
    private val binding get() = _binding!!

    // === VARIABLES
    private var parent: Parent? = null
    private var attrId: Long = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        attrId = requireArguments().getLong(KEY_ID)

        //parent = parentFragment as Parent
        parent = activity as Parent
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = DialogMetaRenameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        parent = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        val attr = loadAttr()
        if (null == attr) {
            dismissAllowingStateLoss()
            return
        }

        binding.name.editText?.setText(attr.displayName)

        binding.actionButton.setOnClickListener { onValidate() }
    }

    private fun onValidate() {
        parent?.onRenameAttribute(
            binding.name.editText?.text.toString(),
            attrId,
            binding.mergeDeleteSwitch.isChecked
        )
        dismissAllowingStateLoss()
    }

    private fun loadAttr(): Attribute? {
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            return dao.selectAttribute(attrId)
        } finally {
            dao.cleanup()
        }
    }

    companion object {
        /*
        fun invoke(parentFragment: Fragment, newAttrName: String) {
            val fragment = AttributeTypePickerDialogFragment()

            val args = Bundle()
            args.putString(KEY_NAME, newAttrName)
            fragment.arguments = args

            fragment.show(parentFragment.childFragmentManager, null)
        }
         */

        fun invoke(parent: FragmentActivity, attrId: Long) {
            val fragment = MetaRenameDialogFragment()

            val args = Bundle()
            args.putLong(KEY_ID, attrId)
            fragment.arguments = args

            fragment.show(parent.supportFragmentManager, null)
        }
    }

    interface Parent {
        fun onRenameAttribute(newName: String, id: Long, createRule: Boolean)
    }
}