package me.devsaki.hentoid.fragments.pin;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;

import java.security.InvalidParameterException;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

public final class ResetPinDialogFragment extends PinDialogFragment {

    private int step;

    private String proposedPin;

    private Parent parent;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        parent = (Parent) getParentFragment();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setHeaderText(R.string.pin_current);
    }

    @Override
    protected void onPinAccept(String pin) {
        switch (step) {
            case 0:
                step0(pin);
                break;
            case 1:
                step1(pin);
                break;
            case 2:
                step2(pin);
                break;
            default:
                throw new InvalidParameterException("Not implemented");
        }
    }

    private void step0(String pin) {
        if (Preferences.getAppLockPin().equals(pin)) {
            step = 1;
            setHeaderText(R.string.pin_new);
        } else {
            vibrate();
        }
        clearPin();
    }

    private void step1(String pin) {
        proposedPin = pin;
        step = 2;
        clearPin();
        setHeaderText(R.string.pin_new_confirm);
    }

    private void step2(String pin) {
        if (proposedPin.equals(pin)) {
            Preferences.setAppLockPin(pin);
            dismiss();
            parent.onPinResetSuccess();
        } else {
            proposedPin = null;
            step = 1;
            clearPin();
            vibrate();
            setHeaderText(R.string.pin_new);
        }
    }

    public interface Parent {

        void onPinResetSuccess();
    }
}
