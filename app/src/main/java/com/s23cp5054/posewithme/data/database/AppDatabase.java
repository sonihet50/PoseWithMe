package com.s23cp5054.posewithme.data.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * The app's SQLite database. Photos themselves live in shared media storage; this only keeps
 * metadata (content URI, pose, score, time) and the user's favourite poses.
 */
public final class AppDatabase extends SQLiteOpenHelper {

    private static final String NAME = "posewithme.db";
    private static final int VERSION = 1;

    public static final String TABLE_PHOTOS = "photos";
    public static final String PHOTO_ID = "_id";
    public static final String PHOTO_URI = "uri";
    public static final String PHOTO_POSE_ID = "pose_id";
    public static final String PHOTO_POSE_NAME = "pose_name";
    /** Match score in [0, 1] at capture time, or NULL when no target pose was selected. */
    public static final String PHOTO_SCORE = "match_score";
    public static final String PHOTO_CAPTURED_AT = "captured_at";

    public static final String TABLE_FAVORITES = "favorites";
    public static final String FAVORITE_POSE_ID = "pose_id";
    public static final String FAVORITE_ADDED_AT = "added_at";

    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = new AppDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AppDatabase(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PHOTOS + " ("
                + PHOTO_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + PHOTO_URI + " TEXT NOT NULL UNIQUE, "
                + PHOTO_POSE_ID + " TEXT, "
                + PHOTO_POSE_NAME + " TEXT, "
                + PHOTO_SCORE + " REAL, "
                + PHOTO_CAPTURED_AT + " INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_photos_captured ON " + TABLE_PHOTOS
                + "(" + PHOTO_CAPTURED_AT + " DESC)");
        db.execSQL("CREATE TABLE " + TABLE_FAVORITES + " ("
                + FAVORITE_POSE_ID + " TEXT PRIMARY KEY, "
                + FAVORITE_ADDED_AT + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first schema; future versions migrate here step by step.
    }
}
