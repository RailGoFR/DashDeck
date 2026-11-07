package com.dashdeck.launcher;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

/** Une app lançable installée sur l'appareil. */
public final class AppInfo {
    public final String pkg;
    public final String activity;
    public final String label;
    private final ResolveInfo info;

    public AppInfo(PackageManager pm, ResolveInfo ri) {
        this.info = ri;
        this.pkg = ri.activityInfo.packageName;
        this.activity = ri.activityInfo.name;
        CharSequence l = ri.loadLabel(pm);
        this.label = l == null ? pkg : l.toString();
    }

    public Drawable loadIcon(PackageManager pm) {
        try {
            return info.loadIcon(pm);
        } catch (Throwable t) {
            return null;
        }
    }
}
