package com.s23cp5054.posewithme.pose.recording;

import androidx.annotation.Nullable;

import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.Landmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures one clean pose from a live stream: after a countdown it waits until the person has
 * held still for a moment, then averages those frames to smooth out detection jitter.
 * Not thread-safe; feed it from one thread.
 */
public final class PoseRecorder {

    public enum State { IDLE, COUNTDOWN, HOLD_STILL, DONE, TIMED_OUT }

    public static final long COUNTDOWN_MS = 3000;
    /** How long the person must stay still. */
    public static final long STILL_MS = 1000;
    /** Give up if no still moment is found within this time after the countdown. */
    public static final long TIMEOUT_MS = 10000;
    /** Mean frame-to-frame joint movement (torso units) still counted as holding still. */
    static final float STILL_THRESHOLD = 0.04f;
    private static final int MIN_FRAMES = 6;
    private static final float MIN_JOINT_VISIBILITY = 0.5f;
    private static final int[] KEY_JOINTS = {
            Landmark.NOSE, Landmark.LEFT_SHOULDER, Landmark.RIGHT_SHOULDER,
            Landmark.LEFT_ELBOW, Landmark.RIGHT_ELBOW, Landmark.LEFT_WRIST, Landmark.RIGHT_WRIST,
            Landmark.LEFT_HIP, Landmark.RIGHT_HIP, Landmark.LEFT_KNEE, Landmark.RIGHT_KNEE,
            Landmark.LEFT_ANKLE, Landmark.RIGHT_ANKLE,
    };

    private State state = State.IDLE;
    private long startMs;
    private final List<BodyPose> stillFrames = new ArrayList<>();
    private long stillSinceMs;
    @Nullable
    private BodyPose previous;
    @Nullable
    private BodyPose result;

    public void start(long nowMs) {
        state = State.COUNTDOWN;
        startMs = nowMs;
        stillFrames.clear();
        previous = null;
        result = null;
    }

    public void cancel() {
        state = State.IDLE;
        stillFrames.clear();
        previous = null;
    }

    public State getState() {
        return state;
    }

    /** Whole seconds left in the countdown, for display (3, 2, 1). */
    public int countdownSecondsLeft(long nowMs) {
        long left = COUNTDOWN_MS - (nowMs - startMs);
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    /** 0..1 progress towards a captured pose while holding still. */
    public float stillProgress(long nowMs) {
        if (state != State.HOLD_STILL || stillFrames.isEmpty()) {
            return state == State.DONE ? 1f : 0f;
        }
        return Math.min(1f, (nowMs - stillSinceMs) / (float) STILL_MS);
    }

    @Nullable
    public BodyPose getResult() {
        return result;
    }

    /** Feeds the latest detection (null if no usable person) and returns the new state. */
    public State onPose(@Nullable BodyPose pose, long nowMs) {
        if (state == State.COUNTDOWN && nowMs - startMs >= COUNTDOWN_MS) {
            state = State.HOLD_STILL;
        }
        if (state != State.HOLD_STILL) {
            return state;
        }
        if (nowMs - startMs - COUNTDOWN_MS > TIMEOUT_MS) {
            state = State.TIMED_OUT;
            return state;
        }
        if (pose == null) {
            stillFrames.clear();
            previous = null;
            return state;
        }
        if (previous == null || movement(previous, pose) > STILL_THRESHOLD) {
            stillFrames.clear();
            stillSinceMs = nowMs;
        }
        stillFrames.add(pose);
        previous = pose;
        if (stillFrames.size() >= MIN_FRAMES && nowMs - stillSinceMs >= STILL_MS) {
            result = BodyPose.average(stillFrames);
            state = State.DONE;
        }
        return state;
    }

    /** Mean 2D displacement of key joints visible in both poses. */
    static float movement(BodyPose a, BodyPose b) {
        float sum = 0f;
        int n = 0;
        for (int i : KEY_JOINTS) {
            if (a.visibility[i] >= MIN_JOINT_VISIBILITY && b.visibility[i] >= MIN_JOINT_VISIBILITY) {
                sum += (float) Math.hypot(a.x[i] - b.x[i], a.y[i] - b.y[i]);
                n++;
            }
        }
        return n == 0 ? Float.MAX_VALUE : sum / n;
    }
}
