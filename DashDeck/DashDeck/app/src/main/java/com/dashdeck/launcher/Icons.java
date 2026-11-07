package com.dashdeck.launcher;

import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

import java.util.ArrayList;

/** Uniformise les icônes d'apps pour obtenir une grille homogène de carrés arrondis. */
final class Icons {
    private Icons() {}

    static Drawable normalize(Drawable src) {
        if (src == null) return new ColorDrawable(0xFF2A2F3C);
        try {
            if (Build.VERSION.SDK_INT >= 26 && src instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable a = (AdaptiveIconDrawable) src;
                ArrayList<Drawable> layers = new ArrayList<>();
                // Les calques adaptatifs font 108dp pour 72dp visibles : on les agrandit de 25 % par côté.
                if (a.getBackground() != null) layers.add(new InsetDrawable(a.getBackground(), -0.25f));
                if (a.getForeground() != null) layers.add(new InsetDrawable(a.getForeground(), -0.25f));
                if (!layers.isEmpty()) {
                    return new LayerDrawable(layers.toArray(new Drawable[0]));
                }
            }
            // Icône classique : posée sur une tuile sombre avec un peu de marge.
            return new LayerDrawable(new Drawable[]{
                    new ColorDrawable(0xFF2A2F3C),
                    new InsetDrawable(src, 0.12f)
            });
        } catch (Throwable t) {
            return src;
        }
    }
}
