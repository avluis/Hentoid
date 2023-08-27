package me.devsaki.hentoid.fragments.pin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentPinPreferenceOffBinding
import me.devsaki.hentoid.util.ThemeHelper

class BiometricsDialogFragment : DialogFragment() {

    private var binding: FragmentPinPreferenceOffBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setStyle(
            requireActivity(),
            this,
            STYLE_NORMAL,
            R.style.Theme_Light_PinEntryDialog
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView: View = inflater.inflate(R.layout.fragment_pin_dialog, container, false)
        // TODO ?
        return rootView
    }

    override fun onStop() {
        super.onStop()
        dialog!!.cancel()
    }
}