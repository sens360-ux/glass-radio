package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Signature Apple Weather Air Quality rainbow spectrum bar with white indicator circle.
 */
public class AqiSpectrumBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    private int aqi = 50;
    private int maxAqi = 300;

    public AqiSpectrumBarView(Context context) {
        this(context, null);
    }

    public AqiSpectrumBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFFFFFFF);

        dotStrokePaint.setStyle(Paint.Style.STROKE);
        dotStrokePaint.setColor(0xAA000000);
        dotStrokePaint.setStrokeWidth(2f);
    }

    public void setAqi(int aqi) {
        this.aqi = Math.max(0, aqi);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float barH = 5f * getResources().getDisplayMetrics().density;
        float top = (h - barH) / 2f;
        float bottom = top + barH;
        float radius = barH / 2f;

        // Rainbow gradient from Good (Green) to Hazardous (Maroon/Purple)
        LinearGradient grad = new LinearGradient(
                0, 0, w, 0,
                new int[]{0xFF22C55E, 0xFFEAB308, 0xFFF97316, 0xFFEF4444, 0xFFA855F7, 0xFF7E22CE},
                new float[]{0f, 0.2f, 0.4f, 0.65f, 0.85f, 1f},
                Shader.TileMode.CLAMP
        );
        trackPaint.setShader(grad);

        trackRect.set(0, top, w, bottom);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        // Dot indicator
        float pct = Math.min(1f, aqi / (float) maxAqi);
        float dotX = Math.max(radius, Math.min(w - radius, pct * w));
        float dotY = h / 2f;
        float dotRadius = radius + 3f;

        canvas.drawCircle(dotX, dotY, dotRadius, dotStrokePaint);
        canvas.drawCircle(dotX, dotY, dotRadius - 1f, dotPaint);
    }
}
