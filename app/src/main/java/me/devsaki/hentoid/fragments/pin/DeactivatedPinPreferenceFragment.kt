package me.devsaki.hentoid.fragments.pin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp.Companion.setUnlocked
import me.devsaki.hentoid.databinding.FragmentPinPreferenceOffBinding

class DeactivatedPinPreferenceFragment : Fragment(), ActivatePinDialogFragment.Parent {
    private var binding: FragmentPinPreferenceOffBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPinPreferenceOffBinding.inflate(inflater, container, false)
        binding?.apply {
            toolbar.setNavigationOnClickListener { requireActivity().finish() }
            lockType.index = 0
            lockType.setOnIndexChangeListener { i -> onLockTypeChanged(i) }
        }
        return binding!!.root
    }

    override fun onPinActivateSuccess() {
        binding?.apply {
            Snackbar.make(root, R.string.app_lock_enable, BaseTransientBottomBar.LENGTH_SHORT)
                .show()
            setUnlocked(true) // Now that PIN lock is enabled, the app needs to be marked as currently unlocked to avoid showing an unnecessary PIN dialog at next navigation action
            parentFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, ActivatedPinPreferenceFragment())
                .commit()
        }
    }

    private fun onLockTypeChanged(index: Int) {
        when (index) {
            1 -> ActivatePinDialogFragment().show(childFragmentManager, null) // PIN
            // TODO Biometrics
        }
    }

    override fun onPinActivateCancel() {
        binding?.lockType?.index = 0 // Off
    }
}