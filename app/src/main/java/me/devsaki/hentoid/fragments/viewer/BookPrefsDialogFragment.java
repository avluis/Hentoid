package me.devsaki.hentoid.fragments.viewer;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

public final class BookPrefsDialogFragment extends DialogFragment {

    private static final String RENDERING_MODE = "render_mode";

    private Parent parent;
    private int renderingMode;


    public static void invoke(Fragment parent, Map<String, String> bookPrefs) {
        BookPrefsDialogFragment fragment = new BookPrefsDialogFragment();

        Bundle args = new Bundle();
        args.putInt(RENDERING_MODE, Preferences.isContentSmoothRendering(bookPrefs) ? 1 : 0);
        fragment.setArguments(args);

        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        renderingMode = getArguments().getInt(RENDERING_MODE);
        parent = (Parent) getParentFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] renderingModes = getResources().getStringArray(R.array.pref_viewer_rendering_entries);

        RadioGroup radioGrp = new RadioGroup(getContext());
        for (int i = 0; i < renderingModes.length; i++) {
            // No smooth mode for Android 5
            if (1 == i && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) continue;

            RadioButton radioButton = new RadioButton(getContext());
            radioButton.setText(renderingModes[i]);
            radioButton.setId(i);
            radioGrp.addView(radioButton);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) radioGrp.check(0);
        else radioGrp.check(renderingMode);

        DialogInterface.OnClickListener onOkClick = (dialog, whichButton) -> {
            Map<String, String> newPrefs = new HashMap<>();
            newPrefs.put(Preferences.Key.PREF_VIEWER_RENDERING, radioGrp.getCheckedRadioButtonId() + "");
            parent.onBookPreferenceChanged(newPrefs);
            dismiss();
        };

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(radioGrp)
                .setPositiveButton(android.R.string.ok, onOkClick)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    public interface Parent {
        void onBookPreferenceChanged(Map<String, String> newPrefs);
    }
}
