package com.s23cp5054.posewithme.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ResolutionInfo;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.pose.detection.PoseDetector;
import com.s23cp5054.posewithme.pose.detection.PoseFrame;
import com.s23cp5054.posewithme.utils.PerfStats;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phase 1 proof of concept: live CameraX preview with on-device pose landmarks drawn on top,
 * plus a live readout of detection rate, latency and memory.
 */
public class CameraActivity extends AppCompatActivity implements PoseDetector.Listener {

    private static final String TAG = "PoseWithMe";
    private static final String PERF_TAG = "PoseWithMe.Perf";

    /** Analysis frames don't need to be large: the model itself runs at 256x256. */
    private static final Size ANALYSIS_TARGET_SIZE = new Size(640, 480);
    private static final long STATS_INTERVAL_MS = 1000;

    private PreviewView previewView;
    private PoseOverlayView poseOverlay;
    private TextView statsText;
    private TextView statusText;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PerfStats perfStats = new PerfStats();
    private ExecutorService analysisExecutor;

    @Nullable
    private ProcessCameraProvider cameraProvider;
    private volatile PoseDetector poseDetector;
    private volatile boolean frontCamera;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private String resolutionSummary = "";
    private boolean showingNoPerson;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showStatus(getString(R.string.camera_permission_denied));
                }
            });

    private final Runnable statsTicker = new Runnable() {
        @Override
        public void run() {
            updateStats();
            mainHandler.postDelayed(this, STATS_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        poseOverlay = findViewById(R.id.poseOverlay);
        statsText = findViewById(R.id.statsText);
        statusText = findViewById(R.id.statusText);

        hideStatusBar();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cameraRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            findViewById(R.id.topBar).setPadding(bars.left, bars.top, bars.right, 0);
            findViewById(R.id.bottomBar).setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        View flipButton = findViewById(R.id.flipButton);
        flipButton.setOnClickListener(v -> {
            v.animate().rotationBy(180f).setDuration(250).start();
            flipCamera();
        });
        findViewById(R.id.closeButton).setOnClickListener(v -> finish());
        findViewById(R.id.gridButton).setOnClickListener(v -> toggleVisibility(
                findViewById(R.id.gridOverlay), (ImageButton) v));
        findViewById(R.id.statsButton).setOnClickListener(v -> toggleVisibility(
                statsText, (ImageButton) v));
        findViewById(R.id.shutterButton).setOnClickListener(v -> Snackbar.make(
                findViewById(R.id.bottomBar), R.string.camera_capture_soon, Snackbar.LENGTH_SHORT)
                .show());
        findViewById(R.id.galleryButton).setOnClickListener(v -> Snackbar.make(
                findViewById(R.id.bottomBar),
                getString(R.string.coming_soon, getString(R.string.home_photos_title)),
                Snackbar.LENGTH_SHORT).show());

        analysisExecutor = Executors.newSingleThreadExecutor();
        analysisExecutor.execute(this::loadDetector);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Some OEM skins re-show the status bar once the window is attached or refocused.
        if (hasFocus) {
            hideStatusBar();
        }
    }

    private void hideStatusBar() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.statusBars());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.post(statsTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(statsTicker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        // Close on the analysis thread so it can't race with a frame being processed.
        analysisExecutor.execute(() -> {
            PoseDetector detector = poseDetector;
            poseDetector = null;
            if (detector != null) {
                detector.close();
            }
        });
        analysisExecutor.shutdown();
    }

    /** Runs on the analysis thread. */
    private void loadDetector() {
        long start = SystemClock.uptimeMillis();
        try {
            poseDetector = PoseDetector.create(this, this);
            long loadMs = SystemClock.uptimeMillis() - start;
            Log.i(PERF_TAG, "Pose model loaded in " + loadMs + " ms");
            mainHandler.post(() -> {
                showingNoPerson = true;
                showStatus(getString(R.string.camera_no_person));
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to load pose model", e);
            mainHandler.post(() -> showStatus(getString(R.string.camera_model_failed, e.getMessage())));
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera provider unavailable", e);
                showStatus(getString(R.string.camera_bind_failed, e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void flipCamera() {
        lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        bindCameraUseCases();
    }

    private void bindCameraUseCases() {
        ProcessCameraProvider provider = cameraProvider;
        if (provider == null || isDestroyed()) {
            return;
        }
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        try {
            if (!provider.hasCamera(selector)) {
                lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                        ? CameraSelector.LENS_FACING_BACK
                        : CameraSelector.LENS_FACING_FRONT;
                selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
            }
        } catch (CameraInfoUnavailableException e) {
            Log.w(TAG, "Could not query cameras", e);
        }

        // Preview and analysis must share an aspect ratio for the overlay mapping to hold.
        Preview preview = new Preview.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build())
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(new ResolutionStrategy(ANALYSIS_TARGET_SIZE,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

        frontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT;
        poseOverlay.setPoseFrame(null);
        try {
            provider.unbindAll();
            provider.bindToLifecycle(this, selector, preview, analysis);
        } catch (RuntimeException e) {
            Log.e(TAG, "Use case binding failed", e);
            showStatus(getString(R.string.camera_bind_failed, e.getMessage()));
            return;
        }

        resolutionSummary = String.format(Locale.US, "%s  preview %s  analysis %s",
                frontCamera ? "front" : "back",
                describe(preview.getResolutionInfo()), describe(analysis.getResolutionInfo()));
        Log.i(PERF_TAG, "Bound camera: " + resolutionSummary);
    }

    private static String describe(@Nullable ResolutionInfo info) {
        if (info == null) {
            return "?";
        }
        Size size = info.getResolution();
        return size.getWidth() + "x" + size.getHeight() + "@" + info.getRotationDegrees();
    }

    /** Runs on the analysis thread. */
    private void analyzeFrame(ImageProxy image) {
        PoseDetector detector = poseDetector;
        if (detector == null) {
            image.close();
            return;
        }
        detector.process(image, frontCamera);
    }

    @Override
    public void onPose(PoseFrame frame) {
        perfStats.record(frame.prepMs, frame.inferenceMs);
        mainHandler.post(() -> {
            poseOverlay.setPoseFrame(frame);
            if (frame.hasPose == showingNoPerson) {
                showingNoPerson = !frame.hasPose;
                if (showingNoPerson) {
                    showStatus(getString(R.string.camera_no_person));
                } else {
                    statusText.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onDetectorError(String message) {
        Log.e(TAG, "Pose detector error: " + message);
        mainHandler.post(() -> showStatus(getString(R.string.camera_detector_error, message)));
    }

    private void toggleVisibility(View target, ImageButton toggle) {
        boolean show = target.getVisibility() != View.VISIBLE;
        target.setVisibility(show ? View.VISIBLE : View.GONE);
        toggle.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                show ? R.color.brand_accent : R.color.white)));
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private void updateStats() {
        PoseDetector detector = poseDetector;
        int dropped = detector != null ? detector.takeDroppedFrames() : 0;
        Runtime runtime = Runtime.getRuntime();
        long javaHeapMb = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
        long nativeHeapMb = Debug.getNativeHeapAllocatedSize() >> 20;

        String text = perfStats.reportAndReset(SystemClock.uptimeMillis(), dropped)
                + "\n" + resolutionSummary
                + String.format(Locale.US, "\njava heap %d MB  native heap %d MB",
                javaHeapMb, nativeHeapMb);
        statsText.setText(text);
        Log.i(PERF_TAG, text.replace('\n', '|'));
    }
}
