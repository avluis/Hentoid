package me.devsaki.hentoid.fragments.pin;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import me.devsaki.hentoid.R;

public final class DeactivatedPinPreferenceFragment extends Fragment implements ActivatePinDialogFragment.Parent {

    private Switch onSwitch;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pin_preference_off, container, false);

        onSwitch = ViewCompat.requireViewById(view, R.id.switch_on);
        onSwitch.setOnClickListener(v -> onOnClick());
        return view;
    }

    @Override
    public void onPinActivateSuccess() {
        Snackbar.make(onSwitch, R.string.app_lock_enable, Snackbar.LENGTH_SHORT).show();

        requireFragmentManager()
                .beginTransaction()
                .replace(R.id.frame_fragment, new ActivatedPinPreferenceFragment(), null)
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
