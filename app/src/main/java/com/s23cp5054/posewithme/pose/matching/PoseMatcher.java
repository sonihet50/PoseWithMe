package com.s23cp5054.posewithme.pose.matching;

import androidx.annotation.Nullable;

import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.Landmark;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.pose.model.PoseGroup;

import java.util.List;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Geometric pose comparison. Both poses are in the {@link BodyPose} frame, so body size and
 * camera distance are already factored out.
 *
 * <p>Limbs are compared bone by bone using each bone's direction measured relative to the
 * person's own torso axis, so a slightly rolled phone does not count against the user. The
 * torso is compared by lean and by how far it is turned from the camera (shoulder width), the
 * head by where the nose sits relative to the shoulders. Bones whose landmarks were not seen
 * reliably, live or in the target, are left out rather than scored as wrong.
 *
 * <p>This is a heuristic, tuned for front-facing photo poses; it is not expected to judge every
 * possible pose well.
 */
public final class PoseMatcher {

    /** A pair of landmarks forming one scored bone. */
    private static final int[][] BONES = {
            {Landmark.LEFT_SHOULDER, Landmark.LEFT_ELBOW},
            {Landmark.LEFT_ELBOW, Landmark.LEFT_WRIST},
            {Landmark.RIGHT_SHOULDER, Landmark.RIGHT_ELBOW},
            {Landmark.RIGHT_ELBOW, Landmark.RIGHT_WRIST},
            {Landmark.LEFT_HIP, Landmark.LEFT_KNEE},
            {Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE},
            {Landmark.RIGHT_HIP, Landmark.RIGHT_KNEE},
            {Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE},
    };
    private static final BodyPart[] BONE_PART = {
            BodyPart.LEFT_ARM, BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.RIGHT_ARM,
            BodyPart.LEFT_LEG, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.RIGHT_LEG,
    };
    private static final boolean[] BONE_IS_LOWER = {
            false, true, false, true, false, true, false, true,
    };

    /** Shoulder width / torso length of a person squarely facing the camera (approx.). */
    private static final float FRONTAL_SHOULDER_RATIO = 0.75f;

    private final MatchConfig config;

    public PoseMatcher(MatchConfig config) {
        this.config = config;
    }

    public MatchResult match(BodyPose live, PoseTemplate target) {
        Set<BodyPart> ignored = EnumSet.noneOf(BodyPart.class);
        for (String key : target.ignoredParts) {
            BodyPart part = BodyPart.fromKey(key);
            if (part != null) {
                ignored.add(part);
            }
        }
        if (target.framing == PoseTemplate.Framing.UPPER_BODY) {
            ignored.add(BodyPart.LEFT_LEG);
            ignored.add(BodyPart.RIGHT_LEG);
        }

        MatchResult direct = match(live, target.pose, ignored, false);
        if (!target.mirrorAllowed) {
            return direct;
        }
        Set<BodyPart> mirroredIgnored = EnumSet.noneOf(BodyPart.class);
        for (BodyPart part : ignored) {
            mirroredIgnored.add(mirrorPart(part));
        }
        MatchResult mirrored = match(live, target.pose.mirrored(), mirroredIgnored, true);
        return mirrored.score > direct.score ? mirrored : direct;
    }

    /** Group roles follow the reference from left to right; every person must be visible. */
    @Nullable
    public MatchResult match(List<BodyPose> live, PoseTemplate target) {
        // shortcut: roles follow horizontal position; add identity tracking for crossing or multi-row groups.
        if (live.size() != target.people.size()) return null;
        if (live.size() == 1) return match(PoseGroup.local(live.get(0)), target);
        List<BodyPose> normalized = PoseGroup.normalize(live);
        MatchResult worst = null;
        int worstIndex = -1;
        float coverage = 1f;
        boolean reliable = true;
        float formationError = 0f;
        for (int i = 0; i < normalized.size(); i++) {
            BodyPose reference = target.people.get(i);
            // A group is mirrored as a formation by the preview, never independently per role.
            PoseTemplate individual = new PoseTemplate(target.id, target.name, target.description,
                    target.category, target.difficulty, target.framing, false,
                    target.instructions, target.scenes, target.ignoredParts, target.source,
                    PoseGroup.local(reference));
            MatchResult result = match(PoseGroup.local(normalized.get(i)), individual);
            if (worst == null || result.score < worst.score) {
                worst = result;
                worstIndex = i;
            }
            coverage = Math.min(coverage, result.coverage);
            reliable &= result.reliable;
            formationError = Math.max(formationError, (float) Math.hypot(
                    PoseGroup.hipX(normalized.get(i)) - PoseGroup.hipX(reference),
                    PoseGroup.hipY(normalized.get(i)) - PoseGroup.hipY(reference)));
        }
        float formationScore = Math.max(0f, Math.min(1f, (1.2f - formationError) / 0.9f));
        MatchResult.Hint hint = worst.hint == null ? null : new MatchResult.Hint(
                worst.hint.part, worst.hint.action, worst.hint.degrees, worstIndex);
        if (formationScore < config.goodThreshold && formationScore < worst.score) {
            hint = new MatchResult.Hint(BodyPart.TORSO, MatchResult.Hint.Action.MATCH_SPACING, 0f);
        }
        return new MatchResult(Math.min(worst.score, formationScore), coverage, reliable,
                false, new EnumMap<>(worst.partScores), new EnumMap<>(worst.partStatus), hint);
    }

    private MatchResult match(BodyPose live, BodyPose target, Set<BodyPart> ignored,
                              boolean mirroredTarget) {
        TorsoFrame liveFrame = new TorsoFrame(live);
        TorsoFrame targetFrame = new TorsoFrame(target);
        LocalFrame livePelvis = LocalFrame.pelvis(live, liveFrame);
        LocalFrame targetPelvis = LocalFrame.pelvis(target, targetFrame);

        EnumMap<BodyPart, Float> scoreSum = new EnumMap<>(BodyPart.class);
        EnumMap<BodyPart, Float> weightSum = new EnumMap<>(BodyPart.class);
        float[] boneError = new float[BONES.length];
        for (int i = 0; i < BONES.length; i++) {
            boneError[i] = -1f;
            BodyPart part = BONE_PART[i];
            int a = BONES[i][0];
            int b = BONES[i][1];
            if (ignored.contains(part) || !seen(live, a, b, config.minLiveVisibility)
                    || !seen(target, a, b, config.minTargetVisibility)) {
                continue;
            }
            float error = angleBetween(frameFor(i, liveFrame, livePelvis).boneAngle(live, a, b),
                    frameFor(i, targetFrame, targetPelvis).boneAngle(target, a, b));
            boneError[i] = error;
            float weight = BONE_IS_LOWER[i] ? config.lowerBoneWeight : 1f;
            accumulate(scoreSum, part, credit(error) * weight);
            accumulate(weightSum, part, weight);
        }

        // Torso: lean and turn.
        float tiltError = Math.abs(liveFrame.tiltDegrees - targetFrame.tiltDegrees);
        float turnError = Math.abs(turnDegrees(live) - turnDegrees(target));
        if (!ignored.contains(BodyPart.TORSO)) {
            accumulate(scoreSum, BodyPart.TORSO,
                    Math.min(torsoCredit(tiltError), torsoCredit(turnError)));
            accumulate(weightSum, BodyPart.TORSO, 1f);
        }

        // Head: direction from the shoulder midpoint to the nose.
        float headError = -1f;
        int nose = Landmark.NOSE;
        if (!ignored.contains(BodyPart.HEAD)
                && live.visibility[nose] >= config.minLiveVisibility
                && target.visibility[nose] >= config.minTargetVisibility) {
            headError = angleBetween(liveFrame.headAngle(live), targetFrame.headAngle(target));
            accumulate(scoreSum, BodyPart.HEAD, credit(headError));
            accumulate(weightSum, BodyPart.HEAD, 1f);
        }

        EnumMap<BodyPart, Float> partScores = new EnumMap<>(BodyPart.class);
        EnumMap<BodyPart, MatchResult.Status> partStatus = new EnumMap<>(BodyPart.class);
        float total = 0f;
        float totalWeight = 0f;
        float possibleWeight = 0f;
        for (BodyPart part : BodyPart.values()) {
            if (ignored.contains(part)) {
                partStatus.put(part, MatchResult.Status.IGNORED);
                continue;
            }
            float weight = partWeight(part);
            possibleWeight += weight;
            Float w = weightSum.get(part);
            if (w == null || w <= 0f) {
                partStatus.put(part, MatchResult.Status.NOT_VISIBLE);
                continue;
            }
            float score = scoreSum.get(part) / w;
            partScores.put(part, score);
            partStatus.put(part, statusFor(score));
            total += score * weight;
            totalWeight += weight;
        }
        float score = totalWeight > 0f ? total / totalWeight : 0f;
        float coverage = possibleWeight > 0f ? totalWeight / possibleWeight : 0f;

        MatchResult.Hint hint = chooseHint(live, target, liveFrame, targetFrame, livePelvis,
                targetPelvis, partScores, boneError, tiltError, turnError, headError);
        return new MatchResult(score, coverage, coverage >= config.minCoverage, mirroredTarget,
                partScores, partStatus, hint);
    }

    @Nullable
    private MatchResult.Hint chooseHint(BodyPose live, BodyPose target, TorsoFrame liveFrame,
                                        TorsoFrame targetFrame, LocalFrame livePelvis,
                                        LocalFrame targetPelvis, EnumMap<BodyPart, Float> scores,
                                        float[] boneError, float tiltError, float turnError,
                                        float headError) {
        // Worst part first, favouring heavier parts on ties.
        BodyPart worst = null;
        float worstKey = Float.MAX_VALUE;
        for (BodyPart part : scores.keySet()) {
            float score = scores.get(part);
            if (score >= config.goodThreshold) {
                continue;
            }
            float key = score - partWeight(part) * 0.01f;
            if (key < worstKey) {
                worstKey = key;
                worst = part;
            }
        }
        if (worst == null) {
            return null;
        }

        switch (worst) {
            case TORSO:
                if (tiltError >= config.hintDegrees && tiltError >= turnError) {
                    float diff = targetFrame.tiltDegrees - liveFrame.tiltDegrees;
                    if (Math.abs(targetFrame.tiltDegrees) < config.hintDegrees) {
                        return new MatchResult.Hint(worst, MatchResult.Hint.Action.STAND_STRAIGHT,
                                tiltError);
                    }
                    // Positive tilt leans towards the image's right, the person's own left.
                    return new MatchResult.Hint(worst, diff > 0
                            ? MatchResult.Hint.Action.LEAN_LEFT
                            : MatchResult.Hint.Action.LEAN_RIGHT, tiltError);
                }
                return new MatchResult.Hint(worst, turnDegrees(target) > turnDegrees(live)
                        ? MatchResult.Hint.Action.TURN_BODY
                        : MatchResult.Hint.Action.FACE_CAMERA, turnError);
            case HEAD: {
                float[] l = liveFrame.toLocal(live, shoulderMidX(live), shoulderMidY(live),
                        Landmark.NOSE);
                float[] t = targetFrame.toLocal(target, shoulderMidX(target),
                        shoulderMidY(target), Landmark.NOSE);
                float dx = t[0] - l[0];
                float dy = t[1] - l[1];
                if (Math.abs(dx) >= Math.abs(dy)) {
                    // Local +x is the image's right, i.e. the person's own left.
                    return new MatchResult.Hint(worst, dx > 0
                            ? MatchResult.Hint.Action.TILT_HEAD_LEFT
                            : MatchResult.Hint.Action.TILT_HEAD_RIGHT, headError);
                }
                // Local +y points down the torso; a more negative y means a higher nose.
                return new MatchResult.Hint(worst, dy < 0 ? MatchResult.Hint.Action.HEAD_UP
                        : MatchResult.Hint.Action.HEAD_DOWN, headError);
            }
            default:
                return limbHint(worst, live, target, liveFrame, targetFrame, livePelvis,
                        targetPelvis, boneError);
        }
    }

    @Nullable
    private MatchResult.Hint limbHint(BodyPart part, BodyPose live, BodyPose target,
                                      TorsoFrame liveFrame, TorsoFrame targetFrame,
                                      LocalFrame livePelvis, LocalFrame targetPelvis,
                                      float[] boneError) {
        boolean arm = part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM;
        boolean leftSide = part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
        int upper = -1;
        int lower = -1;
        for (int i = 0; i < BONES.length; i++) {
            if (BONE_PART[i] == part) {
                if (BONE_IS_LOWER[i]) {
                    lower = i;
                } else {
                    upper = i;
                }
            }
        }

        // The middle joint's bend is the clearest cue when the lower bone is the bigger error.
        if (lower >= 0 && boneError[lower] >= 0
                && (upper < 0 || boneError[upper] < 0 || boneError[lower] > boneError[upper])) {
            int a = BONES[upper >= 0 ? upper : lower][0];
            int mid = BONES[lower][0];
            int c = BONES[lower][1];
            float liveBend = jointAngle(live, a, mid, c);
            float targetBend = jointAngle(target, a, mid, c);
            float diff = Math.abs(liveBend - targetBend);
            if (diff >= config.hintDegrees) {
                boolean bendMore = targetBend < liveBend;
                MatchResult.Hint.Action action = arm
                        ? (bendMore ? MatchResult.Hint.Action.BEND_ELBOW
                        : MatchResult.Hint.Action.STRAIGHTEN_ARM)
                        : (bendMore ? MatchResult.Hint.Action.BEND_KNEE
                        : MatchResult.Hint.Action.STRAIGHTEN_LEG);
                return new MatchResult.Hint(part, action, diff);
            }
        }

        int bone = upper >= 0 && boneError[upper] >= 0 ? upper : lower;
        if (bone < 0 || boneError[bone] < config.hintDegrees) {
            return null;
        }
        int a = BONES[bone][0];
        int b = BONES[bone][1];
        float liveAngle = frameFor(bone, liveFrame, livePelvis).boneAngle(live, a, b);
        float targetAngle = frameFor(bone, targetFrame, targetPelvis).boneAngle(target, a, b);
        float elevationDiff = Math.abs(targetAngle) - Math.abs(liveAngle);
        if (Math.abs(elevationDiff) >= config.hintDegrees) {
            boolean raise = elevationDiff > 0;
            MatchResult.Hint.Action action = arm
                    ? (raise ? MatchResult.Hint.Action.RAISE_ARM
                    : MatchResult.Hint.Action.LOWER_ARM)
                    : (raise ? MatchResult.Hint.Action.RAISE_LEG
                    : MatchResult.Hint.Action.LOWER_LEG);
            return new MatchResult.Hint(part, action, Math.abs(elevationDiff));
        }
        // Same height, different side: the person's left side is at local +x.
        boolean targetOutward = leftSide ? targetAngle > liveAngle : targetAngle < liveAngle;
        MatchResult.Hint.Action action = arm
                ? (targetOutward ? MatchResult.Hint.Action.ARM_OUT
                : MatchResult.Hint.Action.ARM_IN)
                : (targetOutward ? MatchResult.Hint.Action.LEG_OUT
                : MatchResult.Hint.Action.LEG_IN);
        return new MatchResult.Hint(part, action, boneError[bone]);
    }

    private float credit(float errorDegrees) {
        if (errorDegrees <= config.fullCreditDegrees) {
            return 1f;
        }
        if (errorDegrees >= config.zeroCreditDegrees) {
            return 0f;
        }
        return 1f - (errorDegrees - config.fullCreditDegrees)
                / (config.zeroCreditDegrees - config.fullCreditDegrees);
    }

    private float torsoCredit(float errorDegrees) {
        if (errorDegrees <= config.torsoFullCreditDegrees) {
            return 1f;
        }
        if (errorDegrees >= config.torsoZeroCreditDegrees) {
            return 0f;
        }
        return 1f - (errorDegrees - config.torsoFullCreditDegrees)
                / (config.torsoZeroCreditDegrees - config.torsoFullCreditDegrees);
    }

    /** Arms are judged against the torso, legs against the hip line. */
    private static LocalFrame frameFor(int bone, TorsoFrame torso, LocalFrame pelvis) {
        return BONE_PART[bone] == BodyPart.LEFT_LEG || BONE_PART[bone] == BodyPart.RIGHT_LEG
                ? pelvis : torso;
    }

    private MatchResult.Status statusFor(float score) {
        if (score >= config.goodThreshold) {
            return MatchResult.Status.GOOD;
        }
        return score >= config.adjustThreshold ? MatchResult.Status.ADJUST
                : MatchResult.Status.OFF;
    }

    private float partWeight(BodyPart part) {
        switch (part) {
            case HEAD: return config.headWeight;
            case TORSO: return config.torsoWeight;
            case LEFT_ARM:
            case RIGHT_ARM: return config.armWeight;
            default: return config.legWeight;
        }
    }

    private static BodyPart mirrorPart(BodyPart part) {
        switch (part) {
            case LEFT_ARM: return BodyPart.RIGHT_ARM;
            case RIGHT_ARM: return BodyPart.LEFT_ARM;
            case LEFT_LEG: return BodyPart.RIGHT_LEG;
            case RIGHT_LEG: return BodyPart.LEFT_LEG;
            default: return part;
        }
    }

    private static void accumulate(EnumMap<BodyPart, Float> map, BodyPart part, float value) {
        Float current = map.get(part);
        map.put(part, current == null ? value : current + value);
    }

    private static boolean seen(BodyPose pose, int a, int b, float minVisibility) {
        return pose.visibility[a] >= minVisibility && pose.visibility[b] >= minVisibility;
    }

    /** Smallest absolute difference between two angles in degrees, in [0, 180]. */
    static float angleBetween(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180f ? 360f - d : d;
    }

    /** Interior angle at {@code mid} in degrees: 180 is straight, smaller is more bent. */
    static float jointAngle(BodyPose p, int a, int mid, int c) {
        double ax = p.x[a] - p.x[mid];
        double ay = p.y[a] - p.y[mid];
        double cx = p.x[c] - p.x[mid];
        double cy = p.y[c] - p.y[mid];
        double dot = ax * cx + ay * cy;
        double len = Math.hypot(ax, ay) * Math.hypot(cx, cy);
        if (len < 1e-9) {
            return 180f;
        }
        return (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot / len))));
    }

    /** 0 when squarely facing the camera, approaching 90 when side-on. */
    private static float turnDegrees(BodyPose p) {
        float width = (float) Math.hypot(p.x[Landmark.LEFT_SHOULDER] - p.x[Landmark.RIGHT_SHOULDER],
                p.y[Landmark.LEFT_SHOULDER] - p.y[Landmark.RIGHT_SHOULDER]);
        float ratio = Math.min(1f, width / FRONTAL_SHOULDER_RATIO);
        return (float) Math.toDegrees(Math.acos(ratio));
    }

    private static float shoulderMidX(BodyPose p) {
        return (p.x[Landmark.LEFT_SHOULDER] + p.x[Landmark.RIGHT_SHOULDER]) / 2f;
    }

    private static float shoulderMidY(BodyPose p) {
        return (p.y[Landmark.LEFT_SHOULDER] + p.y[Landmark.RIGHT_SHOULDER]) / 2f;
    }

    /**
     * A rotated 2D frame: local +y is "down" for this body region, local +x is perpendicular
     * (towards the image's right, the person's own left, for an upright front-facing person).
     */
    private static class LocalFrame {
        /** Below this hip width (torso units) the hip line is too short to trust. */
        private static final float MIN_HIP_WIDTH = 0.15f;

        final float downX;
        final float downY;
        final float rightX;
        final float rightY;

        LocalFrame(float downX, float downY, float rightX, float rightY) {
            this.downX = downX;
            this.downY = downY;
            this.rightX = rightX;
            this.rightY = rightY;
        }

        /** Frame square to the hip line, falling back to the torso frame for side-on poses. */
        static LocalFrame pelvis(BodyPose p, TorsoFrame torso) {
            float rx = p.x[Landmark.LEFT_HIP] - p.x[Landmark.RIGHT_HIP];
            float ry = p.y[Landmark.LEFT_HIP] - p.y[Landmark.RIGHT_HIP];
            float len = (float) Math.hypot(rx, ry);
            if (len < MIN_HIP_WIDTH) {
                return torso;
            }
            rx /= len;
            ry /= len;
            return new LocalFrame(-ry, rx, rx, ry);
        }

        /** Bone direction in degrees: 0 points local down, +90 local right, 180 local up. */
        float boneAngle(BodyPose p, int a, int b) {
            float vx = p.x[b] - p.x[a];
            float vy = p.y[b] - p.y[a];
            float lx = vx * rightX + vy * rightY;
            float ly = vx * downX + vy * downY;
            return (float) Math.toDegrees(Math.atan2(lx, ly));
        }

        float[] toLocal(BodyPose p, float originX, float originY, int index) {
            float vx = p.x[index] - originX;
            float vy = p.y[index] - originY;
            return new float[]{vx * rightX + vy * rightY, vx * downX + vy * downY};
        }
    }

    /** Frame aligned with the torso: local +y runs from the shoulders down to the hips. */
    private static final class TorsoFrame extends LocalFrame {
        /** Lean of the torso from vertical; positive leans towards the image's right. */
        final float tiltDegrees;

        TorsoFrame(BodyPose p) {
            this(p, unitUp(p));
        }

        private TorsoFrame(BodyPose p, float[] up) {
            super(-up[0], -up[1], -up[1], up[0]);
            tiltDegrees = (float) Math.toDegrees(Math.atan2(up[0], -up[1]));
        }

        /** Unit vector from the hip midpoint (the body-frame origin) to the shoulder midpoint. */
        private static float[] unitUp(BodyPose p) {
            float upX = shoulderMidX(p);
            float upY = shoulderMidY(p);
            float len = (float) Math.max(1e-6, Math.hypot(upX, upY));
            return new float[]{upX / len, upY / len};
        }

        float headAngle(BodyPose p) {
            float[] local = toLocal(p, shoulderMidX(p), shoulderMidY(p), Landmark.NOSE);
            return (float) Math.toDegrees(Math.atan2(local[0], local[1]));
        }
    }
}
