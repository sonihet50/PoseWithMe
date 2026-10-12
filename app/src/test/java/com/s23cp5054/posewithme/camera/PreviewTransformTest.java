package com.s23cp5054.posewithme.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PreviewTransformTest {

    private static final float EPS = 0.01f;

    @Test
    public void sameAspectRatio_scalesWithoutOffset() {
        PreviewTransform t = new PreviewTransform();
        t.update(480, 640, 960, 1280, false);

        assertEquals(0f, t.mapX(0f), EPS);
        assertEquals(960f, t.mapX(1f), EPS);
        assertEquals(640f, t.mapY(0.5f), EPS);
    }

    @Test
    public void tallerView_cropsSidesAndKeepsCentre() {
        // 3:4 frame on a ~9:20 phone screen: scaled to cover the height, sides cropped.
        PreviewTransform t = new PreviewTransform();
        t.update(480, 640, 1080, 2400, false);

        float scale = 2400f / 640f;
        float offsetX = (1080f - 480f * scale) / 2f;
        assertEquals(540f, t.mapX(0.5f), EPS);
        assertEquals(1200f, t.mapY(0.5f), EPS);
        assertEquals(offsetX, t.mapX(0f), EPS);
        assertEquals(0f, t.mapY(0f), EPS);
        assertEquals(2400f, t.mapY(1f), EPS);
    }

    @Test
    public void mirrored_flipsHorizontallyOnly() {
        PreviewTransform plain = new PreviewTransform();
        PreviewTransform mirror = new PreviewTransform();
        plain.update(480, 640, 1080, 2400, false);
        mirror.update(480, 640, 1080, 2400, true);

        assertEquals(plain.mapX(0.75f), mirror.mapX(0.25f), EPS);
        assertEquals(1080f - plain.mapX(0.25f), mirror.mapX(0.25f), EPS);
        assertEquals(plain.mapY(0.3f), mirror.mapY(0.3f), EPS);
    }

    @Test
    public void widerView_cropsTopAndBottom() {
        PreviewTransform t = new PreviewTransform();
        t.update(480, 640, 1000, 1000, false);

        float scale = 1000f / 480f;
        assertEquals(0f, t.mapX(0f), EPS);
        assertEquals((1000f - 640f * scale) / 2f, t.mapY(0f), EPS);
        assertEquals(500f, t.mapY(0.5f), EPS);
    }
}
