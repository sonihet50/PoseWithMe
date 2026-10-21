package com.s23cp5054.posewithme.ui.pose;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.Landmark;
import java.util.Collections;
import java.util.List;

/**
 * Draws a {@link BodyPose} as an abstract mannequin: tapered capsules for limbs, a rounded
 * torso, neck and an oval head. Parts are painted far-to-near using MediaPipe's depth estimate,
 * and parts well behind the torso use the darker colour so crossed limbs stay readable.
 *
 * Proportions are in torso units and mirror LIMBS in tools/pose_pipeline/posekit.py.
 * Allocation-free per draw, so it can run every frame for the camera ghost.
 */
public final class SilhouetteRenderer {

    private static final int[][] LIMBS = {
            {Landmark.LEFT_SHOULDER, Landmark.LEFT_ELBOW},
            {Landmark.RIGHT_SHOULDER, Landmark.RIGHT_ELBOW},
            {Landmark.LEFT_ELBOW, Landmark.LEFT_WRIST},
            {Landmark.RIGHT_ELBOW, Landmark.RIGHT_WRIST},
            {Landmark.LEFT_HIP, Landmark.LEFT_KNEE},
            {Landmark.RIGHT_HIP, Landmark.RIGHT_KNEE},
            {Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE},
            {Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE},
            {Landmark.LEFT_HEEL, Landmark.LEFT_FOOT_INDEX},
            {Landmark.RIGHT_HEEL, Landmark.RIGHT_FOOT_INDEX},
    };
    private static final float SHOULDER_PUSH = 0.03f;
    private static final float HIP_PUSH = 0.07f;
    private static final float WAIST_T = 0.55f;
    private static final float WAIST_PULL = 0.12f;
    private static final float TORSO_CORNER = 0.07f;
    private static final float[][] LIMB_RADII = {
            {0.095f, 0.08f}, {0.095f, 0.08f},
            {0.085f, 0.06f}, {0.085f, 0.06f},
            {0.16f, 0.11f}, {0.16f, 0.11f},
            {0.10f, 0.065f}, {0.10f, 0.065f},
            {0.06f, 0.045f}, {0.06f, 0.045f},
    };
    private static final int HAND_LEFT = LIMBS.length;
    private static final int HAND_RIGHT = LIMBS.length + 1;
    private static final int TORSO = LIMBS.length + 2;
    private static final int HEAD = LIMBS.length + 3;
    private static final int PART_COUNT = LIMBS.length + 4;

    /** Parts this far behind the torso (torso units) are drawn in the far colour. */
    private static final float FAR_DEPTH = 0.15f;
    private static final float HEAD_RADIUS_X = 0.18f;
    private static final float HEAD_RADIUS_Y = 0.23f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final float[] depth = new float[PART_COUNT];
    private final int[] order = new int[PART_COUNT];

    private int nearColor;
    private int farColor;
    private float minVisibility = 0.3f;

    // Current draw transform.
    private BodyPose pose;
    private float originX;
    private float originY;
    private float scale;
    private boolean mirror;

    public SilhouetteRenderer(int nearColor, int farColor) {
        this.nearColor = nearColor;
        this.farColor = farColor;
        paint.setStyle(Paint.Style.FILL);
    }

    public void setColors(int nearColor, int farColor) {
        this.nearColor = nearColor;
        this.farColor = farColor;
    }

    public void setMinVisibility(float minVisibility) {
        this.minVisibility = minVisibility;
    }

    /**
     * Draws the pose with its hip midpoint at (originX, originY) and one torso length equal to
     * {@code scale} pixels. {@code mirror} flips it horizontally (front-camera preview).
     */
    public void draw(Canvas canvas, BodyPose pose, float originX, float originY, float scale,
                     boolean mirror) {
        this.pose = pose;
        this.originX = originX;
        this.originY = originY;
        this.scale = scale;
        this.mirror = mirror;

        int count = 0;
        for (int i = 0; i < LIMBS.length; i++) {
            if (visible(LIMBS[i][0]) && visible(LIMBS[i][1])) {
                depth[i] = (pose.z[LIMBS[i][0]] + pose.z[LIMBS[i][1]]) / 2f;
                order[count++] = i;
            }
        }
        if (visible(Landmark.LEFT_WRIST) && visible(Landmark.LEFT_INDEX)
                && visible(Landmark.LEFT_PINKY)) {
            depth[HAND_LEFT] = pose.z[Landmark.LEFT_WRIST] - 0.01f;
            order[count++] = HAND_LEFT;
        }
        if (visible(Landmark.RIGHT_WRIST) && visible(Landmark.RIGHT_INDEX)
                && visible(Landmark.RIGHT_PINKY)) {
            depth[HAND_RIGHT] = pose.z[Landmark.RIGHT_WRIST] - 0.01f;
            order[count++] = HAND_RIGHT;
        }
        depth[TORSO] = 0f;
        order[count++] = TORSO;
        depth[HEAD] = -0.05f;
        order[count++] = HEAD;

        // Insertion sort, farthest (largest z) first; at most 14 parts.
        for (int i = 1; i < count; i++) {
            int part = order[i];
            int j = i - 1;
            while (j >= 0 && depth[order[j]] < depth[part]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = part;
        }

        for (int i = 0; i < count; i++) {
            int part = order[i];
            paint.setColor(depth[part] > FAR_DEPTH ? farColor : nearColor);
            if (part < LIMBS.length) {
                int a = LIMBS[part][0];
                int b = LIMBS[part][1];
                capsule(canvas, px(a), py(a), px(b), py(b),
                        LIMB_RADII[part][0] * scale, LIMB_RADII[part][1] * scale);
            } else if (part == HAND_LEFT) {
                drawHand(canvas, Landmark.LEFT_WRIST, Landmark.LEFT_INDEX, Landmark.LEFT_PINKY);
            } else if (part == HAND_RIGHT) {
                drawHand(canvas, Landmark.RIGHT_WRIST, Landmark.RIGHT_INDEX,
                        Landmark.RIGHT_PINKY);
            } else if (part == TORSO) {
                drawTorsoAndNeck(canvas);
            } else {
                drawHead(canvas);
            }
        }
        this.pose = null;
    }

    /**
     * Computes origin and scale that fit the visible parts of a pose inside a box, for
     * thumbnails. Writes {originX, originY, scale} into {@code out}.
     */
    public static void fit(BodyPose pose, float minVisibility, float left, float top,
                           float width, float height, float[] out) {
        fit(Collections.singletonList(pose), minVisibility, left, top, width, height, out);
    }

    public static void fit(List<BodyPose> people, float minVisibility, float left, float top,
                           float width, float height, float[] out) {
        float minX = -0.6f;
        float maxX = 0.6f;
        float minY = -1.6f;
        float maxY = 0.3f;
        for (BodyPose pose : people) {
            for (int i = 0; i < Landmark.COUNT; i++) {
                if (pose.visibility[i] >= minVisibility) {
                    minX = Math.min(minX, pose.x[i]);
                    maxX = Math.max(maxX, pose.x[i]);
                    minY = Math.min(minY, pose.y[i]);
                    maxY = Math.max(maxY, pose.y[i]);
                }
            }
        }
        minX -= 0.3f;
        maxX += 0.3f;
        minY -= 0.35f;
        maxY += 0.2f;
        float scale = Math.min(width / (maxX - minX), height / (maxY - minY));
        out[0] = left + (width - (maxX - minX) * scale) / 2f - minX * scale;
        out[1] = top + (height - (maxY - minY) * scale) / 2f - minY * scale;
        out[2] = scale;
    }

    private boolean visible(int i) {
        return pose.visibility[i] >= minVisibility;
    }

    private float px(int i) {
        return originX + (mirror ? -pose.x[i] : pose.x[i]) * scale;
    }

    private float py(int i) {
        return originY + pose.y[i] * scale;
    }

    private void drawHand(Canvas canvas, int wrist, int index, int pinky) {
        float hx = (px(index) + px(pinky)) / 2f;
        float hy = (py(index) + py(pinky)) / 2f;
        capsule(canvas, px(wrist), py(wrist), hx, hy, 0.065f * scale, 0.05f * scale);
    }

    private void drawTorsoAndNeck(Canvas canvas) {
        float lsx = px(Landmark.LEFT_SHOULDER);
        float lsy = py(Landmark.LEFT_SHOULDER);
        float rsx = px(Landmark.RIGHT_SHOULDER);
        float rsy = py(Landmark.RIGHT_SHOULDER);
        float lhx = px(Landmark.LEFT_HIP);
        float lhy = py(Landmark.LEFT_HIP);
        float rhx = px(Landmark.RIGHT_HIP);
        float rhy = py(Landmark.RIGHT_HIP);

        float shoulderPush = SHOULDER_PUSH * scale;
        float hipPush = HIP_PUSH * scale;
        float sdx = lsx - rsx;
        float sdy = lsy - rsy;
        float sLen = Math.max(1e-3f, (float) Math.hypot(sdx, sdy));
        float hdx = lhx - rhx;
        float hdy = lhy - rhy;
        float hLen = Math.max(1e-3f, (float) Math.hypot(hdx, hdy));

        float lsoX = lsx + sdx / sLen * shoulderPush;
        float lsoY = lsy + sdy / sLen * shoulderPush;
        float rsoX = rsx - sdx / sLen * shoulderPush;
        float rsoY = rsy - sdy / sLen * shoulderPush;
        float lhoX = lhx + hdx / hLen * hipPush;
        float lhoY = lhy + hdy / hLen * hipPush;
        float rhoX = rhx - hdx / hLen * hipPush;
        float rhoY = rhy - hdy / hLen * hipPush;
        // Waist points partway down each side, pulled towards the other side for a taper.
        float lwX = lerp(lsoX, lhoX, WAIST_T);
        float lwY = lerp(lsoY, lhoY, WAIST_T);
        float rwX = lerp(rsoX, rhoX, WAIST_T);
        float rwY = lerp(rsoY, rhoY, WAIST_T);

        path.reset();
        path.moveTo(lsoX, lsoY);
        path.lineTo(rsoX, rsoY);
        path.lineTo(lerp(rwX, lwX, WAIST_PULL), lerp(rwY, lwY, WAIST_PULL));
        path.lineTo(rhoX, rhoY);
        path.lineTo(lhoX, lhoY);
        path.lineTo(lerp(lwX, rwX, WAIST_PULL), lerp(lwY, rwY, WAIST_PULL));
        path.close();
        // A round-joined stroke rounds the corners and pads the outline by the corner radius.
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(2f * TORSO_CORNER * scale);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        float neckX = (lsx + rsx) / 2f;
        float neckY = (lsy + rsy) / 2f;
        capsule(canvas, neckX, neckY, headCenterX(), headCenterY(), 0.10f * scale,
                0.085f * scale);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void drawHead(Canvas canvas) {
        float cx = headCenterX();
        float cy = headCenterY();
        oval.set(cx - HEAD_RADIUS_X * scale, cy - HEAD_RADIUS_Y * scale,
                cx + HEAD_RADIUS_X * scale, cy + HEAD_RADIUS_Y * scale);
        canvas.drawOval(oval, paint);
    }

    private boolean earsVisible() {
        return visible(Landmark.LEFT_EAR) && visible(Landmark.RIGHT_EAR);
    }

    private float headCenterX() {
        return earsVisible() ? (px(Landmark.LEFT_EAR) + px(Landmark.RIGHT_EAR)) / 2f
                : px(Landmark.NOSE);
    }

    private float headCenterY() {
        return earsVisible() ? (py(Landmark.LEFT_EAR) + py(Landmark.RIGHT_EAR)) / 2f
                : py(Landmark.NOSE);
    }

    private void capsule(Canvas canvas, float x1, float y1, float x2, float y2, float r1,
                         float r2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = Math.max(1e-3f, (float) Math.hypot(dx, dy));
        float nx = -dy / len;
        float ny = dx / len;
        path.reset();
        path.moveTo(x1 + nx * r1, y1 + ny * r1);
        path.lineTo(x2 + nx * r2, y2 + ny * r2);
        path.lineTo(x2 - nx * r2, y2 - ny * r2);
        path.lineTo(x1 - nx * r1, y1 - ny * r1);
        path.close();
        // Separate draws: mixing path directions in one path can punch winding-rule holes.
        canvas.drawPath(path, paint);
        canvas.drawCircle(x1, y1, r1, paint);
        canvas.drawCircle(x2, y2, r2, paint);
    }
}
