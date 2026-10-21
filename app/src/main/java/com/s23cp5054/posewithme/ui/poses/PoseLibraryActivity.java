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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
    private int peopleFilter;
    private List<PoseTemplate> loadedPoses = new ArrayList<>();

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
        recordButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.library_add)
                .setItems(new String[]{getString(R.string.library_record), getString(R.string.import_title)},
                        (dialog, which) -> startActivity(which == 0
                                ? new Intent(this, CameraActivity.class).putExtra(CameraActivity.EXTRA_RECORD_MODE, true)
                                : new Intent(this, ImportPoseActivity.class)))
                .show());

        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setAdapter(adapter);
        ChipGroup filters = findViewById(R.id.peopleFilters);
        int[] filterLabels = {R.string.filter_all, R.string.filter_solo, R.string.category_couple,
                R.string.category_friends, R.string.category_group};
        for (int i = 0; i < filterLabels.length; i++) {
            final int selected = i;
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(filterLabels[i]);
            chip.setCheckable(true);
            filters.addView(chip);
            chip.setChecked(i == peopleFilter);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) { peopleFilter = selected; renderPoses(); }
            });
        }
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
        loadedPoses = poses;
        renderPoses();
    }

    private void renderPoses() {
        List<PoseTemplate> poses = new ArrayList<>();
        for (PoseTemplate pose : loadedPoses) {
            if (peopleFilter == 0 || peopleFilter == 1 && pose.people.size() == 1
                    || peopleFilter == 2 && "couple".equals(pose.category)
                    || peopleFilter == 3 && "friends".equals(pose.category)
                    || peopleFilter == 4 && pose.people.size() >= 3) poses.add(pose);
        }
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
