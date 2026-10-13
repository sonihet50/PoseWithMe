package com.s23cp5054.posewithme.pose.matching;

/** Body regions that are scored and reported separately. Names match "ignoredParts" in pose files. */
public enum BodyPart {
    HEAD("head"),
    TORSO("torso"),
    LEFT_ARM("left_arm"),
    RIGHT_ARM("right_arm"),
    LEFT_LEG("left_leg"),
    RIGHT_LEG("right_leg");

    public final String key;

    BodyPart(String key) {
        this.key = key;
    }

    public static BodyPart fromKey(String key) {
        for (BodyPart part : values()) {
            if (part.key.equals(key)) {
                return part;
            }
        }
        return null;
    }
}
