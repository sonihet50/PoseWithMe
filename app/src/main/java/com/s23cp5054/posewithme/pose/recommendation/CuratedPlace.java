package com.s23cp5054.posewithme.pose.recommendation;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A landmark with poses hand-picked for it (assets/places.json). These are editorial
 * suggestions, not statistics: nothing here claims a pose is popular with visitors.
 */
public final class CuratedPlace {

    public final String id;
    public final String name;
    public final double lat;
    public final double lon;
    public final double radiusMeters;
    public final Scene scene;
    public final List<String> poseIds;

    public CuratedPlace(String id, String name, double lat, double lon, double radiusMeters,
                        Scene scene, List<String> poseIds) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.radiusMeters = radiusMeters;
        this.scene = scene;
        this.poseIds = Collections.unmodifiableList(new ArrayList<>(poseIds));
    }

    public static List<CuratedPlace> parseList(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<CuratedPlace> places = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            JSONArray ids = o.getJSONArray("poses");
            List<String> poseIds = new ArrayList<>();
            for (int j = 0; j < ids.length(); j++) {
                poseIds.add(ids.getString(j));
            }
            Scene scene = Scene.fromKey(o.optString("scene"));
            places.add(new CuratedPlace(o.getString("id"), o.getString("name"),
                    o.getDouble("lat"), o.getDouble("lon"), o.getDouble("radiusMeters"),
                    scene != null ? scene : Scene.MONUMENT, poseIds));
        }
        return places;
    }

    /** The closest place whose radius contains the point, or null. */
    @Nullable
    public static CuratedPlace find(List<CuratedPlace> places, double lat, double lon) {
        CuratedPlace best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CuratedPlace place : places) {
            double d = GeoUtils.distanceMeters(lat, lon, place.lat, place.lon);
            if (d <= place.radiusMeters && d < bestDistance) {
                best = place;
                bestDistance = d;
            }
        }
        return best;
    }
}
