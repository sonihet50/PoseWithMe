package com.s23cp5054.posewithme.pose.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoCaptureTriggerTest {

    private int fireCount(AutoCaptureTrigger trigger, float score, long fromMs, long toMs) {
        int fired = 0;
        for (long t = fromMs; t <= toMs; t += 50) {
            if (trigger.onScore(score, true, t)) {
                fired++;
            }
        }
        return fired;
    }

    @Test
    public void firesOnceAfterHoldingAboveThreshold() {
        AutoCaptureTrigger trigger = new AutoCaptureTrigger(0.85f);
        assertFalse(trigger.onScore(0.9f, true, 0));
        assertFalse(trigger.onScore(0.9f, true, 500));
        assertTrue(trigger.onScore(0.9f, true, AutoCaptureTrigger.HOLD_MS));
    }

    @Test
    public void holdingThePoseDoesNotFireAgain() {
        AutoCaptureTrigger trigger = new AutoCaptureTrigger(0.85f);
        assertEquals(1, fireCount(trigger, 0.95f, 0, 10_000));
    }

    @Test
    public void dippingBelowThresholdRestartsTheHold() {
        AutoCaptureTrigger trigger = new AutoCaptureTrigger(0.85f);
        trigger.onScore(0.9f, true, 0);
        trigger.onScore(0.8f, true, 600);
        assertFalse(trigger.onScore(0.9f, true, 1100));
        assertTrue(trigger.onScore(0.9f, true, 2100));
    }

    @Test
    public void smallDipAfterFiringDoesNotRearm() {
        AutoCaptureTrigger trigger = new AutoCaptureTrigger(0.85f);
        fireCount(trigger, 0.9f, 0, 1500);
        trigger.onScore(0.8f, true, 1600);
        assertEquals(0, fireCount(trigger, 0.9f, 1700, 5000));
    }

    @Test
    public void breakingThePoseRearms() {
        AutoCaptureTrigger trigger = new AutoCaptureTrigger(0.85f);
        fireCount(trigger, 0.9f, 0, 1500);
        trigger.onScore(0.5f, true, 1600);
        assertEquals(1, fireCount(trigger, 0.9f, 1700, 5000));
    }

    @Test
    public void leavingTheFrameRearms() {
        AutoCaptureTrigger trigger = new AutoCaptureTrigger(0.85f);
        fireCount(trigger, 0.9f, 0, 1500);
        trigger.onScore(0f, false, 1600);
        assertTrue(trigger.isArmed());
    }
}
