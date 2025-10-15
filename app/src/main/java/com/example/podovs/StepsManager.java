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

/** Maneja conteo de pasos en foreground + persistencia diaria local + callback de UI.
 *  Toda persistencia remota (Firestore, etc.) debe hacerse en quien instancie, usando el callback. */
public class StepsManager {

    public interface Callback {
        /** Se llama SIEMPRE en el hilo principal. */
        void onStepsUpdated(long stepsToday, double kmHoy);
    }

    private static final String SP_NAME = "steps_prefs";
    private static final String KEY_BASE_PREFIX  = "base_";   // yyyyMMdd
    private static final String KEY_TOTAL_PREFIX = "total_";  // yyyyMMdd
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

    /** Constructor recomendado (sin BDD). */
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
    }

    /** Constructor legacy para compatibilidad con llamadas existentes: ignora db/userId. */
    public StepsManager(Context context, /*@Nullable*/ Object ignoredDb, long ignoredUserId, Callback callback) {
        this(context, callback);
    }

    /** Idempotente: si ya está escuchando, no hace nada. */
    public void start() {
        if (listening) { emitUpdate(); return; }

        if (stepCounter != null) {
            usingDetector = false;
            sensorManager.registerListener(listener, stepCounter, SensorManager.SENSOR_DELAY_GAME);
        } else if (stepDetector != null) {
            usingDetector = true;
            sensorManager.registerListener(listener, stepDetector, SensorManager.SENSOR_DELAY_GAME);
        } else {
            listening = false;
            emitUpdate();
            return;
        }
        listening = true;

        // “Tick” suave cada 3 s para refrescar UI aunque no haya eventos del sensor.
        if (tickScheduler == null || tickScheduler.isShutdown()) {
            tickScheduler = Executors.newSingleThreadScheduledExecutor();
            tickScheduler.scheduleAtFixedRate(() -> emitUpdate(), 0, 3, TimeUnit.SECONDS);
        }

        emitUpdate();
    }

    /** Detiene sensores y scheduler. Idempotente. */
    public void stop() {
        if (listening) {
            try { sensorManager.unregisterListener(listener); } catch (Exception ignore) {}
            listening = false;
        }
        if (tickScheduler != null) { tickScheduler.shutdownNow(); tickScheduler = null; }
    }

    public long getStepsToday() { return stepsToday; }

    // Listener interno
    private final SensorEventListener listener = new SensorEventListener() {
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
                    emitUpdate();
                }
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
        mainHandler.post(() -> {
            if (callback != null) callback.onStepsUpdated(s, km);
        });
    }

    private String todayKey(String prefix) {
        String ymd = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        return prefix + ymd;
    }

    private void ensureTodayKeys() {
        String expectedBase = todayKey(KEY_BASE_PREFIX);
        if (!expectedBase.equals(keyBaseToday)) {
            // Cambió el día: reseteamos claves y valores locales
            keyBaseToday  = expectedBase;
            keyTotalToday = todayKey(KEY_TOTAL_PREFIX);
            baseOffset = -1f;
            stepsToday = 0L;
            appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
                    .remove(keyTotalToday)   // limpiar total del nuevo día (por si existía)
                    .remove(expectedBase)    // asegurar recalibración del baseOffset
                    .apply();
        }
    }

    private void saveBase(float v) {
        appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
                .putFloat(keyBaseToday, v).apply();
    }

    private void saveToday(long steps) {
        appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
                .putLong(keyTotalToday, steps).apply();
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
