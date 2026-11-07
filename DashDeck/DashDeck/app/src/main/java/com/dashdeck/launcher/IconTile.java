package com.dashdeck.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Affiche une icône d'app dans un carré aux coins arrondis (style iOS / CarPlay). */
public class IconTile extends View {
    private Drawable icon;

    public IconTile(Context context) {
        super(context);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getWidth() * 0.225f);
            }
        });
    }

    public void setIcon(Drawable d) {
        icon = d;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        invalidateOutline();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (icon != null) {
            icon.setBounds(0, 0, getWidth(), getHeight());
            icon.draw(canvas);
        }
    }
}
