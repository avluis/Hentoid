package me.devsaki.hentoid.fragments.pin;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

public final class UnlockPinDialogFragment extends PinDialogFragment {

    private Parent parent;

    public static void invoke(FragmentManager mgr) {
        UnlockPinDialogFragment fragment = new UnlockPinDialogFragment();
        fragment.setCancelable(false);
        fragment.show(mgr, null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
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

    public interface Parent {
        void onPinSuccess();
    }
}
