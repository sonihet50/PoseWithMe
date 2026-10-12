package com.s23cp5054.posewithme.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;

import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

/**
 * Turns RGBA_8888 frames from ImageAnalysis into an upright bitmap for the pose model, which
 * expects people to be standing up in the image. Both bitmaps are allocated once and reused,
 * so callers must finish with the returned bitmap before the next call.
 *
 * Not thread-safe: use from the single analysis thread only.
 */
public final class FrameConverter {

    private Bitmap sensorBitmap;
    private Bitmap uprightBitmap;
    private Canvas uprightCanvas;
    private final Matrix rotationMatrix = new Matrix();
    private int matrixRotation = -1;
    private int matrixWidth = -1;
    private int matrixHeight = -1;

    public Bitmap toUprightBitmap(ImageProxy image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int rotation = image.getImageInfo().getRotationDegrees();

        Bitmap source = copySensorPixels(image, width, height);

        int outWidth = rotation % 180 == 0 ? width : height;
        int outHeight = rotation % 180 == 0 ? height : width;
        if (uprightBitmap == null || uprightBitmap.getWidth() != outWidth
                || uprightBitmap.getHeight() != outHeight) {
            uprightBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
            uprightCanvas = new Canvas(uprightBitmap);
        }
        if (rotation != matrixRotation || width != matrixWidth || height != matrixHeight) {
            buildRotationMatrix(rotation, width, height);
        }
        // Row padding (if any) sits to the right of `width` and lands outside the canvas.
        uprightCanvas.drawBitmap(source, rotationMatrix, null);
        return uprightBitmap;
    }

    private Bitmap copySensorPixels(ImageProxy image, int width, int height) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        int rowPixels = plane.getRowStride() / plane.getPixelStride();
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        if (buffer.remaining() < (long) rowPixels * height * 4) {
            // Last row is not padded, so the buffer can't be bulk-copied; rare, take the slow path.
            return image.toBitmap();
        }
        if (sensorBitmap == null || sensorBitmap.getWidth() != rowPixels
                || sensorBitmap.getHeight() != height) {
            sensorBitmap = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888);
        }
        sensorBitmap.copyPixelsFromBuffer(buffer);
        return sensorBitmap;
    }

    private void buildRotationMatrix(int rotation, int width, int height) {
        rotationMatrix.reset();
        rotationMatrix.postRotate(rotation);
        switch (rotation) {
            case 90:
                rotationMatrix.postTranslate(height, 0);
                break;
            case 180:
                rotationMatrix.postTranslate(width, height);
                break;
            case 270:
                rotationMatrix.postTranslate(0, width);
                break;
            default:
                break;
        }
        matrixRotation = rotation;
        matrixWidth = width;
        matrixHeight = height;
    }
}
