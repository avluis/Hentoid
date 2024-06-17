package me.devsaki.hentoid.fragments.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.databinding.DialogMetaNewAttributeBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.viewholders.AttributeItem

/**
 * Dialog to pick a picture in a content gallery
 */
class AttributeTypePickerDialogFragment :
    BaseDialogFragment<AttributeTypePickerDialogFragment.Parent>() {

    companion object {
        const val KEY_NAME = "name"

        fun invoke(parent: FragmentActivity, newAttrName: String) {
            val args = Bundle()
            args.putString(KEY_NAME, newAttrName)
            invoke(parent, AttributeTypePickerDialogFragment(), args)
        }
    }

    // UI
    private var binding: DialogMetaNewAttributeBinding? = null
    private val itemAdapter = ItemAdapter<AttributeItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    // === VARIABLES
    private lateinit var newAttrName: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val newName = requireArguments().getString(KEY_NAME)
        require(!newName.isNullOrEmpty()) { "No images provided" }

        newAttrName = newName
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogMetaNewAttributeBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding?.title?.text = resources.getString(R.string.meta_choose_type, newAttrName)

        itemAdapter.set(
            listOf(
                AttributeType.ARTIST,
                AttributeType.CIRCLE,
                AttributeType.SERIE,
                AttributeType.CHARACTER,
                AttributeType.TAG,
                AttributeType.LANGUAGE
            )
                .map {
                    AttributeItem(
                        Attribute(
                            type = it,
                            name = rootView.resources.getString(it.displayName)
                        ), false
                    )
                }
        )

        fastAdapter.onClickListener =
            { _: View?, _: IAdapter<AttributeItem>, i: AttributeItem, _: Int ->
                onItemClick(i)
            }

        binding?.recyclerView?.adapter = fastAdapter
    }

    /**
     * Callback for image item click
     *
     * @param item AttributeItem that has been clicked on
     */
    private fun onItemClick(item: AttributeItem): Boolean {
        parent?.onNewAttributeSelected(newAttrName, item.attribute.type)
        dismissAllowingStateLoss()
        return true
    }

    interface Parent {
        fun onNewAttributeSelected(name: String, type: AttributeType)
    }
}