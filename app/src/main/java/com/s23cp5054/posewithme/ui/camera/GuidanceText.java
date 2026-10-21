package com.s23cp5054.posewithme.ui.camera;

import android.content.Context;

import androidx.annotation.StringRes;

import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.pose.matching.BodyPart;
import com.s23cp5054.posewithme.pose.matching.MatchResult;

/** Turns a {@link MatchResult.Hint} into a short instruction in the person's own left/right. */
final class GuidanceText {

    /** Differences below this many degrees are phrased as "a little". */
    private static final float SLIGHT_DEGREES = 25f;

    static String forHint(Context context, MatchResult.Hint hint) {
        if (hint.action == MatchResult.Hint.Action.MATCH_SPACING) {
            return context.getString(R.string.hint_group_spacing);
        }
        String side = context.getString(
                hint.part == BodyPart.LEFT_ARM || hint.part == BodyPart.LEFT_LEG
                        ? R.string.side_left : R.string.side_right);
        String text = context.getString(templateFor(hint.action), side);
        if (hint.degrees < SLIGHT_DEGREES) {
            text = context.getString(R.string.hint_slightly, text);
        }
        return hint.personIndex >= 0
                ? context.getString(R.string.hint_person, hint.personIndex + 1, text) : text;
    }

    static String partLabel(Context context, BodyPart part) {
        switch (part) {
            case HEAD: return context.getString(R.string.part_head);
            case TORSO: return context.getString(R.string.part_torso);
            case LEFT_ARM: return context.getString(R.string.part_left_arm);
            case RIGHT_ARM: return context.getString(R.string.part_right_arm);
            case LEFT_LEG: return context.getString(R.string.part_left_leg);
            default: return context.getString(R.string.part_right_leg);
        }
    }

    @StringRes
    private static int templateFor(MatchResult.Hint.Action action) {
        switch (action) {
            case RAISE_ARM: return R.string.hint_raise_arm;
            case LOWER_ARM: return R.string.hint_lower_arm;
            case BEND_ELBOW: return R.string.hint_bend_elbow;
            case STRAIGHTEN_ARM: return R.string.hint_straighten_arm;
            case ARM_OUT: return R.string.hint_arm_out;
            case ARM_IN: return R.string.hint_arm_in;
            case RAISE_LEG: return R.string.hint_raise_leg;
            case LOWER_LEG: return R.string.hint_lower_leg;
            case BEND_KNEE: return R.string.hint_bend_knee;
            case STRAIGHTEN_LEG: return R.string.hint_straighten_leg;
            case LEG_OUT: return R.string.hint_leg_out;
            case LEG_IN: return R.string.hint_leg_in;
            case LEAN_LEFT: return R.string.hint_lean_left;
            case LEAN_RIGHT: return R.string.hint_lean_right;
            case STAND_STRAIGHT: return R.string.hint_stand_straight;
            case TURN_BODY: return R.string.hint_turn_body;
            case FACE_CAMERA: return R.string.hint_face_camera;
            case TILT_HEAD_LEFT: return R.string.hint_tilt_head_left;
            case TILT_HEAD_RIGHT: return R.string.hint_tilt_head_right;
            case HEAD_UP: return R.string.hint_head_up;
            default: return R.string.hint_head_down;
        }
    }

    private GuidanceText() {
    }
}
