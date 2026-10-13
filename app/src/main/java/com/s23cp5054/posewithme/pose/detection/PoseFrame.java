package com.s23cp5054.posewithme.pose.detection;

import androidx.annotation.Nullable;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.s23cp5054.posewithme.pose.model.BodyPose;

import java.util.List;

/**
 * One pose-detection result, copied out of MediaPipe's objects so it can be handed to the UI
 * thread safely. Coordinates are normalized to the upright analysis image (0..1, may fall
 * slightly outside when a body part is out of frame), un-mirrored, using MediaPipe's
 * convention that "left" means the person's own left.
 */
public final class PoseFrame {

    public static final int LANDMARK_COUNT = 33;

    public final boolean hasPose;
    public final float[] x = new float[LANDMARK_COUNT];
    public final float[] y = new float[LANDMARK_COUNT];
    /** MediaPipe's relative depth, scaled like x; smaller is closer to the camera. */
    public final float[] z = new float[LANDMARK_COUNT];
    public final float[] visibility = new float[LANDMARK_COUNT];
    public final int imageWidth;
    public final int imageHeight;
    /** True when the preview showing this frame is mirrored (front camera). */
    public final boolean mirrored;
    /** Time spent converting and rotating the camera frame before inference. */
    public final long prepMs;
    /** Time from handing the frame to MediaPipe until its result arrived. */
    public final long inferenceMs;

    PoseFrame(PoseLandmarkerResult result, int imageWidth, int imageHeight, boolean mirrored,
              long prepMs, long inferenceMs) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.mirrored = mirrored;
        this.prepMs = prepMs;
        this.inferenceMs = inferenceMs;

        List<List<NormalizedLandmark>> poses = result.landmarks();
        hasPose = !poses.isEmpty() && poses.get(0).size() >= LANDMARK_COUNT;
        if (hasPose) {
            List<NormalizedLandmark> landmarks = poses.get(0);
            for (int i = 0; i < LANDMARK_COUNT; i++) {
                NormalizedLandmark landmark = landmarks.get(i);
                x[i] = landmark.x();
                y[i] = landmark.y();
                z[i] = landmark.z();
                visibility[i] = landmark.visibility().orElse(1f);
            }
        }
    }

    /** This detection in the body frame, or null if shoulders/hips weren't seen well enough. */
    @Nullable
    public BodyPose toBodyPose() {
        return hasPose ? BodyPose.fromImageLandmarks(x, y, z, visibility, imageWidth, imageHeight)
                : null;
    }
}
