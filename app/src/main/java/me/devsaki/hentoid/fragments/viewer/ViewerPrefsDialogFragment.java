package me.devsaki.hentoid.fragments.viewer;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;

import java.security.InvalidParameterException;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

import static android.support.v4.view.ViewCompat.requireViewById;

public class ViewerPrefsDialogFragment extends DialogFragment {

    public static void invoke(Fragment parent) {
        ViewerPrefsDialogFragment fragment = new ViewerPrefsDialogFragment();
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.ViewerBrowseModeDialog);
        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_viewer_prefs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Switch theSwitch = requireViewById(view, R.id.viewer_prefs_resume_reading_action);
        theSwitch.setChecked(Preferences.isViewerResumeLastLeft());
        theSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> Preferences.setViewerResumeLastLeft(isChecked));

        theSwitch = requireViewById(view, R.id.viewer_prefs_keep_screen_action);
        theSwitch.setChecked(Preferences.isViewerKeepScreenOn());
        theSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> Preferences.setViewerKeepScreenOn(isChecked));

        RadioGroup theRadio = requireViewById(view, R.id.viewer_prefs_display_mode_group);
        switch (Preferences.getViewerResizeMode()) {
            case (Preferences.Constant.PREF_VIEWER_DISPLAY_FIT):
                theRadio.check(R.id.viewer_prefs_display_mode_action_fit);
                break;
            case (Preferences.Constant.PREF_VIEWER_DISPLAY_FILL):
                theRadio.check(R.id.viewer_prefs_display_mode_action_fill);
                break;
            default:
                throw new InvalidParameterException("Not implemented");
        }
        theRadio.setOnCheckedChangeListener(this::onChangeDisplayMode);

        theRadio = requireViewById(view, R.id.viewer_prefs_browse_mode_group);
        switch (Preferences.getViewerBrowseMode()) {
            case (Preferences.Constant.PREF_VIEWER_BROWSE_LTR):
                theRadio.check(R.id.viewer_prefs_browse_mode_action_ltr);
                break;
            case (Preferences.Constant.PREF_VIEWER_BROWSE_RTL):
                theRadio.check(R.id.viewer_prefs_browse_mode_action_rtl);
                break;
            case (Preferences.Constant.PREF_VIEWER_BROWSE_TTB):
                theRadio.check(R.id.viewer_prefs_browse_mode_action_ttb);
                break;
            default:
                throw new InvalidParameterException("Not implemented");
        }
        theRadio.setOnCheckedChangeListener(this::onChangeBrowseMode);

        SeekBar flingSensitivity = requireViewById(view, R.id.viewer_prefs_fling_sensitivity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flingSensitivity.setMin(25);
        }
        flingSensitivity.setMax(100);
        flingSensitivity.setProgress(Preferences.getViewerFlingFactor());
        flingSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // No need to do anything
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No need to do anything
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) Preferences.setViewerFlingFactor(progress);
            }
        });
    }

    private void onChangeDisplayMode(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case (R.id.viewer_prefs_display_mode_action_fit):
                Preferences.setViewerResizeMode(Preferences.Constant.PREF_VIEWER_DISPLAY_FIT);
                break;
            case (R.id.viewer_prefs_display_mode_action_fill):
                Preferences.setViewerResizeMode(Preferences.Constant.PREF_VIEWER_DISPLAY_FILL);
                break;
            default:
                throw new InvalidParameterException("Not implemented");
        }
    }

    private void onChangeBrowseMode(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case (R.id.viewer_prefs_browse_mode_action_ltr):
                Preferences.setViewerBrowseMode(Preferences.Constant.PREF_VIEWER_BROWSE_LTR);
                break;
            case (R.id.viewer_prefs_browse_mode_action_rtl):
                Preferences.setViewerBrowseMode(Preferences.Constant.PREF_VIEWER_BROWSE_RTL);
                break;
            case (R.id.viewer_prefs_browse_mode_action_ttb):
                Preferences.setViewerBrowseMode(Preferences.Constant.PREF_VIEWER_BROWSE_TTB);
                break;
            default:
                throw new InvalidParameterException("Not implemented");
        }
    }
}
