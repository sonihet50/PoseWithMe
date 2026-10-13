package com.s23cp5054.posewithme.data.repository;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.s23cp5054.posewithme.pose.model.PoseTemplate;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Loads built-in poses from assets/poses/ and the user's recorded poses from the app's private
 * files dir, and saves new recordings. Files are small JSON documents, one pose per file.
 */
public final class PoseRepository {

    private static final String TAG = "PoseRepository";
    private static final String ASSET_DIR = "poses";
    private static final String USER_DIR = "poses";

    private static volatile PoseRepository instance;

    private final Context appContext;
    @Nullable
    private volatile List<PoseTemplate> cache;

    public static PoseRepository get(Context context) {
        if (instance == null) {
            synchronized (PoseRepository.class) {
                if (instance == null) {
                    instance = new PoseRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private PoseRepository(Context appContext) {
        this.appContext = appContext;
    }

    /** User recordings first (newest first), then built-in poses in file-name order. */
    @WorkerThread
    public List<PoseTemplate> getAll() {
        List<PoseTemplate> poses = cache;
        if (poses == null) {
            List<PoseTemplate> loaded = new ArrayList<>(loadUserPoses());
            loaded.addAll(loadBuiltInPoses());
            poses = Collections.unmodifiableList(loaded);
            cache = poses;
        }
        return poses;
    }

    @WorkerThread
    @Nullable
    public PoseTemplate findById(String id) {
        for (PoseTemplate pose : getAll()) {
            if (pose.id.equals(id)) {
                return pose;
            }
        }
        return null;
    }

    @WorkerThread
    public void saveUserPose(PoseTemplate pose) throws IOException, JSONException {
        File dir = new File(appContext.getFilesDir(), USER_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
        File file = new File(dir, pose.id + ".json");
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(pose.toJson().getBytes(StandardCharsets.UTF_8));
        }
        cache = null;
    }

    private List<PoseTemplate> loadBuiltInPoses() {
        List<PoseTemplate> poses = new ArrayList<>();
        AssetManager assets = appContext.getAssets();
        try {
            String[] names = assets.list(ASSET_DIR);
            if (names == null) {
                return poses;
            }
            Arrays.sort(names);
            for (String name : names) {
                if (!name.endsWith(".json")) {
                    continue;
                }
                try (InputStream in = assets.open(ASSET_DIR + "/" + name)) {
                    poses.add(PoseTemplate.fromJson(readAll(in)));
                } catch (IOException | JSONException e) {
                    Log.w(TAG, "Skipping bad built-in pose " + name, e);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not list built-in poses", e);
        }
        return poses;
    }

    private List<PoseTemplate> loadUserPoses() {
        List<PoseTemplate> poses = new ArrayList<>();
        File[] files = new File(appContext.getFilesDir(), USER_DIR).listFiles(
                (dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return poses;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : files) {
            try (InputStream in = new FileInputStream(file)) {
                poses.add(PoseTemplate.fromJson(readAll(in)));
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Skipping bad user pose " + file.getName(), e);
            }
        }
        return poses;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
