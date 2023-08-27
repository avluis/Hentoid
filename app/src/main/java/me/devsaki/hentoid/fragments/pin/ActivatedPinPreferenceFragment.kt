package me.devsaki.hentoid.fragments.pin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentPinPreferenceOnBinding
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings

class ActivatedPinPreferenceFragment : Fragment(), DeactivatePinDialogFragment.Parent,
    ResetPinDialogFragment.Parent {

    private var initalLockType: Int = 0

    private var binding: FragmentPinPreferenceOnBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPinPreferenceOnBinding.inflate(inflater, container, false)
        binding?.apply {
            toolbar.setNavigationOnClickListener { requireActivity().finish() }
            initalLockType = Settings.lockType
            lockType.index = initalLockType
            lockType.setOnIndexChangeListener { i -> onLockTypeChanged(i) }

            val lockOnAppRestoredEnabled = Preferences.isLockOnAppRestore()
            switchLockOnRestore.isChecked = lockOnAppRestoredEnabled
            switchLockOnRestore.setOnCheckedChangeListener { _: CompoundButton?, v: Boolean ->
                onLockOnAppRestoreClick(v)
            }
            lockTimer.isVisible = lockOnAppRestoredEnabled
            lockTimer.setSelection(Preferences.getLockTimer())
            lockTimer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    Preferences.setLockTimer(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Nothing to do
                }
            }
            textResetPin.setOnClickListener { onResetClick() }
        }
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onPinDeactivateSuccess() {
        binding?.apply {
            Snackbar.make(
                root,
                R.string.app_lock_disabled,
                BaseTransientBottomBar.LENGTH_SHORT
            )
                .show()
            parentFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, DeactivatedPinPreferenceFragment())
                .commit()
        }
    }

    override fun onPinDeactivateCancel() {
        binding?.lockType?.index = initalLockType
    }

    override fun onPinResetSuccess() {
        binding?.apply {
            Snackbar.make(root, R.string.pin_reset_success, BaseTransientBottomBar.LENGTH_SHORT)
                .show()
        }
    }

    private fun onLockTypeChanged(index: Int) {
        if (0 == index) DeactivatePinDialogFragment().show(childFragmentManager, null)
        else if (index != initalLockType) {
            // TODO : PIN & Biometrics
        }
    }

    private fun onLockOnAppRestoreClick(newValue: Boolean) {
        Preferences.setLockOnAppRestore(newValue)
        binding?.lockTimer?.isVisible = newValue
    }

    private fun onResetClick() {
        val fragment = ResetPinDialogFragment()
        fragment.show(childFragmentManager, null)
    }
}