package com.s23cp5054.posewithme.pose.recommendation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.s23cp5054.posewithme.pose.TestPoses;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class RecommendationTest {

    private static Map<String, String> tags(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    public void sceneMapper_mapsCommonPlaces() {
        assertEquals(Scene.BEACH, SceneMapper.sceneFor(tags("natural", "beach")));
        assertEquals(Scene.MONUMENT, SceneMapper.sceneFor(tags("historic", "monument")));
        assertEquals(Scene.MONUMENT, SceneMapper.sceneFor(tags("tourism", "attraction")));
        assertEquals(Scene.SCENIC, SceneMapper.sceneFor(tags("tourism", "viewpoint")));
        assertEquals(Scene.PARK, SceneMapper.sceneFor(tags("leisure", "park")));
        assertEquals(Scene.CAFE, SceneMapper.sceneFor(tags("amenity", "cafe")));
        assertNull(SceneMapper.sceneFor(tags("shop", "bakery")));
    }

    @Test
    public void sceneMapper_landmarkInsideParkIsMonument() {
        assertEquals(Scene.MONUMENT, SceneMapper.sceneFor(tags("leisure", "park",
                "historic", "memorial")));
    }

    @Test
    public void distance_oneDegreeOfLatitude() {
        assertEquals(111_195, GeoUtils.distanceMeters(0, 0, 1, 0), 10);
    }

    @Test
    public void curatedPlace_findsClosestContainingPlace() {
        List<CuratedPlace> places = Arrays.asList(
                new CuratedPlace("a", "A", 28.6129, 77.2295, 500, Scene.MONUMENT,
                        Collections.singletonList("p1")),
                new CuratedPlace("b", "B", 28.5245, 77.1855, 400, Scene.MONUMENT,
                        Collections.singletonList("p2")));

        CuratedPlace near = CuratedPlace.find(places, 28.6140, 77.2300);
        assertNotNull(near);
        assertEquals("a", near.id);
        assertNull(CuratedPlace.find(places, 28.57, 77.20));
    }

    @Test
    public void curatedPlace_parsesJson() throws Exception {
        List<CuratedPlace> places = CuratedPlace.parseList("[{\"id\":\"x\",\"name\":\"X\","
                + "\"lat\":1.5,\"lon\":2.5,\"radiusMeters\":300,\"scene\":\"beach\","
                + "\"poses\":[\"p1\",\"p2\"]}]");
        assertEquals(1, places.size());
        assertEquals(Scene.BEACH, places.get(0).scene);
        assertEquals(Arrays.asList("p1", "p2"), places.get(0).poseIds);
    }

    private static PoseTemplate pose(String id, String category, String... scenes) {
        return new PoseTemplate(id, id, "", category, "medium", PoseTemplate.Framing.FULL_BODY,
                true, Collections.emptyList(), Arrays.asList(scenes), Collections.emptyList(),
                new PoseTemplate.Source("web", null, null, null, null, null),
                TestPoses.standing());
    }

    @Test
    public void recommender_putsSceneMatchesFirst() {
        List<PoseTemplate> poses = Arrays.asList(
                pose("park-pose", "sitting", "park"),
                pose("beach-pose", "beach", "beach"),
                pose("street-pose", "walking", "street"));

        List<PoseTemplate> ranked = new PoseRecommender().rank(poses, Scene.BEACH,
                Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());

        assertEquals("beach-pose", ranked.get(0).id);
    }

    @Test
    public void recommender_usesPreferencesFavouritesAndUsageWithoutScene() {
        List<PoseTemplate> poses = Arrays.asList(
                pose("a", "walking", "street"),
                pose("b", "sitting", "park"),
                pose("c", "portrait", "cafe"));
        Map<String, Integer> uses = new HashMap<>();
        uses.put("b", 1);

        List<PoseTemplate> ranked = new PoseRecommender().rank(poses, null,
                new HashSet<>(Collections.singletonList("portrait")),
                new HashSet<>(Collections.singletonList("a")), uses);

        assertEquals("c", ranked.get(0).id);
        assertEquals("a", ranked.get(1).id);
        assertEquals("b", ranked.get(2).id);
    }
}
