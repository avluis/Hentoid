package me.devsaki.hentoid.fragments.intro;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.IntroActivity;
import me.devsaki.hentoid.util.Preferences;

public class ThemeIntroFragment extends Fragment {

    private IntroActivity parentActivity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof IntroActivity) {
            parentActivity = (IntroActivity) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.intro_slide_06, container, false);

        View lightBtn = view.findViewById(R.id.intro_6_light);
        lightBtn.setOnClickListener(v -> parentActivity.setThemePrefs(Preferences.Constant.DARK_MODE_OFF));

        View darkBtn = view.findViewById(R.id.intro_6_dark);
        darkBtn.setOnClickListener(v -> parentActivity.setThemePrefs(Preferences.Constant.DARK_MODE_ON));

        return view;
    }
}
