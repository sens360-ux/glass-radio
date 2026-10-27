package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Apple Weather Pressure Gauge Dial View.
 */
public class PressureGaugeView extends View {

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeTick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int pressure = 1014;

    public PressureGaugeView(Context context) {
        this(context, null);
    }

    public PressureGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(1.5f);
        tickPaint.setColor(0x35FFFFFF);

        activeTick.setStyle(Paint.Style.STROKE);
        activeTick.setStrokeWidth(2f);
        activeTick.setColor(0xFFFFFFFF);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(0x99FFFFFF);
    }

    public void setPressure(int hPa) {
        this.pressure = hPa;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float cx = w / 2f;
        float cy = h * 0.52f;
        float radius = Math.min(w, h) * 0.35f;

        // Draw radial gauge ticks (arc from 135 deg to 405 deg)
        int numTicks = 24;
        for (int i = 0; i <= numTicks; i++) {
            float angle = 135f + (i * (270f / numTicks));
            double rad = Math.toRadians(angle);
            float x1 = (float) (cx + (radius - (5f * density)) * Math.cos(rad));
            float y1 = (float) (cy + (radius - (5f * density)) * Math.sin(rad));
            float x2 = (float) (cx + radius * Math.cos(rad));
            float y2 = (float) (cy + radius * Math.sin(rad));

            boolean isCurrent = (i == (numTicks / 2));
            canvas.drawLine(x1, y1, x2, y2, isCurrent ? activeTick : tickPaint);
        }

        // Pressure text in center
        textPaint.setTextSize(12f * density);
        canvas.drawText("↑ " + pressure, cx, cy + (1f * density), textPaint);

        labelPaint.setTextSize(8f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("hPa", cx, cy + (10f * density), labelPaint);

        // Low / High labels
        labelPaint.setTextSize(8.5f * density);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Low", cx - radius, h - (3f * density), labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("High", cx + radius, h - (3f * density), labelPaint);
    }
}
