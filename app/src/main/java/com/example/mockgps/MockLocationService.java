package com.example.mockgps;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

/**
 * Har second phone ke GPS + Network provider (aur Google Fused provider) ko
 * aapki chuni hui location bhejta rehta hai. Isse baaki sab apps wahi location dekhti hain.
 */
public class MockLocationService extends Service {

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static volatile boolean isRunning = false;

    private static final String TAG = "MockGPS";
    private static final String CHANNEL_ID = "mock_gps";
    private static final String[] PROVIDERS = {
            LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER
    };

    private LocationManager lm;
    private FusedLocationProviderClient fused;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private double lat, lng;

    private final Runnable pusher = new Runnable() {
        @Override public void run() {
            pushLocation();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        fused = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        lat = intent.getDoubleExtra(EXTRA_LAT, 0);
        lng = intent.getDoubleExtra(EXTRA_LNG, 0);

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, n);
        }

        try {
            setupProviders();
        } catch (SecurityException e) {
            Log.e(TAG, "Mock location app select nahi hai", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        isRunning = true;
        handler.removeCallbacks(pusher);
        handler.post(pusher);
        return START_STICKY;
    }

    private void setupProviders() {
        for (String p : PROVIDERS) {
            try { lm.removeTestProvider(p); } catch (Exception ignored) { }
            lm.addTestProvider(p, false, false, false, false, true, true, true,
                    1 /* POWER_LOW */, 1 /* ACCURACY_FINE */);
            lm.setTestProviderEnabled(p, true);
        }
        try { fused.setMockMode(true); } catch (SecurityException ignored) { }
    }

    @SuppressLint("MissingPermission")
    private void pushLocation() {
        for (String p : PROVIDERS) {
            try {
                lm.setTestProviderLocation(p, makeLocation(p));
            } catch (Exception e) {
                Log.w(TAG, "push failed for " + p, e);
            }
        }
        try { fused.setMockLocation(makeLocation("fused")); } catch (Exception ignored) { }
    }

    private Location makeLocation(String provider) {
        Location l = new Location(provider);
        l.setLatitude(lat);
        l.setLongitude(lng);
        l.setAltitude(400);
        l.setAccuracy(3f);
        l.setSpeed(0f);
        l.setBearing(0f);
        l.setTime(System.currentTimeMillis());
        l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            l.setVerticalAccuracyMeters(3f);
            l.setSpeedAccuracyMetersPerSecond(0.1f);
            l.setBearingAccuracyDegrees(0.1f);
        }
        return l;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Mock GPS", NotificationManager.IMPORTANCE_LOW));
        }
        Intent stop = new Intent(this, MockLocationService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent openPi = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle("Mock location ON")
                .setContentText(String.format("%.6f, %.6f", lat, lng))
                .setOngoing(true)
                .setContentIntent(openPi)
                .addAction(0, "STOP", stopPi)
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(pusher);
        for (String p : PROVIDERS) {
            try { lm.removeTestProvider(p); } catch (Exception ignored) { }
        }
        try { fused.setMockMode(false); } catch (Exception ignored) { }
        isRunning = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
