package com.s23cp5054.posewithme.ui.poses;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.repository.PoseRepository;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.ui.camera.CameraActivity;
import com.s23cp5054.posewithme.ui.pose.PoseThumbnailView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Grid of built-in and recorded poses; tapping one shows details and a "Try this pose" action. */
public class PoseLibraryActivity extends AppCompatActivity {

    /** Boolean extra: return the chosen pose to the caller instead of opening the camera. */
    public static final String EXTRA_PICK_MODE = "com.s23cp5054.posewithme.extra.PICK_MODE";
    /** Result extra holding the id of the pose picked in pick mode. */
    public static final String EXTRA_PICKED_POSE_ID =
            "com.s23cp5054.posewithme.extra.PICKED_POSE_ID";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PoseAdapter adapter = new PoseAdapter();
    private TextView countText;
    private View emptyText;
    private boolean pickMode;

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
        recordButton.setVisibility(pickMode ? View.GONE : View.VISIBLE);
        countText = findViewById(R.id.poseCountText);
        emptyText = findViewById(R.id.emptyText);
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
        ioExecutor.execute(() -> {
            List<PoseTemplate> poses = PoseRepository.get(this).getAll();
            mainHandler.post(() -> showPoses(poses));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void showPoses(List<PoseTemplate> poses) {
        adapter.setPoses(poses);
        countText.setText(getResources().getQuantityString(R.plurals.library_count,
                poses.size(), poses.size()));
        emptyText.setVisibility(poses.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showDetails(PoseTemplate pose) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.sheet_pose_detail, null);
        ((PoseThumbnailView) content.findViewById(R.id.detailThumbnail)).setPose(pose.pose);
        ((TextView) content.findViewById(R.id.detailName)).setText(pose.name);
        ((TextView) content.findViewById(R.id.detailMeta)).setText(
                PoseLabels.longMeta(this, pose));
        TextView description = content.findViewById(R.id.detailDescription);
        description.setText(pose.description);
        description.setVisibility(pose.description.isEmpty() ? View.GONE : View.VISIBLE);

        StringBuilder steps = new StringBuilder();
        for (String step : pose.instructions) {
            if (steps.length() > 0) {
                steps.append('\n');
            }
            steps.append("•  ").append(step);
        }
        TextView instructions = content.findViewById(R.id.detailInstructions);
        instructions.setText(steps);
        instructions.setVisibility(steps.length() == 0 ? View.GONE : View.VISIBLE);

        TextView attribution = content.findViewById(R.id.detailAttribution);
        attribution.setText(attributionFor(pose));
        attribution.setMovementMethod(LinkMovementMethod.getInstance());

        content.findViewById(R.id.tryPoseButton).setOnClickListener(v -> {
            sheet.dismiss();
            if (pickMode) {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_PICKED_POSE_ID, pose.id));
                finish();
            } else {
                startActivity(new Intent(this, CameraActivity.class)
                        .putExtra(CameraActivity.EXTRA_POSE_ID, pose.id));
            }
        });
        sheet.setContentView(content);
        sheet.show();
    }

    private CharSequence attributionFor(PoseTemplate pose) {
        PoseTemplate.Source s = pose.source;
        if ("web".equals(s.type)) {
            String title = s.url != null
                    ? "<a href=\"" + Html.escapeHtml(s.url) + "\">" + Html.escapeHtml(
                    s.title != null ? s.title : "photo") + "</a>"
                    : Html.escapeHtml(s.title != null ? s.title : "photo");
            String license = s.licenseUrl != null
                    ? "<a href=\"" + Html.escapeHtml(s.licenseUrl) + "\">"
                    + Html.escapeHtml(s.license) + "</a>"
                    : Html.escapeHtml(s.license != null ? s.license : "");
            String html = getString(R.string.attribution_web, title,
                    Html.escapeHtml(s.author != null ? s.author : "unknown"), license);
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        }
        return getString("user".equals(s.type)
                ? R.string.attribution_user : R.string.attribution_recorded);
    }

    private final class PoseAdapter extends RecyclerView.Adapter<PoseViewHolder> {
        private final List<PoseTemplate> poses = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged")
        void setPoses(List<PoseTemplate> newPoses) {
            poses.clear();
            poses.addAll(newPoses);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PoseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PoseViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pose_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PoseViewHolder holder, int position) {
            PoseTemplate pose = poses.get(position);
            holder.thumbnail.setPose(pose.pose);
            holder.name.setText(pose.name);
            holder.meta.setText(PoseLabels.shortMeta(holder.itemView.getContext(), pose));
            holder.badge.setVisibility("user".equals(pose.source.type) ? View.VISIBLE
                    : View.GONE);
            holder.itemView.setOnClickListener(v -> showDetails(pose));
        }

        @Override
        public int getItemCount() {
            return poses.size();
        }
    }

    private static final class PoseViewHolder extends RecyclerView.ViewHolder {
        final PoseThumbnailView thumbnail;
        final TextView name;
        final TextView meta;
        final View badge;

        PoseViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.poseThumbnail);
            name = itemView.findViewById(R.id.poseName);
            meta = itemView.findViewById(R.id.poseMeta);
            badge = itemView.findViewById(R.id.sourceBadge);
        }
    }
}
