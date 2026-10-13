package com.s23cp5054.posewithme.pose.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.s23cp5054.posewithme.pose.TestPoses;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PoseModelTest {

    @Test
    public void mirrorOf_swapsSidesAndKeepsNose() {
        assertEquals(Landmark.NOSE, Landmark.mirrorOf(Landmark.NOSE));
        assertEquals(Landmark.RIGHT_EYE, Landmark.mirrorOf(Landmark.LEFT_EYE));
        assertEquals(Landmark.LEFT_EAR, Landmark.mirrorOf(Landmark.RIGHT_EAR));
        assertEquals(Landmark.RIGHT_WRIST, Landmark.mirrorOf(Landmark.LEFT_WRIST));
        assertEquals(Landmark.LEFT_FOOT_INDEX, Landmark.mirrorOf(Landmark.RIGHT_FOOT_INDEX));
        for (int i = 0; i < Landmark.COUNT; i++) {
            assertEquals(i, Landmark.mirrorOf(Landmark.mirrorOf(i)));
        }
    }

    @Test
    public void fromImageLandmarks_centresOnHipsAndScalesByTorso() {
        float[] nx = new float[Landmark.COUNT];
        float[] ny = new float[Landmark.COUNT];
        float[] nz = new float[Landmark.COUNT];
        float[] vis = new float[Landmark.COUNT];
        Arrays.fill(vis, 1f);
        // 400x800 image: hips at pixel y=600, shoulders at y=400 -> torso 200 px.
        nx[Landmark.LEFT_HIP] = 0.6f;
        nx[Landmark.RIGHT_HIP] = 0.4f;
        ny[Landmark.LEFT_HIP] = 0.75f;
        ny[Landmark.RIGHT_HIP] = 0.75f;
        nx[Landmark.LEFT_SHOULDER] = 0.7f;
        nx[Landmark.RIGHT_SHOULDER] = 0.3f;
        ny[Landmark.LEFT_SHOULDER] = 0.5f;
        ny[Landmark.RIGHT_SHOULDER] = 0.5f;
        nx[Landmark.NOSE] = 0.5f;
        ny[Landmark.NOSE] = 0.375f;

        BodyPose p = BodyPose.fromImageLandmarks(nx, ny, nz, vis, 400, 800);

        assertEquals(0f, (p.x[Landmark.LEFT_HIP] + p.x[Landmark.RIGHT_HIP]) / 2f, 1e-5f);
        assertEquals(-1f, p.y[Landmark.LEFT_SHOULDER], 1e-5f);
        // 0.2 * 400 px = 80 px from centre = 0.4 torso lengths: x is scaled by width, not height.
        assertEquals(0.4f, p.x[Landmark.LEFT_SHOULDER], 1e-5f);
        assertEquals(-1.5f, p.y[Landmark.NOSE], 1e-5f);
    }

    @Test
    public void fromImageLandmarks_needsVisibleShouldersAndHips() {
        float[] zeros = new float[Landmark.COUNT];
        float[] vis = new float[Landmark.COUNT];
        Arrays.fill(vis, 1f);
        vis[Landmark.LEFT_HIP] = 0.2f;
        assertNull(BodyPose.fromImageLandmarks(zeros, zeros, zeros, vis, 100, 100));
    }

    @Test
    public void json_roundTripsTemplate() throws Exception {
        PoseTemplate original = new PoseTemplate("hand-on-hip", "Hand on hip", "Relaxed.",
                "casual_standing", "easy", PoseTemplate.Framing.FULL_BODY, true,
                Arrays.asList("Put a hand on your hip", "Shift your weight"),
                Collections.singletonList("park"), Collections.singletonList("left_arm"),
                new PoseTemplate.Source("web", "A photo", "Someone", "https://example.org/p",
                        "CC BY 2.0", "https://creativecommons.org/licenses/by/2.0/"),
                TestPoses.leftArmOut());

        PoseTemplate copy = PoseTemplate.fromJson(original.toJson());

        assertEquals(original.id, copy.id);
        assertEquals(original.name, copy.name);
        assertEquals(original.framing, copy.framing);
        assertEquals(original.instructions, copy.instructions);
        assertEquals(original.ignoredParts, copy.ignoredParts);
        assertEquals("Someone", copy.source.author);
        for (int i = 0; i < Landmark.COUNT; i++) {
            assertEquals(original.pose.x[i], copy.pose.x[i], 1e-4f);
            assertEquals(original.pose.y[i], copy.pose.y[i], 1e-4f);
        }
    }
}
