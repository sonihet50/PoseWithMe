package com.s23cp5054.posewithme.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.util.Consumer;

/**
 * One-shot location with the platform LocationManager (no Play Services dependency). Falls
 * back to the most recent known fix if a fresh one doesn't arrive in time.
 */
public final class LocationHelper {

    private static final long TIMEOUT_MS = 12_000;
    /** A cached fix younger than this is good enough to skip waiting. */
    private static final long FRESH_ENOUGH_MS = 2 * 60_000;

    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public LocationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isEnabled() {
        return manager != null && LocationManagerCompat.isLocationEnabled(manager);
    }

    /** Delivers a location (or null) on the main thread. Caller must hold a location permission. */
    @SuppressWarnings("MissingPermission")
    public void getCurrentLocation(Consumer<Location> callback) {
        if (manager == null || !hasPermission()) {
            callback.accept(null);
            return;
        }
        Location cached = lastKnown();
        if (cached != null && System.currentTimeMillis() - cached.getTime() < FRESH_ENOUGH_MS) {
            callback.accept(cached);
            return;
        }
        String provider = pickProvider();
        if (provider == null) {
            callback.accept(cached);
            return;
        }
        CancellationSignal cancel = new CancellationSignal();
        boolean[] done = {false};
        handler.postDelayed(() -> {
            if (!done[0]) {
                done[0] = true;
                cancel.cancel();
                callback.accept(cached);
            }
        }, TIMEOUT_MS);
        LocationManagerCompat.getCurrentLocation(manager, provider, cancel,
                ContextCompat.getMainExecutor(context), location -> {
                    if (!done[0]) {
                        done[0] = true;
                        handler.removeCallbacksAndMessages(null);
                        callback.accept(location != null ? location : cached);
                    }
                });
    }

    @Nullable
    private String pickProvider() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER;
        }
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return LocationManager.GPS_PROVIDER;
        }
        return null;
    }

    @SuppressWarnings("MissingPermission")
    @Nullable
    private Location lastKnown() {
        Location best = null;
        for (String provider : manager.getProviders(true)) {
            try {
                Location l = manager.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (SecurityException e) {
                // Provider needs a permission we don't hold (e.g. GPS with approximate only).
            }
        }
        return best;
    }
}
