package com.s23cp5054.posewithme.pose.recording;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.s23cp5054.posewithme.pose.TestPoses;
import com.s23cp5054.posewithme.pose.model.BodyPose;
import com.s23cp5054.posewithme.pose.model.Landmark;

import org.junit.Test;

public class PoseRecorderTest {

    private static final long FRAME_MS = 50;

    @Test
    public void countsDownThenCapturesAfterHoldingStill() {
        PoseRecorder recorder = new PoseRecorder();
        recorder.start(0);
        assertEquals(3, recorder.countdownSecondsLeft(0));
        assertEquals(PoseRecorder.State.COUNTDOWN, recorder.onPose(TestPoses.standing(), 1000));

        long t = PoseRecorder.COUNTDOWN_MS;
        PoseRecorder.State state = PoseRecorder.State.COUNTDOWN;
        while (t <= PoseRecorder.COUNTDOWN_MS + 2000 && state != PoseRecorder.State.DONE) {
            state = recorder.onPose(TestPoses.standing(), t);
            t += FRAME_MS;
        }

        assertEquals(PoseRecorder.State.DONE, state);
        assertNotNull(recorder.getResult());
        assertEquals(0.35f, recorder.getResult().x[Landmark.LEFT_SHOULDER], 1e-4f);
    }

    @Test
    public void movementRestartsTheStillWindow() {
        PoseRecorder recorder = new PoseRecorder();
        recorder.start(0);
        long t = PoseRecorder.COUNTDOWN_MS;
        // Alternate between two quite different poses: never still.
        for (int i = 0; i < 40; i++, t += FRAME_MS) {
            BodyPose pose = i % 2 == 0 ? TestPoses.standing() : TestPoses.leftArmOut();
            assertEquals(PoseRecorder.State.HOLD_STILL, recorder.onPose(pose, t));
        }
    }

    @Test
    public void personLeavingFrameRestartsTheStillWindow() {
        PoseRecorder recorder = new PoseRecorder();
        recorder.start(0);
        long t = PoseRecorder.COUNTDOWN_MS;
        for (int i = 0; i < 12; i++, t += FRAME_MS) {
            recorder.onPose(TestPoses.standing(), t);
        }
        recorder.onPose(null, t);
        t += FRAME_MS;
        assertEquals(PoseRecorder.State.HOLD_STILL, recorder.onPose(TestPoses.standing(), t));
        assertEquals(0f, recorder.stillProgress(t), 1e-4f);
    }

    @Test
    public void givesUpAfterTimeout() {
        PoseRecorder recorder = new PoseRecorder();
        recorder.start(0);
        long end = PoseRecorder.COUNTDOWN_MS + PoseRecorder.TIMEOUT_MS + 100;
        assertEquals(PoseRecorder.State.TIMED_OUT, recorder.onPose(null, end));
    }

    @Test
    public void movement_isZeroForSamePose() {
        assertEquals(0f, PoseRecorder.movement(TestPoses.standing(), TestPoses.standing()), 1e-6f);
    }
}
