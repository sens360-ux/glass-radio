package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;

/**
 * Apple Weather Sunset & Horizon Arc Graph View.
 */
public class SunArcView extends View {

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint horizonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String sunsetTime = "6:57 PM";
    private String sunriseTime = "6:56 AM";

    public SunArcView(Context context) {
        this(context, null);
    }

    public SunArcView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(1.6f);
        arcPaint.setColor(0x88FFFFFF);

        horizonPaint.setStyle(Paint.Style.STROKE);
        horizonPaint.setStrokeWidth(1f);
        horizonPaint.setColor(0x40FFFFFF);
        horizonPaint.setPathEffect(new DashPathEffect(new float[]{3f, 3f}, 0));

        sunDotPaint.setStyle(Paint.Style.FILL);
        sunDotPaint.setColor(0xFFFFFFFF);

        sunGlowPaint.setStyle(Paint.Style.FILL);
        sunGlowPaint.setColor(0x44FBBF24);

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(0xCCFFFFFF);
    }

    public void setTimes(String sunrise, String sunset) {
        this.sunriseTime = sunrise;
        this.sunsetTime = sunset;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float horizonY = h * 0.62f;

        // Draw dashed horizon line
        canvas.drawLine(6f * density, horizonY, w - (6f * density), horizonY, horizonPaint);

        // Draw sinusoidal sun path arc
        Path path = new Path();
        float startX = 8f * density;
        float endX = w - (8f * density);
        float midX = (startX + endX) / 2f;
        float peakY = h * 0.20f;

        path.moveTo(startX, horizonY);
        path.quadTo(midX, peakY - (15f * density), endX, horizonY);
        canvas.drawPath(path, arcPaint);

        // Calculate sun position based on time of day
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        float currentMinutes = (hour * 60) + minute;

        // Daylight between 7:00 AM (420 min) and 7:00 PM (1140 min)
        float daylightStart = 7f * 60f;
        float daylightEnd = 19f * 60f;
        float progress = Math.max(0f, Math.min(1f, (currentMinutes - daylightStart) / (daylightEnd - daylightStart)));

        float sunX = startX + progress * (endX - startX);
        float t = progress;
        float sunY = (1 - t) * (1 - t) * horizonY + 2 * (1 - t) * t * (peakY - (15f * density)) + t * t * horizonY;

        // Draw sun glow & dot
        canvas.drawCircle(sunX, sunY, 6f * density, sunGlowPaint);
        canvas.drawCircle(sunX, sunY, 2.5f * density, sunDotPaint);

        // Bottom sunrise label
        labelPaint.setTextSize(8f * density);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Sunrise: " + sunriseTime, startX, h - (2f * density), labelPaint);
    }
}
