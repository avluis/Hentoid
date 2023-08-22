package me.devsaki.hentoid.fragments.pin

import android.content.Context
import android.os.Bundle
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.ThemeHelper
import java.security.InvalidParameterException

abstract class PinDialogFragment : DialogFragment() {
    private val pinValue = StringBuilder(4)
    private lateinit var headerText: TextView
    private lateinit var placeholderImage1: View
    private lateinit var placeholderImage2: View
    private lateinit var placeholderImage3: View
    private lateinit var placeholderImage4: View

    protected abstract fun onPinAccept(pin: String)

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
        headerText = ViewCompat.requireViewById(rootView, R.id.text_header)
        placeholderImage1 = ViewCompat.requireViewById(rootView, R.id.image_placeholder_1)
        placeholderImage2 = ViewCompat.requireViewById(rootView, R.id.image_placeholder_2)
        placeholderImage3 = ViewCompat.requireViewById(rootView, R.id.image_placeholder_3)
        placeholderImage4 = ViewCompat.requireViewById(rootView, R.id.image_placeholder_4)
        val button0 = ViewCompat.requireViewById<View>(rootView, R.id.button_0)
        button0.setOnClickListener { onKeyClick("0") }
        val button1 = ViewCompat.requireViewById<View>(rootView, R.id.button_1)
        button1.setOnClickListener { onKeyClick("1") }
        val button2 = ViewCompat.requireViewById<View>(rootView, R.id.button_2)
        button2.setOnClickListener { onKeyClick("2") }
        val button3 = ViewCompat.requireViewById<View>(rootView, R.id.button_3)
        button3.setOnClickListener { onKeyClick("3") }
        val button4 = ViewCompat.requireViewById<View>(rootView, R.id.button_4)
        button4.setOnClickListener { onKeyClick("4") }
        val button5 = ViewCompat.requireViewById<View>(rootView, R.id.button_5)
        button5.setOnClickListener { onKeyClick("5") }
        val button6 = ViewCompat.requireViewById<View>(rootView, R.id.button_6)
        button6.setOnClickListener { onKeyClick("6") }
        val button7 = ViewCompat.requireViewById<View>(rootView, R.id.button_7)
        button7.setOnClickListener { onKeyClick("7") }
        val button8 = ViewCompat.requireViewById<View>(rootView, R.id.button_8)
        button8.setOnClickListener { onKeyClick("8") }
        val button9 = ViewCompat.requireViewById<View>(rootView, R.id.button_9)
        button9.setOnClickListener { onKeyClick("9") }
        val buttonBackspace = ViewCompat.requireViewById<View>(rootView, R.id.button_backspace)
        buttonBackspace.setOnClickListener { onBackspaceClick() }
        return rootView
    }

    override fun onStop() {
        super.onStop()
        dialog!!.cancel()
    }

    fun clearPin() {
        pinValue.setLength(0)
        placeholderImage1.visibility = View.INVISIBLE
        placeholderImage2.visibility = View.INVISIBLE
        placeholderImage3.visibility = View.INVISIBLE
        placeholderImage4.visibility = View.INVISIBLE
    }

    fun setHeaderText(@StringRes resId: Int) {
        headerText.setText(resId)
    }

    fun vibrate() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        vibrator?.vibrate(300)
    }

    private fun onKeyClick(s: String) {
        if (pinValue.length == 4) return
        pinValue.append(s)
        when (pinValue.length) {
            1 -> placeholderImage1.visibility = View.VISIBLE
            2 -> placeholderImage2.visibility = View.VISIBLE
            3 -> placeholderImage3.visibility = View.VISIBLE
            4 -> {
                placeholderImage4.visibility = View.VISIBLE
                onPinAccept(pinValue.toString())
            }

            else -> throw InvalidParameterException("Not implemented")
        }
    }

    private fun onBackspaceClick() {
        if (pinValue.isEmpty()) return
        pinValue.setLength(pinValue.length - 1)
        when (pinValue.length) {
            0 -> placeholderImage1.visibility = View.INVISIBLE
            1 -> placeholderImage2.visibility = View.INVISIBLE
            2 -> placeholderImage3.visibility = View.INVISIBLE
            3 -> placeholderImage4.visibility = View.INVISIBLE
            else -> throw InvalidParameterException("Not implemented")
        }
    }
}