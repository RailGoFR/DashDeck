package com.dashdeck.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Indicateur de pages : le point actif grossit et change de couleur en suivant le doigt. */
public class DotsView extends View {
    private int count = 1;
    private float pos = 0f;
    private int accent = 0xFF0A84FF;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public DotsView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
    }

    public void setCount(int n) {
        count = Math.max(1, n);
        invalidate();
    }

    public void setPosition(float p) {
        pos = p;
        invalidate();
    }

    public void setAccent(int a) {
        accent = a;
        invalidate();
    }

    private static int blend(int a, int b, float t) {
        int al = Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t);
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.argb(al, r, g, bl);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float spacing = 16f * d;
        float total = (count - 1) * spacing;
        float startX = getWidth() / 2f - total / 2f;
        float cy = getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            float closeness = Math.max(0f, 1f - Math.abs(pos - i));
            float r = (3.2f + 1.8f * closeness) * d;
            paint.setColor(blend(0x55FFFFFF, accent, closeness));
            canvas.drawCircle(startX + i * spacing, cy, r, paint);
        }
    }
}
