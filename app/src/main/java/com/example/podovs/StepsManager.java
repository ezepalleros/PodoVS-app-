package com.example.podovs;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Conteo de pasos local + callback de UI (sin BDD). */
public class StepsManager {

    public interface Callback { void onStepsUpdated(long stepsToday, double kmHoy); }

    private static final String SP_NAME = "steps_prefs";
    private static final String KEY_BASE_PREFIX   = "base_";        // yyyyMMdd
    private static final String KEY_TOTAL_PREFIX  = "total_";       // yyyyMMdd
    private static final String KEY_LAST_COUNTER  = "last_counter"; // float TYPE_STEP_COUNTER
    private static final String KEY_LAST_COUNTER_DAY = "last_counter_day";
    private static final String KEY_RECORD_STEPS  = "record_steps";
    private static final String KEY_RECORD_DAY    = "record_day";

    private static final double METROS_POR_PASO = 0.78;

    private final Context appCtx;
    private final Callback callback;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SensorManager sensorManager;
    private final Sensor stepCounter;
    private final Sensor stepDetector;

    private boolean usingDetector = false;
    private volatile boolean listening = false;

    private String keyBaseToday, keyTotalToday;
    private float baseOffset = -1f;
    private long stepsToday = 0L;

    private ScheduledExecutorService tickScheduler;

    public StepsManager(Context context, Callback callback) {
        this.appCtx = context.getApplicationContext();
        this.callback = callback;

        sensorManager = (SensorManager) appCtx.getSystemService(Context.SENSOR_SERVICE);
        stepCounter   = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        stepDetector  = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        keyBaseToday  = todayKey(KEY_BASE_PREFIX);
        keyTotalToday = todayKey(KEY_TOTAL_PREFIX);

        SharedPreferences prefs = appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        baseOffset = prefs.getFloat(keyBaseToday, -1f);
        stepsToday = prefs.getLong(keyTotalToday, 0L);

        // reconstruir base si ya teníamos contador acumulado guardado del mismo día
        if (baseOffset < 0f) {
            String today = dayCode();
            String lastDay = prefs.getString(KEY_LAST_COUNTER_DAY, "");
            if (today.equals(lastDay)) {
                float lastCounter = prefs.getFloat(KEY_LAST_COUNTER, -1f);
                if (lastCounter >= 0f) {
                    float reconstructed = lastCounter - stepsToday;
                    if (reconstructed < 0f) reconstructed = 0f;
                    baseOffset = reconstructed;
                    prefs.edit().putFloat(keyBaseToday, baseOffset).apply();
                }
            }
        }
    }

    // Compatibilidad con ctor legacy
    public StepsManager(Context context, Object ignoredDb, long ignoredUserId, Callback callback) {
        this(context, callback);
    }

    public void start() {
        if (listening) { emitUpdate(); return; }
        if (stepCounter != null) {
            usingDetector = false;
            sensorManager.registerListener(listener, stepCounter, SensorManager.SENSOR_DELAY_GAME);
        } else if (stepDetector != null) {
            usingDetector = true;
            sensorManager.registerListener(listener, stepDetector, SensorManager.SENSOR_DELAY_GAME);
        } else {
            listening = false; emitUpdate(); return;
        }
        listening = true;

        if (tickScheduler == null || tickScheduler.isShutdown()) {
            tickScheduler = Executors.newSingleThreadScheduledExecutor();
            tickScheduler.scheduleAtFixedRate(this::emitUpdate, 0, 3, TimeUnit.SECONDS);
        }
        emitUpdate();
    }

    public void stop() {
        if (listening) {
            try { sensorManager.unregisterListener(listener); } catch (Exception ignore) {}
            listening = false;
        }
        if (tickScheduler != null) { tickScheduler.shutdownNow(); tickScheduler = null; }
    }

    public long getStepsToday() { return stepsToday; }
    public double getKmToday()  { return round2(stepsToday * METROS_POR_PASO / 1000.0); }
    public long getDailyRecordLocal() {
        return appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getLong(KEY_RECORD_STEPS, 0L);
    }

    private final SensorEventListener listener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            ensureTodayKeys(); // también gestiona récord en el cambio de día

            SharedPreferences prefs = appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);

            if (!usingDetector && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                float totalSinceBoot = event.values[0];

                // detectar reinicio del contador (reboot)
                float lastCounter = prefs.getFloat(KEY_LAST_COUNTER, -1f);
                String lastDay    = prefs.getString(KEY_LAST_COUNTER_DAY, "");
                if (lastCounter >= 0f && totalSinceBoot < lastCounter) {
                    float recalib = totalSinceBoot - stepsToday;
                    if (recalib < 0f) recalib = 0f;
                    baseOffset = recalib;
                    prefs.edit().putFloat(keyBaseToday, baseOffset).apply();
                }

                if (baseOffset < 0f) {
                    baseOffset = totalSinceBoot - stepsToday;
                    if (baseOffset < 0f) baseOffset = totalSinceBoot;
                    prefs.edit().putFloat(keyBaseToday, baseOffset).apply();
                }

                long val = Math.max(0L, Math.round(totalSinceBoot - baseOffset));
                if (val != stepsToday) {
                    stepsToday = val;
                    saveToday(stepsToday);
                    emitUpdate();
                }

                prefs.edit()
                        .putFloat(KEY_LAST_COUNTER, totalSinceBoot)
                        .putString(KEY_LAST_COUNTER_DAY, dayCode())
                        .apply();
                return;
            }

            if (usingDetector && event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                int inc = (int) (event.values.length > 0 ? event.values[0] : 1);
                if (inc <= 0) inc = 1;
                stepsToday += inc;
                saveToday(stepsToday);
                emitUpdate();
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void emitUpdate() {
        final long s = stepsToday;
        final double km = round2(s * METROS_POR_PASO / 1000.0);
        mainHandler.post(() -> { if (callback != null) callback.onStepsUpdated(s, km); });
    }

    private String todayKey(String prefix) { return prefix + dayCode(); }
    private String dayCode() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    /** Si cambia el día, persiste récord local y resetea conteos para hoy. */
    private void ensureTodayKeys() {
        String expectedBase = todayKey(KEY_BASE_PREFIX);
        if (!expectedBase.equals(keyBaseToday)) {
            // guardar récord local si corresponde
            SharedPreferences prefs = appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
            long record = prefs.getLong(KEY_RECORD_STEPS, 0L);
            if (stepsToday > record) {
                prefs.edit()
                        .putLong(KEY_RECORD_STEPS, stepsToday)
                        .putString(KEY_RECORD_DAY, dayCode())
                        .apply();
            }
            // nuevo día
            keyBaseToday  = expectedBase;
            keyTotalToday = todayKey(KEY_TOTAL_PREFIX);
            baseOffset = -1f;
            stepsToday = 0L;
            prefs.edit().remove(keyTotalToday).apply();
        }
    }

    private void saveToday(long steps) {
        appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
                .putLong(keyTotalToday, steps).apply();
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
