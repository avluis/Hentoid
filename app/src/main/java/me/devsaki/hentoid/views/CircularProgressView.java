package me.devsaki.hentoid.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import javax.annotation.Nullable;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.ThemeHelper;

public class CircularProgressView extends View {
    private final float strokeWidth;
    private final Paint totalPaint;
    private final Paint progress1Paint;
    private final Paint progress2Paint;
    private final Paint progress3Paint;
    private float progress1 = 0;
    private float progress2 = 0;
    private float progress3 = 0;
    private float total = 360;
    private TextView textView;

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());

        totalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        totalPaint.setStyle(Paint.Style.STROKE);
        totalPaint.setColor(ContextCompat.getColor(context, R.color.transparent));
        totalPaint.setStrokeWidth(strokeWidth);

        progress1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progress1Paint.setStyle(Paint.Style.STROKE);
        progress1Paint.setColor(ThemeHelper.getColor(context, R.color.secondary_light));
        progress1Paint.setStrokeWidth(strokeWidth);

        progress2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progress2Paint.setStyle(Paint.Style.STROKE);
        progress2Paint.setColor(ThemeHelper.getColor(context, R.color.secondary_variant_light));
        progress2Paint.setStrokeWidth(strokeWidth);

        progress3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progress3Paint.setStyle(Paint.Style.STROKE);
        progress3Paint.setColor(ThemeHelper.getColor(context, R.color.primary_light));
        progress3Paint.setStrokeWidth(strokeWidth);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();

        drawProgress(canvas, totalPaint, 0, 360);
        if (total != 0 && progress1 != 0)
            drawProgress(canvas, progress1Paint, 0, progress1);
        if (total != 0 && progress2 != 0)
            drawProgress(canvas, progress2Paint, progress1, progress2);
        if (total != 0 && progress3 != 0)
            drawProgress(canvas, progress3Paint, progress1 + progress2, progress3);

        canvas.restore();
    }

    private void drawProgress(Canvas canvas, Paint paint, float previousValue, float value) {
        previousValue = Math.min(360, previousValue);
        value = Math.min(360, value);

        float startAngle = -90 + (360f / total * previousValue);
        float sweepAngle = (360f / total * value);

        //noinspection SuspiciousNameCombination
        canvas.drawArc(new RectF(strokeWidth, strokeWidth, getWidth() - strokeWidth, getHeight() - strokeWidth), startAngle, sweepAngle, false, paint);
    }

    public void setProgress1Color(Context context, @ColorRes int color) {
        progress1Paint.setColor(ThemeHelper.getColor(context, color));
        invalidate();
    }

    public void setProgress1(float progress) {
        setProgress1Internal(progress);
    }

    private void setProgress1Internal(float progress) {
        this.progress1 = progress;
        invalidate();
    }

    public void setProgress2(float progress) {
        setProgress2Internal(progress);
    }

    public void setProgress2Color(Context context, @ColorRes int color) {
        progress2Paint.setColor(ThemeHelper.getColor(context, color));
        invalidate();
    }

    private void setProgress2Internal(float progress) {
        this.progress2 = progress;
        invalidate();
    }

    public void setProgress3(float progress) {
        setProgress3Internal(progress);
    }

    public void setProgress3Color(Context context, @ColorRes int color) {
        progress3Paint.setColor(ThemeHelper.getColor(context, color));
        invalidate();
    }

    private void setProgress3Internal(float progress) {
        this.progress3 = progress;
        invalidate();
    }

    public void setTotalColor(Context context, @ColorRes int color) {
        totalPaint.setColor(ThemeHelper.getColor(context, color));
        invalidate();
    }

    public void setTotal(long total) {
        setTotalInternal(total);
    }

    public void setTotal(float total) {
        setTotalInternal(total);
    }

    private void setTotalInternal(float total) {
        this.total = total;
        if (textView != null)
            textView.setText(String.valueOf(total));
        invalidate();
    }
}
