package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.DialogPrefsCookiesBinding

class CookiesDialogFragment : DialogFragment(R.layout.dialog_prefs_cookies) {

    // == UI
    private var binding: DialogPrefsCookiesBinding? = null

    // === VARIABLES
    private var parent: Parent? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parent = parentFragment as Parent
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogPrefsCookiesBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        parent = null
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding?.apply {
            actionButton.setOnClickListener { onActionClick() }

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
            ///
        }
    }

    private fun onActionClick() {
        binding?.apply {
            ///
        }
        dismissAllowingStateLoss()
    }


    companion object {
        fun invoke(fragmentManager: FragmentManager) {
            val fragment = CookiesDialogFragment()
            fragment.show(fragmentManager, null)
        }

        interface Parent {
            fun onMassDelete(keepBookPrefs: Boolean, keepGroupPrefs: Boolean)
        }
    }
}