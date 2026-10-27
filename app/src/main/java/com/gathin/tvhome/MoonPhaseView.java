package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Apple Weather Moon Phase Card View.
 */
public class MoonPhaseView extends View {

    private final Paint moonGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint craterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MoonPhaseView(Context context) {
        this(context, null);
    }

    public MoonPhaseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        moonGlow.setStyle(Paint.Style.FILL);

        moonPaint.setStyle(Paint.Style.FILL);
        moonPaint.setColor(0xFFF1F5F9);

        craterPaint.setStyle(Paint.Style.FILL);
        craterPaint.setColor(0x2894A3B8);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x880B1220);

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xCCFFFFFF);
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
        float radius = Math.min(w * 0.28f, h * 0.28f);

        // Moon subtle halo glow
        RadialGradient glow = new RadialGradient(
                cx, cy, radius * 1.4f,
                new int[]{0x2AFFFFFF, 0x00000000},
                new float[]{0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        moonGlow.setShader(glow);
        canvas.drawCircle(cx, cy, radius * 1.4f, moonGlow);

        // Moon sphere
        canvas.drawCircle(cx, cy, radius, moonPaint);

        // Craters details
        canvas.drawCircle(cx - (radius * 0.3f), cy - (radius * 0.2f), radius * 0.22f, craterPaint);
        canvas.drawCircle(cx + (radius * 0.25f), cy + (radius * 0.2f), radius * 0.28f, craterPaint);
        canvas.drawCircle(cx - (radius * 0.1f), cy + (radius * 0.4f), radius * 0.18f, craterPaint);

        // Waning gibbous shadow crescent
        canvas.drawCircle(cx - (radius * 0.45f), cy, radius * 0.95f, shadowPaint);

        // Bottom moonrise text
        labelPaint.setTextSize(8f * density);
        canvas.drawText("Moonrise: 7:57 PM", cx, h - (2f * density), labelPaint);
    }
}
