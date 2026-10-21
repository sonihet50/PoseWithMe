package com.s23cp5054.posewithme.pose.matching;

import androidx.annotation.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Outcome of comparing a live pose against a target. */
public final class MatchResult {

    public enum Status { GOOD, ADJUST, OFF, NOT_VISIBLE, IGNORED }

    /** Overall similarity in [0, 1], over visible, non-ignored parts. */
    public final float score;
    /** Fraction of the scoring weight that was actually visible, in [0, 1]. */
    public final float coverage;
    /** False when too little of the body is visible for the score to mean much. */
    public final boolean reliable;
    /** True when the mirrored version of the target matched better. */
    public final boolean mirroredTarget;
    public final Map<BodyPart, Float> partScores;
    public final Map<BodyPart, Status> partStatus;
    /** The single most useful correction, or null when nothing needs changing. */
    @Nullable
    public final Hint hint;

    MatchResult(float score, float coverage, boolean reliable, boolean mirroredTarget,
                EnumMap<BodyPart, Float> partScores, EnumMap<BodyPart, Status> partStatus,
                @Nullable Hint hint) {
        this.score = score;
        this.coverage = coverage;
        this.reliable = reliable;
        this.mirroredTarget = mirroredTarget;
        this.partScores = partScores;
        this.partStatus = partStatus;
        this.hint = hint;
    }

    /** A correction expressed as data, turned into words by the UI layer. */
    public static final class Hint {
        public enum Action {
            RAISE_ARM, LOWER_ARM, BEND_ELBOW, STRAIGHTEN_ARM, ARM_OUT, ARM_IN,
            RAISE_LEG, LOWER_LEG, BEND_KNEE, STRAIGHTEN_LEG, LEG_OUT, LEG_IN,
            LEAN_LEFT, LEAN_RIGHT, STAND_STRAIGHT, TURN_BODY, FACE_CAMERA,
            TILT_HEAD_LEFT, TILT_HEAD_RIGHT, HEAD_UP, HEAD_DOWN, MATCH_SPACING
        }

        public final BodyPart part;
        public final Action action;
        /** Size of the difference in degrees, for "slightly" vs "more" wording. */
        public final float degrees;
        /** Zero-based role in the reference image, or -1 for a solo/whole-group hint. */
        public final int personIndex;

        Hint(BodyPart part, Action action, float degrees) {
            this(part, action, degrees, -1);
        }

        Hint(BodyPart part, Action action, float degrees, int personIndex) {
            this.part = part;
            this.action = action;
            this.degrees = degrees;
            this.personIndex = personIndex;
        }
    }
}
