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
import dev.skomlach.biometric.compat.BiometricAuthRequest
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BiometricsHelper
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.core.startBiometric
import me.devsaki.hentoid.databinding.FragmentPinPreferenceOnBinding
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings

class LockPreferenceFragment : Fragment(), DeactivatePinDialogFragment.Parent,
    ResetPinDialogFragment.Parent, ActivatePinDialogFragment.Parent {

    private var initalLockType: Int = 0

    private var binding: FragmentPinPreferenceOnBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPinPreferenceOnBinding.inflate(inflater, container, false)
        binding?.apply {
            refresh()
            toolbar.setNavigationOnClickListener { requireActivity().finish() }
            lockType.setOnIndexChangeListener { i -> onLockTypeChanged(i) }
            switchLockOnRestore.setOnCheckedChangeListener { _: CompoundButton?, v: Boolean ->
                onLockOnAppRestoreClick(v)
            }
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

    private fun refresh() {
        val lockTypeVal = Settings.lockType
        binding?.apply {
            switchLockOnRestore.isVisible = (lockTypeVal > 0)
            textResetPin.isVisible = (1 == lockTypeVal)
            lockType.index = lockTypeVal
            val lockOnAppRestoredEnabled = Preferences.isLockOnAppRestore()
            switchLockOnRestore.isChecked = lockOnAppRestoredEnabled
            lockTimer.isVisible = lockOnAppRestoredEnabled
            lockTimer.setSelection(Preferences.getLockTimer())
        }
    }

    override fun onPinDeactivateSuccess() {
        binding?.apply {
            refresh()
            Snackbar.make(
                root,
                R.string.app_lock_disabled,
                BaseTransientBottomBar.LENGTH_SHORT
            ).show()
        }
    }

    override fun onPinDeactivateCancel() {
        refresh()
    }

    override fun onPinResetSuccess() {
        binding?.apply {
            Snackbar.make(root, R.string.pin_reset_success, BaseTransientBottomBar.LENGTH_SHORT)
                .show()
        }
    }

    override fun onPinActivateSuccess() {
        binding?.apply {
            refresh()
            Snackbar.make(root, R.string.app_lock_enable, BaseTransientBottomBar.LENGTH_SHORT)
                .show()
            HentoidApp.setUnlocked(true) // Now that PIN lock is enabled, the app needs to be marked as currently unlocked to avoid showing an unnecessary PIN dialog at next navigation action
            parentFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, LockPreferenceFragment())
                .commit()
        }
    }

    override fun onPinActivateCancel() {
        refresh()
    }

    private fun onBiometricsResult(result: Boolean) {
        if (result) {
            Settings.lockType = 2
            Preferences.setAppLockPin("")
        }
        refresh()
    }

    private fun onLockTypeChanged(index: Int) {
        if (0 == index) {
            if (1 == initalLockType) DeactivatePinDialogFragment().show(childFragmentManager, null)
            else {
                Settings.lockType = 0
                onPinDeactivateSuccess()
            }
        } else if (index != initalLockType) {
            if (1 == index) { // PIN
                ActivatePinDialogFragment().show(childFragmentManager, null)
            } else { // Biometrics
                val bestBM = BiometricsHelper.detectBestBiometric()
                if (bestBM != null) {
                    activity?.startBiometric(
                        BiometricAuthRequest(bestBM.api, bestBM.type), true,
                        this::onBiometricsResult
                    )
                } else {
                    binding?.lockType?.index = 0 // Off
                    binding?.apply {
                        Snackbar.make(
                            root,
                            R.string.app_lock_biometrics_fail,
                            BaseTransientBottomBar.LENGTH_LONG
                        ).show()
                    }
                }
            }
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