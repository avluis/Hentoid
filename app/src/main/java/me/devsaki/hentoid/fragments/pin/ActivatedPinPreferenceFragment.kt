package me.devsaki.hentoid.fragments.pin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.Spinner
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.Preferences

class ActivatedPinPreferenceFragment : Fragment(), DeactivatePinDialogFragment.Parent,
    ResetPinDialogFragment.Parent, AdapterView.OnItemSelectedListener {

    private lateinit var offSwitch: MaterialSwitch
    private lateinit var lockDelaySpinner: Spinner

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View = inflater.inflate(R.layout.fragment_pin_preference_on, container, false)
        val toolbar = ViewCompat.requireViewById<Toolbar>(rootView, R.id.toolbar)
        toolbar.setNavigationOnClickListener { requireActivity().finish() }
        offSwitch = ViewCompat.requireViewById(rootView, R.id.switch_off)
        offSwitch.setOnClickListener { onOffClick() }
        val lockOnAppRestoredEnabled = Preferences.isLockOnAppRestore()
        val lockOnAppRestored =
            ViewCompat.requireViewById<MaterialSwitch>(rootView, R.id.switch_lock_on_restore)
        lockOnAppRestored.isChecked = lockOnAppRestoredEnabled
        lockOnAppRestored.setOnCheckedChangeListener { _: CompoundButton?, v: Boolean ->
            onLockOnAppRestoreClick(v)
        }
        val lockTimer = Preferences.getLockTimer()
        lockDelaySpinner = ViewCompat.requireViewById(rootView, R.id.lock_timer)
        lockDelaySpinner.visibility = if (lockOnAppRestoredEnabled) View.VISIBLE else View.GONE
        lockDelaySpinner.setSelection(lockTimer)
        lockDelaySpinner.onItemSelectedListener = this
        val resetButton = ViewCompat.requireViewById<View>(rootView, R.id.text_reset_pin)
        resetButton.setOnClickListener { onResetClick() }
        return rootView
    }

    override fun onPinDeactivateSuccess() {
        Snackbar.make(offSwitch, R.string.app_lock_disabled, BaseTransientBottomBar.LENGTH_SHORT)
            .show()
        parentFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, DeactivatedPinPreferenceFragment())
            .commit()
    }

    override fun onPinDeactivateCancel() {
        offSwitch.isChecked = true
    }

    override fun onPinResetSuccess() {
        Snackbar.make(offSwitch, R.string.pin_reset_success, BaseTransientBottomBar.LENGTH_SHORT)
            .show()
    }

    private fun onOffClick() {
        val fragment = DeactivatePinDialogFragment()
        fragment.show(childFragmentManager, null)
    }

    private fun onLockOnAppRestoreClick(newValue: Boolean) {
        Preferences.setLockOnAppRestore(newValue)
        lockDelaySpinner.visibility = if (newValue) View.VISIBLE else View.GONE
    }

    private fun onResetClick() {
        val fragment = ResetPinDialogFragment()
        fragment.show(childFragmentManager, null)
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        Preferences.setLockTimer(position)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Do nothing
    }
}