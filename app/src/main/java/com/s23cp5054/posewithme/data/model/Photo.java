package com.s23cp5054.posewithme.data.model;

import android.net.Uri;

import androidx.annotation.Nullable;

/** A photo taken with the app: where it is stored and what pose it was taken with. */
public final class Photo {

    public final long id;
    public final Uri uri;
    @Nullable public final String poseId;
    @Nullable public final String poseName;
    /** Match score in [0, 1] at capture time, or null if no target pose was selected. */
    @Nullable public final Float score;
    public final long capturedAt;

    public Photo(long id, Uri uri, @Nullable String poseId, @Nullable String poseName,
                 @Nullable Float score, long capturedAt) {
        this.id = id;
        this.uri = uri;
        this.poseId = poseId;
        this.poseName = poseName;
        this.score = score;
        this.capturedAt = capturedAt;
    }
}
