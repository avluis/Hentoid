package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.DialogToolsMassDeleteBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment

class MassDeleteDialogFragment : BaseDialogFragment<MassDeleteDialogFragment.Parent>() {

    companion object {
        fun invoke(fragment: Fragment) {
            invoke(fragment, MassDeleteDialogFragment())
        }
    }

    // == UI
    private var binding: DialogToolsMassDeleteBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogToolsMassDeleteBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding?.apply {
            actionButton.setOnClickListener { onActionClick() }
            keepFavBooks.setOnCheckedChangeListener { _, _ -> refresh() }
            keepFavGroups.setOnCheckedChangeListener { _, _ -> refresh() }
        }

        refresh()
    }

    private suspend fun getFavCount(bookPrefs: Boolean, groupPrefs: Boolean): Int {
        return withContext(Dispatchers.IO) {
            val dao = ObjectBoxDAO(requireContext())
            try {
                return@withContext dao.selectStoredFavContentIds(bookPrefs, groupPrefs).size
            } finally {
                dao.cleanup()
            }
        }
    }

    private fun refresh() {
        binding?.apply {
            actionButton.isEnabled = (keepFavBooks.isChecked || keepFavGroups.isChecked)
            if (actionButton.isEnabled) {
                lifecycleScope.launch {
                    val favCount = getFavCount(keepFavBooks.isChecked, keepFavGroups.isChecked)
                    bookCount.text =
                        resources.getQuantityString(R.plurals.book_keep, favCount, favCount)
                    bookCount.isVisible = true
                }
            } else {
                bookCount.isVisible = false
            }
        }
    }

    private fun onActionClick() {
        binding?.apply {
            parent?.onMassDelete(keepFavBooks.isChecked, keepFavGroups.isChecked)
        }
        dismissAllowingStateLoss()
    }

    interface Parent {
        fun onMassDelete(keepBookPrefs: Boolean, keepGroupPrefs: Boolean)
    }
}