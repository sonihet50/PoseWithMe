package com.s23cp5054.posewithme.pose.recommendation;

import androidx.annotation.Nullable;

import com.s23cp5054.posewithme.pose.model.PoseTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ranks the pose library with simple, explainable rules: poses tagged for the current scene
 * first, then nudged by the user's preferred styles, favourites and how often they've used a
 * pose on this phone. Deliberately not a learned model.
 */
public final class PoseRecommender {

    static final float SCENE_MATCH = 3f;
    static final float CATEGORY_MATCHES_SCENE = 1f;
    static final float PREFERRED_CATEGORY = 1.5f;
    static final float FAVORITE = 1f;
    static final float PER_USE = 0.25f;
    static final int MAX_COUNTED_USES = 4;
    static final float EASY_BONUS = 0.2f;

    public List<PoseTemplate> rank(List<PoseTemplate> poses, @Nullable Scene scene,
                                   Set<String> preferredCategories, Set<String> favorites,
                                   Map<String, Integer> useCounts) {
        List<Scored> scored = new ArrayList<>();
        for (PoseTemplate pose : poses) {
            float score = 0f;
            if (scene != null) {
                if (pose.scenes.contains(scene.key)) {
                    score += SCENE_MATCH;
                }
                if (pose.category.equals(scene.key)) {
                    score += CATEGORY_MATCHES_SCENE;
                }
            }
            if (preferredCategories.contains(pose.category)) {
                score += PREFERRED_CATEGORY;
            }
            if (favorites.contains(pose.id)) {
                score += FAVORITE;
            }
            Integer uses = useCounts.get(pose.id);
            if (uses != null) {
                score += PER_USE * Math.min(uses, MAX_COUNTED_USES);
            }
            if ("easy".equals(pose.difficulty)) {
                score += EASY_BONUS;
            }
            scored.add(new Scored(pose, score));
        }
        Collections.sort(scored, (a, b) -> {
            int byScore = Float.compare(b.score, a.score);
            return byScore != 0 ? byScore : a.pose.name.compareTo(b.pose.name);
        });
        List<PoseTemplate> result = new ArrayList<>();
        for (Scored s : scored) {
            result.add(s.pose);
        }
        return result;
    }

    private static final class Scored {
        final PoseTemplate pose;
        final float score;

        Scored(PoseTemplate pose, float score) {
            this.pose = pose;
            this.score = score;
        }
    }
}
