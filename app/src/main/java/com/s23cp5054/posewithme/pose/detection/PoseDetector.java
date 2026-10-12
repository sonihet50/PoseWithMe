package com.s23cp5054.posewithme.pose.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.s23cp5054.posewithme.camera.FrameConverter;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs MediaPipe Pose Landmarker Lite in live-stream mode on camera frames.
 *
 * Only one frame is in flight at a time: frames that arrive while the model is busy are dropped
 * instead of queued, so results never fall further and further behind the camera.
 */
public final class PoseDetector implements Closeable {

    public interface Listener {
        /** Called on a MediaPipe worker thread. */
        void onPose(PoseFrame frame);

        /** Called on a MediaPipe worker thread or the analysis thread. */
        void onDetectorError(String message);
    }

    public static final String MODEL_ASSET = "pose_landmarker_lite.task";

    /** Give up waiting for a result after this long, so a lost result can't stall detection. */
    private static final long STALE_RESULT_MS = 1000;

    private final PoseLandmarker landmarker;
    private final Listener listener;
    private final FrameConverter frameConverter = new FrameConverter();
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final AtomicInteger droppedFrames = new AtomicInteger();

    // Describe the single in-flight frame; written on the analysis thread before detectAsync.
    private volatile long pendingFrameStartMs;
    private volatile long pendingSubmitMs;
    private volatile int pendingWidth;
    private volatile int pendingHeight;
    private volatile boolean pendingMirrored;

    private long lastTimestampMs;

    /** Loads the model; takes a few hundred milliseconds, so call it off the main thread. */
    public static PoseDetector create(Context context, Listener listener) {
        return new PoseDetector(context.getApplicationContext(), listener);
    }

    private PoseDetector(Context context, Listener listener) {
        this.listener = listener;
        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .setDelegate(Delegate.CPU)
                .build();
        PoseLandmarker.PoseLandmarkerOptions options =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumPoses(1)
                        .setMinPoseDetectionConfidence(0.5f)
                        .setMinPosePresenceConfidence(0.5f)
                        .setMinTrackingConfidence(0.5f)
                        .setOutputSegmentationMasks(false)
                        .setResultListener(this::onResult)
                        .setErrorListener(e -> {
                            inFlight.set(false);
                            listener.onDetectorError(e.getMessage());
                        })
                        .build();
        landmarker = PoseLandmarker.createFromOptions(context, options);
    }

    /**
     * Submits a camera frame for detection, or drops it if the previous frame is still being
     * processed. Always closes {@code image}. Call from the single analysis thread.
     */
    public void process(ImageProxy image, boolean mirrored) {
        long frameStartMs = SystemClock.uptimeMillis();
        if (inFlight.get() && frameStartMs - pendingSubmitMs < STALE_RESULT_MS) {
            droppedFrames.incrementAndGet();
            image.close();
            return;
        }

        Bitmap upright;
        try {
            upright = frameConverter.toUprightBitmap(image);
        } finally {
            image.close();
        }

        long timestampMs = Math.max(frameStartMs, lastTimestampMs + 1);
        lastTimestampMs = timestampMs;
        pendingFrameStartMs = frameStartMs;
        pendingWidth = upright.getWidth();
        pendingHeight = upright.getHeight();
        pendingMirrored = mirrored;
        pendingSubmitMs = SystemClock.uptimeMillis();
        inFlight.set(true);
        try {
            // MediaPipe copies the pixels during this call, so the reused bitmap is safe to overwrite.
            landmarker.detectAsync(new BitmapImageBuilder(upright).build(), timestampMs);
        } catch (RuntimeException e) {
            inFlight.set(false);
            listener.onDetectorError(e.getMessage());
        }
    }

    private void onResult(PoseLandmarkerResult result, MPImage input) {
        long now = SystemClock.uptimeMillis();
        long submitMs = pendingSubmitMs;
        PoseFrame frame = new PoseFrame(result, pendingWidth, pendingHeight, pendingMirrored,
                submitMs - pendingFrameStartMs, now - submitMs);
        inFlight.set(false);
        listener.onPose(frame);
    }

    /** Returns how many frames were dropped since the last call. */
    public int takeDroppedFrames() {
        return droppedFrames.getAndSet(0);
    }

    @Override
    public void close() {
        landmarker.close();
    }
}
