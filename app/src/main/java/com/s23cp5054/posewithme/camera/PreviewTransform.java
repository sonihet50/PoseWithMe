package com.s23cp5054.posewithme.camera;

/**
 * Maps normalized landmark coordinates of an upright analysis frame onto a view that displays
 * the same camera stream with PreviewView's FILL_CENTER scaling (scale to cover, centre crop).
 *
 * This only lines up when the preview stream and the analysis stream share an aspect ratio and
 * field of view, which is why CameraActivity requests 4:3 for both.
 */
public final class PreviewTransform {

    private int imageWidth = 1;
    private int imageHeight = 1;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private boolean mirrored;

    public void update(int imageWidth, int imageHeight, int viewWidth, int viewHeight,
                       boolean mirrored) {
        this.imageWidth = Math.max(1, imageWidth);
        this.imageHeight = Math.max(1, imageHeight);
        this.mirrored = mirrored;
        scale = Math.max((float) viewWidth / this.imageWidth, (float) viewHeight / this.imageHeight);
        offsetX = (viewWidth - this.imageWidth * scale) / 2f;
        offsetY = (viewHeight - this.imageHeight * scale) / 2f;
    }

    public float mapX(float normalizedX) {
        float x = mirrored ? 1f - normalizedX : normalizedX;
        return x * imageWidth * scale + offsetX;
    }

    public float mapY(float normalizedY) {
        return normalizedY * imageHeight * scale + offsetY;
    }
}
