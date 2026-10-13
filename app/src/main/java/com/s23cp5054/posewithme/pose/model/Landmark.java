package com.s23cp5054.posewithme.pose.model;

/** MediaPipe Pose landmark indices. "Left" is the person's own left. */
public final class Landmark {

    public static final int COUNT = 33;

    public static final int NOSE = 0;
    public static final int LEFT_EYE = 2;
    public static final int RIGHT_EYE = 5;
    public static final int LEFT_EAR = 7;
    public static final int RIGHT_EAR = 8;
    public static final int LEFT_SHOULDER = 11;
    public static final int RIGHT_SHOULDER = 12;
    public static final int LEFT_ELBOW = 13;
    public static final int RIGHT_ELBOW = 14;
    public static final int LEFT_WRIST = 15;
    public static final int RIGHT_WRIST = 16;
    public static final int LEFT_PINKY = 17;
    public static final int RIGHT_PINKY = 18;
    public static final int LEFT_INDEX = 19;
    public static final int RIGHT_INDEX = 20;
    public static final int LEFT_HIP = 23;
    public static final int RIGHT_HIP = 24;
    public static final int LEFT_KNEE = 25;
    public static final int RIGHT_KNEE = 26;
    public static final int LEFT_ANKLE = 27;
    public static final int RIGHT_ANKLE = 28;
    public static final int LEFT_HEEL = 29;
    public static final int RIGHT_HEEL = 30;
    public static final int LEFT_FOOT_INDEX = 31;
    public static final int RIGHT_FOOT_INDEX = 32;

    /** Index of the same landmark on the other side of the body (itself for the nose). */
    public static int mirrorOf(int index) {
        if (index == NOSE) {
            return NOSE;
        }
        if (index <= 6) {
            return index <= 3 ? index + 3 : index - 3;
        }
        return index % 2 == 1 ? index + 1 : index - 1;
    }

    private Landmark() {
    }
}
