package com.s23cp5054.posewithme.ui.nearby;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.repository.FavoritesRepository;
import com.s23cp5054.posewithme.data.repository.PhotoRepository;
import com.s23cp5054.posewithme.data.repository.PlacesRepository;
import com.s23cp5054.posewithme.data.repository.PoseRepository;
import com.s23cp5054.posewithme.data.settings.AppSettings;
import com.s23cp5054.posewithme.location.LocationHelper;
import com.s23cp5054.posewithme.location.PlaceFinder;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.pose.recommendation.CuratedPlace;
import com.s23cp5054.posewithme.pose.recommendation.PoseRecommender;
import com.s23cp5054.posewithme.pose.recommendation.Scene;
import com.s23cp5054.posewithme.ui.camera.CameraActivity;
import com.s23cp5054.posewithme.ui.poses.PoseCardAdapter;
import com.s23cp5054.posewithme.ui.poses.PoseDetailSheet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Location-aware suggestions: finds where the user is (curated landmark list offline, then
 * OpenStreetMap when online), maps it to a scene and ranks the pose library for it. Every
 * failure path falls back to a manual scene picker.
 */
public class NearbyActivity extends AppCompatActivity implements PoseDetailSheet.Listener {

    private static final String TAG = "NearbyActivity";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PoseCardAdapter curatedAdapter = new PoseCardAdapter(this::showDetails);
    private final PoseCardAdapter recommendedAdapter = new PoseCardAdapter(this::showDetails);
    private final PoseRecommender recommender = new PoseRecommender();
    private final Map<Scene, Chip> sceneChips = new EnumMap<>(Scene.class);

    private AppSettings settings;
    private LocationHelper locationHelper;
    private TextView placeTitle;
    private TextView placeSubtitle;
    private View placeProgress;
    private View useLocationButton;
    private View curatedSection;
    private TextView curatedTitle;
    private TextView recommendedTitle;

    private List<PoseTemplate> allPoses = Collections.emptyList();
    private Set<String> favorites = Collections.emptySet();
    private Map<String, Integer> useCounts = Collections.emptyMap();
    @Nullable private Scene scene;
    @Nullable private CuratedPlace curatedPlace;
    private boolean dataLoaded;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (locationHelper.hasPermission()) {
                            locate();
                        } else {
                            showPlaceMessage(R.string.nearby_permission_denied, true);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_nearby);
        View header = findViewById(R.id.nearbyHeader);
        View scroll = findViewById(R.id.nearbyScroll);
        int headerTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nearbyRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            scroll.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        findViewById(R.id.nearbyBack).setOnClickListener(v -> finish());

        settings = new AppSettings(this);
        locationHelper = new LocationHelper(this);
        placeTitle = findViewById(R.id.placeTitle);
        placeSubtitle = findViewById(R.id.placeSubtitle);
        placeProgress = findViewById(R.id.placeProgress);
        useLocationButton = findViewById(R.id.useLocationButton);
        curatedSection = findViewById(R.id.curatedSection);
        curatedTitle = findViewById(R.id.curatedTitle);
        recommendedTitle = findViewById(R.id.recommendedTitle);
        useLocationButton.setOnClickListener(v -> requestLocation());

        setUpGrid(R.id.curatedGrid, curatedAdapter);
        setUpGrid(R.id.recommendedGrid, recommendedAdapter);
        buildSceneChips();

        scene = Scene.fromKey(settings.getLastScene());
        loadData();
        if (locationHelper.hasPermission()) {
            locate();
        } else {
            showPlaceMessage(R.string.nearby_permission_denied, true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void setUpGrid(int id, PoseCardAdapter adapter) {
        RecyclerView grid = findViewById(id);
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setAdapter(adapter);
    }

    private void buildSceneChips() {
        ChipGroup group = findViewById(R.id.sceneChips);
        for (Scene s : Scene.values()) {
            Chip chip = new Chip(this);
            chip.setCheckable(true);
            chip.setText(sceneLabel(this, s));
            chip.setOnClickListener(v -> {
                boolean selected = ((Chip) v).isChecked();
                scene = selected ? s : null;
                settings.setLastScene(scene != null ? scene.key : null);
                render();
            });
            group.addView(chip);
            sceneChips.put(s, chip);
        }
    }

    private void loadData() {
        ioExecutor.execute(() -> {
            List<PoseTemplate> poses = PoseRepository.get(this).getAll();
            Set<String> favs = new FavoritesRepository(this).getAll();
            Map<String, Integer> uses = new PhotoRepository(this).countByPose();
            mainHandler.post(() -> {
                allPoses = poses;
                favorites = favs;
                useCounts = uses;
                dataLoaded = true;
                render();
            });
        });
    }

    private void requestLocation() {
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    private void locate() {
        useLocationButton.setVisibility(View.GONE);
        placeProgress.setVisibility(View.VISIBLE);
        placeTitle.setText(R.string.nearby_locating);
        placeSubtitle.setVisibility(View.GONE);
        if (!locationHelper.isEnabled()) {
            showPlaceMessage(R.string.nearby_no_location, true);
            return;
        }
        locationHelper.getCurrentLocation(location -> {
            if (isDestroyed()) {
                return;
            }
            if (location == null) {
                showPlaceMessage(R.string.nearby_no_location, true);
                return;
            }
            lookUpPlace(location);
        });
    }

    private void lookUpPlace(Location location) {
        placeTitle.setText(R.string.nearby_looking_up);
        boolean online = isOnline();
        ioExecutor.execute(() -> {
            // The curated list is bundled, so landmark suggestions work offline too.
            CuratedPlace curated = CuratedPlace.find(PlacesRepository.get(this),
                    location.getLatitude(), location.getLongitude());
            PlaceFinder.Place place = null;
            boolean lookupFailed = false;
            if (curated == null && online) {
                try {
                    place = new PlaceFinder().findNearby(location.getLatitude(),
                            location.getLongitude());
                } catch (IOException e) {
                    Log.w(TAG, "Place lookup failed", e);
                    lookupFailed = true;
                }
            }
            PlaceFinder.Place found = place;
            boolean failed = lookupFailed || (curated == null && !online);
            mainHandler.post(() -> onPlaceResult(curated, found, failed));
        });
    }

    private void onPlaceResult(@Nullable CuratedPlace curated, @Nullable PlaceFinder.Place place,
                               boolean lookupFailed) {
        placeProgress.setVisibility(View.GONE);
        curatedPlace = curated;
        if (curated != null) {
            placeTitle.setText(getString(R.string.nearby_found, curated.name));
            setDetectedScene(curated.scene);
        } else if (place != null) {
            placeTitle.setText(getString(R.string.nearby_found, place.name));
            setDetectedScene(place.scene);
        } else {
            showPlaceMessage(lookupFailed ? R.string.nearby_offline : R.string.nearby_no_place,
                    false);
            return;
        }
        render();
    }

    private void setDetectedScene(Scene detected) {
        scene = detected;
        settings.setLastScene(detected.key);
        placeSubtitle.setText(getString(R.string.nearby_scene_detected,
                sceneLabel(this, detected)));
        placeSubtitle.setVisibility(View.VISIBLE);
    }

    private void showPlaceMessage(int message, boolean offerLocationButton) {
        placeProgress.setVisibility(View.GONE);
        placeTitle.setText(message);
        placeSubtitle.setVisibility(View.GONE);
        useLocationButton.setVisibility(offerLocationButton ? View.VISIBLE : View.GONE);
        render();
    }

    private void render() {
        for (Map.Entry<Scene, Chip> entry : sceneChips.entrySet()) {
            entry.getValue().setChecked(entry.getKey() == scene);
        }
        if (!dataLoaded) {
            return;
        }
        List<PoseTemplate> curatedPoses = new ArrayList<>();
        if (curatedPlace != null) {
            for (String id : curatedPlace.poseIds) {
                for (PoseTemplate pose : allPoses) {
                    if (pose.id.equals(id)) {
                        curatedPoses.add(pose);
                    }
                }
            }
            curatedTitle.setText(getString(R.string.nearby_curated_title, curatedPlace.name));
        }
        curatedSection.setVisibility(curatedPoses.isEmpty() ? View.GONE : View.VISIBLE);
        curatedAdapter.setPoses(curatedPoses);

        List<PoseTemplate> ranked = recommender.rank(allPoses, scene,
                settings.getPreferredCategories(), favorites, useCounts);
        recommendedAdapter.setPoses(ranked.subList(0, Math.min(8, ranked.size())));
        recommendedTitle.setText(scene != null
                ? getString(R.string.nearby_recommended_for_scene, sceneLabel(this, scene))
                : getString(R.string.nearby_recommended_title));
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = network != null ? cm.getNetworkCapabilities(network) : null;
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
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
        loadData();
    }

    public static String sceneLabel(Context context, Scene scene) {
        switch (scene) {
            case PARK: return context.getString(R.string.scene_park);
            case BEACH: return context.getString(R.string.scene_beach);
            case MONUMENT: return context.getString(R.string.scene_monument);
            case CAFE: return context.getString(R.string.scene_cafe);
            case SCENIC: return context.getString(R.string.scene_scenic);
            case INDOOR: return context.getString(R.string.scene_indoor);
            default: return context.getString(R.string.scene_street);
        }
    }
}
