package com.s23cp5054.posewithme.storage;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.camera.core.ImageCapture;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Where captured photos go: the shared Pictures/PoseWithMe album, via MediaStore. */
public final class PhotoStorage {

    public static final String ALBUM = "PoseWithMe";

    /** Android 9 and older need the legacy storage permission to add to shared Pictures. */
    public static boolean needsWritePermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    public static ImageCapture.OutputFileOptions outputOptions(Context context, long timeMs,
                                                               boolean mirror) {
        String name = "PoseWithMe_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date(timeMs));
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + ALBUM);
        }
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        metadata.setReversedHorizontal(mirror);
        return new ImageCapture.OutputFileOptions.Builder(context.getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                .setMetadata(metadata)
                .build();
    }

    /** A chooser for sending a photo through any app, granting read access to just that file. */
    public static Intent shareIntent(Uri uri, CharSequence title) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(send, title);
    }

    private PhotoStorage() {
    }
}
