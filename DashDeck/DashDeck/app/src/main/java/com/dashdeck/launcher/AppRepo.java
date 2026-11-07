package com.dashdeck.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Liste des apps installées + liste des apps épinglées (persistée). */
public final class AppRepo {

    /** Apps épinglées au premier lancement, si elles sont installées. */
    private static final String[] DEFAULTS = {
            "com.google.android.apps.maps",
            "com.waze",
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.chrome",
            "com.android.settings"
    };

    private final Context ctx;
    private final PackageManager pm;
    private final SharedPreferences sp;
    private List<AppInfo> all = new ArrayList<>();
    private final Map<String, AppInfo> byPkg = new HashMap<>();

    public AppRepo(Context context) {
        ctx = context.getApplicationContext();
        pm = ctx.getPackageManager();
        sp = ctx.getSharedPreferences("dashdeck", Context.MODE_PRIVATE);
        reload();
        if (!sp.getBoolean("seeded", false)) {
            seed();
        }
    }

    public SharedPreferences prefs() {
        return sp;
    }

    /** Relit la liste des apps installées (à appeler après installation/désinstallation). */
    public void reload() {
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        List<AppInfo> out = new ArrayList<>();
        Map<String, AppInfo> map = new HashMap<>();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (p.equals(ctx.getPackageName()) || map.containsKey(p)) continue;
            AppInfo a = new AppInfo(pm, ri);
            map.put(p, a);
            out.add(a);
        }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        all = out;
        byPkg.clear();
        byPkg.putAll(map);
    }

    public List<AppInfo> all() {
        return all;
    }

    private void seed() {
        List<String> pinned = new ArrayList<>();
        for (String p : DEFAULTS) {
            if (byPkg.containsKey(p) && !pinned.contains(p)) pinned.add(p);
        }
        writePinned(pinned);
        sp.edit().putBoolean("seeded", true).apply();
    }

    private List<String> readPinned() {
        String s = sp.getString("pinned", "");
        List<String> r = new ArrayList<>();
        if (s == null || s.isEmpty()) return r;
        for (String p : s.split("\n")) {
            if (!p.isEmpty()) r.add(p);
        }
        return r;
    }

    private void writePinned(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String p : l) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p);
        }
        sp.edit().putString("pinned", sb.toString()).apply();
    }

    /** Apps épinglées, dans l'ordre, sans celles qui ont été désinstallées. */
    public List<AppInfo> resolvePinned() {
        List<AppInfo> out = new ArrayList<>();
        for (String p : readPinned()) {
            AppInfo a = byPkg.get(p);
            if (a != null) out.add(a);
        }
        return out;
    }

    public boolean isPinned(String pkg) {
        return readPinned().contains(pkg);
    }

    public void pin(String pkg) {
        List<String> l = readPinned();
        if (!l.contains(pkg)) {
            l.add(pkg);
            writePinned(l);
        }
    }

    public void unpin(String pkg) {
        List<String> l = readPinned();
        if (l.remove(pkg)) writePinned(l);
    }
}
