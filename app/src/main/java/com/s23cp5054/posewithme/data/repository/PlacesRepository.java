package com.s23cp5054.posewithme.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.s23cp5054.posewithme.pose.recommendation.CuratedPlace;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** Hand-picked landmarks and their suggested poses, bundled in assets/places.json. */
public final class PlacesRepository {

    private static final String TAG = "PlacesRepository";
    private static volatile List<CuratedPlace> cache;

    @WorkerThread
    public static List<CuratedPlace> get(Context context) {
        List<CuratedPlace> places = cache;
        if (places == null) {
            try (InputStream in = context.getAssets().open("places.json")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                places = Collections.unmodifiableList(CuratedPlace.parseList(
                        out.toString(StandardCharsets.UTF_8.name())));
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Could not load curated places", e);
                places = Collections.emptyList();
            }
            cache = places;
        }
        return places;
    }

    private PlacesRepository() {
    }
}
