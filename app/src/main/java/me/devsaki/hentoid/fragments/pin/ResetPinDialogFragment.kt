package me.devsaki.hentoid.fragments.pin

import android.content.Context
import android.os.Bundle
import android.view.View
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.Preferences
import java.security.InvalidParameterException

class ResetPinDialogFragment : PinDialogFragment() {

    private var step = 0

    private var proposedPin: String? = null

    private var parent: Parent? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parent = parentFragment as Parent?
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHeaderText(R.string.pin_current)
    }

    override fun onPinAccept(pin: String) {
        when (step) {
            0 -> step0(pin)
            1 -> step1(pin)
            2 -> step2(pin)
            else -> throw InvalidParameterException("Not implemented")
        }
    }

    private fun step0(pin: String) {
        if (Preferences.getAppLockPin() == pin) {
            step = 1
            setHeaderText(R.string.pin_new)
        } else {
            vibrate()
        }
        clearPin()
    }

    private fun step1(pin: String) {
        proposedPin = pin
        step = 2
        clearPin()
        setHeaderText(R.string.pin_new_confirm)
    }

    private fun step2(pin: String) {
        if (proposedPin == pin) {
            Preferences.setAppLockPin(pin)
            dismiss()
            parent?.onPinResetSuccess()
        } else {
            proposedPin = null
            step = 1
            clearPin()
            vibrate()
            setHeaderText(R.string.pin_new)
        }
    }

    interface Parent {
        fun onPinResetSuccess()
    }
}