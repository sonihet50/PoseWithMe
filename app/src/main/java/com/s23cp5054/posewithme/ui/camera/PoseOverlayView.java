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
import com.s23cp5054.posewithme.pose.matching.BodyPart;
import com.s23cp5054.posewithme.pose.matching.MatchResult;
import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.Landmark;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.pose.model.PoseGroup;
import com.s23cp5054.posewithme.ui.pose.SilhouetteRenderer;

import java.util.Map;

/**
 * Draws over the camera preview: the target pose as a translucent mannequin "ghost" anchored
 * to the user's hips and scaled to their torso, and the user's detected skeleton on top.
 * Without a target the skeleton uses left/right colours; with one, each body part is coloured
 * by how well it matches.
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
    private static final int GHOST_ALPHA = 0x70;
    /** Smoothing for the ghost's anchor so it doesn't jitter with the detections. */
    private static final float ANCHOR_ALPHA = 0.3f;

    private final PreviewTransform transform = new PreviewTransform();
    private final SilhouetteRenderer ghostRenderer;
    private final Paint leftPaint;
    private final Paint rightPaint;
    private final Paint centerPaint;
    private final Paint goodPaint;
    private final Paint adjustPaint;
    private final Paint offPaint;
    private final Paint jointFillPaint;
    private final float jointRadius;

    @Nullable private PoseFrame frame;
    @Nullable private PoseTemplate target;
    @Nullable private BodyPose targetMirrored;
    private boolean useMirroredTarget;
    @Nullable private Map<BodyPart, MatchResult.Status> partStatus;
    private boolean showSkeleton = true;
    private boolean showGhost = true;

    private boolean anchorValid;
    private float anchorX;
    private float anchorY;
    private float anchorScale;
    private final float[] groupFit = new float[3];
    private final Paint rolePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PoseOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        jointRadius = 5f * density;
        leftPaint = linePaint(color(R.color.pose_left), density);
        rightPaint = linePaint(color(R.color.pose_right), density);
        centerPaint = linePaint(color(R.color.pose_center), density);
        goodPaint = linePaint(color(R.color.match_good), density);
        adjustPaint = linePaint(color(R.color.match_adjust), density);
        offPaint = linePaint(color(R.color.match_off), density);
        jointFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        jointFillPaint.setColor(color(R.color.white));
        rolePaint.setColor(color(R.color.white));
        rolePaint.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
        rolePaint.setTextAlign(Paint.Align.CENTER);
        rolePaint.setShadowLayer(3f * density, 0, 0, color(R.color.black));
        ghostRenderer = new SilhouetteRenderer(color(R.color.ghost_near), color(R.color.ghost_far));
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
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
    public void setTarget(@Nullable PoseTemplate target) {
        this.target = target;
        this.targetMirrored = target != null && target.mirrorAllowed ? target.pose.mirrored() : null;
        anchorValid = false;
        invalidate();
    }

    /** Must be called on the main thread. */
    public void setPoseFrame(@Nullable PoseFrame frame) {
        this.frame = frame;
        invalidate();
    }

    /** Per-part match colours and which side of the target matched. Main thread only. */
    public void setMatchState(@Nullable Map<BodyPart, MatchResult.Status> partStatus,
                              boolean useMirroredTarget) {
        this.partStatus = partStatus;
        this.useMirroredTarget = useMirroredTarget;
    }

    public void setShowGhost(boolean show) {
        showGhost = show;
        invalidate();
    }

    public void setShowSkeleton(boolean show) {
        showSkeleton = show;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        PoseFrame f = frame;
        boolean hasPerson = f != null && f.hasPose;
        if (hasPerson) {
            transform.update(f.imageWidth, f.imageHeight, getWidth(), getHeight(), f.mirrored);
        }
        if (target != null && showGhost) {
            drawGhost(canvas, hasPerson ? f : null);
        }
        if (hasPerson && showSkeleton) {
            for (int i = 0; i < f.imagePoses.size(); i++) {
                drawSkeleton(canvas, f.imagePoses.get(i), f.imageWidth, f.imageHeight);
                if (target != null && target.people.size() > 1) {
                    BodyPose person = f.imagePoses.get(i);
                    canvas.drawText(String.valueOf(i + 1),
                            transform.mapX(PoseGroup.hipX(person) / f.imageWidth),
                            transform.mapY(PoseGroup.hipY(person) / f.imageHeight), rolePaint);
                }
            }
        }
    }

    private void drawGhost(Canvas canvas, @Nullable PoseFrame f) {
        if (target.people.size() > 1) {
            drawGroupGhost(canvas, f);
            return;
        }
        float x;
        float y;
        float scale;
        boolean mirror;
        // Waist-up poses anchor on MediaPipe's estimate of hips just below the frame.
        float minHip = target.framing == PoseTemplate.Framing.UPPER_BODY
                ? BodyPose.MIN_HIDDEN_HIP_VISIBILITY : MIN_VISIBILITY;
        if (f != null && f.visibility[Landmark.LEFT_HIP] >= minHip
                && f.visibility[Landmark.RIGHT_HIP] >= minHip
                && f.visibility[Landmark.LEFT_SHOULDER] >= MIN_VISIBILITY
                && f.visibility[Landmark.RIGHT_SHOULDER] >= MIN_VISIBILITY) {
            float hipX = (transform.mapX(f.x[Landmark.LEFT_HIP])
                    + transform.mapX(f.x[Landmark.RIGHT_HIP])) / 2f;
            float hipY = (transform.mapY(f.y[Landmark.LEFT_HIP])
                    + transform.mapY(f.y[Landmark.RIGHT_HIP])) / 2f;
            float shX = (transform.mapX(f.x[Landmark.LEFT_SHOULDER])
                    + transform.mapX(f.x[Landmark.RIGHT_SHOULDER])) / 2f;
            float shY = (transform.mapY(f.y[Landmark.LEFT_SHOULDER])
                    + transform.mapY(f.y[Landmark.RIGHT_SHOULDER])) / 2f;
            x = hipX;
            y = hipY;
            scale = (float) Math.hypot(shX - hipX, shY - hipY);
            mirror = f.mirrored;
        } else {
            // Nobody to anchor to: show the pose where a well-framed person would stand.
            boolean upper = target.framing == PoseTemplate.Framing.UPPER_BODY;
            x = getWidth() / 2f;
            y = getHeight() * (upper ? 0.98f : 0.6f);
            scale = getHeight() * (upper ? 0.34f : 0.18f);
            mirror = false;
            anchorValid = false;
        }

        if (!anchorValid) {
            anchorX = x;
            anchorY = y;
            anchorScale = scale;
            anchorValid = f != null;
        } else {
            anchorX += ANCHOR_ALPHA * (x - anchorX);
            anchorY += ANCHOR_ALPHA * (y - anchorY);
            anchorScale += ANCHOR_ALPHA * (scale - anchorScale);
        }

        BodyPose pose = useMirroredTarget && targetMirrored != null ? targetMirrored : target.pose;
        int save = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), GHOST_ALPHA);
        ghostRenderer.draw(canvas, pose, anchorX, anchorY, anchorScale, mirror);
        canvas.restoreToCount(save);
    }

    private void drawGroupGhost(Canvas canvas, @Nullable PoseFrame f) {
        boolean usable = f != null && f.imagePoses.size() == target.people.size()
                && !f.toBodyPoses(target.framing == PoseTemplate.Framing.UPPER_BODY).isEmpty();
        boolean mirror = frame != null && frame.mirrored;
        if (usable) {
            float x = 0f, y = 0f, scale = 0f;
            for (BodyPose person : f.imagePoses) {
                x += transform.mapX(PoseGroup.hipX(person) / f.imageWidth);
                y += transform.mapY(PoseGroup.hipY(person) / f.imageHeight);
                scale += PoseGroup.torso(person);
            }
            int count = f.imagePoses.size();
            x /= count;
            y /= count;
            scale = scale / count * Math.max((float) getWidth() / f.imageWidth,
                    (float) getHeight() / f.imageHeight);
            if (!anchorValid) {
                anchorX = x; anchorY = y; anchorScale = scale;
            } else {
                anchorX += ANCHOR_ALPHA * (x - anchorX);
                anchorY += ANCHOR_ALPHA * (y - anchorY);
                anchorScale += ANCHOR_ALPHA * (scale - anchorScale);
            }
            anchorValid = true;
        } else {
            SilhouetteRenderer.fit(target.people, 0.3f, getWidth() * 0.05f,
                    getHeight() * 0.05f, getWidth() * 0.9f, getHeight() * 0.9f, groupFit);
            anchorX = mirror ? getWidth() - groupFit[0] : groupFit[0];
            anchorY = groupFit[1];
            anchorScale = groupFit[2];
            anchorValid = false;
        }
        int save = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), GHOST_ALPHA);
        for (int i = 0; i < target.people.size(); i++) {
            BodyPose person = target.people.get(i);
            ghostRenderer.draw(canvas, person, anchorX, anchorY, anchorScale, mirror);
            canvas.drawText(String.valueOf(i + 1), anchorX + (mirror ? -1f : 1f)
                    * PoseGroup.hipX(person) * anchorScale,
                    anchorY + PoseGroup.hipY(person) * anchorScale, rolePaint);
        }
        canvas.restoreToCount(save);
    }

    private void drawSkeleton(Canvas canvas, BodyPose f, int imageWidth, int imageHeight) {
        for (int[] connection : CONNECTIONS) {
            int a = connection[0];
            int b = connection[1];
            if (f.visibility[a] < MIN_VISIBILITY || f.visibility[b] < MIN_VISIBILITY) {
                continue;
            }
            canvas.drawLine(transform.mapX(f.x[a] / imageWidth), transform.mapY(f.y[a] / imageHeight),
                    transform.mapX(f.x[b] / imageWidth), transform.mapY(f.y[b] / imageHeight), paintFor(a, b));
        }
        for (int i : JOINTS) {
            if (f.visibility[i] < MIN_VISIBILITY) {
                continue;
            }
            float cx = transform.mapX(f.x[i] / imageWidth);
            float cy = transform.mapY(f.y[i] / imageHeight);
            canvas.drawCircle(cx, cy, jointRadius, jointFillPaint);
            canvas.drawCircle(cx, cy, jointRadius, paintFor(i, i));
        }
    }

    private Paint paintFor(int a, int b) {
        Map<BodyPart, MatchResult.Status> status = partStatus;
        if (target != null && target.people.size() == 1 && status != null) {
            MatchResult.Status s = status.get(partOf(a, b));
            if (s == MatchResult.Status.GOOD) {
                return goodPaint;
            } else if (s == MatchResult.Status.ADJUST) {
                return adjustPaint;
            } else if (s == MatchResult.Status.OFF) {
                return offPaint;
            }
            return centerPaint;
        }
        if (isLeft(a) && isLeft(b)) {
            return leftPaint;
        }
        if (isRight(a) && isRight(b)) {
            return rightPaint;
        }
        return centerPaint;
    }

    /** Body part a skeleton segment (or joint, when a == b) is coloured by. */
    private static BodyPart partOf(int a, int b) {
        if (a == Landmark.NOSE && b == Landmark.NOSE) {
            return BodyPart.HEAD;
        }
        if (isTorsoJoint(a) && isTorsoJoint(b)) {
            return BodyPart.TORSO;
        }
        // Limb segments are named by their outer joint; odd indices are the person's left.
        int outer = Math.max(a, b);
        if (outer <= Landmark.RIGHT_INDEX + 2) {
            return outer % 2 == 1 ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
        }
        return outer % 2 == 1 ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
    }

    private static boolean isTorsoJoint(int i) {
        return i == Landmark.LEFT_SHOULDER || i == Landmark.RIGHT_SHOULDER
                || i == Landmark.LEFT_HIP || i == Landmark.RIGHT_HIP;
    }

    private static boolean isLeft(int index) {
        return (index >= 1 && index <= 3) || (index >= 7 && index % 2 == 1);
    }

    private static boolean isRight(int index) {
        return (index >= 4 && index <= 6) || (index >= 8 && index % 2 == 0);
    }
}
