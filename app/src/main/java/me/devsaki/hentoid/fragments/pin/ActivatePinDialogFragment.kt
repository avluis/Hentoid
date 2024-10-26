package me.devsaki.hentoid.fragments.pin

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.Settings

class ActivatePinDialogFragment : PinDialogFragment() {

    private var parent: Parent? = null

    private var proposedPin: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parent = parentFragment as Parent?
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHeaderText(R.string.pin_new)
    }

    override fun onPinAccept(pin: String) {
        when (proposedPin) {
            null -> {
                proposedPin = pin
                clearPin()
                setHeaderText(R.string.pin_new_confirm)
            }

            pin -> {
                Settings.appLockPin = pin
                Settings.lockType = 1
                dismiss()
                parent?.onPinActivateSuccess()
            }

            else -> {
                proposedPin = null
                setHeaderText(R.string.pin_new)
                vibrate()
                clearPin()
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        parent?.onPinActivateCancel()
    }

    interface Parent {
        fun onPinActivateSuccess()
        fun onPinActivateCancel()
    }
}