package me.devsaki.hentoid.views;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

/**
 * {@link SubsamplingScaleImageView} that does not listen to any touch event
 * TODO
 *  - add a "setPreloadSize" to be used instead of View.getWidth / View.getHeight during initialiseBaseLayer
 *   (by overriding getWidth and getHeight ?)
 */
public class MutedSubsamplingScaleImageView extends SubsamplingScaleImageView {

    private boolean ignoreTouchEvents = false;

    public MutedSubsamplingScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public MutedSubsamplingScaleImageView(Context context) {
        super(context);
    }

    public void setIgnoreTouchEvents(boolean ignoreTouchEvents)
    {
        this.ignoreTouchEvents = ignoreTouchEvents;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event)
    {
        if (ignoreTouchEvents) return false;
        else return super.onTouchEvent(event);
    }
}
