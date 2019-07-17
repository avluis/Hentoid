package me.devsaki.hentoid.fragments.intro;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by avluis on 03/20/2016.
 * Slide Fragment for use with AppIntro Slides
 */
public class BaseSlide extends Fragment {

    private static final String ARG_LAYOUT_RES_ID = "layoutResId";

    public static BaseSlide newInstance(int layoutResId) {
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_RES_ID, layoutResId);

        BaseSlide baseSlide = new BaseSlide();
        baseSlide.setArguments(args);
        return baseSlide;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if (arguments == null)
            throw new IllegalArgumentException("No arguments supplied to BaseSlide fragment");

        int layoutResId = arguments.getInt(ARG_LAYOUT_RES_ID, -1);
        if (layoutResId == -1)
            throw new IllegalArgumentException("No layout argument supplied to BaseSlide fragment");

        return inflater.inflate(layoutResId, container, false);
    }
}
