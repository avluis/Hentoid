package me.devsaki.hentoid.fragments.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.databinding.DialogMetaRuleEditBinding
import me.devsaki.hentoid.enums.AttributeType

/**
 * Dialog to edit an attribute naming rule
 */
const val KEY_RULE_ID = "id"
const val KEY_MODE_CREATE = "mode_create"
const val KEY_ATTR_TYPE_CODE = "attr_type_code"

class MetaEditRuleDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogMetaRuleEditBinding? = null
    private val binding get() = _binding!!

    // === VARIABLES
    private var parent: Parent? = null
    private var isCreateMode: Boolean = false
    private var ruleId: Long = 0
    private var attrType: AttributeType? = null
    private val attributeTypes = ArrayList<AttributeType>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCreateMode = requireArguments().getBoolean(KEY_MODE_CREATE)
        ruleId = requireArguments().getLong(KEY_RULE_ID)
        val attrTypeCode = requireArguments().getInt(KEY_ATTR_TYPE_CODE, 99)
        if (attrTypeCode < 99) attrType = AttributeType.searchByCode(attrTypeCode)

        //parent = parentFragment as Parent
        parent = activity as Parent
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = DialogMetaRuleEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        parent = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        if (isCreateMode) {
            if (null == attrType) { // No defined attribute type
                attributeTypes.addAll(
                    listOf(
                        AttributeType.ARTIST,
                        AttributeType.CIRCLE,
                        AttributeType.SERIE,
                        AttributeType.TAG,
                        AttributeType.CHARACTER,
                        AttributeType.LANGUAGE
                    )
                )
                binding.attributeType.setIsFocusable(true)
                binding.attributeType.lifecycleOwner = viewLifecycleOwner
                binding.attributeType.setItems(attributeTypes.map { AttributeType::getDisplayName })
            }
        } else {
            val rule = loadRule()
            if (null == rule) {
                dismissAllowingStateLoss()
                return
            }
            binding.sourceName.editText?.setText(rule.sourceName)
            binding.targetName.editText?.setText(rule.targetName)
        }

        binding.attributeType.visibility = if (isCreateMode) View.VISIBLE else View.GONE
        binding.actionNew.visibility = if (isCreateMode) View.VISIBLE else View.GONE
        binding.actionEdit.visibility = if (!isCreateMode) View.VISIBLE else View.GONE
        binding.actionRemove.visibility = if (!isCreateMode) View.VISIBLE else View.GONE

        binding.actionNew.setOnClickListener { onCreateClick() }
        binding.actionEdit.setOnClickListener { onEditClick() }
        binding.actionRemove.setOnClickListener { onRemoveClick() }
    }

    private fun onCreateClick() {
        parent?.onCreateRule(
            if (null == attrType) attributeTypes[binding.attributeType.selectedIndex] else attrType!!,
            binding.sourceName.editText?.text.toString(),
            binding.targetName.editText?.text.toString()
        )
        dismissAllowingStateLoss()
    }

    private fun onEditClick() {
        parent?.onEditRule(
            ruleId,
            binding.sourceName.editText?.text.toString(),
            binding.targetName.editText?.text.toString()
        )
        dismissAllowingStateLoss()
    }

    private fun onRemoveClick() {
        parent?.onRemoveRule(ruleId)
        dismissAllowingStateLoss()
    }

    private fun loadRule(): RenamingRule? {
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            return dao.selectRenamingRule(ruleId)
        } finally {
            dao.cleanup()
        }
    }

    companion object {
        fun invoke(
            parent: FragmentActivity,
            createMode: Boolean,
            ruleId: Long,
            attrType: AttributeType?
        ) {
            val fragment = MetaEditRuleDialogFragment()

            val args = Bundle()
            args.putBoolean(KEY_MODE_CREATE, createMode)
            args.putLong(KEY_RULE_ID, ruleId)
            if (attrType != null)
                args.putInt(KEY_ATTR_TYPE_CODE, attrType.code)
            fragment.arguments = args

            fragment.show(parent.supportFragmentManager, null)
        }
    }

    interface Parent {
        fun onCreateRule(type: AttributeType, source: String, target: String)
        fun onEditRule(id: Long, source: String, target: String)
        fun onRemoveRule(id: Long)
    }
}