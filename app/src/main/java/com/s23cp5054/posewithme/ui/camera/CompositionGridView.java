package com.s23cp5054.posewithme.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Rule-of-thirds guide lines drawn over the viewfinder. */
public class CompositionGridView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CompositionGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        linePaint.setColor(0x66FFFFFF);
        linePaint.setStrokeWidth(getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        for (int i = 1; i <= 2; i++) {
            canvas.drawLine(w * i / 3f, 0, w * i / 3f, h, linePaint);
            canvas.drawLine(0, h * i / 3f, w, h * i / 3f, linePaint);
        }
    }
}
