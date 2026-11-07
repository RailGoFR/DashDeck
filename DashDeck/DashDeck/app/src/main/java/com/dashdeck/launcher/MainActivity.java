package com.dashdeck.launcher;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Écran d'accueil façon CarPlay : colonne horloge/boutons à gauche, grille d'apps paginée,
 * mode édition (appui long) pour retirer des apps, feuille pour en ajouter.
 */
public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int[] ACCENTS = {0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2};
    private static final int[][] WALLS = {
            {0xFF0B1220, 0xFF1D2B4F},
            {0xFF000000, 0xFF101014},
            {0xFF0E2A33, 0xFF1F5B6E},
            {0xFF1A0B2E, 0xFF4A2C82}
    };
    private static final String[] WALL_NAMES = {"Nuit", "Noir", "Océan", "Violet"};
    private static final int[] CELL_DP = {104, 128, 156};
    private static final String[] SIZE_NAMES = {"Petites", "Moyennes", "Grandes"};

    private final Random rnd = new Random();

    private AppRepo repo;
    private SharedPreferences sp;
    private PackageManager pm;

    private FrameLayout rootFrame;
    private LinearLayout main;
    private ViewPager2 pager;
    private DotsView dots;
    private FrameLayout overlay;
    private TextView batteryText;
    private FrameLayout editButton;
    private IconView editIcon;
    private PageAdapter adapter;

    private List<AppInfo> items = new ArrayList<>();
    private boolean editMode = false;
    private boolean sheetOpen = false;
    private Runnable sheetOnClose;

    private int cols = 4;
    private int rows = 2;
    private int cellW = 0;
    private int cellH = 0;
    private int iconPx = 0;
    private int lastW = -1;
    private int lastH = -1;
    private long introUntil = 0;
    private boolean firstBuild = true;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        repo = new AppRepo(this);
        sp = repo.prefs();

        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams wlp = getWindow().getAttributes();
            wlp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(wlp);
        }

        applyOrientation();
        applyKeepOn();
        items = repo.resolvePinned();

        buildUi();
        setContentView(rootFrame);
        applyTheme();
        enterImmersive();

        main.setAlpha(0f);
        main.animate().alpha(1f).setDuration(380).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent sticky;
        if (Build.VERSION.SDK_INT >= 33) {
            sticky = registerReceiver(batteryReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            sticky = registerReceiver(batteryReceiver, f);
        }
        updateBattery(sticky);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        // Une app a pu être installée / désinstallée pendant qu'on était ailleurs.
        repo.reload();
        List<AppInfo> now = repo.resolvePinned();
        if (!samePackages(items, now)) {
            items = now;
            rebuildGrid();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Appui sur Home : on revient à l'état de repos.
        if (sheetOpen) hideSheet();
        if (editMode) setEditMode(false);
        if (pager != null && pager.getCurrentItem() != 0) pager.setCurrentItem(0, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (sheetOpen) {
            hideSheet();
        } else if (editMode) {
            setEditMode(false);
        } else if (pager != null && pager.getCurrentItem() != 0) {
            pager.setCurrentItem(0, true);
        } else {
            moveTaskToBack(true);
        }
    }

    // ------------------------------------------------------------------ interface

    private void buildUi() {
        final boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        rootFrame = new FrameLayout(this);
        main = new LinearLayout(this);
        main.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        rootFrame.addView(main, new FrameLayout.LayoutParams(MATCH, MATCH));

        rootFrame.setOnApplyWindowInsetsListener((view, insets) -> {
            int l = insets.getSystemWindowInsetLeft();
            int t = insets.getSystemWindowInsetTop();
            int r = insets.getSystemWindowInsetRight();
            int b = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout dc = insets.getDisplayCutout();
                if (dc != null) {
                    l = Math.max(l, dc.getSafeInsetLeft());
                    t = Math.max(t, dc.getSafeInsetTop());
                    r = Math.max(r, dc.getSafeInsetRight());
                    b = Math.max(b, dc.getSafeInsetBottom());
                }
            }
            main.setPadding(l, t, r, b);
            return insets;
        });

        // ----- colonne latérale (ou barre du bas en portrait) -----
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(land ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        rail.setGravity(Gravity.CENTER);
        rail.setBackground(rounded(0x1AFFFFFF, dp(30)));
        if (land) {
            rail.setPadding(dp(8), dp(18), dp(8), dp(18));
        } else {
            rail.setPadding(dp(20), dp(8), dp(16), dp(8));
        }

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(land ? Gravity.CENTER_HORIZONTAL : (Gravity.START | Gravity.CENTER_VERTICAL));

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, land ? 27 : 30);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        clockBox.addView(time, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE d MMM");
        date.setFormat24Hour("EEE d MMM");
        date.setTextColor(0xB3FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        dateLp.topMargin = dp(2);
        clockBox.addView(date, dateLp);

        batteryText = new TextView(this);
        batteryText.setTextColor(0x99FFFFFF);
        batteryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams batLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        batLp.topMargin = dp(2);
        clockBox.addView(batteryText, batLp);

        rail.addView(clockBox, new LinearLayout.LayoutParams(WRAP, WRAP));

        View spacer = new View(this);
        rail.addView(spacer, land ? new LinearLayout.LayoutParams(1, 0, 1f)
                : new LinearLayout.LayoutParams(0, 1, 1f));

        IconView addIcon = new IconView(this, IconView.PLUS);
        FrameLayout addBtn = railButton(addIcon);
        addBtn.setContentDescription("Ajouter des apps");
        addBtn.setOnClickListener(x -> showPicker());

        editIcon = new IconView(this, IconView.GRID);
        editButton = railButton(editIcon);
        editButton.setContentDescription("Modifier la grille");
        editButton.setOnClickListener(x -> setEditMode(!editMode));

        IconView setIcon = new IconView(this, IconView.SLIDERS);
        FrameLayout setBtn = railButton(setIcon);
        setBtn.setContentDescription("Réglages");
        setBtn.setOnClickListener(x -> showSettings());

        FrameLayout[] btns = {addBtn, editButton, setBtn};
        for (int i = 0; i < btns.length; i++) {
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(54), dp(54));
            if (land) {
                if (i > 0) bl.topMargin = dp(12);
            } else {
                bl.leftMargin = dp(12);
            }
            rail.addView(btns[i], bl);
        }

        // ----- contenu : pages + points -----
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        pager = new ViewPager2(this);
        pager.setOffscreenPageLimit(1);
        adapter = new PageAdapter();
        pager.setAdapter(adapter);
        View inner = pager.getChildAt(0);
        if (inner instanceof RecyclerView) {
            inner.setOverScrollMode(View.OVER_SCROLL_NEVER);
            ((RecyclerView) inner).setItemAnimator(null);
        }
        pager.setPageTransformer((page, position) -> {
            float a = Math.min(1f, Math.abs(position));
            page.setAlpha(1f - a * 0.65f);
            float s = 1f - a * 0.07f;
            page.setScaleX(s);
            page.setScaleY(s);
        });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float offset, int offsetPx) {
                dots.setPosition(position + offset);
            }
        });
        pager.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, oldRight, ob) -> {
            int w = r - l;
            int h = b - t;
            if (w != lastW || h != lastH) {
                lastW = w;
                lastH = h;
                pager.post(this::rebuildGrid);
            }
        });
        content.addView(pager, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        dots = new DotsView(this);
        content.addView(dots, new LinearLayout.LayoutParams(MATCH, dp(22)));

        // ----- assemblage -----
        if (land) {
            LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(dp(104), MATCH);
            railLp.setMargins(dp(14), dp(14), 0, dp(14));
            main.addView(rail, railLp);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, MATCH, 1f);
            cLp.setMargins(dp(8), dp(8), dp(8), dp(6));
            main.addView(content, cLp);
        } else {
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
            cLp.setMargins(dp(8), dp(12), dp(8), 0);
            main.addView(content, cLp);
            LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(MATCH, dp(84));
            railLp.setMargins(dp(14), dp(6), dp(14), dp(14));
            main.addView(rail, railLp);
        }

        // ----- couche des feuilles (ajout d'apps, réglages) -----
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x99000000);
        overlay.setVisibility(View.GONE);
        overlay.setOnClickListener(x -> hideSheet());
        rootFrame.addView(overlay, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private FrameLayout railButton(IconView icon) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(oval(0x22FFFFFF));
        f.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        pressable(f);
        return f;
    }

    private void updateEditButton() {
        if (editButton == null) return;
        if (editMode) {
            editButton.setBackground(oval(accent()));
            editIcon.setType(IconView.CHECK);
            editButton.setContentDescription("Terminer la modification");
        } else {
            editButton.setBackground(oval(0x22FFFFFF));
            editIcon.setType(IconView.GRID);
            editButton.setContentDescription("Modifier la grille");
        }
    }

    // ------------------------------------------------------------------ grille

    private void computeGrid(int w, int h) {
        int target = dp(CELL_DP[sizeIndex()]);
        cols = clamp(Math.round((float) w / target), 2, 8);
        rows = clamp(Math.round((float) h / (target * 1.15f)), 1, 5);
        cellW = w / cols;
        cellH = h / rows;
        iconPx = clamp(Math.min((int) (cellW * 0.60f), (int) (cellH * 0.56f)), dp(44), dp(108));
    }

    private boolean showAddTile() {
        return editMode || items.isEmpty();
    }

    private void rebuildGrid() {
        if (pager == null || pager.getWidth() == 0 || pager.getHeight() == 0) return;
        if (firstBuild) {
            firstBuild = false;
            introUntil = SystemClock.uptimeMillis() + 700;
        }
        computeGrid(pager.getWidth(), pager.getHeight());
        int slots = cols * rows;
        int total = items.size() + (showAddTile() ? 1 : 0);
        int pages = Math.max(1, (total + slots - 1) / slots);
        int cur = Math.min(pager.getCurrentItem(), pages - 1);
        adapter.pageCount = pages;
        adapter.notifyDataSetChanged();
        pager.setCurrentItem(cur, false);
        dots.setCount(pages);
        dots.setVisibility(pages > 1 ? View.VISIBLE : View.INVISIBLE);
        dots.setPosition(cur);
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        int pageCount = 1;

        @Override
        public PageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            GridPage page = new GridPage(MainActivity.this);
            page.setLayoutParams(new RecyclerView.LayoutParams(MATCH, MATCH));
            return new PageHolder(page);
        }

        @Override
        public void onBindViewHolder(PageHolder holder, int position) {
            GridPage page = holder.page;
            page.removeAllViews();
            page.setGrid(cols, rows);
            int slots = cols * rows;
            int start = position * slots;
            int total = items.size() + (showAddTile() ? 1 : 0);
            int end = Math.min(total, start + slots);
            for (int i = start; i < end; i++) {
                View tile = i < items.size() ? makeAppTile(items.get(i), i - start) : makeAddTile();
                page.addView(tile);
            }
        }

        @Override
        public int getItemCount() {
            return pageCount;
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final GridPage page;

        PageHolder(GridPage p) {
            super(p);
            page = p;
        }
    }

    private View makeAppTile(final AppInfo app, int indexOnPage) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);

        FrameLayout wrap = new FrameLayout(this);
        int wrapPx = iconPx + dp(12);
        IconTile icon = new IconTile(this);
        icon.setIcon(Icons.normalize(app.loadIcon(pm)));
        wrap.addView(icon, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        tile.addView(wrap, new LinearLayout.LayoutParams(wrapPx, wrapPx));

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(0xF2FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setShadowLayer(4f, 0f, 1f, 0x66000000);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Math.max(cellW - dp(8), dp(40)), WRAP);
        llp.topMargin = dp(2);
        tile.addView(label, llp);

        if (editMode) {
            FrameLayout badge = new FrameLayout(this);
            badge.setBackground(oval(0xFFFF453A));
            IconView minus = new IconView(this, IconView.MINUS);
            badge.addView(minus, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
            wrap.addView(badge, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.START));
            badge.setContentDescription("Retirer " + app.label);
            badge.setOnClickListener(x -> removeApp(app, tile));
            startWiggleOnAttach(wrap);
        }

        tile.setOnClickListener(x -> {
            if (!editMode) launch(app);
        });
        tile.setOnLongClickListener(x -> {
            if (!editMode) {
                x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                setEditMode(true);
            }
            return true;
        });
        pressable(tile);

        if (SystemClock.uptimeMillis() < introUntil && !editMode) {
            tile.setAlpha(0f);
            tile.setScaleX(0.8f);
            tile.setScaleY(0.8f);
            tile.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(55L * indexOnPage)
                    .setDuration(340)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
        }
        return tile;
    }

    private View makeAddTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);

        FrameLayout wrap = new FrameLayout(this);
        int wrapPx = iconPx + dp(12);
        FrameLayout box = new FrameLayout(this);
        GradientDrawable g = rounded(0x1FFFFFFF, iconPx * 0.225f);
        g.setStroke(dp(2), 0x45FFFFFF, dp(7), dp(5));
        box.setBackground(g);
        IconView plus = new IconView(this, IconView.PLUS);
        int plusPx = Math.round(iconPx * 0.42f);
        box.addView(plus, new FrameLayout.LayoutParams(plusPx, plusPx, Gravity.CENTER));
        wrap.addView(box, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        tile.addView(wrap, new LinearLayout.LayoutParams(wrapPx, wrapPx));

        TextView label = new TextView(this);
        label.setText("Ajouter");
        label.setTextColor(0xCCFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(WRAP, WRAP);
        llp.topMargin = dp(2);
        tile.addView(label, llp);

        tile.setOnClickListener(x -> showPicker());
        pressable(tile);
        return tile;
    }

    private void startWiggleOnAttach(final View target) {
        final ObjectAnimator[] holder = new ObjectAnimator[1];
        target.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                ObjectAnimator a = ObjectAnimator.ofFloat(view, View.ROTATION, -1.8f, 1.8f);
                a.setDuration(120 + rnd.nextInt(50));
                a.setRepeatMode(ObjectAnimator.REVERSE);
                a.setRepeatCount(ObjectAnimator.INFINITE);
                a.setStartDelay(rnd.nextInt(120));
                a.start();
                holder[0] = a;
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                if (holder[0] != null) holder[0].cancel();
                view.setRotation(0f);
            }
        });
    }

    private void removeApp(final AppInfo app, final View tile) {
        tile.animate().scaleX(0f).scaleY(0f).alpha(0f)
                .setStartDelay(0)
                .setDuration(190)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    repo.unpin(app.pkg);
                    items = repo.resolvePinned();
                    rebuildGrid();
                })
                .start();
    }

    private void setEditMode(boolean on) {
        if (editMode == on) return;
        editMode = on;
        updateEditButton();
        rebuildGrid();
    }

    private void launch(AppInfo app) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(app.pkg, app.activity))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ feuille « ajouter des apps »

    private void showPicker() {
        if (sheetOpen) return;
        repo.reload();
        final List<AppInfo> all = repo.all();

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(rounded(0xFF1C1C1E, dp(28)));
        sheet.setClipToOutline(true);
        sheet.setPadding(dp(20), dp(18), dp(20), dp(10));

        LinearLayout head = row();
        head.addView(tv("Mes apps", 22, Color.WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(pill("Terminé", accent(), x -> hideSheet()));
        sheet.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView hint = tv("Touchez une app pour l'ajouter ou la retirer.", 13, 0x99FFFFFF, false);
        hint.setPadding(0, dp(4), 0, dp(10));
        sheet.addView(hint, new LinearLayout.LayoutParams(MATCH, WRAP));

        EditText search = new EditText(this);
        search.setHint("Rechercher une app");
        search.setHintTextColor(0x88FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setBackground(rounded(0xFF2C2C2E, dp(14)));
        search.setPadding(dp(16), dp(11), dp(16), dp(11));
        sheet.addView(search, new LinearLayout.LayoutParams(MATCH, WRAP));

        final PickerAdapter pa = new PickerAdapter(all);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(pa);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        rvLp.topMargin = dp(8);
        sheet.addView(rv, rvLp);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                pa.filter(s.toString());
            }
        });

        int w = Math.min(dp(560), rootFrame.getWidth() - dp(32));
        int h = Math.round(rootFrame.getHeight() * 0.86f);
        showSheet(sheet, new FrameLayout.LayoutParams(w, h, Gravity.CENTER), () -> {
            items = repo.resolvePinned();
            rebuildGrid();
        });
    }

    private final class PickerAdapter extends RecyclerView.Adapter<PickerHolder> {
        private final List<AppInfo> all;
        private List<AppInfo> shown;

        PickerAdapter(List<AppInfo> all) {
            this.all = all;
            this.shown = all;
        }

        void filter(String q) {
            String s = q.trim().toLowerCase();
            if (s.isEmpty()) {
                shown = all;
            } else {
                List<AppInfo> r = new ArrayList<>();
                for (AppInfo a : all) {
                    if (a.label.toLowerCase().contains(s)) r.add(a);
                }
                shown = r;
            }
            notifyDataSetChanged();
        }

        @Override
        public PickerHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(8), dp(6), dp(8));
            row.setLayoutParams(new RecyclerView.LayoutParams(MATCH, WRAP));

            IconTile ic = new IconTile(MainActivity.this);
            row.addView(ic, new LinearLayout.LayoutParams(dp(46), dp(46)));

            TextView label = tv("", 16, Color.WHITE, false);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            lp.leftMargin = dp(14);
            lp.rightMargin = dp(10);
            row.addView(label, lp);

            FrameLayout check = new FrameLayout(MainActivity.this);
            IconView checkIcon = new IconView(MainActivity.this, IconView.CHECK);
            check.addView(checkIcon, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
            row.addView(check, new LinearLayout.LayoutParams(dp(28), dp(28)));

            return new PickerHolder(row, ic, label, check, checkIcon);
        }

        @Override
        public void onBindViewHolder(final PickerHolder h, int position) {
            final AppInfo app = shown.get(position);
            h.icon.setIcon(Icons.normalize(app.loadIcon(pm)));
            h.label.setText(app.label);
            applyCheck(h, repo.isPinned(app.pkg));
            h.itemView.setOnClickListener(x -> {
                boolean now = !repo.isPinned(app.pkg);
                if (now) repo.pin(app.pkg);
                else repo.unpin(app.pkg);
                applyCheck(h, now);
            });
        }

        private void applyCheck(PickerHolder h, boolean on) {
            if (on) {
                h.check.setBackground(oval(accent()));
                h.checkIcon.setVisibility(View.VISIBLE);
            } else {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Color.TRANSPARENT);
                g.setStroke(dp(2), 0x55FFFFFF);
                h.check.setBackground(g);
                h.checkIcon.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    private static final class PickerHolder extends RecyclerView.ViewHolder {
        final IconTile icon;
        final TextView label;
        final FrameLayout check;
        final IconView checkIcon;

        PickerHolder(View row, IconTile icon, TextView label, FrameLayout check, IconView checkIcon) {
            super(row);
            this.icon = icon;
            this.label = label;
            this.check = check;
            this.checkIcon = checkIcon;
        }
    }

    // ------------------------------------------------------------------ feuille « réglages »

    private void showSettings() {
        if (sheetOpen) return;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(rounded(0xFF1C1C1E, dp(28)));
        scroll.setClipToOutline(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(22));
        scroll.addView(box, new FrameLayout.LayoutParams(MATCH, WRAP));
        fillSettings(box);

        int w = Math.min(dp(520), rootFrame.getWidth() - dp(32));
        showSheet(scroll, new FrameLayout.LayoutParams(w, WRAP, Gravity.CENTER), null);
    }

    private void fillSettings(final LinearLayout box) {
        box.removeAllViews();

        LinearLayout head = row();
        head.addView(tv("Réglages", 22, Color.WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(pill("Terminé", accent(), x -> hideSheet()));
        box.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Couleur d'accentuation
        box.addView(section("Couleur d'accentuation"));
        LinearLayout accents = row();
        int curAccent = sp.getInt("accent", 0);
        for (int i = 0; i < ACCENTS.length; i++) {
            final int idx = i;
            FrameLayout f = new FrameLayout(this);
            if (i == curAccent) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(Color.TRANSPARENT);
                ring.setStroke(dp(3), Color.WHITE);
                f.setBackground(ring);
            }
            View dot = new View(this);
            dot.setBackground(oval(ACCENTS[i]));
            f.addView(dot, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
            f.setOnClickListener(x -> {
                sp.edit().putInt("accent", idx).apply();
                applyTheme();
                fillSettings(box);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
            lp.rightMargin = dp(8);
            accents.addView(f, lp);
        }
        box.addView(accents, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Fond d'écran
        box.addView(section("Fond d'écran"));
        LinearLayout walls = row();
        int curWall = wallIndex();
        for (int i = 0; i < WALLS.length; i++) {
            final int idx = i;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{WALLS[i][0], WALLS[i][1]});
            g.setCornerRadius(dp(12));
            g.setStroke(dp(i == curWall ? 3 : 1), i == curWall ? Color.WHITE : 0x44FFFFFF);
            View thumb = new View(this);
            thumb.setBackground(g);
            col.addView(thumb, new LinearLayout.LayoutParams(dp(64), dp(44)));
            TextView cap = tv(WALL_NAMES[i], 11, 0xAAFFFFFF, false);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(WRAP, WRAP);
            cLp.topMargin = dp(4);
            col.addView(cap, cLp);
            col.setOnClickListener(x -> {
                sp.edit().putInt("wall", idx).apply();
                applyTheme();
                fillSettings(box);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(12);
            walls.addView(col, lp);
        }
        box.addView(walls, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Taille des icônes
        box.addView(section("Taille des icônes"));
        LinearLayout sizes = row();
        int curSize = sizeIndex();
        for (int i = 0; i < SIZE_NAMES.length; i++) {
            final int idx = i;
            TextView t = pill(SIZE_NAMES[i], i == curSize ? accent() : 0x22FFFFFF, x -> {
                sp.edit().putInt("size", idx).apply();
                rebuildGrid();
                fillSettings(box);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(8);
            sizes.addView(t, lp);
        }
        box.addView(sizes, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Interrupteurs
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        swLp.topMargin = dp(14);
        box.addView(switchRow("Garder l'écran allumé", sp.getBoolean("keepOn", true), (b, on) -> {
            sp.edit().putBoolean("keepOn", on).apply();
            applyKeepOn();
        }), swLp);
        box.addView(switchRow("Forcer le mode paysage", sp.getBoolean("land", true), (b, on) -> {
            sp.edit().putBoolean("land", on).apply();
            applyOrientation();
        }), new LinearLayout.LayoutParams(MATCH, WRAP));

        // Écran d'accueil par défaut
        TextView home = pill("Utiliser DashDeck comme écran d'accueil", 0x22FFFFFF, x -> openHomeSettings());
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hLp.topMargin = dp(14);
        box.addView(home, hLp);
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private View switchRow(String text, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(8));
        r.addView(tv(text, 16, Color.WHITE, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
        Switch s = new Switch(this);
        s.setChecked(checked);
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, 0xFFD1D1D6}));
        s.setTrackTintList(new ColorStateList(states, new int[]{accent(), 0x66FFFFFF}));
        s.setOnCheckedChangeListener(l);
        r.addView(s, new LinearLayout.LayoutParams(WRAP, WRAP));
        return r;
    }

    // ------------------------------------------------------------------ feuilles : mécanique commune

    private void showSheet(View sheet, FrameLayout.LayoutParams lp, Runnable onClose) {
        sheetOnClose = onClose;
        overlay.animate().cancel();
        overlay.removeAllViews();
        sheet.setClickable(true);
        overlay.addView(sheet, lp);
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        sheet.setTranslationY(dp(48));
        overlay.animate().alpha(1f).setDuration(220).start();
        sheet.animate().translationY(0f).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        sheetOpen = true;
    }

    private void hideSheet() {
        if (!sheetOpen) return;
        sheetOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(rootFrame.getWindowToken(), 0);
        View sheet = overlay.getChildAt(0);
        if (sheet != null) {
            sheet.animate().translationY(dp(48)).setDuration(200).start();
        }
        overlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            overlay.setVisibility(View.GONE);
            overlay.removeAllViews();
            Runnable r = sheetOnClose;
            sheetOnClose = null;
            if (r != null) r.run();
        }).start();
    }

    // ------------------------------------------------------------------ thème, écran, plein écran

    private int accent() {
        int i = sp.getInt("accent", 0);
        return ACCENTS[clamp(i, 0, ACCENTS.length - 1)];
    }

    private int wallIndex() {
        return clamp(sp.getInt("wall", 0), 0, WALLS.length - 1);
    }

    private int sizeIndex() {
        return clamp(sp.getInt("size", 1), 0, CELL_DP.length - 1);
    }

    private void applyTheme() {
        int[] w = WALLS[wallIndex()];
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{w[0], w[1]});
        rootFrame.setBackground(g);
        getWindow().getDecorView().setBackgroundColor(w[0]);
        dots.setAccent(accent());
        updateEditButton();
    }

    private void applyOrientation() {
        setRequestedOrientation(sp.getBoolean("land", true)
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void applyKeepOn() {
        if (sp.getBoolean("keepOn", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @SuppressWarnings("deprecation")
    private void enterImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void updateBattery(Intent i) {
        if (i == null || batteryText == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (level < 0 || scale <= 0) return;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        int pct = Math.round(level * 100f / scale);
        batteryText.setText((charging ? "⚡ " : "") + pct + " %");
    }

    // ------------------------------------------------------------------ petits utilitaires

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean samePackages(List<AppInfo> a, List<AppInfo> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).pkg.equals(b.get(i).pkg)) return false;
        }
        return true;
    }

    private GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView tv(String text, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private TextView section(String text) {
        TextView t = tv(text.toUpperCase(), 12, 0x88FFFFFF, false);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(20), 0, dp(10));
        return t;
    }

    private TextView pill(String text, int bgColor, View.OnClickListener l) {
        TextView t = tv(text, 15, Color.WHITE, true);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setBackground(rounded(bgColor, dp(22)));
        t.setOnClickListener(l);
        pressable(t);
        return t;
    }

    /** Petit effet de rebond au toucher, sans bloquer le clic. */
    private void pressable(View target) {
        target.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.92f).scaleY(0.92f)
                            .setStartDelay(0).setDuration(110)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f)
                            .setStartDelay(0).setDuration(240)
                            .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
