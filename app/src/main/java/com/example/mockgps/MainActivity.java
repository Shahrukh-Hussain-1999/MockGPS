package com.example.mockgps;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private EditText latInput, lngInput, searchInput;
    private Button toggleBtn;
    private TextView statusText;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("mockgps", MODE_PRIVATE);
        Configuration.getInstance().setUserAgentValue("MockGPS-Personal/1.0");
        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);
        latInput = findViewById(R.id.latInput);
        lngInput = findViewById(R.id.lngInput);
        searchInput = findViewById(R.id.searchInput);
        toggleBtn = findViewById(R.id.toggleBtn);
        statusText = findViewById(R.id.statusText);

        // OSM ke volunteer server block karte hain, isliye CARTO ke free tiles use kar rahe hain
        map.setTileSource(new XYTileSource("CartoVoyager", 1, 20, 256, ".png",
                new String[]{
                        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
                        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
                        "https://d.basemaps.cartocdn.com/rastertiles/voyager/"},
                "© OpenStreetMap contributors © CARTO"));
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        marker = new Marker(map);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setDraggable(true);
        marker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDrag(Marker m) { }
            @Override public void onMarkerDragStart(Marker m) { }
            @Override public void onMarkerDragEnd(Marker m) { setPoint(m.getPosition(), false); }
        });
        map.getOverlays().add(marker);

        map.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { setPoint(p, false); return true; }
            @Override public boolean longPressHelper(GeoPoint p) { setPoint(p, false); return true; }
        }));

        // Last saved location (default: Jaipur)
        double lat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(26.9124)));
        double lng = Double.longBitsToDouble(prefs.getLong("lng", Double.doubleToLongBits(75.7873)));
        setPoint(new GeoPoint(lat, lng), true);

        findViewById(R.id.searchBtn).setOnClickListener(v -> doSearch());
        searchInput.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) { doSearch(); return true; }
            return false;
        });
        toggleBtn.setOnClickListener(v -> {
            if (MockLocationService.isRunning) stopMock(); else startMock();
        });

        requestPermissionsIfNeeded();
    }

    @Override protected void onResume() {
        super.onResume();
        map.onResume();
        refreshUi();
    }

    @Override protected void onPause() {
        super.onPause();
        map.onPause();
    }

    private void setPoint(GeoPoint p, boolean center) {
        marker.setPosition(p);
        latInput.setText(String.format(Locale.US, "%.6f", p.getLatitude()));
        lngInput.setText(String.format(Locale.US, "%.6f", p.getLongitude()));
        if (center) map.getController().setCenter(p);
        map.invalidate();
        if (MockLocationService.isRunning) startMock(); // chalte-chalte location update
    }

    // ---------- Start / Stop ----------

    private void startMock() {
        double lat, lng;
        try {
            lat = Double.parseDouble(latInput.getText().toString().trim());
            lng = Double.parseDouble(lngInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            toast("Latitude / Longitude sahi daalein");
            return;
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            toast("Range galat hai");
            return;
        }
        if (!hasLocationPermission()) { requestPermissionsIfNeeded(); return; }
        if (!isMockAppSelected()) { showMockSetupDialog(); return; }

        prefs.edit()
                .putLong("lat", Double.doubleToLongBits(lat))
                .putLong("lng", Double.doubleToLongBits(lng))
                .apply();

        Intent i = new Intent(this, MockLocationService.class)
                .setAction(MockLocationService.ACTION_START)
                .putExtra(MockLocationService.EXTRA_LAT, lat)
                .putExtra(MockLocationService.EXTRA_LNG, lng);
        ContextCompat.startForegroundService(this, i);
        MockLocationService.isRunning = true;
        refreshUi();
    }

    private void stopMock() {
        startService(new Intent(this, MockLocationService.class)
                .setAction(MockLocationService.ACTION_STOP));
        MockLocationService.isRunning = false;
        refreshUi();
    }

    private void refreshUi() {
        if (MockLocationService.isRunning) {
            toggleBtn.setText("STOP – Asli location wapas");
            statusText.setText("✅ Fake location ON – baaki apps ab yahi location dekhengi");
        } else {
            toggleBtn.setText("START – Ye location set karo");
            statusText.setText("Map par tap karein ya search karein");
        }
    }

    // ---------- Mock location checks ----------

    /** Check: kya Developer options me "Select mock location app" = Mock GPS hai? */
    private boolean isMockAppSelected() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String test = "mockgps_check";
        try {
            lm.addTestProvider(test, false, false, false, false, false, false, false, 1, 1);
            lm.removeTestProvider(test);
            return true;
        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private void showMockSetupDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Ek baar setup karna hoga")
                .setMessage("1. Settings → About phone → 'Build number' par 7 baar tap karein (Developer options ON).\n\n"
                        + "2. Settings → Developer options → 'Select mock location app' → 'Mock GPS' chunein.\n\n"
                        + "3. Wapas aakar START dabayein.")
                .setPositiveButton("Developer options kholo", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                    } catch (ActivityNotFoundException e) {
                        startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
                    }
                })
                .setNegativeButton("Baad me", null)
                .show();
    }

    // ---------- Permissions ----------

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionsIfNeeded() {
        List<String> need = new ArrayList<>();
        if (!hasLocationPermission()) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 10);
        }
    }

    // ---------- Search (OpenStreetMap Nominatim, free, no API key) ----------

    private void doSearch() {
        String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        statusText.setText("Search ho raha hai…");

        new Thread(() -> {
            try {
                URL url = new URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q="
                        + URLEncoder.encode(q, "UTF-8"));
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestProperty("User-Agent", "MockGPS-Personal/1.0 (Android)");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONArray arr = new JSONArray(sb.toString());
                runOnUiThread(() -> {
                    try {
                        if (arr.length() == 0) { toast("Jagah nahi mili"); refreshUi(); return; }
                        JSONObject o = arr.getJSONObject(0);
                        GeoPoint p = new GeoPoint(o.getDouble("lat"), o.getDouble("lon"));
                        setPoint(p, true);
                        map.getController().setZoom(16.0);
                        refreshUi();
                        toast(o.optString("display_name"));
                    } catch (Exception e) { toast("Error: " + e.getMessage()); }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { toast("Internet check karein"); refreshUi(); });
            }
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
