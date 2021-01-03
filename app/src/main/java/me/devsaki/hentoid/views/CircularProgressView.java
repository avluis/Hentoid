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
    private double progress1 = 0;
    private double progress2 = 0;
    private double total = 360;
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
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();

        drawProgress(canvas, (int) 360f, totalPaint);
        if (total != 0 && progress1 != 0)
            drawProgress(canvas, total <= progress1 ? 360 : (int) ((360f / total) * progress1), progress1Paint);
        if (total != 0 && progress2 != 0)
            drawProgress(canvas, total <= progress2 ? 360 : (int) ((360f / total) * progress2), progress2Paint);

        canvas.restore();
    }

    private void drawProgress(Canvas canvas, int total, Paint paint) {
        //noinspection SuspiciousNameCombination
        canvas.drawArc(new RectF(strokeWidth, strokeWidth, getWidth() - strokeWidth, getHeight() - strokeWidth), -90, total, false, paint);
    }

    public void setProgress1Color(Context context, @ColorRes int color) {
        progress1Paint.setColor(ThemeHelper.getColor(context, color));
        invalidate();
    }

    public void setProgress1(long progress) {
        setProgress1Internal(progress);
    }

    public void setProgress1(double progress) {
        setProgress1Internal(progress);
    }

    private void setProgress1Internal(double progress) {
        this.progress1 = progress;
        invalidate();
    }

    public void setProgress2Color(Context context, @ColorRes int color) {
        progress2Paint.setColor(ThemeHelper.getColor(context, color));
        invalidate();
    }

    public void setProgress2(long progress) {
        setProgress2Internal(progress);
    }

    public void setProgress2(double progress) {
        setProgress2Internal(progress);
    }

    private void setProgress2Internal(double progress) {
        this.progress2 = progress;
        invalidate();
    }

    public void setTotalColor(Context context, @ColorRes int color) {
        totalPaint.setColor(ThemeHelper.getColor(context, color));
        invalidate();
    }

    public void setTotal(long total) {
        setTotalInternal(total);
    }

    public void setTotal(double total) {
        setTotalInternal(total);
    }

    private void setTotalInternal(double total) {
        this.total = total;
        if (textView != null)
            textView.setText(String.valueOf(total));
        invalidate();
    }
}
