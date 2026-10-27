package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Apple Weather Wind Compass Tile View.
 * Displays a circular compass dial with directional arrow, wind speed, and gusts.
 */
public class WindCompassView extends View {

    private final Paint dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int speed = 5;
    private int gusts = 15;
    private float windAngle = 180f; // South

    public WindCompassView(Context context) {
        this(context, null);
    }

    public WindCompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dialPaint.setStyle(Paint.Style.STROKE);
        dialPaint.setStrokeWidth(1.2f);
        dialPaint.setColor(0x35FFFFFF);

        needlePaint.setStyle(Paint.Style.FILL);
        needlePaint.setColor(0xFF38BDF8);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0x99FFFFFF);
    }

    public void setWind(int speed, int gusts, float angle) {
        this.speed = speed;
        this.gusts = gusts;
        this.windAngle = angle;
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
        float cy = h * 0.38f;
        float radius = Math.min(w * 0.28f, h * 0.30f);

        // Compass perimeter circle
        canvas.drawCircle(cx, cy, radius, dialPaint);

        // Cardinal directions (N, E, S, W)
        labelPaint.setTextSize(7.5f * density);
        labelPaint.setColor(0x88FFFFFF);
        canvas.drawText("N", cx, cy - radius + (6.5f * density), labelPaint);
        canvas.drawText("S", cx, cy + radius - (1.5f * density), labelPaint);
        canvas.drawText("E", cx + radius - (4f * density), cy + (2.5f * density), labelPaint);
        canvas.drawText("W", cx - radius + (4f * density), cy + (2.5f * density), labelPaint);

        // Rotating Arrow Needle pointing in wind direction
        canvas.save();
        canvas.rotate(windAngle, cx, cy);
        Path arrow = new Path();
        float needleLen = radius * 0.65f;
        arrow.moveTo(cx, cy - needleLen);
        arrow.lineTo(cx - (3f * density), cy - (needleLen * 0.45f));
        arrow.lineTo(cx, cy - (needleLen * 0.58f));
        arrow.lineTo(cx + (3f * density), cy - (needleLen * 0.45f));
        arrow.close();
        canvas.drawPath(arrow, needlePaint);
        canvas.restore();

        // Speed in center
        textPaint.setTextSize(10.5f * density);
        canvas.drawText(String.valueOf(speed), cx, cy + (1f * density), textPaint);

        labelPaint.setTextSize(6.5f * density);
        canvas.drawText("km/h", cx, cy + (7.5f * density), labelPaint);

        // Bottom Gusts text
        labelPaint.setTextSize(8f * density);
        labelPaint.setColor(0xCCFFFFFF);
        canvas.drawText("Gusts: " + gusts + " km/h S", cx, h - (2f * density), labelPaint);
    }
}
