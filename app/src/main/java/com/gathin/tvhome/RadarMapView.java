package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Apple Weather Regional Precipitation Radar Map Card View.
 * Displays local geography, radar cloud reflectivity heatmaps, and location temperature badge.
 */
public class RadarMapView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint landPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int temp = 29;
    private String locationName = "Putrajaya";

    public RadarMapView(Context context) {
        this(context, null);
    }

    public RadarMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFF131D2A);

        landPaint.setStyle(Paint.Style.FILL);
        landPaint.setColor(0xFF1C2B3C);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xCCFFFFFF);

        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(0xFF1E3A8A);

        badgeStroke.setStyle(Paint.Style.STROKE);
        badgeStroke.setStrokeWidth(1.5f);
        badgeStroke.setColor(0xFFFFFFFF);
    }

    public void setDetails(int temp, String loc) {
        this.temp = temp;
        this.locationName = loc;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        // Clip to rounded corners
        Path clipPath = new Path();
        clipPath.addRoundRect(new RectF(0, 0, w, h), 14f * density, 14f * density, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // 1. Sea / Ocean background (Straits of Malacca)
        canvas.drawRect(0, 0, w, h, bgPaint);

        // 2. West Coast Landmass Polygon (Selangor, KL, N. Sembilan)
        Path land = new Path();
        land.moveTo(w * 0.22f, 0);
        land.lineTo(w, 0);
        land.lineTo(w, h);
        land.lineTo(w * 0.48f, h);
        land.lineTo(w * 0.36f, h * 0.85f); // Port Dickson
        land.lineTo(w * 0.28f, h * 0.65f); // Sepang
        land.lineTo(w * 0.16f, h * 0.45f); // Klang / Pandamaran
        land.lineTo(w * 0.20f, h * 0.25f); // Kuala Selangor
        land.lineTo(w * 0.12f, 0);          // Sabak Bernam
        land.close();
        canvas.drawPath(land, landPaint);

        // 3. Radar Precipitation Heatmap Overlays (Apple Weather Doppler return)
        // Storm cell 1 (Violet / Magenta core)
        RadialGradient cell1 = new RadialGradient(
                w * 0.38f, h * 0.45f, w * 0.32f,
                new int[]{0xDDDB2777, 0xAA7C3AED, 0x662563EB, 0x001E3A8A},
                new float[]{0f, 0.35f, 0.70f, 1f},
                Shader.TileMode.CLAMP
        );
        radarPaint.setShader(cell1);
        canvas.drawCircle(w * 0.38f, h * 0.45f, w * 0.32f, radarPaint);

        // Storm cell 2 (Amber / Orange / Crimson precipitation band)
        RadialGradient cell2 = new RadialGradient(
                w * 0.52f, h * 0.68f, w * 0.26f,
                new int[]{0xCCE11D48, 0x99F59E0B, 0x4410B981, 0x000D9488},
                new float[]{0f, 0.40f, 0.75f, 1f},
                Shader.TileMode.CLAMP
        );
        radarPaint.setShader(cell2);
        canvas.drawCircle(w * 0.52f, h * 0.68f, w * 0.26f, radarPaint);

        // 4. Map Geography Labels (Carefully positioned to avoid overlap)
        textPaint.setTextAlign(Paint.Align.LEFT);

        // Kuala Lumpur with city marker dot
        textPaint.setTextSize(9f * density);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);
        canvas.drawCircle(w * 0.59f, h * 0.43f, 2f * density, textPaint);
        canvas.drawText("Kuala Lumpur", w * 0.62f, h * 0.45f, textPaint);

        // Region & Town Labels
        textPaint.setTextSize(7f * density);
        textPaint.setColor(0xA0FFFFFF);
        textPaint.setFakeBoldText(false);
        canvas.drawText("Bagan Nakhoda Omar", w * 0.16f, h * 0.16f, textPaint);
        canvas.drawText("Kuala Selangor", w * 0.24f, h * 0.28f, textPaint);
        canvas.drawText("SELANGOR", w * 0.30f, h * 0.36f, textPaint);
        canvas.drawText("Pandamaran", w * 0.12f, h * 0.52f, textPaint);
        canvas.drawText("Mentakab", w * 0.74f, h * 0.28f, textPaint);
        canvas.drawText("Sepang", w * 0.35f, h * 0.75f, textPaint);
        canvas.drawText("Seremban", w * 0.64f, h * 0.65f, textPaint);
        canvas.drawText("Port Dickson", w * 0.52f, h * 0.84f, textPaint);

        // Sea label in bottom right corner
        textPaint.setTextSize(6.5f * density);
        textPaint.setColor(0x70FFFFFF);
        canvas.drawText("MALACCA STRAIT", w * 0.70f, h * 0.92f, textPaint);

        // 5. My Location Badge (Circular disc: temp e.g. 29 + 'My Location')
        float badgeX = w * 0.38f;
        float badgeY = h * 0.58f;
        float bRadius = 9f * density;

        canvas.drawCircle(badgeX, badgeY, bRadius, badgePaint);
        canvas.drawCircle(badgeX, badgeY, bRadius, badgeStroke);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(7.5f * density);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(temp), badgeX, badgeY + (2.5f * density), textPaint);

        textPaint.setTextSize(7f * density);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(0xEEFFFFFF);
        canvas.drawText("My Location", badgeX, badgeY + (11.5f * density), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }
}
