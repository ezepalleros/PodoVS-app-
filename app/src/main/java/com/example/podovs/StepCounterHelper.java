package com.example.podovs;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StepCounterHelper implements SensorEventListener {

    public interface Listener {
        void onStepsToday(long stepsToday);
    }

    private final Context ctx;
    private final SensorManager sensorManager;
    private final Sensor stepCounter;
    private final Listener listener;

    private static final String SP_NAME = "steps_prefs";
    private static final String KEY_BASE_PREFIX = "base_"; // + yyyMMdd
    private static final String KEY_LAST_TOTAL_PREFIX = "total_"; // opcional

    private String todayKeyBase;
    private String todayKeyTotal;

    private float baseOffset = -1f; // valor del sensor tomado como "inicio del día"

    public StepCounterHelper(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        this.sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        this.stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        String ymd = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        this.todayKeyBase = KEY_BASE_PREFIX + ymd;
        this.todayKeyTotal = KEY_LAST_TOTAL_PREFIX + ymd;
        restoreBase();
    }

    /** Registrar el listener. Llamar en onResume(). */
    public boolean register() {
        if (stepCounter == null) return false;
        sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
        return true;
    }

    /** Desregistrar. Llamar en onPause(). */
    public void unregister() {
        sensorManager.unregisterListener(this);
    }

    /** Reinicia la base si cambió el día (por si la app queda abierta a las 00:00). */
    private void ensureTodayKeys() {
        String ymd = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String expectedBase = KEY_BASE_PREFIX + ymd;
        if (!expectedBase.equals(todayKeyBase)) {
            todayKeyBase = expectedBase;
            todayKeyTotal = KEY_LAST_TOTAL_PREFIX + ymd;
            baseOffset = -1f;
            restoreBase(); // crea nueva base para el nuevo día
        }
    }

    private void restoreBase() {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        if (sp.contains(todayKeyBase)) {
            baseOffset = sp.getFloat(todayKeyBase, -1f);
        }
    }

    private void saveBase(float base) {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putFloat(todayKeyBase, base).apply();
    }

    private void saveLastTotal(long stepsToday) {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putLong(todayKeyTotal, stepsToday).apply();
    }

    public long getLastSavedToday() {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getLong(todayKeyTotal, 0L);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;

        ensureTodayKeys();

        float totalSinceBoot = event.values[0]; // pasos acumulados desde último reboot
        if (baseOffset < 0f) {
            baseOffset = totalSinceBoot; // primera lectura del día
            saveBase(baseOffset);
        }
        long stepsToday = Math.max(0L, Math.round(totalSinceBoot - baseOffset));
        saveLastTotal(stepsToday);

        if (listener != null) listener.onStepsToday(stepsToday);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* no-op */ }

    /** Devuelve true si el dispositivo tiene sensor de pasos. */
    public boolean isStepCounterAvailable() {
        return stepCounter != null;
    }
}
