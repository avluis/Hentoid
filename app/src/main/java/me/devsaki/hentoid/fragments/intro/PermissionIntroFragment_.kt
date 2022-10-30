package me.devsaki.hentoid.fragments.intro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.appintro.SlidePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.IntroActivity_
import me.devsaki.hentoid.databinding.IntroSlide02Binding
import me.devsaki.hentoid.util.Preferences

class PermissionIntroFragment_ :
    Fragment(R.layout.intro_slide_02), SlidePolicy {

    private lateinit var parentActivity: IntroActivity_
    private var _binding: IntroSlide02Binding? = null
    private val binding get() = _binding!!

    // Ask for permissions
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                lifecycleScope.launch {
                    delay(75)
                    parentActivity.nextStep()
                }
            } else {
                binding.modeSelect.check(R.id.mode_browser)
            }
        }


    // If user should be allowed to leave this slide
    override val isPolicyRespected: Boolean
        get() {
            if (R.id.mode_browser == binding.modeSelect.checkedButtonId) return true
            val permissionCode =
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            return (permissionCode == PackageManager.PERMISSION_GRANTED)
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is IntroActivity_) {
            parentActivity = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = IntroSlide02Binding.inflate(inflater, container, false)

        binding.modeSelect.addOnButtonCheckedListener { _, _, b ->
            if (!b) return@addOnButtonCheckedListener

            Preferences.setBrowserMode(binding.modeBrowser.isChecked)
            binding.descTxt.setText(if (binding.modeBrowser.isChecked) R.string.slide_02_browser_mode_description else R.string.slide_02_library_mode_description)
        }

        val browserMode = Preferences.getBrowserMode()
        if (browserMode != null) binding.modeSelect.check(if (browserMode) R.id.mode_browser else R.id.mode_library)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onUserIllegallyRequestedNextPage() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) -> {
                parentActivity.nextStep()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}