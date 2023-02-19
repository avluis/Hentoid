package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.DialogToolsMassDeleteBinding

class MassDeleteFragment : DialogFragment(R.layout.dialog_tools_mass_delete) {

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
        super.onDestroyView()
        binding = null
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
                return@withContext dao.selectStoredContentFavIds(bookPrefs, groupPrefs).size
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
        // TODO align actual query used by the worker with new query used here
    }


    companion object {
        fun invoke(fragmentManager: FragmentManager) {
            val fragment = MassDeleteFragment()
            fragment.show(fragmentManager, null)
        }
    }
}