package com.s23cp5054.posewithme.pose.model;

/**
 * A pose expressed in a body-relative frame, so poses from different people, image sizes and
 * camera distances can be compared and drawn the same way.
 *
 * <p>Frame definition (shared with tools/pose_pipeline/posekit.py, keep them identical):
 * <ul>
 *   <li>Start from MediaPipe image landmarks and undo the per-axis normalization: x * width,
 *       y * height, z * width (MediaPipe scales z like x).</li>
 *   <li>Origin is the midpoint of the two hips.</li>
 *   <li>Unit length is the 2D distance from the hip midpoint to the shoulder midpoint.</li>
 *   <li>Axes follow the image: +x to the image's right, +y downwards, +z away from the camera.
 *       The image is never mirrored, so a person's left side appears on the image's right.</li>
 * </ul>
 */
public final class BodyPose {

    /** Shoulders and hips must be at least this visible to define the body frame. */
    public static final float MIN_FRAME_VISIBILITY = 0.5f;

    public final float[] x;
    public final float[] y;
    public final float[] z;
    public final float[] visibility;

    public BodyPose(float[] x, float[] y, float[] z, float[] visibility) {
        if (x.length != Landmark.COUNT || y.length != Landmark.COUNT
                || z.length != Landmark.COUNT || visibility.length != Landmark.COUNT) {
            throw new IllegalArgumentException("Expected " + Landmark.COUNT + " landmarks");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.visibility = visibility;
    }

    /**
     * Converts MediaPipe's image-normalized landmarks into the body frame, or returns null when
     * the shoulders and hips are not visible enough to define it.
     */
    public static BodyPose fromImageLandmarks(float[] nx, float[] ny, float[] nz, float[] vis,
                                              int imageWidth, int imageHeight) {
        int ls = Landmark.LEFT_SHOULDER;
        int rs = Landmark.RIGHT_SHOULDER;
        int lh = Landmark.LEFT_HIP;
        int rh = Landmark.RIGHT_HIP;
        if (vis[ls] < MIN_FRAME_VISIBILITY || vis[rs] < MIN_FRAME_VISIBILITY
                || vis[lh] < MIN_FRAME_VISIBILITY || vis[rh] < MIN_FRAME_VISIBILITY) {
            return null;
        }
        float hipX = (nx[lh] + nx[rh]) / 2f * imageWidth;
        float hipY = (ny[lh] + ny[rh]) / 2f * imageHeight;
        float hipZ = (nz[lh] + nz[rh]) / 2f * imageWidth;
        float shoulderX = (nx[ls] + nx[rs]) / 2f * imageWidth;
        float shoulderY = (ny[ls] + ny[rs]) / 2f * imageHeight;
        float torso = (float) Math.hypot(shoulderX - hipX, shoulderY - hipY);
        if (torso < 1e-3f) {
            return null;
        }

        float[] x = new float[Landmark.COUNT];
        float[] y = new float[Landmark.COUNT];
        float[] z = new float[Landmark.COUNT];
        float[] v = new float[Landmark.COUNT];
        for (int i = 0; i < Landmark.COUNT; i++) {
            x[i] = (nx[i] * imageWidth - hipX) / torso;
            y[i] = (ny[i] * imageHeight - hipY) / torso;
            z[i] = (nz[i] * imageWidth - hipZ) / torso;
            v[i] = vis[i];
        }
        return new BodyPose(x, y, z, v);
    }

    /** The same pose performed with the other side of the body. */
    public BodyPose mirrored() {
        float[] mx = new float[Landmark.COUNT];
        float[] my = new float[Landmark.COUNT];
        float[] mz = new float[Landmark.COUNT];
        float[] mv = new float[Landmark.COUNT];
        for (int i = 0; i < Landmark.COUNT; i++) {
            int j = Landmark.mirrorOf(i);
            mx[i] = -x[j];
            my[i] = y[j];
            mz[i] = z[j];
            mv[i] = visibility[j];
        }
        return new BodyPose(mx, my, mz, mv);
    }

    /** Element-wise average, used to smooth a burst of frames into one recorded pose. */
    public static BodyPose average(java.util.List<BodyPose> poses) {
        if (poses.isEmpty()) {
            throw new IllegalArgumentException("Nothing to average");
        }
        float[] x = new float[Landmark.COUNT];
        float[] y = new float[Landmark.COUNT];
        float[] z = new float[Landmark.COUNT];
        float[] v = new float[Landmark.COUNT];
        for (BodyPose p : poses) {
            for (int i = 0; i < Landmark.COUNT; i++) {
                x[i] += p.x[i];
                y[i] += p.y[i];
                z[i] += p.z[i];
                v[i] += p.visibility[i];
            }
        }
        float n = poses.size();
        for (int i = 0; i < Landmark.COUNT; i++) {
            x[i] /= n;
            y[i] /= n;
            z[i] /= n;
            v[i] /= n;
        }
        return new BodyPose(x, y, z, v);
    }
}
