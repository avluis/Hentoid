package me.devsaki.hentoid.fragments.pin;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import me.devsaki.hentoid.R;

import static androidx.core.view.ViewCompat.requireViewById;

public final class ActivatedPinPreferenceFragment extends Fragment
        implements DeactivatePinDialogFragment.Parent, ResetPinDialogFragment.Parent {

    private Switch offSwitch;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pin_preference_on, container, false);

        offSwitch = requireViewById(view, R.id.switch_off);
        offSwitch.setOnClickListener(v -> onOffClick());

        View resetButton = requireViewById(view, R.id.text_reset_pin);
        resetButton.setOnClickListener(v -> onResetClick());

        return view;
    }

    @Override
    public void onPinDeactivateSuccess() {
        Snackbar.make(offSwitch, R.string.app_lock_disabled, Snackbar.LENGTH_SHORT).show();

        requireFragmentManager()
                .beginTransaction()
                .replace(R.id.frame_fragment, new DeactivatedPinPreferenceFragment(), null)
                .commit();
    }

    @Override
    public void onPinDeactivateCancel() {
        offSwitch.setChecked(true);
    }

    @Override
    public void onPinResetSuccess() {
        Snackbar.make(offSwitch, R.string.pin_reset, Snackbar.LENGTH_SHORT).show();
    }

    private void onOffClick() {
        DeactivatePinDialogFragment fragment = new DeactivatePinDialogFragment();
        fragment.show(getChildFragmentManager(), null);
    }

    private void onResetClick() {
        ResetPinDialogFragment fragment = new ResetPinDialogFragment();
        fragment.show(getChildFragmentManager(), null);
    }
}
