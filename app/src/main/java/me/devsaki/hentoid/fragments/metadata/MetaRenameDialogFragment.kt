package me.devsaki.hentoid.fragments.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.databinding.DialogMetaRenameBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment

/**
 * Dialog to rename an attribute
 */
class MetaRenameDialogFragment : BaseDialogFragment<MetaRenameDialogFragment.Parent>() {

    companion object {
        const val KEY_ID = "id"

        fun invoke(parent: FragmentActivity, attrId: Long) {
            val args = Bundle()
            args.putLong(KEY_ID, attrId)
            invoke(parent, MetaRenameDialogFragment(), args)
        }
    }


    // UI
    private var binding: DialogMetaRenameBinding? = null

    // === VARIABLES
    private var attrId: Long = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        attrId = requireArguments().getLong(KEY_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogMetaRenameBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        val attr = loadAttr()
        if (null == attr) {
            dismissAllowingStateLoss()
            return
        }

        binding?.apply {
            name.editText?.setText(attr.displayName)
            actionButton.setOnClickListener { onValidate() }
        }
    }

    private fun onValidate() {
        binding?.apply {
            parent?.onRenameAttribute(
                name.editText?.text.toString(),
                attrId,
                mergeDeleteSwitch.isChecked
            )
        }
        dismissAllowingStateLoss()
    }

    private fun loadAttr(): Attribute? {
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            return dao.selectAttribute(attrId)
        } finally {
            dao.cleanup()
        }
    }

    interface Parent {
        fun onRenameAttribute(newName: String, id: Long, createRule: Boolean)
    }
}