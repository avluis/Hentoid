package me.devsaki.hentoid.fragments.metadata

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.databinding.DialogMetaRuleEditBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.fragments.BaseDialogFragment

/**
 * Dialog to edit an attribute naming rule
 */
class MetaEditRuleDialogFragment : BaseDialogFragment<MetaEditRuleDialogFragment.Parent>() {

    companion object {
        const val KEY_RULE_ID = "id"
        const val KEY_MODE_CREATE = "mode_create"
        const val KEY_ATTR_TYPE_CODE = "attr_type_code"

        fun invoke(
            parent: FragmentActivity,
            createMode: Boolean,
            ruleId: Long,
            attrType: AttributeType? = null
        ) {
            val args = Bundle()
            args.putBoolean(KEY_MODE_CREATE, createMode)
            args.putLong(KEY_RULE_ID, ruleId)
            if (attrType != null) args.putInt(KEY_ATTR_TYPE_CODE, attrType.code)

            invoke(parent, MetaEditRuleDialogFragment(), args)
        }
    }


    // UI
    private var binding: DialogMetaRuleEditBinding? = null

    // === VARIABLES
    private lateinit var attrType: AttributeType
    private var isCreateMode: Boolean = false
    private var ruleId: Long = 0
    private val attributeTypes = ArrayList<AttributeType>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCreateMode = requireArguments().getBoolean(KEY_MODE_CREATE)
        ruleId = requireArguments().getLong(KEY_RULE_ID)
        val attrTypeCode = requireArguments().getInt(KEY_ATTR_TYPE_CODE, 99)
        attrType = AttributeType.searchByCode(attrTypeCode)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        binding = DialogMetaRuleEditBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding?.apply {
            if (isCreateMode) {
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
                attributeType.let {
                    it.entries = attributeTypes.map { a -> resources.getString(a.displayName) }
                    if (attrType != AttributeType.UNDEFINED) it.index =
                        attributeTypes.indexOf(attrType)
                }
            } else {
                val rule = loadRule()
                if (null == rule) {
                    dismissAllowingStateLoss()
                    return
                }
                sourceName.editText?.setText(rule.sourceName)
                targetName.editText?.setText(rule.targetName)
            }
        }

        binding?.apply {
            attributeType.setOnIndexChangeListener { updateNewBtnStates() }

            sourceName.editText?.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s != null) updateNewBtnStates()
                }

                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) {
                }
            })

            targetName.editText?.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s != null) updateNewBtnStates()
                }

                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) {
                }
            })

            attributeType.visibility = if (isCreateMode) View.VISIBLE else View.GONE
            actionNew.visibility = if (isCreateMode) View.VISIBLE else View.GONE
            actionEdit.visibility = if (!isCreateMode) View.VISIBLE else View.GONE
            actionRemove.visibility = if (!isCreateMode) View.VISIBLE else View.GONE

            updateNewBtnStates()

            actionNew.setOnClickListener { onCreateClick() }
            actionEdit.setOnClickListener { onEditClick() }
            actionRemove.setOnClickListener { onRemoveClick() }
        }
    }

    private fun updateNewBtnStates() {
        binding?.apply {
            val typeIndex = attributeType.index
            val source = sourceName.editText?.text ?: ""
            val target = targetName.editText?.text ?: ""
            val enabled =
                ((typeIndex > -1 || !isCreateMode) && source.isNotEmpty() && target.isNotEmpty())

            actionNew.isEnabled = enabled
            actionEdit.isEnabled = enabled
        }
    }

    private fun loadRule(): RenamingRule? {
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            return dao.selectRenamingRule(ruleId)
        } finally {
            dao.cleanup()
        }
    }

    private fun onCreateClick() {
        binding?.apply {
            val sourceName = sourceName.editText?.text.toString()
            val targetName = targetName.editText?.text.toString()
            if (!checkConsistency(sourceName, targetName)) return

            parent?.onCreateRule(
                attributeTypes[attributeType.index], sourceName, targetName
            )
        }
        dismissAllowingStateLoss()
    }

    private fun onEditClick() {
        binding?.apply {
            val sourceName = sourceName.editText?.text.toString()
            val targetName = targetName.editText?.text.toString()
            if (!checkConsistency(sourceName, targetName)) return

            parent?.onEditRule(
                ruleId, sourceName, targetName
            )
        }
        dismissAllowingStateLoss()
    }

    private fun checkConsistency(sourceName: String, targetName: String): Boolean {
        if (targetName.contains('*') && !sourceName.contains('*')) {
            binding?.let { bdg ->
                val snack = Snackbar.make(
                    bdg.root, R.string.meta_rule_wildcard, BaseTransientBottomBar.LENGTH_SHORT
                )
                snack.show()
            }
            return false
        }
        return true
    }

    private fun onRemoveClick() {
        parent?.onRemoveRule(ruleId)
        dismissAllowingStateLoss()
    }

    interface Parent {
        fun onCreateRule(type: AttributeType, source: String, target: String)
        fun onEditRule(id: Long, source: String, target: String)
        fun onRemoveRule(id: Long)
    }
}