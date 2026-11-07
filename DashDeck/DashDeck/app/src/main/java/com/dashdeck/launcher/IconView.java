package com.dashdeck.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Petites icônes vectorielles dessinées à la main (aucun fichier image nécessaire). */
public class IconView extends View {
    public static final int PLUS = 0;
    public static final int MINUS = 1;
    public static final int CHECK = 2;
    public static final int SLIDERS = 3;
    public static final int GRID = 4;

    private int type;
    private int color = Color.WHITE;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public IconView(Context context, int type) {
        super(context);
        this.type = type;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setType(int t) {
        type = t;
        invalidate();
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth();
        float h = getHeight();
        float s = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f;
        p.setColor(color);
        p.setStrokeWidth(s * 0.09f);
        p.setStyle(Paint.Style.STROKE);

        switch (type) {
            case PLUS: {
                float r = s * 0.28f;
                cv.drawLine(cx - r, cy, cx + r, cy, p);
                cv.drawLine(cx, cy - r, cx, cy + r, p);
                break;
            }
            case MINUS: {
                float r = s * 0.28f;
                cv.drawLine(cx - r, cy, cx + r, cy, p);
                break;
            }
            case CHECK: {
                path.reset();
                path.moveTo(cx - s * 0.26f, cy + s * 0.02f);
                path.lineTo(cx - s * 0.08f, cy + s * 0.20f);
                path.lineTo(cx + s * 0.28f, cy - s * 0.20f);
                cv.drawPath(path, p);
                break;
            }
            case SLIDERS: {
                float x0 = cx - s * 0.32f;
                float x1 = cx + s * 0.32f;
                float[] ys = {cy - s * 0.24f, cy, cy + s * 0.24f};
                float[] ks = {cx - s * 0.12f, cx + s * 0.14f, cx - s * 0.04f};
                for (int i = 0; i < 3; i++) {
                    p.setStyle(Paint.Style.STROKE);
                    cv.drawLine(x0, ys[i], x1, ys[i], p);
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(ks[i], ys[i], s * 0.09f, p);
                }
                break;
            }
            case GRID: {
                p.setStyle(Paint.Style.FILL);
                float sq = s * 0.24f;
                float gap = s * 0.09f;
                float r = s * 0.06f;
                float startX = cx - sq - gap / 2f;
                float startY = cy - sq - gap / 2f;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        float l = startX + i * (sq + gap);
                        float t = startY + j * (sq + gap);
                        rect.set(l, t, l + sq, t + sq);
                        cv.drawRoundRect(rect, r, r, p);
                    }
                }
                break;
            }
            default:
                break;
        }
    }
}
