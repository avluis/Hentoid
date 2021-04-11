package me.devsaki.hentoid.fragments.pin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.R;

import static androidx.core.view.ViewCompat.requireViewById;

public final class DeactivatedPinPreferenceFragment extends Fragment implements ActivatePinDialogFragment.Parent {

    private SwitchMaterial onSwitch;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_pin_preference_off, container, false);

        Toolbar toolbar = requireViewById(rootView, R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().finish());

        onSwitch = requireViewById(rootView, R.id.switch_on);
        onSwitch.setOnClickListener(v -> onOnClick());
        return rootView;
    }

    @Override
    public void onPinActivateSuccess() {
        Snackbar.make(onSwitch, R.string.app_lock_enable, BaseTransientBottomBar.LENGTH_SHORT).show();
        HentoidApp.setUnlocked(true); // Now that PIN lock is enabled, the app needs to be marked as currently unlocked to avoid showing an unnecessary PIN dialog at next navigation action

        getParentFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new ActivatedPinPreferenceFragment())
                .commit();
    }

    @Override
    public void onPinActivateCancel() {
        onSwitch.setChecked(false);
    }

    private void onOnClick() {
        ActivatePinDialogFragment fragment = new ActivatePinDialogFragment();
        fragment.show(getChildFragmentManager(), null);
    }
}
