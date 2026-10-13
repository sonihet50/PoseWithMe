package com.s23cp5054.posewithme.ui.poses;

import android.content.Context;

import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;

/** Human-readable labels for the keys stored in pose files. */
public final class PoseLabels {

    public static String category(Context context, String key) {
        switch (key) {
            case "hands_in_pockets": return context.getString(R.string.category_hands_in_pockets);
            case "crossed_arms": return context.getString(R.string.category_crossed_arms);
            case "walking": return context.getString(R.string.category_walking);
            case "looking_back": return context.getString(R.string.category_looking_back);
            case "sitting": return context.getString(R.string.category_sitting);
            case "portrait": return context.getString(R.string.category_portrait);
            case "beach": return context.getString(R.string.category_beach);
            case "monument": return context.getString(R.string.category_monument);
            case "scenic": return context.getString(R.string.category_scenic);
            case "cafe": return context.getString(R.string.category_cafe);
            default: return context.getString(R.string.category_casual_standing);
        }
    }

    public static String difficulty(Context context, String key) {
        switch (key) {
            case "medium": return context.getString(R.string.difficulty_medium);
            case "hard": return context.getString(R.string.difficulty_hard);
            default: return context.getString(R.string.difficulty_easy);
        }
    }

    public static String framing(Context context, PoseTemplate.Framing framing) {
        return context.getString(framing == PoseTemplate.Framing.UPPER_BODY
                ? R.string.framing_upper_body : R.string.framing_full_body);
    }

    /** "Full body · Easy" */
    public static String shortMeta(Context context, PoseTemplate pose) {
        return framing(context, pose.framing) + context.getString(R.string.meta_separator)
                + difficulty(context, pose.difficulty);
    }

    /** "Casual standing · Full body · Easy" */
    public static String longMeta(Context context, PoseTemplate pose) {
        return category(context, pose.category) + context.getString(R.string.meta_separator)
                + shortMeta(context, pose);
    }

    private PoseLabels() {
    }
}
