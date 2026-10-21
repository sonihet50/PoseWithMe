package com.s23cp5054.posewithme.pose.matching;

import androidx.annotation.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Smooths per-frame match results for display so the score and advice don't flicker:
 * scores are exponentially averaged, and a new correction hint only replaces the current one
 * after it has been suggested consistently for a short while.
 * Not thread-safe; feed it from one thread.
 */
public final class GuidanceSmoother {

    private static final float SCORE_ALPHA = 0.25f;
    private static final long HINT_SETTLE_MS = 600;

    private final MatchConfig config;
    private float score = -1f;
    private final Map<BodyPart, Float> partScores = new EnumMap<>(BodyPart.class);
    private final Map<BodyPart, MatchResult.Status> partStatus = new EnumMap<>(BodyPart.class);
    private boolean reliable;
    private boolean mirroredTarget;

    @Nullable private MatchResult.Hint shownHint;
    @Nullable private MatchResult.Hint candidateHint;
    private long candidateSinceMs;

    public GuidanceSmoother(MatchConfig config) {
        this.config = config;
    }

    public void reset() {
        score = -1f;
        partScores.clear();
        partStatus.clear();
        shownHint = null;
        candidateHint = null;
        reliable = false;
    }

    public void update(MatchResult result, long nowMs) {
        score = score < 0f ? result.score : score + SCORE_ALPHA * (result.score - score);
        reliable = result.reliable;
        mirroredTarget = result.mirroredTarget;
        for (BodyPart part : BodyPart.values()) {
            MatchResult.Status raw = result.partStatus.get(part);
            Float rawScore = result.partScores.get(part);
            if (rawScore == null) {
                partScores.remove(part);
                partStatus.put(part, raw);
                continue;
            }
            Float previous = partScores.get(part);
            float smoothed = previous == null ? rawScore
                    : previous + SCORE_ALPHA * (rawScore - previous);
            partScores.put(part, smoothed);
            partStatus.put(part, smoothed >= config.goodThreshold ? MatchResult.Status.GOOD
                    : smoothed >= config.adjustThreshold ? MatchResult.Status.ADJUST
                    : MatchResult.Status.OFF);
        }

        MatchResult.Hint hint = result.hint;
        if (!sameHint(hint, candidateHint)) {
            candidateHint = hint;
            candidateSinceMs = nowMs;
        }
        if (nowMs - candidateSinceMs >= HINT_SETTLE_MS) {
            shownHint = candidateHint;
        }
    }

    /** Smoothed overall score in [0, 1], or -1 before the first update. */
    public float getScore() {
        return score;
    }

    public boolean isReliable() {
        return reliable;
    }

    public boolean isMirroredTarget() {
        return mirroredTarget;
    }

    public Map<BodyPart, MatchResult.Status> getPartStatus() {
        return partStatus;
    }

    @Nullable
    public MatchResult.Hint getHint() {
        return shownHint;
    }

    private static boolean sameHint(@Nullable MatchResult.Hint a, @Nullable MatchResult.Hint b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.part == b.part && a.action == b.action && a.personIndex == b.personIndex;
    }
}
