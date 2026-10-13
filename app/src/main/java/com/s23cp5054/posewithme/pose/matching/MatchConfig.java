package com.s23cp5054.posewithme.pose.matching;

/**
 * Tunable thresholds and weights for {@link PoseMatcher}. Kept in one place so they can be
 * adjusted during testing; the defaults were picked by hand and should be tuned on device.
 */
public final class MatchConfig {

    /** Angle error (degrees) that still earns full credit for a bone. */
    public float fullCreditDegrees = 15f;
    /** Angle error (degrees) at which a bone earns zero credit. */
    public float zeroCreditDegrees = 70f;

    /** Torso lean/turn is held to a tighter tolerance than limbs. */
    public float torsoFullCreditDegrees = 10f;
    public float torsoZeroCreditDegrees = 45f;

    /** Live landmarks below this visibility are treated as not seen. */
    public float minLiveVisibility = 0.5f;
    /** Target landmarks below this visibility were not seen when the pose was captured. */
    public float minTargetVisibility = 0.4f;

    /** Part score at or above which a part is "good". */
    public float goodThreshold = 0.8f;
    /** Part score at or above which a part needs only a small adjustment. */
    public float adjustThreshold = 0.5f;

    /** Fraction of total part weight that must be visible for a trustworthy score. */
    public float minCoverage = 0.5f;

    public float headWeight = 0.4f;
    public float torsoWeight = 1.0f;
    public float armWeight = 1.0f;
    public float legWeight = 0.8f;

    /** Weight of the lower bone (forearm, shin) relative to the upper bone of a limb. */
    public float lowerBoneWeight = 0.8f;

    /** Differences smaller than this (degrees) produce no correction hint. */
    public float hintDegrees = 12f;
}
