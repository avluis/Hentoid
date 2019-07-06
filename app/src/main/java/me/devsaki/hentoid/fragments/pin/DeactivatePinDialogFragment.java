package me.devsaki.hentoid.fragments.pin;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

public final class DeactivatePinDialogFragment extends PinDialogFragment {

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
        if (Preferences.getAppLockPin().equals(pin)) {
            Preferences.setAppLockPin("");
            dismiss();
            parent.onPinDeactivateSuccess();
        } else {
            vibrate();
            clearPin();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        parent.onPinDeactivateCancel();
    }

    public interface Parent {

        void onPinDeactivateSuccess();

        void onPinDeactivateCancel();
    }
}
