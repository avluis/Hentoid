package me.devsaki.hentoid.fragments.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputLayout
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.views.ListPickerView

/**
 * Dialog to select or create a custom group
 */
class ChangeGroupDialogFragment : DialogFragment() {
    companion object {
        private const val BOOK_IDS = "BOOK_IDS"

        operator fun invoke(parent: Fragment, bookIds: LongArray) {
            val args = Bundle()
            args.putLongArray(BOOK_IDS, bookIds)
            val dialogFragment = ChangeGroupDialogFragment()
            dialogFragment.arguments = args
            dialogFragment.show(parent.childFragmentManager, null)
        }
    }

    private var bookIds: LongArray? = null
    private var customGroups: List<Group>? = null

    private lateinit var existingRadio: RadioButton
    private lateinit var existingSpin: ListPickerView
    private lateinit var newRadio: RadioButton
    private lateinit var newNameTxt: TextInputLayout
    private lateinit var detachRadio: RadioButton


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_library_change_group, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        if (arguments != null) {
            bookIds = requireArguments().getLongArray(BOOK_IDS)
            existingRadio = rootView.findViewById(R.id.change_group_existing_radio)
            existingSpin = rootView.findViewById(R.id.change_group_existing_list)
            newRadio = rootView.findViewById(R.id.change_group_new_radio)
            newNameTxt = rootView.findViewById(R.id.change_group_new_name)
            detachRadio = rootView.findViewById(R.id.remove_group_radio)

            // Get existing custom groups
            val dao: CollectionDAO = ObjectBoxDAO(requireContext())
            try {
                customGroups = dao.selectGroups(
                    Grouping.CUSTOM.id,
                    0
                ).toList()

                // Don't select the "Ungrouped" group there
                if (customGroups!!.isNotEmpty()) { // "Existing group" by default
                    existingRadio.isChecked = true
                    existingSpin.visibility = View.VISIBLE
                    existingSpin.entries = customGroups!!.map { g -> g.name }

                    // If there's only one content selected, indicate its group
                    if (1 == bookIds!!.size) {
                        val gi = dao.selectGroupItems(bookIds!![0], Grouping.CUSTOM)
                        if (gi.isNotEmpty()) for (i in customGroups!!.indices) {
                            if (gi[0].group.targetId == customGroups!![i].id) {
                                existingSpin.index = i
                                break
                            }
                        } else  // If no group attached, no need to detach from it (!)
                            detachRadio.visibility = View.GONE
                    }
                } else { // If none of them exist, "new group" is suggested by default
                    existingRadio.visibility = View.GONE
                    newRadio.isChecked = true
                    newNameTxt.visibility = View.VISIBLE
                }

                // Radio logic
                existingRadio.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    onExistingRadioSelect(b)
                }
                newRadio.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    onNewRadioSelect(b)
                }
                detachRadio.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    onDetachRadioSelect(b)
                }

                // Item click listener
                rootView.findViewById<View>(R.id.action_button).setOnClickListener { onOkClick() }
            } finally {
                dao.cleanup()
            }
        }
    }

    private fun onExistingRadioSelect(isChecked: Boolean) {
        if (isChecked) {
            existingSpin.visibility = View.VISIBLE
            newNameTxt.visibility = View.GONE
            newRadio.isChecked = false
            detachRadio.isChecked = false
        }
    }

    private fun onNewRadioSelect(isChecked: Boolean) {
        if (isChecked) {
            existingSpin.visibility = View.GONE
            newNameTxt.visibility = View.VISIBLE
            existingRadio.isChecked = false
            detachRadio.isChecked = false
        }
    }

    private fun onDetachRadioSelect(isChecked: Boolean) {
        if (isChecked) {
            existingSpin.visibility = View.GONE
            newNameTxt.visibility = View.GONE
            newRadio.isChecked = false
            existingRadio.isChecked = false
        }
    }

    private fun onOkClick() {
        val vmFactory = ViewModelFactory(requireActivity().application)
        val viewModel =
            ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        if (existingRadio.isChecked) {
            if (existingSpin.index > -1) {
                viewModel.moveContentsToCustomGroup(
                    bookIds, customGroups!![existingSpin.index]
                ) { getParent()?.onChangeGroupSuccess() }
                dismissAllowingStateLoss()
            } else {
                ToastHelper.toast(R.string.group_not_selected)
            }
        } else if (detachRadio.isChecked) {
            viewModel.moveContentsToCustomGroup(
                bookIds,
                null
            ) { getParent()?.onChangeGroupSuccess() }
            dismissAllowingStateLoss()
        } else newNameTxt.editText?.let { edit -> // New group
            val newNameStr = edit.text.toString().trim { it <= ' ' }
            if (newNameStr.isNotEmpty()) {
                val groupMatchingName =
                    customGroups!!.filter { g -> g.name.equals(newNameStr, ignoreCase = true) }
                if (groupMatchingName.isEmpty()) { // No existing group with same name -> OK
                    viewModel.moveContentsToNewCustomGroup(
                        bookIds, newNameStr
                    ) { getParent()?.onChangeGroupSuccess() }
                    dismissAllowingStateLoss()
                } else {
                    ToastHelper.toast(R.string.group_name_exists)
                }
            } else {
                ToastHelper.toast(R.string.group_name_empty)
            }
        }
    }

    private fun getParent(): Parent? {
        return parentFragment as Parent?
    }

    interface Parent {
        fun onChangeGroupSuccess()
    }
}