package me.devsaki.hentoid.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import javax.annotation.Nullable;

import me.devsaki.hentoid.R;

public class CircularProgressView extends View {
    private final float strokeWidth;
    private Paint totalPaint;
    private Paint progressPaint;
    private float progress = 360;
    private float total = 360;
    private TextView textView;

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());

        totalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        totalPaint.setStyle(Paint.Style.STROKE);
        totalPaint.setColor(getResources().getColor(R.color.transparent));
        totalPaint.setStrokeWidth(strokeWidth);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setColor(getResources().getColor(R.color.secondary));
        progressPaint.setStrokeWidth(strokeWidth);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();

        drawProgress(canvas, (int) 360f, totalPaint);
        if (total != 0 && progress != 0)
            drawProgress(canvas, total == progress ? 360 : (int) ((360f / total) * progress), progressPaint);

        canvas.restore();
    }

    private void drawProgress(Canvas canvas, int total, Paint paint) {
        canvas.drawArc(new RectF(strokeWidth, strokeWidth, getWidth() - strokeWidth, getHeight() - strokeWidth), -90, total, false, paint);
    }

    public void setProgress(int progress) {
        this.progress = progress;
        invalidate();
    }

    public void setTotal(int total) {
        this.total = total;
        if (textView != null)
            textView.setText(String.valueOf(total));
        invalidate();
    }
}
