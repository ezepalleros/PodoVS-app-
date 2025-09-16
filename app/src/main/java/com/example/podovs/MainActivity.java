package com.example.podovs;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private TextView tvBienvenido, tvKmHoy, tvKmSemana, tvPasosHoy;
    private DatabaseHelper db;
    private long userId = -1L;

    // Sensores (estilo CuentaPasos)
    private SensorManager sensorManager;
    private Sensor stepCounter;     // preferido
    private Sensor stepDetector;    // fallback
    private boolean usingDetector = false;
    private boolean listening = false;

    // Persistencia diaria
    private static final String SP_NAME = "steps_prefs";
    private static final String KEY_BASE_PREFIX  = "base_";   // yyyyMMdd
    private static final String KEY_TOTAL_PREFIX = "total_";  // yyyyMMdd
    private String keyBaseToday, keyTotalToday;
    private float baseOffset = -1f; // primer valor del día para STEP_COUNTER
    private long stepsToday = 0L;

    private static final double METROS_POR_PASO = 0.78;

    // Guardado en BDD cada 3 s (solo en foreground)
    private ScheduledExecutorService dbScheduler;

    private final ActivityResultLauncher<String[]> permsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean arGranted = result.getOrDefault(Manifest.permission.ACTIVITY_RECOGNITION, false);
                if (arGranted) startListening();
                else Toast.makeText(this, "Permiso de actividad física denegado.", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvBienvenido = findViewById(R.id.tvBienvenido);
        tvKmHoy      = findViewById(R.id.tvKmHoy);
        tvKmSemana   = findViewById(R.id.tvKmSemana);
        tvPasosHoy   = findViewById(R.id.tvPasosHoy);

        db = new DatabaseHelper(this);

        // Sesión
        SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
        userId = sp.getLong("user_id", -1L);
        String nombre = sp.getString("user_name", null);
        if (userId <= 0 || nombre == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish(); return;
        }
        tvBienvenido.setText("Bienvenido, " + nombre);

        // Cargar BDD inicial
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT " + DatabaseHelper.COL_KM_HOY + ", " + DatabaseHelper.COL_KM_SEMANA +
                        " FROM " + DatabaseHelper.TABLE_USUARIOS +
                        " WHERE " + DatabaseHelper.COL_ID + "=? LIMIT 1",
                new String[]{String.valueOf(userId)}
        );
        if (c != null && c.moveToFirst()) {
            double kmHoy    = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_KM_HOY));
            double kmSemana = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_KM_SEMANA));
            c.close();
            tvKmHoy.setText(String.format(Locale.getDefault(),"Kilómetros hoy: %.2f km", kmHoy));
            tvKmSemana.setText(String.format(Locale.getDefault(),"Kilómetros esta semana: %.2f km", kmSemana));
        }

        // Claves/persistencia del día
        keyBaseToday  = todayKey(KEY_BASE_PREFIX);
        keyTotalToday = todayKey(KEY_TOTAL_PREFIX);
        SharedPreferences prefs = getSharedPreferences(SP_NAME, MODE_PRIVATE);
        baseOffset = prefs.getFloat(keyBaseToday, -1f);
        stepsToday = prefs.getLong(keyTotalToday, 0L);
        tvPasosHoy.setText("Pasos hoy: " + stepsToday);
        tvKmHoy.setText(String.format(Locale.getDefault(),"Kilómetros hoy: %.2f km",
                round2(stepsToday * METROS_POR_PASO / 1000.0)));

        // Sensores
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepCounter   = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        stepDetector  = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        // Permisos
        requestRuntimePermissions();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ensureARGranted() && !listening) startListening();
    }

    @Override protected void onPause() {
        super.onPause();
        stopListening();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopListening();
        if (dbScheduler != null) dbScheduler.shutdownNow();
    }

    // ====== Registro al sensor (sin servicio) ======
    private void startListening() {
        if (listening) return;
        listening = true;

        if (stepCounter != null) {
            usingDetector = false;
            sensorManager.registerListener(localListener, stepCounter, SensorManager.SENSOR_DELAY_GAME);
        } else if (stepDetector != null) {
            usingDetector = true;
            sensorManager.registerListener(localListener, stepDetector, SensorManager.SENSOR_DELAY_GAME);
        } else {
            Toast.makeText(this, "El dispositivo no tiene sensor de pasos.", Toast.LENGTH_LONG).show();
            listening = false;
            return;
        }

        // BDD cada 3 s
        dbScheduler = Executors.newSingleThreadScheduledExecutor();
        dbScheduler.scheduleAtFixedRate(() -> {
            long s = stepsToday;
            double km = round2(s * METROS_POR_PASO / 1000.0);
            if (userId > 0) {
                db.getWritableDatabase().execSQL(
                        "UPDATE " + DatabaseHelper.TABLE_USUARIOS +
                                " SET " + DatabaseHelper.COL_KM_HOY + " = ? WHERE " + DatabaseHelper.COL_ID + " = ?",
                        new Object[]{km, userId}
                );
            }
            runOnUiThread(() -> tvKmHoy.setText(String.format(Locale.getDefault(),"Kilómetros hoy: %.2f km", km)));
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void stopListening() {
        if (!listening) return;
        listening = false;
        try { sensorManager.unregisterListener(localListener); } catch (Exception ignore) {}
        if (dbScheduler != null) { dbScheduler.shutdownNow(); dbScheduler = null; }
    }

    // Listener del sensor: UI en tiempo real + persistencia en prefs
    private final SensorEventListener localListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            ensureTodayKeys();

            if (!usingDetector && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                float totalSinceBoot = event.values[0];
                if (baseOffset < 0f) {
                    baseOffset = totalSinceBoot;
                    saveBase(baseOffset);
                }
                long val = Math.max(0L, Math.round(totalSinceBoot - baseOffset));
                if (val != stepsToday) {
                    stepsToday = val;
                    saveToday(stepsToday);
                    tvPasosHoy.setText("Pasos hoy: " + stepsToday);
                    tvKmHoy.setText(String.format(Locale.getDefault(),"Kilómetros hoy: %.2f km",
                            round2(stepsToday * METROS_POR_PASO / 1000.0)));
                }
                return;
            }

            if (usingDetector && event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                int inc = (int) (event.values.length > 0 ? event.values[0] : 1);
                if (inc <= 0) inc = 1;
                stepsToday += inc;
                saveToday(stepsToday);
                tvPasosHoy.setText("Pasos hoy: " + stepsToday);
                tvKmHoy.setText(String.format(Locale.getDefault(),"Kilómetros hoy: %.2f km",
                        round2(stepsToday * METROS_POR_PASO / 1000.0)));
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // ====== Helpers día/prefs ======
    private String todayKey(String prefix) {
        String ymd = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        return prefix + ymd;
    }
    private void ensureTodayKeys() {
        String expected = todayKey(KEY_BASE_PREFIX);
        if (!expected.equals(keyBaseToday)) {
            keyBaseToday  = expected;
            keyTotalToday = todayKey(KEY_TOTAL_PREFIX);
            baseOffset = -1f;
            stepsToday = 0L;
            getSharedPreferences(SP_NAME, MODE_PRIVATE).edit().remove(keyTotalToday).apply();
        }
    }
    private void saveBase(float v) {
        getSharedPreferences(SP_NAME, MODE_PRIVATE).edit().putFloat(keyBaseToday, v).apply();
    }
    private void saveToday(long steps) {
        getSharedPreferences(SP_NAME, MODE_PRIVATE).edit().putLong(keyTotalToday, steps).apply();
    }

    // ====== Permisos ======
    private void requestRuntimePermissions() {
        boolean needAR = (Build.VERSION.SDK_INT >= 29) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED;
        if (needAR) {
            permsLauncher.launch(new String[]{ Manifest.permission.ACTIVITY_RECOGNITION });
        } else if (!listening) {
            startListening();
        }
    }
    private boolean ensureARGranted() {
        return !(Build.VERSION.SDK_INT >= 29) ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
