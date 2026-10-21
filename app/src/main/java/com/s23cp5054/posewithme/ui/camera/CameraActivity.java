package com.s23cp5054.posewithme.ui.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.Camera;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.TorchState;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ResolutionInfo;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.extensions.ExtensionMode;
import androidx.camera.extensions.ExtensionsManager;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.model.Photo;
import com.s23cp5054.posewithme.data.repository.PhotoRepository;
import com.s23cp5054.posewithme.data.repository.PoseRepository;
import com.s23cp5054.posewithme.data.settings.AppSettings;
import com.s23cp5054.posewithme.pose.detection.PoseDetector;
import com.s23cp5054.posewithme.pose.detection.PoseFrame;
import com.s23cp5054.posewithme.pose.matching.AutoCaptureTrigger;
import com.s23cp5054.posewithme.pose.matching.BodyPart;
import com.s23cp5054.posewithme.pose.matching.GuidanceSmoother;
import com.s23cp5054.posewithme.pose.matching.MatchConfig;
import com.s23cp5054.posewithme.pose.matching.MatchResult;
import com.s23cp5054.posewithme.pose.matching.PoseMatcher;
import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.pose.recording.PoseRecorder;
import com.s23cp5054.posewithme.storage.PhotoStorage;
import com.s23cp5054.posewithme.ui.gallery.GalleryActivity;
import com.s23cp5054.posewithme.ui.gallery.PhotoActivity;
import com.s23cp5054.posewithme.ui.pose.PoseThumbnailView;
import com.s23cp5054.posewithme.ui.poses.PoseLabels;
import com.s23cp5054.posewithme.ui.poses.PoseLibraryActivity;
import com.s23cp5054.posewithme.utils.ImageLoader;
import com.s23cp5054.posewithme.utils.PerfStats;

import org.json.JSONException;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The live camera: CameraX preview with on-device pose detection drawn on top. Three modes:
 * free (just the skeleton), guide (a target pose ghost with live score and corrections) and
 * record (capture the user's own pose into the library). Photos can be taken in free and guide
 * mode, manually (with an optional self-timer) or automatically once the pose is matched.
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
    private static final long TICK_MS = 100;
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
    private ImageButton galleryButton;
    private ImageButton torchButton;
    @Nullable private Camera camera;
    private MaterialButtonToggleGroup framingToggle;
    private View flashView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PerfStats perfStats = new PerfStats();
    private final MatchConfig matchConfig = new MatchConfig();
    private final PoseMatcher matcher = new PoseMatcher(matchConfig);
    private final GuidanceSmoother smoother = new GuidanceSmoother(matchConfig);
    private final PoseRecorder recorder = new PoseRecorder();
    private final AutoCaptureTrigger autoCapture = new AutoCaptureTrigger(0.85f);
    private final Map<BodyPart, TextView> partPills = new EnumMap<>(BodyPart.class);
    private ExecutorService analysisExecutor;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private AppSettings settings;

    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private ImageCapture imageCapture;
    @Nullable private ExtensionsManager extensionsManager;
    private boolean captureSession;
    private volatile boolean analysisPaused;
    private volatile PoseDetector poseDetector;
    private volatile boolean frontCamera;
    /** Read on the MediaPipe thread for matching; written on the main thread. */
    @Nullable
    private volatile PoseTemplate target;
    /** Accept hips just below the frame (half-body framing); read on the MediaPipe thread. */
    private volatile boolean waistUp;
    private Mode mode = Mode.FREE;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private String resolutionSummary = "";
    private boolean showingNoPerson;
    private boolean modelReady;
    private boolean showHints = true;
    private boolean autoCaptureEnabled;
    /** Uptime at which the self-timer fires, or -1 when no countdown is running. */
    private long captureAtMs = -1;
    private boolean capturing;
    private float focusX = 0.5f;
    private float focusY = 0.5f;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showStatus(getString(R.string.camera_permission_denied));
                }
            });

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    takePhoto();
                } else {
                    Snackbar.make(galleryButton, R.string.camera_storage_denied,
                            Snackbar.LENGTH_LONG).show();
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
                mainHandler.postDelayed(this, TICK_MS);
            }
        }
    };

    private final Runnable captureTicker = new Runnable() {
        @Override
        public void run() {
            if (captureAtMs < 0) {
                return;
            }
            long left = captureAtMs - SystemClock.uptimeMillis();
            if (left <= 0) {
                captureAtMs = -1;
                countdownText.setVisibility(View.GONE);
                takePhoto();
                return;
            }
            countdownText.setVisibility(View.VISIBLE);
            countdownText.setText(String.valueOf((left + 999) / 1000));
            mainHandler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera);
        settings = new AppSettings(this);

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
        galleryButton = findViewById(R.id.galleryButton);
        torchButton = findViewById(R.id.torchButton);
        torchButton.setOnClickListener(v -> toggleTorch());
        framingToggle = findViewById(R.id.framingToggle);
        flashView = findViewById(R.id.flashView);
        buildPartPills();

        hideStatusBar();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cameraRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            findViewById(R.id.topBar).setPadding(bars.left, bars.top, bars.right, 0);
            findViewById(R.id.bottomBar).setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.flipButton).setOnClickListener(v -> {
            v.animate().rotationBy(180f).setDuration(250).start();
            flipCamera();
        });
        findViewById(R.id.closeButton).setOnClickListener(v -> finish());
        findViewById(R.id.gridButton).setOnClickListener(v -> toggleVisibility(
                findViewById(R.id.gridOverlay), (ImageButton) v));
        findViewById(R.id.statsButton).setOnClickListener(v -> toggleVisibility(
                statsText, (ImageButton) v));
        shutterButton.setOnClickListener(v -> onShutter());
        galleryButton.setOnClickListener(v ->
                startActivity(new Intent(this, GalleryActivity.class)));
        modeLabel.setOnClickListener(v -> {
            if (mode != Mode.RECORD) {
                pickPoseLauncher.launch(new Intent(this, PoseLibraryActivity.class)
                        .putExtra(PoseLibraryActivity.EXTRA_PICK_MODE, true));
            }
        });
        framingToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked && mode == Mode.RECORD) {
                waistUp = checkedId == R.id.framingHalf;
                updateRecordingUi();
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
        if (captureSession && !capturing) bindCameraUseCases(false);
        showHints = settings.isShowHints();
        autoCaptureEnabled = settings.isAutoCapture();
        autoCapture.setThreshold(settings.getAutoCaptureThreshold());
        // Coming back from photo review with the pose still held must not fire again.
        autoCapture.disarm();
        poseOverlay.setShowGhost(settings.isShowGhost());
        poseOverlay.setShowSkeleton(settings.isShowSkeleton());
        refreshGalleryThumbnail();
        mainHandler.post(statsTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(false);
        }
        mainHandler.removeCallbacks(statsTicker);
        cancelCaptureCountdown();
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
        waistUp = false;
        poseOverlay.setTarget(null);
        poseOverlay.setMatchState(null, false);
        modeLabel.setText(R.string.camera_choose_pose);
        shutterButton.setBackgroundResource(R.drawable.bg_shutter);
        shutterButton.setContentDescription(getString(R.string.camera_shutter));
        guidancePanel.setVisibility(View.GONE);
        matchChip.setVisibility(View.GONE);
        framingToggle.setVisibility(View.GONE);
    }

    private void enterGuideMode(PoseTemplate pose) {
        mode = Mode.GUIDE;
        smoother.reset();
        autoCapture.reset();
        waistUp = pose.framing == PoseTemplate.Framing.UPPER_BODY;
        target = pose;
        poseOverlay.setTarget(pose);
        poseOverlay.setMatchState(null, false);
        modeLabel.setText(pose.name.toUpperCase(Locale.getDefault()));
        modeLabel.setContentDescription(getString(R.string.camera_change_pose));
        shutterButton.setBackgroundResource(R.drawable.bg_shutter);
        shutterButton.setContentDescription(getString(R.string.camera_shutter));
        statusText.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        framingToggle.setVisibility(View.GONE);
        guidancePanel.setVisibility(View.VISIBLE);
        hintText.setText(framingHint(pose));
        for (TextView pill : partPills.values()) {
            pill.setVisibility(View.GONE);
        }
    }

    private void enterRecordMode() {
        mode = Mode.RECORD;
        target = null;
        waistUp = framingToggle.getCheckedButtonId() == R.id.framingHalf;
        poseOverlay.setTarget(null);
        poseOverlay.setMatchState(null, false);
        modeLabel.setText(R.string.record_mode_label);
        shutterButton.setBackgroundResource(R.drawable.bg_shutter_record);
        shutterButton.setContentDescription(getString(R.string.record_start));
        guidancePanel.setVisibility(View.GONE);
        matchChip.setVisibility(View.GONE);
        framingToggle.setVisibility(View.VISIBLE);
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

    private int stepBackHint(PoseTemplate pose) {
        return pose.framing == PoseTemplate.Framing.UPPER_BODY
                ? R.string.hint_step_back_upper : R.string.hint_step_back_full;
    }

    private String framingHint(PoseTemplate pose) {
        return pose.people.size() > 1 ? getString(R.string.hint_group_visible, pose.people.size())
                : getString(stepBackHint(pose));
    }

    // ---- Shutter & capture ---------------------------------------------------------------

    private void onShutter() {
        if (mode == Mode.RECORD) {
            PoseRecorder.State state = recorder.getState();
            if (state == PoseRecorder.State.COUNTDOWN || state == PoseRecorder.State.HOLD_STILL) {
                recorder.cancel();
            } else {
                recorder.start(SystemClock.uptimeMillis());
                mainHandler.removeCallbacks(recordTicker);
                mainHandler.post(recordTicker);
            }
            updateRecordingUi();
            return;
        }
        if (captureAtMs >= 0) {
            cancelCaptureCountdown();
            return;
        }
        int seconds = settings.getCountdownSeconds();
        if (seconds <= 0) {
            takePhoto();
        } else {
            captureAtMs = SystemClock.uptimeMillis() + seconds * 1000L;
            mainHandler.post(captureTicker);
        }
    }

    private void cancelCaptureCountdown() {
        captureAtMs = -1;
        mainHandler.removeCallbacks(captureTicker);
        if (mode != Mode.RECORD) {
            countdownText.setVisibility(View.GONE);
        }
    }

    private void takePhoto() {
        if (imageCapture == null || capturing) {
            return;
        }
        if (PhotoStorage.needsWritePermission(this)) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        setCapturing(true);
        autoCapture.disarm();
        PoseTemplate pose = mode == Mode.GUIDE ? target : null;
        Float score = pose != null && smoother.isReliable() && smoother.getScore() >= 0
                ? smoother.getScore() : null;
        long now = System.currentTimeMillis();
        shutterButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        // The still session releases the analysis stream's resolution and OEM-processing limits.
        if (!bindCameraUseCases(true) && !bindCameraUseCases(false)) {
            setCapturing(false);
            return;
        }
        ImageCapture stillCapture = imageCapture;
        showStatus(getString(R.string.camera_processing_photo));

        boolean mirror = frontCamera && settings.isMirrorSelfies();
        Runnable shoot = () -> {
            if (isDestroyed() || isFinishing()) return;
            stillCapture.takePicture(PhotoStorage.outputOptions(this, now, mirror), ioExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        if (results.getSavedUri() == null) {
                            onError(new ImageCaptureException(ImageCapture.ERROR_FILE_IO,
                                    "No URI returned", null));
                            return;
                        }
                        long id = new PhotoRepository(CameraActivity.this).insert(
                                results.getSavedUri(), pose != null ? pose.id : null,
                                pose != null ? pose.name : null, score, now);
                        mainHandler.post(() -> {
                            if (isDestroyed()) return;
                            setCapturing(false);
                            flash();
                            refreshGalleryThumbnail();
                            startActivity(new Intent(CameraActivity.this, PhotoActivity.class)
                                    .putExtra(PhotoActivity.EXTRA_PHOTO_ID, id)
                                    .putExtra(PhotoActivity.EXTRA_REVIEW, true));
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "Photo capture failed", e);
                        mainHandler.post(() -> {
                            if (isDestroyed()) return;
                            setCapturing(false);
                            bindCameraUseCases(false);
                            statusText.setVisibility(View.GONE);
                            Snackbar.make(galleryButton,
                                    getString(R.string.camera_capture_failed, e.getMessage()),
                                    Snackbar.LENGTH_LONG).show();
                        });
                    }
                });
        };
        // Reapply the chosen subject after switching sessions; fixed-focus cameras can still capture.
        FocusMeteringAction focus = new FocusMeteringAction.Builder(
                previewView.getMeteringPointFactory().createPoint(
                        focusX * previewView.getWidth(), focusY * previewView.getHeight()),
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(5, TimeUnit.SECONDS).build();
        camera.getCameraControl().startFocusAndMetering(focus)
                .addListener(shoot, ContextCompat.getMainExecutor(this));
    }

    private void setCapturing(boolean value) {
        capturing = value;
        shutterButton.setEnabled(!value);
        galleryButton.setEnabled(!value);
        torchButton.setEnabled(!value && camera != null && camera.getCameraInfo().hasFlashUnit());
        findViewById(R.id.flipButton).setEnabled(!value);
        modeLabel.setEnabled(!value);
    }

    private void flash() {
        flashView.setVisibility(View.VISIBLE);
        flashView.setAlpha(0.85f);
        flashView.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> flashView.setVisibility(View.GONE)).start();
    }

    private void refreshGalleryThumbnail() {
        int sizePx = Math.round(52 * getResources().getDisplayMetrics().density);
        ioExecutor.execute(() -> {
            Photo latest = new PhotoRepository(this).getLatest();
            Bitmap bitmap = latest != null
                    ? ImageLoader.get(this).decode(latest.uri, sizePx * 2) : null;
            mainHandler.post(() -> {
                if (isDestroyed()) {
                    return;
                }
                if (bitmap != null) {
                    galleryButton.setPadding(0, 0, 0, 0);
                    galleryButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    galleryButton.setImageTintList(null);
                    galleryButton.setClipToOutline(true);
                    galleryButton.setImageBitmap(bitmap);
                } else {
                    int pad = Math.round(14 * getResources().getDisplayMetrics().density);
                    galleryButton.setPadding(pad, pad, pad, pad);
                    galleryButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    galleryButton.setImageTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.white)));
                    galleryButton.setImageResource(R.drawable.ic_gallery);
                }
            });
        });
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
                bindCameraUseCases(false);
                ListenableFuture<ExtensionsManager> extensions =
                        ExtensionsManager.getInstanceAsync(this, cameraProvider);
                extensions.addListener(() -> {
                    try {
                        extensionsManager = extensions.get();
                    } catch (ExecutionException e) {
                        Log.w(TAG, "OEM photo extensions unavailable", e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, ContextCompat.getMainExecutor(this));
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera provider unavailable", e);
                showStatus(getString(R.string.camera_bind_failed, e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void flipCamera() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(false);
        }
        lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        bindCameraUseCases(false);
    }

    private boolean bindCameraUseCases(boolean captureOnly) {
        ProcessCameraProvider provider = cameraProvider;
        if (provider == null || isDestroyed()) {
            return false;
        }
        // The viewport is only known once the preview has been laid out.
        if (previewView.getViewPort() == null) {
            if (!captureOnly) previewView.post(() -> bindCameraUseCases(false));
            return false;
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

        boolean torchOn = camera != null
                && Integer.valueOf(TorchState.ON).equals(camera.getCameraInfo().getTorchState().getValue());
        CameraSelector photoSelector = selector;
        String processing = "standard";
        if (captureOnly && !torchOn && extensionsManager != null) {
            try {
                for (int extension : new int[]{ExtensionMode.HDR, ExtensionMode.AUTO}) {
                    if (extensionsManager.isExtensionAvailable(selector, extension)) {
                        photoSelector = extensionsManager.getExtensionEnabledCameraSelector(selector, extension);
                        processing = extension == ExtensionMode.HDR ? "OEM HDR" : "OEM auto";
                        break;
                    }
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not query OEM extensions; using standard capture", e);
            }
        }

        // Keep the preview and saved image framing consistent with the guide.
        ResolutionSelector fourByThree = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build();
        Preview preview = new Preview.Builder().setResolutionSelector(fourByThree).build();
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

        ImageCapture capture = new ImageCapture.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .setAllowedResolutionMode(captureOnly
                                ? ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                                : ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                        .build())
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(100)
                .build();

        ViewPort viewPort = previewView.getViewPort();
        UseCaseGroup.Builder groupBuilder = new UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(preview)
                .addUseCase(capture);
        if (!captureOnly) groupBuilder.addUseCase(analysis);
        UseCaseGroup group = groupBuilder.build();

        frontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT;
        poseOverlay.setPoseFrame(null);
        try {
            analysisPaused = captureOnly;
            if (camera != null) camera.getCameraInfo().getTorchState().removeObservers(this);
            camera = null;
            provider.unbindAll();
            try {
                camera = provider.bindToLifecycle(this, photoSelector, group);
            } catch (RuntimeException e) {
                if (photoSelector == selector) throw e;
                Log.w(TAG, "OEM capture unavailable; falling back to high-resolution capture", e);
                provider.unbindAll();
                camera = provider.bindToLifecycle(this, selector, group);
                processing = "standard (OEM fallback)";
            }
            imageCapture = capture;
            captureSession = captureOnly;
            boolean hasFlash = camera.getCameraInfo().hasFlashUnit();
            if (hasFlash && torchOn) camera.getCameraControl().enableTorch(true);
            torchButton.setEnabled(hasFlash && !capturing);
            torchButton.setAlpha(hasFlash ? 1f : 0.35f);
            torchButton.setContentDescription(getString(hasFlash
                    ? R.string.camera_torch_on : R.string.camera_torch_unavailable));
            camera.getCameraInfo().getTorchState().observe(this, state -> {
                if (camera == null) return;
                boolean on = state == TorchState.ON;
                torchButton.setSelected(on);
                torchButton.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                        on ? R.color.brand_accent : R.color.white)));
                if (camera.getCameraInfo().hasFlashUnit()) {
                    torchButton.setContentDescription(getString(on
                            ? R.string.camera_torch_off : R.string.camera_torch_on));
                }
            });
            previewView.setOnTouchListener((view, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP && camera != null && !capturing) {
                    view.performClick();
                    focusX = event.getX() / previewView.getWidth();
                    focusY = event.getY() / previewView.getHeight();
                    FocusMeteringAction focus = new FocusMeteringAction.Builder(
                            previewView.getMeteringPointFactory().createPoint(event.getX(), event.getY()))
                            .setAutoCancelDuration(5, TimeUnit.SECONDS).build();
                    camera.getCameraControl().startFocusAndMetering(focus);
                }
                return true;
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Use case binding failed", e);
            imageCapture = null;
            if (!captureOnly) showStatus(getString(R.string.camera_bind_failed, e.getMessage()));
            return false;
        }

        resolutionSummary = String.format(Locale.US, "%s  preview %s  analysis %s",
                frontCamera ? "front" : "back",
                describe(preview.getResolutionInfo()), describe(analysis.getResolutionInfo()));
        Log.i(PERF_TAG, "Bound camera: " + resolutionSummary + "  capture "
                + describe(capture.getResolutionInfo()) + "  processing=" + processing
                + "  captureOnly=" + captureOnly);
        return true;
    }

    private void toggleTorch() {
        Camera current = camera;
        if (current == null || !current.getCameraInfo().hasFlashUnit()) return;
        boolean enabled = current.getCameraInfo().getTorchState().getValue() != null
                && current.getCameraInfo().getTorchState().getValue() == TorchState.ON;
        ListenableFuture<Void> result = current.getCameraControl().enableTorch(!enabled);
        result.addListener(() -> {
            try {
                result.get();
            } catch (ExecutionException e) {
                Snackbar.make(torchButton, R.string.camera_torch_failed, Snackbar.LENGTH_SHORT).show();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, ContextCompat.getMainExecutor(this));
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
        if (detector == null || analysisPaused) {
            image.close();
            return;
        }
        detector.process(image, frontCamera);
    }

    // ---- Results -------------------------------------------------------------------------

    @Override
    public void onPose(PoseFrame frame) {
        if (analysisPaused) return;
        perfStats.record(frame.prepMs, frame.inferenceMs);
        BodyPose live = frame.toBodyPose(waistUp);
        PoseTemplate currentTarget = target;
        // Matching is a few microseconds of arithmetic, so it runs here on MediaPipe's thread.
        List<BodyPose> livePeople = currentTarget != null ? frame.toBodyPoses(waistUp)
                : Collections.emptyList();
        MatchResult match = currentTarget != null ? matcher.match(livePeople, currentTarget) : null;
        mainHandler.post(() -> {
            if (target == currentTarget) onFrameResult(frame, live, match);
        });
    }

    @Override
    public void onDetectorError(String message) {
        Log.e(TAG, "Pose detector error: " + message);
        mainHandler.post(() -> showStatus(getString(R.string.camera_detector_error, message)));
    }

    private void onFrameResult(PoseFrame frame, @Nullable BodyPose live,
                               @Nullable MatchResult match) {
        if (isDestroyed() || analysisPaused || capturing) {
            return;
        }
        poseOverlay.setPoseFrame(frame);
        switch (mode) {
            case GUIDE:
                updateGuidance(match);
                break;
            case RECORD:
                recorder.onPose(frame.imagePoses.size() == 1 ? live : null, SystemClock.uptimeMillis());
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
        long now = SystemClock.uptimeMillis();
        if (match == null || !match.reliable) {
            smoother.reset();
            poseOverlay.setMatchState(null, false);
            autoCapture.onScore(0f, false, now);
            matchChip.setVisibility(View.GONE);
            hintText.setVisibility(View.VISIBLE);
            hintText.setText(framingHint(pose));
            for (TextView pill : partPills.values()) {
                pill.setVisibility(View.GONE);
            }
            return;
        }

        smoother.update(match, now);
        float score = smoother.getScore();
        int percent = Math.round(score * 100f);
        matchChip.setText(getString(R.string.match_percent, percent));
        matchChip.setVisibility(View.VISIBLE);
        int chipColor = ContextCompat.getColor(this, score >= matchConfig.goodThreshold
                ? R.color.match_good : score >= matchConfig.adjustThreshold
                ? R.color.match_adjust : R.color.match_off);
        matchChip.setBackgroundTintList(ColorStateList.valueOf(chipColor));
        matchChip.setTextColor(ContextCompat.getColor(this, R.color.black));

        boolean holding = false;
        if (autoCaptureEnabled && !capturing && captureAtMs < 0) {
            if (autoCapture.onScore(score, true, now)) {
                takePhoto();
            }
            holding = autoCapture.holdProgress(now) > 0f;
        }

        hintText.setVisibility(showHints || holding ? View.VISIBLE : View.GONE);
        MatchResult.Hint hint = smoother.getHint();
        if (holding) {
            hintText.setText(R.string.hint_auto_capture_hold);
        } else if (hint != null) {
            hintText.setText(GuidanceText.forHint(this, hint));
        } else if (score >= HOLD_IT_SCORE) {
            hintText.setText(R.string.hint_hold_it);
        } else {
            hintText.setText(R.string.hint_follow_guide);
        }

        Map<BodyPart, MatchResult.Status> status = smoother.getPartStatus();
        for (Map.Entry<BodyPart, TextView> entry : partPills.entrySet()) {
            MatchResult.Status s = status.get(entry.getKey());
            TextView pill = entry.getValue();
            if (!showHints || pose.people.size() > 1 || s == null || s == MatchResult.Status.IGNORED) {
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
        boolean busy = false;
        switch (recorder.getState()) {
            case COUNTDOWN:
                busy = true;
                countdownText.setVisibility(View.VISIBLE);
                countdownText.setText(String.valueOf(
                        Math.max(1, recorder.countdownSecondsLeft(now))));
                statusText.setVisibility(View.GONE);
                break;
            case HOLD_STILL:
                busy = true;
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
                    showStatus(getString(waistUp ? R.string.record_ready_half
                            : R.string.record_ready));
                }
                break;
        }
        // The framing can't change mid-capture.
        framingToggle.setEnabled(!busy);
        for (int i = 0; i < framingToggle.getChildCount(); i++) {
            framingToggle.getChildAt(i).setEnabled(!busy);
        }
    }

    private void showSaveDialog(BodyPose pose) {
        PoseTemplate.Framing framing = waistUp ? PoseTemplate.Framing.UPPER_BODY
                : PoseTemplate.Framing.FULL_BODY;

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
        if (framing == PoseTemplate.Framing.UPPER_BODY) {
            categorySpinner.setSelection(java.util.Arrays.asList(CATEGORY_KEYS).indexOf("portrait"));
        }

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

    private void toggleVisibility(View view, ImageButton toggle) {
        boolean show = view.getVisibility() != View.VISIBLE;
        view.setVisibility(show ? View.VISIBLE : View.GONE);
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
