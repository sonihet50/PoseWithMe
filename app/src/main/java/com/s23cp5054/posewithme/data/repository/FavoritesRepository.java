package com.s23cp5054.posewithme.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.WorkerThread;

import com.s23cp5054.posewithme.data.database.AppDatabase;

import java.util.LinkedHashSet;
import java.util.Set;

/** The user's favourite poses, by pose id, in SQLite. */
public final class FavoritesRepository {

    private final AppDatabase db;

    public FavoritesRepository(Context context) {
        db = AppDatabase.get(context);
    }

    /** Newest favourite first. */
    @WorkerThread
    public Set<String> getAll() {
        Set<String> ids = new LinkedHashSet<>();
        try (Cursor c = db.getReadableDatabase().query(AppDatabase.TABLE_FAVORITES,
                new String[]{AppDatabase.FAVORITE_POSE_ID}, null, null, null, null,
                AppDatabase.FAVORITE_ADDED_AT + " DESC")) {
            while (c.moveToNext()) {
                ids.add(c.getString(0));
            }
        }
        return ids;
    }

    @WorkerThread
    public boolean isFavorite(String poseId) {
        try (Cursor c = db.getReadableDatabase().query(AppDatabase.TABLE_FAVORITES,
                new String[]{AppDatabase.FAVORITE_POSE_ID}, AppDatabase.FAVORITE_POSE_ID + " = ?",
                new String[]{poseId}, null, null, null)) {
            return c.moveToFirst();
        }
    }

    @WorkerThread
    public void setFavorite(String poseId, boolean favorite) {
        SQLiteDatabase w = db.getWritableDatabase();
        if (favorite) {
            ContentValues values = new ContentValues();
            values.put(AppDatabase.FAVORITE_POSE_ID, poseId);
            values.put(AppDatabase.FAVORITE_ADDED_AT, System.currentTimeMillis());
            w.insertWithOnConflict(AppDatabase.TABLE_FAVORITES, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            w.delete(AppDatabase.TABLE_FAVORITES, AppDatabase.FAVORITE_POSE_ID + " = ?",
                    new String[]{poseId});
        }
    }
}
