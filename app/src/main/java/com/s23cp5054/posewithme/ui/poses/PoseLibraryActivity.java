package com.s23cp5054.posewithme.ui.poses;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.repository.FavoritesRepository;
import com.s23cp5054.posewithme.data.repository.PoseRepository;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.ui.camera.CameraActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Grid of built-in and recorded poses (or only favourites); tapping one shows details and a
 * "Try this pose" action. In pick mode the chosen pose is returned to the caller.
 */
public class PoseLibraryActivity extends AppCompatActivity implements PoseDetailSheet.Listener {

    /** Boolean extra: return the chosen pose to the caller instead of opening the camera. */
    public static final String EXTRA_PICK_MODE = "com.s23cp5054.posewithme.extra.PICK_MODE";
    /** Result extra holding the id of the pose picked in pick mode. */
    public static final String EXTRA_PICKED_POSE_ID =
            "com.s23cp5054.posewithme.extra.PICKED_POSE_ID";
    /** Boolean extra: show only the user's favourite poses. */
    public static final String EXTRA_FAVORITES_ONLY =
            "com.s23cp5054.posewithme.extra.FAVORITES_ONLY";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PoseCardAdapter adapter = new PoseCardAdapter(this::showDetails);
    private TextView countText;
    private TextView emptyText;
    private boolean pickMode;
    private boolean favoritesOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pose_library);

        View header = findViewById(R.id.libraryHeader);
        RecyclerView grid = findViewById(R.id.poseGrid);
        View recordButton = findViewById(R.id.recordButton);
        int headerTop = header.getPaddingTop();
        int gridBottom = grid.getPaddingBottom();
        ViewGroup.MarginLayoutParams fabParams =
                (ViewGroup.MarginLayoutParams) recordButton.getLayoutParams();
        int fabBottom = fabParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.libraryRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(),
                    gridBottom + bars.bottom);
            fabParams.bottomMargin = fabBottom + bars.bottom;
            recordButton.setLayoutParams(fabParams);
            return insets;
        });

        pickMode = getIntent().getBooleanExtra(EXTRA_PICK_MODE, false);
        favoritesOnly = getIntent().getBooleanExtra(EXTRA_FAVORITES_ONLY, false);
        // Recording opens a second camera; from inside the camera (pick mode) that would stack
        // two cameras, so recording is offered from the home screen and library instead.
        recordButton.setVisibility(pickMode || favoritesOnly ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.libraryTitle)).setText(favoritesOnly
                ? R.string.home_favorites_title : R.string.home_browse_title);
        countText = findViewById(R.id.poseCountText);
        emptyText = findViewById(R.id.emptyText);
        emptyText.setText(favoritesOnly ? R.string.favorites_empty : R.string.library_empty);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        recordButton.setOnClickListener(v -> startActivity(
                new Intent(this, CameraActivity.class)
                        .putExtra(CameraActivity.EXTRA_RECORD_MODE, true)));

        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload so a pose recorded in the camera shows up when the user comes back.
        loadPoses();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void loadPoses() {
        ioExecutor.execute(() -> {
            List<PoseTemplate> all = PoseRepository.get(this).getAll();
            List<PoseTemplate> poses = all;
            if (favoritesOnly) {
                Set<String> favorites = new FavoritesRepository(this).getAll();
                poses = new ArrayList<>();
                for (PoseTemplate pose : all) {
                    if (favorites.contains(pose.id)) {
                        poses.add(pose);
                    }
                }
            }
            List<PoseTemplate> result = poses;
            mainHandler.post(() -> showPoses(result));
        });
    }

    private void showPoses(List<PoseTemplate> poses) {
        adapter.setPoses(poses);
        countText.setText(getResources().getQuantityString(R.plurals.library_count,
                poses.size(), poses.size()));
        emptyText.setVisibility(poses.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showDetails(PoseTemplate pose) {
        PoseDetailSheet.show(this, pose, ioExecutor, this);
    }

    @Override
    public void onTryPose(PoseTemplate pose) {
        if (pickMode) {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_PICKED_POSE_ID, pose.id));
            finish();
        } else {
            startActivity(new Intent(this, CameraActivity.class)
                    .putExtra(CameraActivity.EXTRA_POSE_ID, pose.id));
        }
    }

    @Override
    public void onPosesChanged() {
        loadPoses();
    }
}
