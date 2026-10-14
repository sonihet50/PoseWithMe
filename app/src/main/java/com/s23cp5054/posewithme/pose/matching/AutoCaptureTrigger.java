package com.s23cp5054.posewithme.pose.matching;

/**
 * Decides when to take a photo automatically: the match score must stay at or above the
 * threshold for {@link #HOLD_MS}. After firing it stays disarmed until the person clearly
 * breaks the pose (score drops by {@link #REARM_MARGIN}) or leaves the frame, so holding a
 * pose produces one photo, not a burst. Not thread-safe; use from one thread.
 */
public final class AutoCaptureTrigger {

    public static final long HOLD_MS = 1000;
    public static final float REARM_MARGIN = 0.10f;

    private float threshold;
    private boolean armed = true;
    private long aboveSinceMs = -1;

    public AutoCaptureTrigger(float threshold) {
        this.threshold = threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    /** Re-arms after a manual photo or a change of target pose. */
    public void reset() {
        armed = true;
        aboveSinceMs = -1;
    }

    /** Prevents firing until the pose is broken, e.g. right after any photo was taken. */
    public void disarm() {
        armed = false;
        aboveSinceMs = -1;
    }

    public boolean isArmed() {
        return armed;
    }

    /** 0..1 progress of the current hold, for a "hold it" indicator. */
    public float holdProgress(long nowMs) {
        if (!armed || aboveSinceMs < 0) {
            return 0f;
        }
        return Math.min(1f, (nowMs - aboveSinceMs) / (float) HOLD_MS);
    }

    /** Called with the smoothed score; returns true exactly when a photo should be taken. */
    public boolean onScore(float score, boolean personInFrame, long nowMs) {
        if (!personInFrame) {
            armed = true;
            aboveSinceMs = -1;
            return false;
        }
        if (!armed) {
            if (score < threshold - REARM_MARGIN) {
                armed = true;
            }
            return false;
        }
        if (score < threshold) {
            aboveSinceMs = -1;
            return false;
        }
        if (aboveSinceMs < 0) {
            aboveSinceMs = nowMs;
        }
        if (nowMs - aboveSinceMs >= HOLD_MS) {
            disarm();
            return true;
        }
        return false;
    }
}
