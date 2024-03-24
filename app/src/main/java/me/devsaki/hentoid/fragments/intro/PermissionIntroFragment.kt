package me.devsaki.hentoid.fragments.intro

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.appintro.SlidePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.IntroActivity
import me.devsaki.hentoid.databinding.IntroSlide02Binding
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.file.checkExternalStorageReadWritePermission

class PermissionIntroFragment : Fragment(R.layout.intro_slide_02), SlidePolicy {

    private lateinit var parentActivity: IntroActivity
    private var binding: IntroSlide02Binding? = null

    // Ask for permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            lifecycleScope.launch {
                delay(75)
                parentActivity.nextStep()
            }
        } else {
            binding?.modeSelect?.check(R.id.mode_browser)
        }
    }


    // If user should be allowed to leave this slide
    override val isPolicyRespected: Boolean
        get() {
            binding?.apply {
                if (R.id.mode_browser == modeSelect.checkedButtonId) return true
            }
            return requireActivity().checkExternalStorageReadWritePermission()
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is IntroActivity) {
            parentActivity = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = IntroSlide02Binding.inflate(inflater, container, false)

        binding?.apply {
            modeSelect.addOnButtonCheckedListener { _, _, b ->
                if (!b) return@addOnButtonCheckedListener

                Preferences.setBrowserMode(modeBrowser.isChecked)
                descTxt.setText(
                    if (modeBrowser.isChecked) R.string.slide_02_browser_mode_description
                    else R.string.slide_02_library_mode_description
                )
            }
            modeSelect.check(if (Preferences.isBrowserMode()) R.id.mode_browser else R.id.mode_library)
        }

        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onUserIllegallyRequestedNextPage() {
        if (requireActivity().checkExternalStorageReadWritePermission()) parentActivity.nextStep()
        else requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}