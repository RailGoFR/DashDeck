package com.dashdeck.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Une page de la grille : place ses enfants en lignes/colonnes de cellules égales. */
public class GridPage extends ViewGroup {
    private int cols = 4;
    private int rows = 2;

    public GridPage(Context context) {
        super(context);
    }

    public void setGrid(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        int cw = w / cols;
        int ch = h / rows;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        int cw = w / cols;
        int ch = h / rows;
        for (int i = 0; i < getChildCount(); i++) {
            int col = i % cols;
            int row = i / cols;
            getChildAt(i).layout(col * cw, row * ch, (col + 1) * cw, (row + 1) * ch);
        }
    }
}
