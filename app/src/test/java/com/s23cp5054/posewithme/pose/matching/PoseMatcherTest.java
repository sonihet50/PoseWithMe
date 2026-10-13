package com.s23cp5054.posewithme.pose.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.s23cp5054.posewithme.pose.TestPoses;
import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.Landmark;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;

import org.junit.Test;

import java.util.Collections;

public class PoseMatcherTest {

    private final PoseMatcher matcher = new PoseMatcher(new MatchConfig());

    @Test
    public void identicalPose_scoresFullWithNoHint() {
        MatchResult r = matcher.match(TestPoses.standing(), TestPoses.template(TestPoses.standing()));

        assertEquals(1f, r.score, 1e-4f);
        assertTrue(r.reliable);
        for (BodyPart part : BodyPart.values()) {
            assertEquals(part.name(), MatchResult.Status.GOOD, r.partStatus.get(part));
        }
        assertNull(r.hint);
    }

    @Test
    public void armDownWhenTargetRaisesIt_flagsThatArmAndSaysRaise() {
        MatchResult r = matcher.match(TestPoses.standing(),
                TestPoses.template(TestPoses.leftArmOut()));

        assertEquals(MatchResult.Status.OFF, r.partStatus.get(BodyPart.LEFT_ARM));
        assertEquals(MatchResult.Status.GOOD, r.partStatus.get(BodyPart.RIGHT_ARM));
        assertTrue(r.score < 0.9f);
        assertNotNull(r.hint);
        assertEquals(BodyPart.LEFT_ARM, r.hint.part);
        assertEquals(MatchResult.Hint.Action.RAISE_ARM, r.hint.action);
    }

    @Test
    public void armRaisedWhenTargetHasItDown_saysLower() {
        MatchResult r = matcher.match(TestPoses.leftArmOut(),
                TestPoses.template(TestPoses.standing()));

        assertNotNull(r.hint);
        assertEquals(MatchResult.Hint.Action.LOWER_ARM, r.hint.action);
    }

    @Test
    public void straightArmWhenTargetBendsElbow_saysBend() {
        MatchResult r = matcher.match(TestPoses.standing(),
                TestPoses.template(TestPoses.leftElbowBent()));

        assertNotNull(r.hint);
        assertEquals(BodyPart.LEFT_ARM, r.hint.part);
        assertEquals(MatchResult.Hint.Action.BEND_ELBOW, r.hint.action);
    }

    @Test
    public void otherSideAllowed_mirroredPerformanceMatches() {
        BodyPose rightArmOut = TestPoses.leftArmOut().mirrored();
        PoseTemplate target = TestPoses.template(rightArmOut, PoseTemplate.Framing.FULL_BODY,
                true, Collections.emptyList());

        MatchResult r = matcher.match(TestPoses.leftArmOut(), target);

        assertEquals(1f, r.score, 1e-3f);
        assertTrue(r.mirroredTarget);
    }

    @Test
    public void otherSideNotAllowed_mirroredPerformanceDoesNotMatch() {
        BodyPose rightArmOut = TestPoses.leftArmOut().mirrored();
        MatchResult r = matcher.match(TestPoses.leftArmOut(), TestPoses.template(rightArmOut));

        assertTrue(r.score < 0.9f);
        assertFalse(r.mirroredTarget);
    }

    @Test
    public void upperBodyFraming_ignoresLegs() {
        BodyPose live = TestPoses.standing();
        live.x[Landmark.LEFT_KNEE] = 0.9f;
        live.x[Landmark.LEFT_ANKLE] = 1.6f;
        PoseTemplate target = TestPoses.template(TestPoses.standing(),
                PoseTemplate.Framing.UPPER_BODY, false, Collections.emptyList());

        MatchResult r = matcher.match(live, target);

        assertEquals(MatchResult.Status.IGNORED, r.partStatus.get(BodyPart.LEFT_LEG));
        assertEquals(1f, r.score, 1e-4f);
    }

    @Test
    public void ignoredPartsFromFile_areNotScored() {
        PoseTemplate target = TestPoses.template(TestPoses.leftArmOut(),
                PoseTemplate.Framing.FULL_BODY, false, Collections.singletonList("left_arm"));

        MatchResult r = matcher.match(TestPoses.standing(), target);

        assertEquals(MatchResult.Status.IGNORED, r.partStatus.get(BodyPart.LEFT_ARM));
        assertEquals(1f, r.score, 1e-4f);
    }

    @Test
    public void unseenLegs_areReportedNotVisibleAndReduceCoverage() {
        BodyPose live = TestPoses.standing();
        for (int i = Landmark.LEFT_KNEE; i < Landmark.COUNT; i++) {
            live.visibility[i] = 0.1f;
        }

        MatchResult r = matcher.match(live, TestPoses.template(TestPoses.standing()));

        assertEquals(MatchResult.Status.NOT_VISIBLE, r.partStatus.get(BodyPart.LEFT_LEG));
        assertEquals(MatchResult.Status.NOT_VISIBLE, r.partStatus.get(BodyPart.RIGHT_LEG));
        assertTrue(r.coverage < 1f);
        assertTrue(r.reliable);
        assertEquals(1f, r.score, 1e-4f);
    }

    @Test
    public void onlyHeadAndTorsoVisible_isNotReliable() {
        BodyPose live = TestPoses.standing();
        for (int i = Landmark.LEFT_ELBOW; i < Landmark.COUNT; i++) {
            if (i != Landmark.LEFT_HIP && i != Landmark.RIGHT_HIP) {
                live.visibility[i] = 0.1f;
            }
        }

        MatchResult r = matcher.match(live, TestPoses.template(TestPoses.standing()));

        assertFalse(r.reliable);
    }

    @Test
    public void uprightWhenTargetLeans_saysLeanTowardsPersonsLeft() {
        // Leaning towards the image's right is towards the person's own left.
        MatchResult r = matcher.match(TestPoses.standing(),
                TestPoses.template(TestPoses.leaning(25f)));

        assertNotNull(r.hint);
        assertEquals(BodyPart.TORSO, r.hint.part);
        assertEquals(MatchResult.Hint.Action.LEAN_LEFT, r.hint.action);
    }

    @Test
    public void leaningWhenTargetIsUpright_saysStandStraight() {
        MatchResult r = matcher.match(TestPoses.leaning(-25f),
                TestPoses.template(TestPoses.standing()));

        assertNotNull(r.hint);
        assertEquals(MatchResult.Hint.Action.STAND_STRAIGHT, r.hint.action);
    }

    @Test
    public void wholePoseRotatedSlightly_stillGood() {
        // Phone held a little crooked: limbs are judged relative to the torso, but the torso
        // lean itself is still compared, so only a small tilt is tolerated.
        MatchResult r = matcher.match(TestPoses.leaning(8f),
                TestPoses.template(TestPoses.standing()));

        assertTrue(r.score > 0.95f);
    }

    @Test
    public void angleBetween_wrapsAround() {
        assertEquals(20f, PoseMatcher.angleBetween(170f, -170f), 1e-4f);
        assertEquals(0f, PoseMatcher.angleBetween(-180f, 180f), 1e-4f);
    }
}
