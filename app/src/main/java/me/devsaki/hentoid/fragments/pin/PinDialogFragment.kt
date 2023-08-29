package me.devsaki.hentoid.fragments.pin

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentPinDialogBinding
import me.devsaki.hentoid.util.ThemeHelper
import java.security.InvalidParameterException

abstract class PinDialogFragment : DialogFragment() {
    private val pinValue = StringBuilder(4)
    private var binding: FragmentPinDialogBinding? = null

    protected abstract fun onPinAccept(pin: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setStyle(
            requireActivity(), this, STYLE_NORMAL, R.style.Theme_Light_PinEntryDialog
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPinDialogBinding.inflate(inflater, container, false)
        binding?.apply {
            button0.setOnClickListener { onKeyClick("0") }
            button1.setOnClickListener { onKeyClick("1") }
            button2.setOnClickListener { onKeyClick("2") }
            button3.setOnClickListener { onKeyClick("3") }
            button4.setOnClickListener { onKeyClick("4") }
            button5.setOnClickListener { onKeyClick("5") }
            button6.setOnClickListener { onKeyClick("6") }
            button7.setOnClickListener { onKeyClick("7") }
            button8.setOnClickListener { onKeyClick("8") }
            button9.setOnClickListener { onKeyClick("9") }
            buttonBackspace.setOnClickListener { onBackspaceClick() }
        }
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onStop() {
        super.onStop()
        dialog!!.cancel()
    }

    fun clearPin() {
        pinValue.setLength(0)
        binding?.apply {
            imagePlaceholder1.visibility = View.INVISIBLE
            imagePlaceholder2.visibility = View.INVISIBLE
            imagePlaceholder3.visibility = View.INVISIBLE
            imagePlaceholder4.visibility = View.INVISIBLE
        }
    }

    fun setHeaderText(@StringRes resId: Int) {
        binding?.textHeader?.setText(resId)
    }

    @Suppress("DEPRECATION")
    fun vibrate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
            vibrator?.vibrate(300)
        } else {
            (requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).apply {
                if (defaultVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    vibrate(
                        CombinedVibration.createParallel(
                            VibrationEffect.startComposition()
                                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
                                .compose()
                        )
                    )
                }
            }
        }
    }

    private fun onKeyClick(s: String) {
        if (pinValue.length == 4) return
        pinValue.append(s)
        binding?.apply {
            when (pinValue.length) {
                1 -> imagePlaceholder1.visibility = View.VISIBLE
                2 -> imagePlaceholder2.visibility = View.VISIBLE
                3 -> imagePlaceholder3.visibility = View.VISIBLE
                4 -> {
                    imagePlaceholder4.visibility = View.VISIBLE
                    onPinAccept(pinValue.toString())
                }

                else -> throw InvalidParameterException("Not implemented")
            }
        }
    }

    private fun onBackspaceClick() {
        if (pinValue.isEmpty()) return
        pinValue.setLength(pinValue.length - 1)
        binding?.apply {
            when (pinValue.length) {
                0 -> imagePlaceholder1.visibility = View.INVISIBLE
                1 -> imagePlaceholder2.visibility = View.INVISIBLE
                2 -> imagePlaceholder3.visibility = View.INVISIBLE
                3 -> imagePlaceholder4.visibility = View.INVISIBLE
                else -> throw InvalidParameterException("Not implemented")
            }
        }
    }
}