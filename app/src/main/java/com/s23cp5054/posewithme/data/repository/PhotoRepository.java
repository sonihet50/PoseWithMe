package com.s23cp5054.posewithme.data.repository;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.s23cp5054.posewithme.data.database.AppDatabase;
import com.s23cp5054.posewithme.data.model.Photo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Photo metadata in SQLite, kept in step with the image files in MediaStore. */
public final class PhotoRepository {

    private final Context appContext;
    private final AppDatabase db;

    public PhotoRepository(Context context) {
        appContext = context.getApplicationContext();
        db = AppDatabase.get(appContext);
    }

    @WorkerThread
    public long insert(Uri uri, @Nullable String poseId, @Nullable String poseName,
                       @Nullable Float score, long capturedAt) {
        ContentValues values = new ContentValues();
        values.put(AppDatabase.PHOTO_URI, uri.toString());
        values.put(AppDatabase.PHOTO_POSE_ID, poseId);
        values.put(AppDatabase.PHOTO_POSE_NAME, poseName);
        if (score != null) {
            values.put(AppDatabase.PHOTO_SCORE, score);
        }
        values.put(AppDatabase.PHOTO_CAPTURED_AT, capturedAt);
        return db.getWritableDatabase().insertWithOnConflict(AppDatabase.TABLE_PHOTOS, null,
                values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Newest first. Rows whose image was deleted outside the app are dropped on the way. */
    @WorkerThread
    public List<Photo> getAll() {
        List<Photo> photos = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query(AppDatabase.TABLE_PHOTOS, null, null,
                null, null, null, AppDatabase.PHOTO_CAPTURED_AT + " DESC")) {
            while (c.moveToNext()) {
                Photo photo = read(c);
                if (exists(photo.uri)) {
                    photos.add(photo);
                } else {
                    missing.add(photo.id);
                }
            }
        }
        for (long id : missing) {
            deleteRow(id);
        }
        return photos;
    }

    @WorkerThread
    @Nullable
    public Photo get(long id) {
        try (Cursor c = db.getReadableDatabase().query(AppDatabase.TABLE_PHOTOS, null,
                AppDatabase.PHOTO_ID + " = ?", new String[]{String.valueOf(id)},
                null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    @WorkerThread
    @Nullable
    public Photo getLatest() {
        List<Photo> all = getAll();
        return all.isEmpty() ? null : all.get(0);
    }

    /** Deletes the image file (the app owns it) and its row. */
    @WorkerThread
    public void delete(Photo photo) {
        try {
            appContext.getContentResolver().delete(photo.uri, null, null);
        } catch (SecurityException | UnsupportedOperationException e) {
            // Already gone or no longer ours; dropping the row is still right.
        }
        deleteRow(photo.id);
    }

    /** How many photos were taken with each pose on this phone (real usage, nothing else). */
    @WorkerThread
    public Map<String, Integer> countByPose() {
        Map<String, Integer> counts = new HashMap<>();
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT " + AppDatabase.PHOTO_POSE_ID
                + ", COUNT(*) FROM " + AppDatabase.TABLE_PHOTOS + " WHERE "
                + AppDatabase.PHOTO_POSE_ID + " IS NOT NULL GROUP BY "
                + AppDatabase.PHOTO_POSE_ID, null)) {
            while (c.moveToNext()) {
                counts.put(c.getString(0), c.getInt(1));
            }
        }
        return counts;
    }

    @WorkerThread
    public int count() {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + AppDatabase.TABLE_PHOTOS, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private void deleteRow(long id) {
        db.getWritableDatabase().delete(AppDatabase.TABLE_PHOTOS, AppDatabase.PHOTO_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    private boolean exists(Uri uri) {
        ContentResolver resolver = appContext.getContentResolver();
        try (InputStream in = resolver.openInputStream(uri)) {
            return in != null;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private static Photo read(Cursor c) {
        int scoreIndex = c.getColumnIndexOrThrow(AppDatabase.PHOTO_SCORE);
        return new Photo(
                c.getLong(c.getColumnIndexOrThrow(AppDatabase.PHOTO_ID)),
                Uri.parse(c.getString(c.getColumnIndexOrThrow(AppDatabase.PHOTO_URI))),
                c.getString(c.getColumnIndexOrThrow(AppDatabase.PHOTO_POSE_ID)),
                c.getString(c.getColumnIndexOrThrow(AppDatabase.PHOTO_POSE_NAME)),
                c.isNull(scoreIndex) ? null : c.getFloat(scoreIndex),
                c.getLong(c.getColumnIndexOrThrow(AppDatabase.PHOTO_CAPTURED_AT)));
    }
}
