package com.s23cp5054.posewithme.pose.recommendation;

import androidx.annotation.Nullable;

import java.util.Map;

/**
 * Curated mapping from OpenStreetMap tags to a {@link Scene}. Order matters: a monument in a
 * park is a monument, a café on a beach is a beach.
 */
public final class SceneMapper {

    @Nullable
    public static Scene sceneFor(Map<String, String> tags) {
        String tourism = tags.get("tourism");
        String historic = tags.get("historic");
        String leisure = tags.get("leisure");
        String natural = tags.get("natural");
        String amenity = tags.get("amenity");

        if ("beach".equals(natural) || "beach_resort".equals(leisure)) {
            return Scene.BEACH;
        }
        if (historic != null || is(tourism, "attraction", "museum", "artwork")
                || "place_of_worship".equals(amenity)) {
            return Scene.MONUMENT;
        }
        if (is(tourism, "viewpoint") || is(natural, "peak", "cliff", "water", "wood", "bay")
                || is(leisure, "nature_reserve")) {
            return Scene.SCENIC;
        }
        if (is(leisure, "park", "garden") || is(tourism, "zoo", "theme_park")) {
            return Scene.PARK;
        }
        if (is(amenity, "cafe", "restaurant", "fast_food", "ice_cream", "bar")) {
            return Scene.CAFE;
        }
        return null;
    }

    /** Lower is more photogenic: used to prefer a landmark over a café next to it. */
    public static int priority(Scene scene) {
        switch (scene) {
            case MONUMENT: return 0;
            case BEACH: return 1;
            case SCENIC: return 2;
            case PARK: return 3;
            case CAFE: return 4;
            default: return 5;
        }
    }

    private static boolean is(@Nullable String value, String... options) {
        if (value == null) {
            return false;
        }
        for (String option : options) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private SceneMapper() {
    }
}
