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
 * Signature Apple Weather temperature range spectrum bar.
 * Visualizes day's min/max range relative to the week's temperature extremes,
 * with colored thermal gradient and a glowing current temperature dot indicator.
 */
public class TempRangeBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF trackRect = new RectF();
    private final RectF barRect = new RectF();

    private int weekMin = 20;
    private int weekMax = 35;
    private int dayMin = 24;
    private int dayMax = 30;
    private int currentTemp = -999; // -999 indicates not today

    public TempRangeBarView(Context context) {
        this(context, null);
    }

    public TempRangeBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(0x33FFFFFF);

        barPaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFFFFFFF);

        dotStrokePaint.setStyle(Paint.Style.STROKE);
        dotStrokePaint.setColor(0x88000000);
        dotStrokePaint.setStrokeWidth(1.5f);
    }

    public void setRange(int weekMin, int weekMax, int dayMin, int dayMax, int currentTemp) {
        this.weekMin = Math.min(weekMin, dayMin);
        this.weekMax = Math.max(weekMax, dayMax);
        if (this.weekMax == this.weekMin) {
            this.weekMax = this.weekMin + 1;
        }
        this.dayMin = dayMin;
        this.dayMax = dayMax;
        this.currentTemp = currentTemp;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float trackH = Math.min(h * 0.5f, 5f * getResources().getDisplayMetrics().density);
        float top = (h - trackH) / 2f;
        float bottom = top + trackH;
        float radius = trackH / 2f;

        // 1. Draw base track
        trackRect.set(0, top, w, bottom);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        // 2. Calculate day range positions
        float totalRange = (float) (weekMax - weekMin);
        if (totalRange <= 0) totalRange = 1f;

        float startPct = Math.max(0f, Math.min(1f, (dayMin - weekMin) / totalRange));
        float endPct = Math.max(0f, Math.min(1f, (dayMax - weekMin) / totalRange));

        float barLeft = startPct * w;
        float barRight = Math.max(barLeft + radius * 2, endPct * w);

        // Gradient representing thermal spectrum: Cyan -> Amber -> Coral
        LinearGradient barGrad = new LinearGradient(
                0, 0, w, 0,
                new int[]{0xFF38BDF8, 0xFF34D399, 0xFFFBBF24, 0xFFF97316},
                new float[]{0f, 0.35f, 0.7f, 1f},
                Shader.TileMode.CLAMP
        );
        barPaint.setShader(barGrad);

        barRect.set(barLeft, top, barRight, bottom);
        canvas.drawRoundRect(barRect, radius, radius, barPaint);

        // 3. Draw current temperature dot if today
        if (currentTemp >= weekMin && currentTemp <= weekMax) {
            float dotPct = Math.max(0f, Math.min(1f, (currentTemp - weekMin) / totalRange));
            float dotX = dotPct * w;
            float dotY = h / 2f;
            float dotRadius = radius + 2.5f;

            canvas.drawCircle(dotX, dotY, dotRadius, dotStrokePaint);
            canvas.drawCircle(dotX, dotY, dotRadius - 0.75f, dotPaint);
        }
    }
}
