package com.s23cp5054.posewithme.location;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.s23cp5054.posewithme.pose.recommendation.GeoUtils;
import com.s23cp5054.posewithme.pose.recommendation.Scene;
import com.s23cp5054.posewithme.pose.recommendation.SceneMapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Finds the most photogenic named place near a point using OpenStreetMap's Overpass API. No API
 * key; only the coordinates are sent, and only when the user opens nearby suggestions.
 */
public final class PlaceFinder {

    public static final class Place {
        public final String name;
        public final Scene scene;
        public final double distanceMeters;

        Place(String name, Scene scene, double distanceMeters) {
            this.name = name;
            this.scene = scene;
            this.distanceMeters = distanceMeters;
        }
    }

    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";
    private static final String USER_AGENT = "PoseWithMe/1.0 (student Android project)";
    private static final int RADIUS_METERS = 250;
    private static final int TIMEOUT_MS = 12_000;
    /** Each priority step is worth this many metres when choosing between places. */
    private static final double PRIORITY_STEP_METERS = 60;

    /** Returns the best nearby place, or null when nothing photogenic is mapped nearby. */
    @WorkerThread
    @Nullable
    public Place findNearby(double lat, double lon) throws IOException {
        String around = String.format(Locale.US, "(around:%d,%.6f,%.6f)", RADIUS_METERS, lat, lon);
        String query = "[out:json][timeout:10];("
                + "nwr" + around + "[\"tourism\"~\"attraction|viewpoint|museum|artwork|zoo|theme_park\"][name];"
                + "nwr" + around + "[\"historic\"][name];"
                + "nwr" + around + "[\"leisure\"~\"park|garden|nature_reserve|beach_resort\"][name];"
                + "nwr" + around + "[\"natural\"~\"beach|water|peak|cliff|bay\"][name];"
                + "nwr" + around + "[\"amenity\"~\"cafe|restaurant|place_of_worship|ice_cream\"][name];"
                + ");out center tags 40;";

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(("data=" + URLEncoder.encode(query, "UTF-8"))
                        .getBytes(StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Overpass returned " + connection.getResponseCode());
            }
            String body;
            try (InputStream in = connection.getInputStream()) {
                body = readAll(in);
            }
            return pickBest(body, lat, lon);
        } catch (JSONException e) {
            throw new IOException("Bad Overpass response", e);
        } finally {
            connection.disconnect();
        }
    }

    @Nullable
    static Place pickBest(String json, double lat, double lon) throws JSONException {
        JSONArray elements = new JSONObject(json).optJSONArray("elements");
        if (elements == null) {
            return null;
        }
        Place best = null;
        double bestCost = Double.MAX_VALUE;
        for (int i = 0; i < elements.length(); i++) {
            JSONObject e = elements.getJSONObject(i);
            JSONObject tagsJson = e.optJSONObject("tags");
            if (tagsJson == null || !tagsJson.has("name")) {
                continue;
            }
            Map<String, String> tags = new HashMap<>();
            for (Iterator<String> it = tagsJson.keys(); it.hasNext(); ) {
                String key = it.next();
                tags.put(key, tagsJson.optString(key));
            }
            Scene scene = SceneMapper.sceneFor(tags);
            if (scene == null) {
                continue;
            }
            JSONObject center = e.optJSONObject("center");
            double pLat = center != null ? center.optDouble("lat") : e.optDouble("lat");
            double pLon = center != null ? center.optDouble("lon") : e.optDouble("lon");
            if (Double.isNaN(pLat) || Double.isNaN(pLon)) {
                continue;
            }
            double distance = GeoUtils.distanceMeters(lat, lon, pLat, pLon);
            double cost = distance + SceneMapper.priority(scene) * PRIORITY_STEP_METERS;
            if (cost < bestCost) {
                bestCost = cost;
                best = new Place(tags.get("name"), scene, distance);
            }
        }
        return best;
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
