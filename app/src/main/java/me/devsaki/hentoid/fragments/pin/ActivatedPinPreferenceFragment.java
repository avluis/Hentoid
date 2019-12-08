package me.devsaki.hentoid.fragments.pin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import me.devsaki.hentoid.R;

import static androidx.core.view.ViewCompat.requireViewById;

public final class ActivatedPinPreferenceFragment extends Fragment
        implements DeactivatePinDialogFragment.Parent, ResetPinDialogFragment.Parent {

    private Switch offSwitch;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_pin_preference_on, container, false);

        Toolbar toolbar = requireViewById(rootView, R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().finish());

        offSwitch = requireViewById(rootView, R.id.switch_off);
        offSwitch.setOnClickListener(v -> onOffClick());

        View resetButton = requireViewById(rootView, R.id.text_reset_pin);
        resetButton.setOnClickListener(v -> onResetClick());

        return rootView;
    }

    @Override
    public void onPinDeactivateSuccess() {
        Snackbar.make(offSwitch, R.string.app_lock_disabled, BaseTransientBottomBar.LENGTH_SHORT).show();

        requireFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new DeactivatedPinPreferenceFragment())
                .commit();
    }

    @Override
    public void onPinDeactivateCancel() {
        offSwitch.setChecked(true);
    }

    @Override
    public void onPinResetSuccess() {
        Snackbar.make(offSwitch, R.string.pin_reset_success, BaseTransientBottomBar.LENGTH_SHORT).show();
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
