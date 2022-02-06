package me.devsaki.hentoid.fragments.viewer;

import static me.devsaki.hentoid.util.Preferences.Key.VIEWER_BROWSE_MODE;
import static me.devsaki.hentoid.util.Preferences.Key.VIEWER_RENDERING;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.skydoves.powerspinner.PowerSpinnerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.bundles.PrefsBundle;
import me.devsaki.hentoid.util.Preferences;

public final class ViewerPrefsDialogFragment extends DialogFragment {

    private static final String RENDERING_MODE = "render_mode";
    private static final String BROWSE_MODE = "browse_mode";

    private Parent parent;
    private int renderingMode;
    private int browseMode;


    public static void invoke(Fragment parent, Map<String, String> bookPrefs) {
        ViewerPrefsDialogFragment fragment = new ViewerPrefsDialogFragment();

        Bundle args = new Bundle();
        if (bookPrefs != null) {
            if (bookPrefs.containsKey(VIEWER_RENDERING))
                args.putInt(RENDERING_MODE, Preferences.isContentSmoothRendering(bookPrefs) ? 1 : 0);
            if (bookPrefs.containsKey(VIEWER_BROWSE_MODE))
                args.putInt(BROWSE_MODE, Preferences.getContentBrowseMode(bookPrefs));
        }
        fragment.setArguments(args);

        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        renderingMode = getArguments().getInt(RENDERING_MODE, -1);
        browseMode = getArguments().getInt(BROWSE_MODE, -1);
        parent = (Parent) getParentFragment();
    }

    @Override
    public void onDestroy() {
        parent = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_viewer_book_prefs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        Resources res = rootView.getContext().getResources();

        // == Dropdown lists

        String[] browseModes = getResources().getStringArray(R.array.pref_viewer_browse_mode_entries);
        List<String> browseItems = new ArrayList<>();
        // App pref
        browseItems.add(res.getString(R.string.use_app_prefs, browseModes[Preferences.getViewerBrowseMode()]));
        // Available prefs
        browseItems.addAll(Arrays.asList(browseModes));

        PowerSpinnerView browseSpin = rootView.findViewById(R.id.book_prefs_browse_spin);
        browseSpin.setItems(browseItems);
        browseSpin.selectItemByIndex(browseMode + 1);


        String[] renderingModes = getResources().getStringArray(R.array.pref_viewer_rendering_entries);
        List<String> renderingItems = new ArrayList<>();
        // App pref
        renderingItems.add(res.getString(R.string.use_app_prefs, renderingModes[Preferences.isViewerSmoothRendering() ? 1 : 0].replace(" (default)", "")));
        // Available prefs
        for (int i = 0; i < renderingModes.length; i++) {
            // No smooth mode for Androidfindview 5
            if (1 == i && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) continue;
            renderingItems.add(renderingModes[i].replace(" (default)", ""));
        }

        PowerSpinnerView renderSpin = rootView.findViewById(R.id.book_prefs_rendering_spin);
        renderSpin.setItems(renderingItems);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            renderSpin.selectItemByIndex(0);
            renderSpin.setEnabled(false);
        } else renderSpin.selectItemByIndex(renderingMode + 1);


        // == Bottom buttons

        View appSettingsBtn = rootView.findViewById(R.id.book_prefs_app_prefs_btn);
        appSettingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), PrefsActivity.class);

            PrefsBundle prefsBundle = new PrefsBundle();
            prefsBundle.setViewerPrefs(true);
            intent.putExtras(prefsBundle.toBundle());

            requireContext().startActivity(intent);
        });

        View okBtn = rootView.findViewById(R.id.book_prefs_ok_btn);
        okBtn.setOnClickListener(v -> {
            Map<String, String> newPrefs = new HashMap<>();
            if (renderSpin.getSelectedIndex() > 0)
                newPrefs.put(VIEWER_RENDERING, (renderSpin.getSelectedIndex() - 1) + "");
            if (browseSpin.getSelectedIndex() > 0)
                newPrefs.put(VIEWER_BROWSE_MODE, (browseSpin.getSelectedIndex() - 1) + "");
            parent.onBookPreferenceChanged(newPrefs);
            dismiss();
        });
    }

    public interface Parent {
        void onBookPreferenceChanged(Map<String, String> newPrefs);
    }
}
