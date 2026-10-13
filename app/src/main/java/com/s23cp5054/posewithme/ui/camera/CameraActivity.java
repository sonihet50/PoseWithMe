package com.s23cp5054.posewithme.ui.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.repository.PoseRepository;
import com.s23cp5054.posewithme.pose.detection.PoseDetector;
import com.s23cp5054.posewithme.pose.detection.PoseFrame;
import com.s23cp5054.posewithme.pose.matching.BodyPart;
import com.s23cp5054.posewithme.pose.matching.GuidanceSmoother;
import com.s23cp5054.posewithme.pose.matching.MatchConfig;
import com.s23cp5054.posewithme.pose.matching.MatchResult;
import com.s23cp5054.posewithme.pose.matching.PoseMatcher;
import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.Landmark;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.pose.recording.PoseRecorder;
import com.s23cp5054.posewithme.ui.pose.PoseThumbnailView;
import com.s23cp5054.posewithme.ui.poses.PoseLabels;
import com.s23cp5054.posewithme.ui.poses.PoseLibraryActivity;
import com.s23cp5054.posewithme.utils.PerfStats;

import org.json.JSONException;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The live camera: CameraX preview with on-device pose detection drawn on top. Three modes:
 * free (just the skeleton), guide (a target pose ghost with live score and corrections) and
 * record (capture the user's own pose into the library).
 */
public class CameraActivity extends AppCompatActivity implements PoseDetector.Listener {

    /** String extra: id of the target pose to guide the user towards. */
    public static final String EXTRA_POSE_ID = "com.s23cp5054.posewithme.extra.POSE_ID";
    /** Boolean extra: open in pose-recording mode instead of guidance mode. */
    public static final String EXTRA_RECORD_MODE = "com.s23cp5054.posewithme.extra.RECORD_MODE";

    private static final String TAG = "PoseWithMe";
    private static final String PERF_TAG = "PoseWithMe.Perf";

    /** Analysis frames don't need to be large: the model itself runs at 256x256. */
    private static final Size ANALYSIS_TARGET_SIZE = new Size(640, 480);
    private static final long STATS_INTERVAL_MS = 1000;
    private static final long RECORD_TICK_MS = 100;
    /** Smoothed score at which the user is told to hold the pose. */
    private static final float HOLD_IT_SCORE = 0.85f;
    private static final String[] CATEGORY_KEYS = {
            "casual_standing", "hands_in_pockets", "crossed_arms", "walking", "looking_back",
            "sitting", "portrait", "beach", "monument", "scenic", "cafe",
    };

    private enum Mode { FREE, GUIDE, RECORD }

    private PreviewView previewView;
    private PoseOverlayView poseOverlay;
    private TextView statsText;
    private TextView statusText;
    private TextView matchChip;
    private TextView countdownText;
    private View guidancePanel;
    private TextView hintText;
    private LinearLayout partsRow;
    private TextView modeLabel;
    private ImageButton shutterButton;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PerfStats perfStats = new PerfStats();
    private final MatchConfig matchConfig = new MatchConfig();
    private final PoseMatcher matcher = new PoseMatcher(matchConfig);
    private final GuidanceSmoother smoother = new GuidanceSmoother(matchConfig);
    private final PoseRecorder recorder = new PoseRecorder();
    private final Map<BodyPart, TextView> partPills = new EnumMap<>(BodyPart.class);
    private ExecutorService analysisExecutor;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Nullable
    private ProcessCameraProvider cameraProvider;
    private volatile PoseDetector poseDetector;
    private volatile boolean frontCamera;
    /** Read on the MediaPipe thread for matching; written on the main thread. */
    @Nullable
    private volatile PoseTemplate target;
    private Mode mode = Mode.FREE;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private String resolutionSummary = "";
    private boolean showingNoPerson;
    private boolean modelReady;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showStatus(getString(R.string.camera_permission_denied));
                }
            });

    private final ActivityResultLauncher<Intent> pickPoseLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Intent data = result.getData();
                        if (result.getResultCode() == RESULT_OK && data != null) {
                            loadTarget(data.getStringExtra(PoseLibraryActivity.EXTRA_PICKED_POSE_ID));
                        }
                    });

    private final Runnable statsTicker = new Runnable() {
        @Override
        public void run() {
            updateStats();
            mainHandler.postDelayed(this, STATS_INTERVAL_MS);
        }
    };

    private final Runnable recordTicker = new Runnable() {
        @Override
        public void run() {
            updateRecordingUi();
            PoseRecorder.State state = recorder.getState();
            if (state == PoseRecorder.State.COUNTDOWN || state == PoseRecorder.State.HOLD_STILL) {
                mainHandler.postDelayed(this, RECORD_TICK_MS);
            }
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
        matchChip = findViewById(R.id.matchChip);
        countdownText = findViewById(R.id.countdownText);
        guidancePanel = findViewById(R.id.guidancePanel);
        hintText = findViewById(R.id.hintText);
        partsRow = findViewById(R.id.partsRow);
        modeLabel = findViewById(R.id.modeLabel);
        shutterButton = findViewById(R.id.shutterButton);
        buildPartPills();

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
        shutterButton.setOnClickListener(v -> onShutter());
        findViewById(R.id.galleryButton).setOnClickListener(v -> Snackbar.make(
                findViewById(R.id.bottomBar),
                getString(R.string.coming_soon, getString(R.string.home_photos_title)),
                Snackbar.LENGTH_SHORT).show());
        modeLabel.setOnClickListener(v -> {
            if (mode != Mode.RECORD) {
                pickPoseLauncher.launch(new Intent(this, PoseLibraryActivity.class)
                        .putExtra(PoseLibraryActivity.EXTRA_PICK_MODE, true));
            }
        });

        analysisExecutor = Executors.newSingleThreadExecutor();
        analysisExecutor.execute(this::loadDetector);

        if (getIntent().getBooleanExtra(EXTRA_RECORD_MODE, false)) {
            enterRecordMode();
        } else {
            enterFreeMode();
            String poseId = getIntent().getStringExtra(EXTRA_POSE_ID);
            if (poseId != null) {
                loadTarget(poseId);
            }
        }

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
        if (mode == Mode.RECORD && recorder.getState() != PoseRecorder.State.IDLE) {
            recorder.cancel();
            mainHandler.removeCallbacks(recordTicker);
            updateRecordingUi();
        }
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
        ioExecutor.shutdown();
    }

    // ---- Modes ---------------------------------------------------------------------------

    private void enterFreeMode() {
        mode = Mode.FREE;
        target = null;
        poseOverlay.setTarget(null);
        poseOverlay.setMatchState(null, false);
        modeLabel.setText(R.string.camera_choose_pose);
        shutterButton.setBackgroundResource(R.drawable.bg_shutter);
        guidancePanel.setVisibility(View.GONE);
        matchChip.setVisibility(View.GONE);
    }

    private void enterGuideMode(PoseTemplate pose) {
        mode = Mode.GUIDE;
        smoother.reset();
        target = pose;
        poseOverlay.setTarget(pose);
        poseOverlay.setMatchState(null, false);
        modeLabel.setText(pose.name.toUpperCase(Locale.getDefault()));
        modeLabel.setContentDescription(getString(R.string.camera_change_pose));
        shutterButton.setBackgroundResource(R.drawable.bg_shutter);
        statusText.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        guidancePanel.setVisibility(View.VISIBLE);
        hintText.setText(pose.framing == PoseTemplate.Framing.UPPER_BODY
                ? R.string.hint_step_back_upper : R.string.hint_step_back_full);
        for (TextView pill : partPills.values()) {
            pill.setVisibility(View.GONE);
        }
    }

    private void enterRecordMode() {
        mode = Mode.RECORD;
        target = null;
        poseOverlay.setTarget(null);
        poseOverlay.setMatchState(null, false);
        modeLabel.setText(R.string.record_mode_label);
        shutterButton.setBackgroundResource(R.drawable.bg_shutter_record);
        guidancePanel.setVisibility(View.GONE);
        matchChip.setVisibility(View.GONE);
        recorder.cancel();
        updateRecordingUi();
    }

    private void loadTarget(@Nullable String poseId) {
        if (poseId == null) {
            return;
        }
        ioExecutor.execute(() -> {
            PoseTemplate pose = PoseRepository.get(this).findById(poseId);
            mainHandler.post(() -> {
                if (pose != null && !isDestroyed()) {
                    enterGuideMode(pose);
                }
            });
        });
    }

    private void onShutter() {
        if (mode != Mode.RECORD) {
            Snackbar.make(findViewById(R.id.bottomBar), R.string.camera_capture_soon,
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        PoseRecorder.State state = recorder.getState();
        if (state == PoseRecorder.State.COUNTDOWN || state == PoseRecorder.State.HOLD_STILL) {
            recorder.cancel();
        } else {
            recorder.start(SystemClock.uptimeMillis());
            mainHandler.removeCallbacks(recordTicker);
            mainHandler.post(recordTicker);
        }
        updateRecordingUi();
    }

    // ---- Camera --------------------------------------------------------------------------

    /** Runs on the analysis thread. */
    private void loadDetector() {
        long start = SystemClock.uptimeMillis();
        try {
            poseDetector = PoseDetector.create(this, this);
            long loadMs = SystemClock.uptimeMillis() - start;
            Log.i(PERF_TAG, "Pose model loaded in " + loadMs + " ms");
            mainHandler.post(() -> {
                modelReady = true;
                showingNoPerson = true;
                if (mode == Mode.FREE) {
                    showStatus(getString(R.string.camera_no_person));
                } else if (mode == Mode.RECORD) {
                    updateRecordingUi();
                } else {
                    statusText.setVisibility(View.GONE);
                }
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

    // ---- Results -------------------------------------------------------------------------

    @Override
    public void onPose(PoseFrame frame) {
        perfStats.record(frame.prepMs, frame.inferenceMs);
        BodyPose live = frame.toBodyPose();
        PoseTemplate currentTarget = target;
        // Matching is a few microseconds of arithmetic, so it runs here on MediaPipe's thread.
        MatchResult match = currentTarget != null && live != null
                ? matcher.match(live, currentTarget) : null;
        mainHandler.post(() -> onFrameResult(frame, live, match));
    }

    @Override
    public void onDetectorError(String message) {
        Log.e(TAG, "Pose detector error: " + message);
        mainHandler.post(() -> showStatus(getString(R.string.camera_detector_error, message)));
    }

    private void onFrameResult(PoseFrame frame, @Nullable BodyPose live,
                               @Nullable MatchResult match) {
        poseOverlay.setPoseFrame(frame);
        switch (mode) {
            case GUIDE:
                updateGuidance(match);
                break;
            case RECORD:
                recorder.onPose(live, SystemClock.uptimeMillis());
                updateRecordingUi();
                break;
            default:
                if (modelReady && frame.hasPose == showingNoPerson) {
                    showingNoPerson = !frame.hasPose;
                    if (showingNoPerson) {
                        showStatus(getString(R.string.camera_no_person));
                    } else {
                        statusText.setVisibility(View.GONE);
                    }
                }
                break;
        }
    }

    private void updateGuidance(@Nullable MatchResult match) {
        PoseTemplate pose = target;
        if (pose == null) {
            return;
        }
        if (match == null || !match.reliable) {
            if (match == null) {
                smoother.reset();
                poseOverlay.setMatchState(null, false);
            }
            matchChip.setVisibility(View.GONE);
            hintText.setText(pose.framing == PoseTemplate.Framing.UPPER_BODY
                    ? R.string.hint_step_back_upper : R.string.hint_step_back_full);
            for (TextView pill : partPills.values()) {
                pill.setVisibility(View.GONE);
            }
            return;
        }

        smoother.update(match, SystemClock.uptimeMillis());
        float score = smoother.getScore();
        int percent = Math.round(score * 100f);
        matchChip.setText(getString(R.string.match_percent, percent));
        matchChip.setVisibility(View.VISIBLE);
        int chipColor = ContextCompat.getColor(this, score >= matchConfig.goodThreshold
                ? R.color.match_good : score >= matchConfig.adjustThreshold
                ? R.color.match_adjust : R.color.match_off);
        matchChip.setBackgroundTintList(ColorStateList.valueOf(chipColor));
        matchChip.setTextColor(ContextCompat.getColor(this, R.color.black));

        MatchResult.Hint hint = smoother.getHint();
        if (hint != null) {
            hintText.setText(GuidanceText.forHint(this, hint));
        } else if (score >= HOLD_IT_SCORE) {
            hintText.setText(R.string.hint_hold_it);
        }

        Map<BodyPart, MatchResult.Status> status = smoother.getPartStatus();
        for (Map.Entry<BodyPart, TextView> entry : partPills.entrySet()) {
            MatchResult.Status s = status.get(entry.getKey());
            TextView pill = entry.getValue();
            if (s == null || s == MatchResult.Status.IGNORED) {
                pill.setVisibility(View.GONE);
                continue;
            }
            pill.setVisibility(View.VISIBLE);
            int color = s == MatchResult.Status.GOOD ? R.color.match_good
                    : s == MatchResult.Status.ADJUST ? R.color.match_adjust
                    : s == MatchResult.Status.OFF ? R.color.match_off : R.color.text_secondary;
            pill.setTextColor(ContextCompat.getColor(this, color));
        }
        poseOverlay.setMatchState(status, smoother.isMirroredTarget());
    }

    private void buildPartPills() {
        float density = getResources().getDisplayMetrics().density;
        for (BodyPart part : BodyPart.values()) {
            TextView pill = new TextView(this);
            pill.setText(getString(R.string.part_pill, GuidanceText.partLabel(this, part)));
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pill.setBackgroundResource(R.drawable.bg_hud);
            int padH = Math.round(8 * density);
            int padV = Math.round(4 * density);
            pill.setPadding(padH, padV, padH, padV);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(Math.round(3 * density));
            params.setMarginEnd(Math.round(3 * density));
            pill.setVisibility(View.GONE);
            partsRow.addView(pill, params);
            partPills.put(part, pill);
        }
    }

    // ---- Recording -----------------------------------------------------------------------

    private void updateRecordingUi() {
        if (mode != Mode.RECORD) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        switch (recorder.getState()) {
            case COUNTDOWN:
                countdownText.setVisibility(View.VISIBLE);
                countdownText.setText(String.valueOf(
                        Math.max(1, recorder.countdownSecondsLeft(now))));
                statusText.setVisibility(View.GONE);
                break;
            case HOLD_STILL:
                countdownText.setVisibility(View.GONE);
                showStatus(getString(R.string.record_hold_still) + "  "
                        + Math.round(recorder.stillProgress(now) * 100f) + "%");
                break;
            case DONE:
                countdownText.setVisibility(View.GONE);
                statusText.setVisibility(View.GONE);
                mainHandler.removeCallbacks(recordTicker);
                BodyPose result = recorder.getResult();
                recorder.cancel();
                if (result != null) {
                    showSaveDialog(result);
                }
                break;
            case TIMED_OUT:
                countdownText.setVisibility(View.GONE);
                mainHandler.removeCallbacks(recordTicker);
                recorder.cancel();
                showStatus(getString(R.string.record_timed_out));
                break;
            default:
                countdownText.setVisibility(View.GONE);
                if (modelReady) {
                    showStatus(getString(R.string.record_ready));
                }
                break;
        }
    }

    private void showSaveDialog(BodyPose pose) {
        boolean fullBody = pose.visibility[Landmark.LEFT_KNEE] >= 0.6f
                && pose.visibility[Landmark.RIGHT_KNEE] >= 0.6f
                && pose.visibility[Landmark.LEFT_ANKLE] >= 0.6f
                && pose.visibility[Landmark.RIGHT_ANKLE] >= 0.6f;
        PoseTemplate.Framing framing = fullBody ? PoseTemplate.Framing.FULL_BODY
                : PoseTemplate.Framing.UPPER_BODY;

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_save_pose, null);
        ((PoseThumbnailView) content.findViewById(R.id.savePreview)).setPose(pose);
        EditText nameField = content.findViewById(R.id.saveName);
        Spinner categorySpinner = content.findViewById(R.id.saveCategory);
        CheckBox mirrorBox = content.findViewById(R.id.saveMirror);
        ((TextView) content.findViewById(R.id.saveFraming)).setText(getString(
                R.string.record_detected_framing, PoseLabels.framing(this, framing)));

        String[] labels = new String[CATEGORY_KEYS.length];
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            labels[i] = PoseLabels.category(this, CATEGORY_KEYS[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.record_save_title)
                .setView(content)
                .setCancelable(false)
                .setNegativeButton(R.string.record_discard, (d, w) -> updateRecordingUi())
                .setPositiveButton(R.string.record_save, (d, w) -> {
                    String name = nameField.getText() == null ? ""
                            : nameField.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = getString(R.string.record_default_name);
                    }
                    PoseTemplate template = new PoseTemplate(
                            "user-" + System.currentTimeMillis(), name, "",
                            CATEGORY_KEYS[categorySpinner.getSelectedItemPosition()], "easy",
                            framing, mirrorBox.isChecked(), Collections.emptyList(),
                            Collections.emptyList(), Collections.emptyList(),
                            new PoseTemplate.Source("user", null, null, null, null, null),
                            pose);
                    savePose(template);
                })
                .show();
    }

    private void savePose(PoseTemplate template) {
        ioExecutor.execute(() -> {
            try {
                PoseRepository.get(this).saveUserPose(template);
                mainHandler.post(() -> Snackbar.make(findViewById(R.id.bottomBar),
                                R.string.record_saved, Snackbar.LENGTH_LONG)
                        .setAction(R.string.record_try_it, v -> enterGuideMode(template))
                        .show());
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Saving pose failed", e);
                mainHandler.post(() -> showStatus(
                        getString(R.string.record_save_failed, e.getMessage())));
            }
        });
        updateRecordingUi();
    }

    // ---- Misc UI -------------------------------------------------------------------------

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
