package com.s23cp5054.posewithme.ui.pose;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.pose.model.BodyPose;

/** Shows a pose as a mannequin silhouette scaled to fit the view. */
public class PoseThumbnailView extends View {

    private static final float MIN_VISIBILITY = 0.3f;

    private final SilhouetteRenderer renderer;
    private final float[] fit = new float[3];
    @Nullable
    private BodyPose pose;

    public PoseThumbnailView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        renderer = new SilhouetteRenderer(
                ContextCompat.getColor(context, R.color.silhouette_near),
                ContextCompat.getColor(context, R.color.silhouette_far));
        renderer.setMinVisibility(MIN_VISIBILITY);
    }

    public void setPose(@Nullable BodyPose pose) {
        this.pose = pose;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pose == null) {
            return;
        }
        float pad = getResources().getDisplayMetrics().density * 8f;
        SilhouetteRenderer.fit(pose, MIN_VISIBILITY, getPaddingLeft() + pad,
                getPaddingTop() + pad,
                getWidth() - getPaddingLeft() - getPaddingRight() - 2 * pad,
                getHeight() - getPaddingTop() - getPaddingBottom() - 2 * pad, fit);
        renderer.draw(canvas, pose, fit[0], fit[1], fit[2], false);
    }
}
