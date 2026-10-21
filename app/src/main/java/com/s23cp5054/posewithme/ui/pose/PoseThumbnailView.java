package com.s23cp5054.posewithme.ui.pose;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.pose.model.BodyPose;
import java.util.Collections;
import java.util.List;

/** Shows a pose as a mannequin silhouette scaled to fit the view. */
public class PoseThumbnailView extends View {

    private static final float MIN_VISIBILITY = 0.3f;

    private final SilhouetteRenderer renderer;
    private final float[] fit = new float[3];
    @Nullable
    private List<BodyPose> people = Collections.emptyList();

    public PoseThumbnailView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        renderer = new SilhouetteRenderer(
                ContextCompat.getColor(context, R.color.silhouette_near),
                ContextCompat.getColor(context, R.color.silhouette_far));
        renderer.setMinVisibility(MIN_VISIBILITY);
    }

    public void setPose(@Nullable BodyPose pose) {
        setPeople(pose == null ? Collections.emptyList() : Collections.singletonList(pose));
    }

    public void setPeople(List<BodyPose> people) {
        this.people = people;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (people.isEmpty()) {
            return;
        }
        float pad = getResources().getDisplayMetrics().density * 8f;
        SilhouetteRenderer.fit(people, MIN_VISIBILITY, getPaddingLeft() + pad,
                getPaddingTop() + pad,
                getWidth() - getPaddingLeft() - getPaddingRight() - 2 * pad,
                getHeight() - getPaddingTop() - getPaddingBottom() - 2 * pad, fit);
        for (BodyPose pose : people) {
            renderer.draw(canvas, pose, fit[0], fit[1], fit[2], false);
        }
    }
}
