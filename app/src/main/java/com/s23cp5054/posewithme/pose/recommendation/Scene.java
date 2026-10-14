package com.s23cp5054.posewithme.pose.recommendation;

import androidx.annotation.Nullable;

/** Kinds of surroundings poses are recommended for. Keys match "scenes" in pose files. */
public enum Scene {
    STREET("street"),
    PARK("park"),
    BEACH("beach"),
    MONUMENT("monument"),
    CAFE("cafe"),
    SCENIC("scenic"),
    INDOOR("indoor");

    public final String key;

    Scene(String key) {
        this.key = key;
    }

    @Nullable
    public static Scene fromKey(@Nullable String key) {
        for (Scene scene : values()) {
            if (scene.key.equals(key)) {
                return scene;
            }
        }
        return null;
    }
}
