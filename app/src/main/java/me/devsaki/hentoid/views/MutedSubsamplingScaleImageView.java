package me.devsaki.hentoid.views;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

/**
 * {@link SubsamplingScaleImageView} that does not listen to any touch event
 */
public class MutedSubsamplingScaleImageView extends SubsamplingScaleImageView {

    public MutedSubsamplingScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public MutedSubsamplingScaleImageView(Context context) {
        super(context);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return false;
    }

}
