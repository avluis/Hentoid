package me.devsaki.hentoid.fragments.pin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricType
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BiometricsHelper
import me.devsaki.hentoid.core.HentoidApp.Companion.setUnlocked
import me.devsaki.hentoid.core.startBiometric
import me.devsaki.hentoid.databinding.FragmentPinPreferenceOffBinding
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings

class DeactivatedLockPreferenceFragment : Fragment(), ActivatePinDialogFragment.Parent {
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
                .replace(android.R.id.content, ActivatedLockPreferenceFragment())
                .commit()
        }
    }

    override fun onPinActivateCancel() {
        binding?.lockType?.index = 0 // Off
    }

    private fun onBiometricsResult(result: Boolean) {
        if (result) {
            Settings.lockType = 2
            Preferences.setAppLockPin("")
        }
    }

    private fun onLockTypeChanged(index: Int) {
        when (index) {
            1 -> ActivatePinDialogFragment().show(childFragmentManager, null) // PIN
            2 -> { // Biometrics
                val bestBM = BiometricsHelper.detectBestBiometric()
                if (bestBM != null) {
                    activity?.startBiometric(
                        BiometricAuthRequest(bestBM.api, bestBM.type),
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
}