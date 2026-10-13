package com.s23cp5054.posewithme.pose;

import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.Landmark;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;

import java.util.Collections;
import java.util.List;

/** Hand-built body-frame poses for tests. The person's left side is at +x (unmirrored image). */
public final class TestPoses {

    /** Upright, facing the camera, arms hanging slightly away from the body. */
    public static BodyPose standing() {
        float[] x = new float[Landmark.COUNT];
        float[] y = new float[Landmark.COUNT];
        float[] z = new float[Landmark.COUNT];
        float[] v = new float[Landmark.COUNT];
        java.util.Arrays.fill(v, 1f);
        set(x, y, Landmark.NOSE, 0f, -1.45f);
        for (int i = 1; i <= 10; i++) {
            set(x, y, i, (i % 2 == 1 ? 0.05f : -0.05f), -1.5f);
        }
        set(x, y, Landmark.LEFT_EAR, 0.12f, -1.45f);
        set(x, y, Landmark.RIGHT_EAR, -0.12f, -1.45f);
        set(x, y, Landmark.LEFT_SHOULDER, 0.35f, -1f);
        set(x, y, Landmark.RIGHT_SHOULDER, -0.35f, -1f);
        set(x, y, Landmark.LEFT_ELBOW, 0.42f, -0.5f);
        set(x, y, Landmark.RIGHT_ELBOW, -0.42f, -0.5f);
        set(x, y, Landmark.LEFT_WRIST, 0.45f, 0f);
        set(x, y, Landmark.RIGHT_WRIST, -0.45f, 0f);
        for (int i = 17; i <= 22; i++) {
            set(x, y, i, i % 2 == 1 ? 0.46f : -0.46f, 0.12f);
        }
        set(x, y, Landmark.LEFT_HIP, 0.15f, 0f);
        set(x, y, Landmark.RIGHT_HIP, -0.15f, 0f);
        set(x, y, Landmark.LEFT_KNEE, 0.17f, 0.9f);
        set(x, y, Landmark.RIGHT_KNEE, -0.17f, 0.9f);
        set(x, y, Landmark.LEFT_ANKLE, 0.18f, 1.8f);
        set(x, y, Landmark.RIGHT_ANKLE, -0.18f, 1.8f);
        set(x, y, Landmark.LEFT_HEEL, 0.18f, 1.88f);
        set(x, y, Landmark.RIGHT_HEEL, -0.18f, 1.88f);
        set(x, y, Landmark.LEFT_FOOT_INDEX, 0.2f, 1.95f);
        set(x, y, Landmark.RIGHT_FOOT_INDEX, -0.2f, 1.95f);
        return new BodyPose(x, y, z, v);
    }

    /** Standing, with the person's left arm held straight out to their side. */
    public static BodyPose leftArmOut() {
        BodyPose p = standing();
        set(p.x, p.y, Landmark.LEFT_ELBOW, 0.85f, -1f);
        set(p.x, p.y, Landmark.LEFT_WRIST, 1.35f, -1f);
        return p;
    }

    /** Standing, with the left forearm folded up so the hand is near the shoulder. */
    public static BodyPose leftElbowBent() {
        BodyPose p = standing();
        set(p.x, p.y, Landmark.LEFT_WRIST, 0.4f, -0.95f);
        return p;
    }

    /** Whole upper body rotated about the hips; positive degrees lean towards the image's right. */
    public static BodyPose leaning(float degrees) {
        BodyPose p = standing();
        double t = Math.toRadians(degrees);
        float cos = (float) Math.cos(t);
        float sin = (float) Math.sin(t);
        for (int i = 0; i < Landmark.COUNT; i++) {
            if (i >= Landmark.LEFT_HIP) {
                continue;
            }
            float x = p.x[i];
            float y = p.y[i];
            p.x[i] = x * cos - y * sin;
            p.y[i] = x * sin + y * cos;
        }
        return p;
    }

    public static PoseTemplate template(BodyPose pose, PoseTemplate.Framing framing,
                                        boolean mirrorAllowed, List<String> ignoredParts) {
        return new PoseTemplate("test", "Test", "", "casual_standing", "easy", framing,
                mirrorAllowed, Collections.emptyList(), Collections.emptyList(), ignoredParts,
                new PoseTemplate.Source("recorded", null, null, null, null, null), pose);
    }

    public static PoseTemplate template(BodyPose pose) {
        return template(pose, PoseTemplate.Framing.FULL_BODY, false, Collections.emptyList());
    }

    private static void set(float[] x, float[] y, int i, float px, float py) {
        x[i] = px;
        y[i] = py;
    }

    private TestPoses() {
    }
}
