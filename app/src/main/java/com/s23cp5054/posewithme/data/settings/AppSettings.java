package com.s23cp5054.posewithme.data.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** User preferences, stored in SharedPreferences. Cheap to create; reads are not cached. */
public final class AppSettings {

    private static final String FILE = "settings";
    private static final String AUTO_CAPTURE = "auto_capture";
    private static final String AUTO_CAPTURE_THRESHOLD = "auto_capture_threshold";
    private static final String COUNTDOWN_SECONDS = "countdown_seconds";
    private static final String SHOW_HINTS = "show_hints";
    private static final String SHOW_GHOST = "show_ghost";
    private static final String SHOW_SKELETON = "show_skeleton";
    private static final String MIRROR_SELFIES = "mirror_selfies";
    private static final String PREFERRED_CATEGORIES = "preferred_categories";
    private static final String LAST_SCENE = "last_scene";

    public static final int[] COUNTDOWN_CHOICES = {0, 3, 5, 10};

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isAutoCapture() {
        return prefs.getBoolean(AUTO_CAPTURE, false);
    }

    public void setAutoCapture(boolean value) {
        prefs.edit().putBoolean(AUTO_CAPTURE, value).apply();
    }

    /** Match score in [0, 1] that must be held to take a photo automatically. */
    public float getAutoCaptureThreshold() {
        return prefs.getFloat(AUTO_CAPTURE_THRESHOLD, 0.85f);
    }

    public void setAutoCaptureThreshold(float value) {
        prefs.edit().putFloat(AUTO_CAPTURE_THRESHOLD, value).apply();
    }

    /** Self-timer before a photo is taken; 0 means immediate. */
    public int getCountdownSeconds() {
        return prefs.getInt(COUNTDOWN_SECONDS, 3);
    }

    public void setCountdownSeconds(int value) {
        prefs.edit().putInt(COUNTDOWN_SECONDS, value).apply();
    }

    public boolean isShowHints() {
        return prefs.getBoolean(SHOW_HINTS, true);
    }

    public void setShowHints(boolean value) {
        prefs.edit().putBoolean(SHOW_HINTS, value).apply();
    }

    public boolean isShowGhost() {
        return prefs.getBoolean(SHOW_GHOST, true);
    }

    public void setShowGhost(boolean value) {
        prefs.edit().putBoolean(SHOW_GHOST, value).apply();
    }

    public boolean isShowSkeleton() {
        return prefs.getBoolean(SHOW_SKELETON, true);
    }

    public void setShowSkeleton(boolean value) {
        prefs.edit().putBoolean(SHOW_SKELETON, value).apply();
    }

    /** Save front-camera photos mirrored, the way the preview shows them. */
    public boolean isMirrorSelfies() {
        return prefs.getBoolean(MIRROR_SELFIES, true);
    }

    public void setMirrorSelfies(boolean value) {
        prefs.edit().putBoolean(MIRROR_SELFIES, value).apply();
    }

    public Set<String> getPreferredCategories() {
        return Collections.unmodifiableSet(
                new HashSet<>(prefs.getStringSet(PREFERRED_CATEGORIES, Collections.emptySet())));
    }

    public void setPreferredCategories(Set<String> categories) {
        prefs.edit().putStringSet(PREFERRED_CATEGORIES, new HashSet<>(categories)).apply();
    }

    /** Scene picked or detected most recently, used when location is unavailable. */
    @Nullable
    public String getLastScene() {
        return prefs.getString(LAST_SCENE, null);
    }

    public void setLastScene(@Nullable String scene) {
        prefs.edit().putString(LAST_SCENE, scene).apply();
    }
}
