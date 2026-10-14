package com.s23cp5054.posewithme;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.s23cp5054.posewithme.data.repository.FavoritesRepository;
import com.s23cp5054.posewithme.data.repository.PhotoRepository;
import com.s23cp5054.posewithme.data.repository.PoseRepository;
import com.s23cp5054.posewithme.data.settings.AppSettings;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.pose.recommendation.PoseRecommender;
import com.s23cp5054.posewithme.pose.recommendation.Scene;
import com.s23cp5054.posewithme.ui.camera.CameraActivity;
import com.s23cp5054.posewithme.ui.gallery.GalleryActivity;
import com.s23cp5054.posewithme.ui.nearby.NearbyActivity;
import com.s23cp5054.posewithme.ui.poses.PoseCardAdapter;
import com.s23cp5054.posewithme.ui.poses.PoseDetailSheet;
import com.s23cp5054.posewithme.ui.poses.PoseLibraryActivity;
import com.s23cp5054.posewithme.ui.settings.SettingsActivity;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PoseDetailSheet.Listener {

    private static final int HOME_RECOMMENDATIONS = 6;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PoseCardAdapter recommendedAdapter =
            new PoseCardAdapter(this::showDetails, R.layout.item_pose_mini);
    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        settings = new AppSettings(this);

        View root = findViewById(R.id.main);
        int basePaddingTop = root.getPaddingTop();
        int basePaddingBottom = root.getPaddingBottom();
        int basePaddingLeft = root.getPaddingLeft();
        int basePaddingRight = root.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(basePaddingLeft + bars.left, basePaddingTop + bars.top,
                    basePaddingRight + bars.right, basePaddingBottom + bars.bottom);
            return insets;
        });

        ((TextView) findViewById(R.id.greetingText)).setText(greetingForNow());

        findViewById(R.id.heroCard).setOnClickListener(
                v -> startActivity(new Intent(this, CameraActivity.class)));
        findViewById(R.id.recordCard).setOnClickListener(v -> startActivity(
                new Intent(this, CameraActivity.class)
                        .putExtra(CameraActivity.EXTRA_RECORD_MODE, true)));
        findViewById(R.id.settingsButton).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.recommendedMore).setOnClickListener(
                v -> startActivity(new Intent(this, NearbyActivity.class)));
        ((RecyclerView) findViewById(R.id.recommendedList)).setAdapter(recommendedAdapter);

        setUpTile(R.id.tileBrowse, R.drawable.ic_poses, R.color.brand_primary,
                R.string.home_browse_title, R.string.home_browse_subtitle,
                v -> startActivity(new Intent(this, PoseLibraryActivity.class)));
        setUpTile(R.id.tileNearby, R.drawable.ic_explore, R.color.brand_teal,
                R.string.home_nearby_title, R.string.home_nearby_subtitle,
                v -> startActivity(new Intent(this, NearbyActivity.class)));
        setUpTile(R.id.tilePhotos, R.drawable.ic_gallery, R.color.brand_sky,
                R.string.home_photos_title, R.string.home_photos_subtitle,
                v -> startActivity(new Intent(this, GalleryActivity.class)));
        setUpTile(R.id.tileFavorites, R.drawable.ic_favorite, R.color.brand_pink,
                R.string.home_favorites_title, R.string.home_favorites_subtitle,
                v -> startActivity(new Intent(this, PoseLibraryActivity.class)
                        .putExtra(PoseLibraryActivity.EXTRA_FAVORITES_ONLY, true)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecommendations();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
    }

    /** Generic picks: the last scene seen in Nearby, preferred styles, favourites, usage. */
    private void loadRecommendations() {
        Scene scene = Scene.fromKey(settings.getLastScene());
        Set<String> preferred = settings.getPreferredCategories();
        ioExecutor.execute(() -> {
            List<PoseTemplate> poses = PoseRepository.get(this).getAll();
            Set<String> favorites = new FavoritesRepository(this).getAll();
            Map<String, Integer> uses = new PhotoRepository(this).countByPose();
            List<PoseTemplate> ranked = new PoseRecommender().rank(poses, scene, preferred,
                    favorites, uses);
            List<PoseTemplate> top = ranked.subList(0, Math.min(HOME_RECOMMENDATIONS,
                    ranked.size()));
            mainHandler.post(() -> {
                recommendedAdapter.setPoses(top);
                ((TextView) findViewById(R.id.recommendedSubtitle)).setText(scene != null
                        ? getString(R.string.home_recommended_for,
                        NearbyActivity.sceneLabel(this, scene))
                        : getString(R.string.home_recommended_subtitle));
            });
        });
    }

    private void showDetails(PoseTemplate pose) {
        PoseDetailSheet.show(this, pose, ioExecutor, this);
    }

    @Override
    public void onTryPose(PoseTemplate pose) {
        startActivity(new Intent(this, CameraActivity.class)
                .putExtra(CameraActivity.EXTRA_POSE_ID, pose.id));
    }

    @Override
    public void onPosesChanged() {
        loadRecommendations();
    }

    /** A null {@code onClick} marks the feature as not built yet. */
    private void setUpTile(int tileId, @DrawableRes int icon, @ColorRes int tint,
                           @StringRes int title, @StringRes int subtitle,
                           @Nullable View.OnClickListener onClick) {
        View tile = findViewById(tileId);
        ((ImageView) tile.findViewById(R.id.tileIcon)).setImageResource(icon);
        tile.findViewById(R.id.tileIconBg).setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, tint)));
        ((TextView) tile.findViewById(R.id.tileTitle)).setText(title);
        ((TextView) tile.findViewById(R.id.tileSubtitle)).setText(subtitle);
        tile.findViewById(R.id.tileBadge).setVisibility(onClick == null ? View.VISIBLE
                : View.GONE);
        tile.setOnClickListener(onClick != null ? onClick : v -> showComingSoon(title));
    }

    private void showComingSoon(@StringRes int feature) {
        Snackbar.make(findViewById(R.id.main),
                getString(R.string.coming_soon, getString(feature)), Snackbar.LENGTH_SHORT).show();
    }

    private String greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return getString(R.string.greeting_morning);
        } else if (hour < 17) {
            return getString(R.string.greeting_afternoon);
        }
        return getString(R.string.greeting_evening);
    }
}
