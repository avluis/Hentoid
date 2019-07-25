package me.devsaki.hentoid.fragments.intro;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.IntroActivity;

// TODO: 6/23/2018 implement ISlidePolicy to force user to select a storage option
public class ImportIntroFragment extends Fragment {

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
        View view = inflater.inflate(R.layout.intro_slide_05, container, false);

        TextView customTv = view.findViewById(R.id.tv_library_custom);
        customTv.setOnClickListener(v -> parentActivity.onCustomStorageSelected());

        TextView defaultTv = view.findViewById(R.id.tv_library_default);
        defaultTv.setOnClickListener(v -> parentActivity.onDefaultStorageSelected());

        return view;
    }
}