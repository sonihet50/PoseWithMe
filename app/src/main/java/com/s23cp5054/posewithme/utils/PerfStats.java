package com.s23cp5054.posewithme.utils;

import java.util.Locale;

/**
 * Aggregates pose-detection timings over a reporting window. Recorded on the MediaPipe thread,
 * reported on the main thread.
 */
public final class PerfStats {

    private int results;
    private long prepSumMs;
    private long inferenceSumMs;
    private long totalMaxMs;
    private long windowStartMs = -1;

    public synchronized void record(long prepMs, long inferenceMs) {
        results++;
        prepSumMs += prepMs;
        inferenceSumMs += inferenceMs;
        totalMaxMs = Math.max(totalMaxMs, prepMs + inferenceMs);
    }

    /** Summarises the window since the previous call and starts a new one. */
    public synchronized String reportAndReset(long nowMs, int droppedFrames) {
        String report;
        if (windowStartMs < 0 || nowMs <= windowStartMs) {
            report = "measuring…";
        } else {
            float seconds = (nowMs - windowStartMs) / 1000f;
            float n = Math.max(1, results);
            report = String.format(Locale.US,
                    "pose %.1f/s  drop %.1f/s\nprep %.1f ms  infer %.1f ms\nframe→result avg %.1f ms  max %d ms",
                    results / seconds, droppedFrames / seconds,
                    prepSumMs / n, inferenceSumMs / n,
                    (prepSumMs + inferenceSumMs) / n, totalMaxMs);
        }
        results = 0;
        prepSumMs = 0;
        inferenceSumMs = 0;
        totalMaxMs = 0;
        windowStartMs = nowMs;
        return report;
    }
}
