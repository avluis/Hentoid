package me.devsaki.hentoid.fragments.pin;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

public final class ActivatePinDialogFragment extends PinDialogFragment {

    private Parent parent;

    private String proposedPin;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        parent = (Parent) getParentFragment();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setHeaderText(R.string.pin_new);
    }

    @Override
    protected void onPinAccept(String pin) {
        if (proposedPin == null) {
            proposedPin = pin;
            clearPin();
            setHeaderText(R.string.pin_new_confirm);
        } else if (proposedPin.equals(pin)) {
            Preferences.setAppLockPin(proposedPin);
            dismiss();
            parent.onPinActivateSuccess();
        } else {
            proposedPin = null;
            setHeaderText(R.string.pin_new);
            vibrate();
            clearPin();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        parent.onPinActivateCancel();
    }

    public interface Parent {

        void onPinActivateSuccess();

        void onPinActivateCancel();
    }
}
