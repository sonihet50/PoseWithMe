package com.s23cp5054.posewithme.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.camera.PreviewTransform;
import com.s23cp5054.posewithme.pose.detection.PoseFrame;

/**
 * Draws the detected skeleton on top of the camera preview. The person's own left side is drawn
 * in orange and their right side in blue, which makes mirroring mistakes easy to spot.
 */
public class PoseOverlayView extends View {

    /**
     * MediaPipe Pose landmark pairs drawn as the skeleton. Face points and finger detail are
     * left out to keep the guide readable; one wrist-to-index and ankle-to-toe segment still
     * shows which way hands and feet point.
     */
    private static final int[][] CONNECTIONS = {
            {11, 12}, {11, 13}, {13, 15}, {15, 19},
            {12, 14}, {14, 16}, {16, 20},
            {11, 23}, {12, 24}, {23, 24},
            {23, 25}, {25, 27}, {27, 31},
            {24, 26}, {26, 28}, {28, 32},
    };

    /** Nose, shoulders, elbows, wrists, hips, knees, ankles. */
    private static final int[] JOINTS = {0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28};

    private static final float MIN_VISIBILITY = 0.5f;

    private final PreviewTransform transform = new PreviewTransform();
    private final Paint leftPaint;
    private final Paint rightPaint;
    private final Paint centerPaint;
    private final Paint jointFillPaint;
    private final float jointRadius;

    @Nullable
    private PoseFrame frame;

    public PoseOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        jointRadius = 5f * density;
        leftPaint = linePaint(ContextCompat.getColor(context, R.color.pose_left), density);
        rightPaint = linePaint(ContextCompat.getColor(context, R.color.pose_right), density);
        centerPaint = linePaint(ContextCompat.getColor(context, R.color.pose_center), density);
        jointFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        jointFillPaint.setColor(ContextCompat.getColor(context, R.color.white));
    }

    private static Paint linePaint(int color, float density) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setAlpha(230);
        paint.setStrokeWidth(3.5f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        return paint;
    }

    /** Must be called on the main thread. */
    public void setPoseFrame(@Nullable PoseFrame frame) {
        this.frame = frame;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        PoseFrame f = frame;
        if (f == null || !f.hasPose) {
            return;
        }
        transform.update(f.imageWidth, f.imageHeight, getWidth(), getHeight(), f.mirrored);

        for (int[] connection : CONNECTIONS) {
            int a = connection[0];
            int b = connection[1];
            if (f.visibility[a] < MIN_VISIBILITY || f.visibility[b] < MIN_VISIBILITY) {
                continue;
            }
            canvas.drawLine(transform.mapX(f.x[a]), transform.mapY(f.y[a]),
                    transform.mapX(f.x[b]), transform.mapY(f.y[b]), paintFor(a, b));
        }
        for (int i : JOINTS) {
            if (f.visibility[i] < MIN_VISIBILITY) {
                continue;
            }
            float cx = transform.mapX(f.x[i]);
            float cy = transform.mapY(f.y[i]);
            canvas.drawCircle(cx, cy, jointRadius, jointFillPaint);
            canvas.drawCircle(cx, cy, jointRadius, paintFor(i, i));
        }
    }

    private Paint paintFor(int a, int b) {
        if (isLeft(a) && isLeft(b)) {
            return leftPaint;
        }
        if (isRight(a) && isRight(b)) {
            return rightPaint;
        }
        return centerPaint;
    }

    private static boolean isLeft(int index) {
        return (index >= 1 && index <= 3) || (index >= 7 && index % 2 == 1);
    }

    private static boolean isRight(int index) {
        return (index >= 4 && index <= 6) || (index >= 8 && index % 2 == 0);
    }
}
