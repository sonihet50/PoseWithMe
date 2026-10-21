package com.s23cp5054.posewithme.pose.detection;

import androidx.annotation.Nullable;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.PoseGroup;
import com.s23cp5054.posewithme.pose.model.Landmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    /** Pixel coordinates in the upright image, ordered left-to-right. */
    public final List<BodyPose> imagePoses;
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

    public PoseFrame(PoseLandmarkerResult result, int imageWidth, int imageHeight, boolean mirrored,
              long prepMs, long inferenceMs) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.mirrored = mirrored;
        this.prepMs = prepMs;
        this.inferenceMs = inferenceMs;

        List<List<NormalizedLandmark>> poses = result.landmarks();
        List<BodyPose> people = new ArrayList<>();
        for (List<NormalizedLandmark> landmarks : poses) {
            if (landmarks.size() != LANDMARK_COUNT) continue;
            float[] px = new float[LANDMARK_COUNT];
            float[] py = new float[LANDMARK_COUNT];
            float[] pz = new float[LANDMARK_COUNT];
            float[] pv = new float[LANDMARK_COUNT];
            for (int i = 0; i < LANDMARK_COUNT; i++) {
                NormalizedLandmark landmark = landmarks.get(i);
                px[i] = landmark.x() * imageWidth;
                py[i] = landmark.y() * imageHeight;
                pz[i] = landmark.z() * imageWidth;
                pv[i] = landmark.visibility().orElse(1f);
            }
            BodyPose detected = new BodyPose(px, py, pz, pv);
            boolean duplicate = false;
            for (BodyPose person : people) duplicate |= PoseGroup.samePerson(person, detected);
            if (!duplicate) people.add(detected);
        }
        people.sort(Comparator.comparingDouble(PoseGroup::hipX));
        imagePoses = Collections.unmodifiableList(people);
        hasPose = !people.isEmpty();
        if (hasPose) {
            BodyPose first = people.get(0);
            for (int i = 0; i < LANDMARK_COUNT; i++) {
                x[i] = first.x[i] / imageWidth;
                y[i] = first.y[i] / imageHeight;
                z[i] = first.z[i] / imageWidth;
                visibility[i] = first.visibility[i];
            }
        }
    }

    /**
     * This detection in the body frame, or null if shoulders/hips weren't seen well enough.
     * With {@code waistUp}, hips just out of frame are accepted using MediaPipe's estimate.
     */
    @Nullable
    public BodyPose toBodyPose(boolean waistUp) {
        if (!hasPose) {
            return null;
        }
        return BodyPose.fromImageLandmarks(x, y, z, visibility, imageWidth, imageHeight,
                waistUp ? BodyPose.MIN_HIDDEN_HIP_VISIBILITY : BodyPose.MIN_FRAME_VISIBILITY);
    }

    /** Empty if any person lacks a usable body frame; never silently drops a group member. */
    public List<BodyPose> toBodyPoses(boolean waistUp) {
        if (imagePoses.isEmpty() || imagePoses.size() > PoseGroup.MAX_PEOPLE) return Collections.emptyList();
        for (BodyPose person : imagePoses) {
            float minHip = waistUp ? BodyPose.MIN_HIDDEN_HIP_VISIBILITY : BodyPose.MIN_FRAME_VISIBILITY;
            if (person.visibility[Landmark.LEFT_SHOULDER] < BodyPose.MIN_FRAME_VISIBILITY
                    || person.visibility[Landmark.RIGHT_SHOULDER] < BodyPose.MIN_FRAME_VISIBILITY
                    || person.visibility[Landmark.LEFT_HIP] < minHip
                    || person.visibility[Landmark.RIGHT_HIP] < minHip
                    || PoseGroup.torso(person) < 1f) return Collections.emptyList();
        }
        return PoseGroup.normalize(imagePoses);
    }
}
