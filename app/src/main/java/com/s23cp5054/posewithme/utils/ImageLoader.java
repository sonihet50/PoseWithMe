package com.s23cp5054.posewithme.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads photos off the main thread at roughly the size they are shown, never at full
 * resolution, with a small memory cache for gallery thumbnails.
 */
public final class ImageLoader {

    private static volatile ImageLoader instance;

    private final ContentResolver resolver;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    public static ImageLoader get(Context context) {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new ImageLoader(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private ImageLoader(Context appContext) {
        resolver = appContext.getContentResolver();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    /**
     * Shows {@code uri} in {@code view}, decoded to about {@code sizePx} on its longer side.
     * Safe with recycled views: a stale result is dropped if the view was rebound meanwhile.
     */
    public void load(Uri uri, ImageView view, int sizePx) {
        String key = uri + "@" + sizePx;
        view.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        executor.execute(() -> {
            Bitmap bitmap = decode(uri, sizePx);
            if (bitmap != null) {
                cache.put(key, bitmap);
            }
            mainHandler.post(() -> {
                if (key.equals(view.getTag()) && bitmap != null) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    @WorkerThread
    @Nullable
    public Bitmap decode(Uri uri, int sizePx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sizePx <= 512) {
                return resolver.loadThumbnail(uri, new Size(sizePx, sizePx), null);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    Size size = info.getSize();
                    int longer = Math.max(size.getWidth(), size.getHeight());
                    if (longer > sizePx) {
                        float scale = sizePx / (float) longer;
                        decoder.setTargetSize(Math.max(1, Math.round(size.getWidth() * scale)),
                                Math.max(1, Math.round(size.getHeight() * scale)));
                    }
                });
            }
            return decodeLegacy(uri, sizePx);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    /** API 24-27: two-pass BitmapFactory decode with a power-of-two sample size. */
    private Bitmap decodeLegacy(Uri uri, int sizePx) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        int longer = Math.max(bounds.outWidth, bounds.outHeight);
        while (longer / (sample * 2) >= sizePx) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            return null;
        }
        // BitmapFactory ignores the EXIF orientation that ImageDecoder applies for us.
        int degrees;
        try (InputStream in = resolver.openInputStream(uri)) {
            degrees = in == null ? 0 : new ExifInterface(in).getRotationDegrees();
        }
        if (degrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
                true);
    }
}
