package me.devsaki.hentoid.fragments.pin

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.Preferences

class UnlockPinDialogFragment : PinDialogFragment() {
    companion object {
        operator fun invoke(mgr: FragmentManager) {
            val fragment = UnlockPinDialogFragment()
            fragment.isCancelable = false
            fragment.show(mgr, null)
        }
    }

    private var parent: Parent? = null


    override fun onAttach(context: Context) {
        super.onAttach(context)
        parent = context as Parent
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHeaderText(R.string.app_lock_pin)
    }

    override fun onPinAccept(pin: String) {
        if (Preferences.getAppLockPin() == pin) {
            dismiss()
            parent?.onPinSuccess()
        } else {
            vibrate()
            clearPin()
        }
    }

    interface Parent {
        fun onPinSuccess()
    }
}