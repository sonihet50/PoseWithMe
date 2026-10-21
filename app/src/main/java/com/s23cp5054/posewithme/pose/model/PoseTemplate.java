package com.s23cp5054.posewithme.pose.model;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A target pose plus the metadata shown in the library. Stored as one JSON file per pose:
 * built-in poses in assets/poses/, poses the user records in the app's files dir.
 * See tools/pose_pipeline/README.md for the file format.
 */
public final class PoseTemplate {

    public static final int FORMAT_VERSION = 2;

    public enum Framing { FULL_BODY, UPPER_BODY }

    public final String id;
    public final String name;
    public final String description;
    public final String category;
    public final String difficulty;
    public final Framing framing;
    /** Whether the pose may be performed with either side of the body. */
    public final boolean mirrorAllowed;
    public final List<String> instructions;
    public final List<String> scenes;
    /** Body parts left out of scoring, e.g. hands hidden in pockets. Names from PoseMatcher. */
    public final List<String> ignoredParts;
    public final Source source;
    public final BodyPose pose;
    /** Group members share one coordinate frame, preserving their relative spacing. */
    public final List<BodyPose> people;

    public static final class Source {
        /** "web", "recorded" (by the team, bundled) or "user" (recorded on this phone). */
        public final String type;
        @Nullable public final String title;
        @Nullable public final String author;
        @Nullable public final String url;
        @Nullable public final String license;
        @Nullable public final String licenseUrl;

        public Source(String type, @Nullable String title, @Nullable String author,
                      @Nullable String url, @Nullable String license,
                      @Nullable String licenseUrl) {
            this.type = type;
            this.title = title;
            this.author = author;
            this.url = url;
            this.license = license;
            this.licenseUrl = licenseUrl;
        }
    }

    public PoseTemplate(String id, String name, String description, String category,
                        String difficulty, Framing framing, boolean mirrorAllowed,
                        List<String> instructions, List<String> scenes,
                        List<String> ignoredParts, Source source, BodyPose pose) {
        this(id, name, description, category, difficulty, framing, mirrorAllowed, instructions,
                scenes, ignoredParts, source, Collections.singletonList(pose));
    }

    public PoseTemplate(String id, String name, String description, String category,
                        String difficulty, Framing framing, boolean mirrorAllowed,
                        List<String> instructions, List<String> scenes,
                        List<String> ignoredParts, Source source, List<BodyPose> people) {
        if (!id.matches("[A-Za-z0-9_-]+") || people.isEmpty()
                || people.size() > PoseGroup.MAX_PEOPLE) {
            throw new IllegalArgumentException("Invalid pose id or number of people");
        }
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.difficulty = difficulty;
        this.framing = framing;
        this.mirrorAllowed = mirrorAllowed;
        this.instructions = Collections.unmodifiableList(new ArrayList<>(instructions));
        this.scenes = Collections.unmodifiableList(new ArrayList<>(scenes));
        this.ignoredParts = Collections.unmodifiableList(new ArrayList<>(ignoredParts));
        this.source = source;
        this.people = Collections.unmodifiableList(PoseGroup.normalize(people));
        this.pose = this.people.get(0);
    }

    public static PoseTemplate fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        int version = o.optInt("formatVersion", 1);
        if (version < 1 || version > FORMAT_VERSION) {
            throw new JSONException("Unsupported pose format version " + version);
        }
        List<BodyPose> people = new ArrayList<>();
        if (version == 1) {
            people.add(readPose(o.getJSONArray("landmarks")));
        } else {
            JSONArray members = o.getJSONArray("people");
            if (members.length() < 1 || members.length() > PoseGroup.MAX_PEOPLE) {
                throw new JSONException("Expected 1 to " + PoseGroup.MAX_PEOPLE + " people");
            }
            for (int i = 0; i < members.length(); i++) {
                people.add(readPose(members.getJSONArray(i)));
            }
        }

        JSONObject s = o.optJSONObject("source");
        Source source = s == null
                ? new Source("unknown", null, null, null, null, null)
                : new Source(s.optString("type", "unknown"), optString(s, "title"),
                optString(s, "author"), optString(s, "url"), optString(s, "license"),
                optString(s, "licenseUrl"));

        try {
            return new PoseTemplate(o.getString("id"), o.getString("name"),
                    o.optString("description", ""), o.optString("category", "casual_standing"),
                    o.optString("difficulty", "easy"),
                    "upper_body".equals(o.optString("framing")) ? Framing.UPPER_BODY
                            : Framing.FULL_BODY,
                    o.optBoolean("mirrorAllowed", true), stringList(o.optJSONArray("instructions")),
                    stringList(o.optJSONArray("scenes")), stringList(o.optJSONArray("ignoredParts")),
                    source, people);
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    private static BodyPose readPose(JSONArray landmarks) throws JSONException {
        if (landmarks.length() != Landmark.COUNT) {
            throw new JSONException("Expected " + Landmark.COUNT + " landmarks");
        }
        float[] x = new float[Landmark.COUNT];
        float[] y = new float[Landmark.COUNT];
        float[] z = new float[Landmark.COUNT];
        float[] v = new float[Landmark.COUNT];
        for (int i = 0; i < Landmark.COUNT; i++) {
            JSONArray p = landmarks.getJSONArray(i);
            x[i] = (float) p.getDouble(0);
            y[i] = (float) p.getDouble(1);
            z[i] = (float) p.getDouble(2);
            v[i] = (float) p.getDouble(3);
            if (!Float.isFinite(x[i]) || !Float.isFinite(y[i]) || !Float.isFinite(z[i])
                    || !Float.isFinite(v[i]) || v[i] < 0f || v[i] > 1f) {
                throw new JSONException("Invalid landmark coordinate or visibility");
            }
        }
        return new BodyPose(x, y, z, v);
    }

    public String toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("formatVersion", people.size() == 1 ? 1 : FORMAT_VERSION);
        o.put("id", id);
        o.put("name", name);
        o.put("description", description);
        o.put("category", category);
        o.put("difficulty", difficulty);
        o.put("framing", framing == Framing.UPPER_BODY ? "upper_body" : "full_body");
        o.put("mirrorAllowed", mirrorAllowed);
        o.put("instructions", new JSONArray(instructions));
        o.put("scenes", new JSONArray(scenes));
        o.put("ignoredParts", new JSONArray(ignoredParts));

        JSONObject s = new JSONObject();
        s.put("type", source.type);
        s.putOpt("title", source.title);
        s.putOpt("author", source.author);
        s.putOpt("url", source.url);
        s.putOpt("license", source.license);
        s.putOpt("licenseUrl", source.licenseUrl);
        o.put("source", s);

        if (people.size() == 1) {
            o.put("landmarks", writePose(pose));
        } else {
            JSONArray members = new JSONArray();
            for (BodyPose person : people) {
                members.put(writePose(person));
            }
            o.put("people", members);
        }
        return o.toString(2);
    }

    private static JSONArray writePose(BodyPose pose) throws JSONException {
        JSONArray landmarks = new JSONArray();
        for (int i = 0; i < Landmark.COUNT; i++) {
            JSONArray p = new JSONArray();
            p.put(round(pose.x[i]));
            p.put(round(pose.y[i]));
            p.put(round(pose.z[i]));
            p.put(round(pose.visibility[i]));
            landmarks.put(p);
        }
        return landmarks;
    }

    private static double round(float value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    @Nullable
    private static String optString(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }

    private static List<String> stringList(@Nullable JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                list.add(array.optString(i));
            }
        }
        return list;
    }
}
