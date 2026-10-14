package com.s23cp5054.posewithme.ui.gallery;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.model.Photo;
import com.s23cp5054.posewithme.data.repository.PhotoRepository;
import com.s23cp5054.posewithme.storage.PhotoStorage;
import com.s23cp5054.posewithme.ui.camera.CameraActivity;
import com.s23cp5054.posewithme.utils.ImageLoader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows one photo. In review mode (straight after capture) it offers Retake / Share / Keep;
 * opened from the gallery it offers Delete / Share / Try this pose.
 */
public class PhotoActivity extends AppCompatActivity {

    public static final String EXTRA_PHOTO_ID = "com.s23cp5054.posewithme.extra.PHOTO_ID";
    public static final String EXTRA_REVIEW = "com.s23cp5054.posewithme.extra.REVIEW";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PhotoRepository photos;
    private Photo photo;
    private boolean review;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_photo);
        View topBar = findViewById(R.id.photoTopBar);
        View panel = findViewById(R.id.photoPanel);
        int panelBottom = panel.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.photoRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            topBar.setPadding(topBar.getPaddingLeft(), bars.top, topBar.getPaddingRight(), 0);
            panel.setPadding(panel.getPaddingLeft(), panel.getPaddingTop(),
                    panel.getPaddingRight(), panelBottom + bars.bottom);
            return insets;
        });

        photos = new PhotoRepository(this);
        review = getIntent().getBooleanExtra(EXTRA_REVIEW, false);
        long id = getIntent().getLongExtra(EXTRA_PHOTO_ID, -1);
        findViewById(R.id.photoBack).setOnClickListener(v -> finish());

        ioExecutor.execute(() -> {
            Photo loaded = photos.get(id);
            int size = Math.max(getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            Bitmap bitmap = loaded != null ? ImageLoader.get(this).decode(loaded.uri, size) : null;
            mainHandler.post(() -> {
                if (loaded == null || isDestroyed()) {
                    finish();
                    return;
                }
                show(loaded, bitmap);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void show(Photo p, Bitmap bitmap) {
        photo = p;
        ((ImageView) findViewById(R.id.photoImage)).setImageBitmap(bitmap);
        ((TextView) findViewById(R.id.photoDate)).setText(DateUtils.formatDateTime(this,
                p.capturedAt, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_ABBREV_MONTH));
        TextView poseName = findViewById(R.id.photoPoseName);
        poseName.setText(p.poseName != null ? p.poseName : getString(R.string.photo_free_pose));
        TextView score = findViewById(R.id.photoScore);
        if (p.score != null) {
            int percent = Math.round(p.score * 100f);
            score.setText(getString(R.string.match_percent, percent));
            score.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                    p.score >= 0.8f ? R.color.match_good : p.score >= 0.5f
                            ? R.color.match_adjust : R.color.match_off)));
        } else {
            score.setVisibility(View.GONE);
        }

        findViewById(R.id.photoShare).setOnClickListener(v -> startActivity(
                PhotoStorage.shareIntent(p.uri, getString(R.string.photo_share))));
        MaterialButton secondary = findViewById(R.id.photoSecondary);
        MaterialButton primary = findViewById(R.id.photoPrimary);
        if (review) {
            secondary.setText(R.string.photo_retake);
            secondary.setOnClickListener(v -> deleteAndFinish());
            primary.setText(R.string.photo_keep);
            primary.setOnClickListener(v -> finish());
        } else {
            secondary.setText(R.string.photo_delete);
            secondary.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.photo_delete_title)
                    .setMessage(R.string.photo_delete_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.photo_delete, (d, w) -> deleteAndFinish())
                    .show());
            primary.setText(R.string.detail_try_pose);
            primary.setEnabled(p.poseId != null);
            primary.setOnClickListener(v -> {
                startActivity(new Intent(this, CameraActivity.class)
                        .putExtra(CameraActivity.EXTRA_POSE_ID, p.poseId));
                finish();
            });
        }
    }

    private void deleteAndFinish() {
        Photo p = photo;
        ioExecutor.execute(() -> {
            photos.delete(p);
            mainHandler.post(this::finish);
        });
    }
}
