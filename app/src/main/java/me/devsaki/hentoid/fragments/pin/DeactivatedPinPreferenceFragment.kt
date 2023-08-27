package me.devsaki.hentoid.fragments.pin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp.Companion.setUnlocked

class DeactivatedPinPreferenceFragment : Fragment(), ActivatePinDialogFragment.Parent {
    private lateinit var onSwitch: MaterialSwitch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View =
            inflater.inflate(R.layout.fragment_pin_preference_off, container, false)
        val toolbar = ViewCompat.requireViewById<Toolbar>(rootView, R.id.toolbar)
        toolbar.setNavigationOnClickListener { requireActivity().finish() }
        onSwitch = ViewCompat.requireViewById(rootView, R.id.switch_on)
        onSwitch.setOnClickListener { onOnClick() }
        return rootView
    }

    override fun onPinActivateSuccess() {
        Snackbar.make(onSwitch, R.string.app_lock_enable, BaseTransientBottomBar.LENGTH_SHORT)
            .show()
        setUnlocked(true) // Now that PIN lock is enabled, the app needs to be marked as currently unlocked to avoid showing an unnecessary PIN dialog at next navigation action
        parentFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, ActivatedPinPreferenceFragment())
            .commit()
    }

    override fun onPinActivateCancel() {
        onSwitch.isChecked = false
    }

    private fun onOnClick() {
        val fragment = ActivatePinDialogFragment()
        fragment.show(childFragmentManager, null)
    }
}