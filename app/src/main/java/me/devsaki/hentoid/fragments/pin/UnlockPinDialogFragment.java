package me.devsaki.hentoid.fragments.pin;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

public final class UnlockPinDialogFragment extends PinDialogFragment {

    private Parent parent;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        parent = (Parent) context;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setHeaderText(R.string.app_lock_pin);
    }

    @Override
    protected void onPinAccept(String pin) {
        if (Preferences.getAppLockPin().equals(pin)) {
            dismiss();
            parent.onPinSuccess();
        } else {
            vibrate();
            clearPin();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        parent.onPinCancel();
    }

    public interface Parent {

        void onPinSuccess();

        void onPinCancel();
    }
}
