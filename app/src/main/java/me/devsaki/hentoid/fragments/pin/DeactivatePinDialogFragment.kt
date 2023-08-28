package me.devsaki.hentoid.fragments.pin

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings

class DeactivatePinDialogFragment : PinDialogFragment() {
    private var parent: Parent? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parent = parentFragment as Parent?
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHeaderText(R.string.pin_current)
    }

    override fun onPinAccept(pin: String) {
        if (Preferences.getAppLockPin() == pin) {
            Settings.lockType = 0
            Preferences.setAppLockPin("")
            dismiss()
            parent?.onPinDeactivateSuccess()
        } else {
            vibrate()
            clearPin()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        parent?.onPinDeactivateCancel()
    }

    interface Parent {
        fun onPinDeactivateSuccess()
        fun onPinDeactivateCancel()
    }
}