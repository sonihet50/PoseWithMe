package com.s23cp5054.posewithme.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.s23cp5054.posewithme.pose.recommendation.Scene;

import org.junit.Test;

public class PlaceFinderTest {

    @Test
    public void prefersLandmarkOverSlightlyCloserCafe() throws Exception {
        String json = "{\"elements\":["
                + "{\"type\":\"node\",\"lat\":28.61300,\"lon\":77.22950,"
                + "\"tags\":{\"amenity\":\"cafe\",\"name\":\"Gate Cafe\"}},"
                + "{\"type\":\"way\",\"center\":{\"lat\":28.61330,\"lon\":77.22950},"
                + "\"tags\":{\"historic\":\"memorial\",\"name\":\"India Gate\"}}]}";

        PlaceFinder.Place place = PlaceFinder.pickBest(json, 28.6129, 77.2295);

        assertNotNull(place);
        assertEquals("India Gate", place.name);
        assertEquals(Scene.MONUMENT, place.scene);
    }

    @Test
    public void ignoresUnnamedAndUnmappedElements() throws Exception {
        String json = "{\"elements\":["
                + "{\"type\":\"node\",\"lat\":1,\"lon\":1,\"tags\":{\"amenity\":\"cafe\"}},"
                + "{\"type\":\"node\",\"lat\":1,\"lon\":1,\"tags\":{\"shop\":\"bakery\","
                + "\"name\":\"Bread\"}}]}";

        assertNull(PlaceFinder.pickBest(json, 1, 1));
    }
}
