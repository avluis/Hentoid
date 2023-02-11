package me.devsaki.hentoid.fragments.pin;

import static androidx.core.view.ViewCompat.requireViewById;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

public final class ActivatedPinPreferenceFragment extends Fragment
        implements DeactivatePinDialogFragment.Parent, ResetPinDialogFragment.Parent, AdapterView.OnItemSelectedListener {

    private MaterialSwitch offSwitch;
    private Spinner lockDelaySpinner;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_pin_preference_on, container, false);

        Toolbar toolbar = requireViewById(rootView, R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().finish());

        offSwitch = requireViewById(rootView, R.id.switch_off);
        offSwitch.setOnClickListener(v -> onOffClick());

        boolean lockOnAppRestoredEnabled = Preferences.isLockOnAppRestore();
        MaterialSwitch lockOnAppRestored = requireViewById(rootView, R.id.switch_lock_on_restore);
        lockOnAppRestored.setChecked(lockOnAppRestoredEnabled);
        lockOnAppRestored.setOnCheckedChangeListener((b, v) -> onLockOnAppRestoreClick(v));

        int lockTimer = Preferences.getLockTimer();
        lockDelaySpinner = requireViewById(rootView, R.id.lock_timer);
        lockDelaySpinner.setVisibility(lockOnAppRestoredEnabled ? View.VISIBLE : View.GONE);
        lockDelaySpinner.setSelection(lockTimer);
        lockDelaySpinner.setOnItemSelectedListener(this);

        View resetButton = requireViewById(rootView, R.id.text_reset_pin);
        resetButton.setOnClickListener(v -> onResetClick());

        return rootView;
    }

    @Override
    public void onPinDeactivateSuccess() {
        Snackbar.make(offSwitch, R.string.app_lock_disabled, BaseTransientBottomBar.LENGTH_SHORT).show();

        getParentFragmentManager()
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

    private void onLockOnAppRestoreClick(boolean newValue) {
        Preferences.setLockOnAppRestore(newValue);
        lockDelaySpinner.setVisibility(newValue ? View.VISIBLE : View.GONE);
    }

    private void onResetClick() {
        ResetPinDialogFragment fragment = new ResetPinDialogFragment();
        fragment.show(getChildFragmentManager(), null);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Preferences.setLockTimer(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
    }
}
