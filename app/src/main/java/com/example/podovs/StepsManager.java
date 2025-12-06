package com.example.podovs;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StepsManager {

    public interface Callback {
        void onStepsUpdated(long stepsToday, double kmHoy);
    }

    private static final String SP_NAME = "steps_prefs";
    private static final String KEY_BASE_PREFIX = "base_";
    private static final String KEY_TOTAL_PREFIX = "total_";
    private static final String KEY_LAST_COUNTER = "last_counter";
    private static final String KEY_LAST_COUNTER_DAY = "last_counter_day";
    private static final String KEY_RECORD_STEPS = "record_steps";
    private static final String KEY_RECORD_DAY = "record_day";

    private static final double METROS_POR_PASO = 0.78;

    private final Context appCtx;
    private final Callback callback;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SensorManager sensorManager;
    private final Sensor stepCounter;
    private final Sensor stepDetector;

    private final PowerManager.WakeLock wakeLock;

    private boolean usingDetector = false;
    private volatile boolean listening = false;

    private String keyBaseToday, keyTotalToday;
    private float baseOffset = -1f;
    private long stepsToday = 0L;

    private ScheduledExecutorService tickScheduler;

    private FirestoreRepo repo;
    private String userId;
    private FirebaseFirestore fs;
    private ListenerRegistration activeVsListener;
    private final List<String> activeVersusIds = new ArrayList<>();
    private long lastPushedSteps = -1L;

    // ================== CONSTRUCTORES ==================

    public StepsManager(Context context, Callback callback) {
        this.appCtx = context.getApplicationContext();
        this.callback = callback;

        sensorManager = (SensorManager) appCtx.getSystemService(Context.SENSOR_SERVICE);
        stepCounter = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
        stepDetector = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) : null;

        keyBaseToday = todayKey(KEY_BASE_PREFIX);
        keyTotalToday = todayKey(KEY_TOTAL_PREFIX);

        SharedPreferences prefs = appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        baseOffset = prefs.getFloat(keyBaseToday, -1f);
        stepsToday = prefs.getLong(keyTotalToday, 0L);

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

        PowerManager pm = (PowerManager) appCtx.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PodoVS:StepWakeLock");
            wakeLock.setReferenceCounted(false);
        } else {
            wakeLock = null;
        }
    }

    public StepsManager(Context context, FirestoreRepo repo, String uid, Callback callback) {
        this(context, callback);
        this.repo = repo;
        this.userId = uid;
        this.fs = FirebaseFirestore.getInstance();
        startActiveVersusListener();
    }

    // ================== CONTROL SENSOR ==================

    public void start() {
        if (listening) {
            emitUpdate();
            return;
        }

        if (sensorManager == null) {
            listening = false;
            emitUpdate();
            return;
        }

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

        if (wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire();
            } catch (Exception ignored) {
            }
        }

        if (tickScheduler == null || tickScheduler.isShutdown()) {
            tickScheduler = Executors.newSingleThreadScheduledExecutor();
            tickScheduler.scheduleAtFixedRate(this::emitUpdate, 0, 5, TimeUnit.SECONDS);
        }
        emitUpdate();
    }

    public void stop() {
        listening = false;

        if (sensorManager != null) {
            sensorManager.unregisterListener(listener);
        }

        if (tickScheduler != null) {
            tickScheduler.shutdownNow();
            tickScheduler = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }

        stopActiveVersusListener();
    }

    public long getStepsToday() {
        return stepsToday;
    }

    public double getKmToday() {
        return round2(stepsToday * METROS_POR_PASO / 1000.0);
    }

    public long getDailyRecordLocal() {
        return appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_RECORD_STEPS, 0L);
    }

    // ================== SENSOR CALLBACK ==================

    private final SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            ensureTodayKeys();

            SharedPreferences prefs = appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);

            if (!usingDetector && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                float totalSinceBoot = event.values[0];

                float lastCounter = prefs.getFloat(KEY_LAST_COUNTER, -1f);
                String lastDay = prefs.getString(KEY_LAST_COUNTER_DAY, "");
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

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    // ================== EMISIÓN / SYNC VERSUS ==================

    private void emitUpdate() {
        final long s = stepsToday;
        final double km = round2(s * METROS_POR_PASO / 1000.0);

        if (repo != null && userId != null) {
            if (s != lastPushedSteps) {
                lastPushedSteps = s;
                pushStepsToAllActiveVersus(s);
            }
        }

        mainHandler.post(() -> {
            if (callback != null) callback.onStepsUpdated(s, km);
        });
    }

    private void pushStepsToAllActiveVersus(long steps) {
        List<String> vsIdsCopy;
        synchronized (activeVersusIds) {
            vsIdsCopy = new ArrayList<>(activeVersusIds);
        }
        for (String vsId : vsIdsCopy) {
            repo.updateVersusStepsQuiet(vsId, userId, steps);
        }
    }

    private void startActiveVersusListener() {
        if (fs == null || userId == null) return;

        stopActiveVersusListener();

        activeVsListener = fs.collection("versus")
                .whereArrayContains("ver_players", userId)
                .whereEqualTo("ver_finished", false)
                .addSnapshotListener((QuerySnapshot qs, FirebaseFirestoreException e) -> {
                    if (e != null || qs == null) return;
                    synchronized (activeVersusIds) {
                        activeVersusIds.clear();
                        for (DocumentSnapshot d : qs.getDocuments()) {
                            activeVersusIds.add(d.getId());
                        }
                    }
                });
    }

    private void stopActiveVersusListener() {
        if (activeVsListener != null) {
            activeVsListener.remove();
            activeVsListener = null;
        }
        synchronized (activeVersusIds) {
            activeVersusIds.clear();
        }
    }

    // ================== PERSISTENCIA LOCAL ==================

    private String todayKey(String prefix) {
        return prefix + dayCode();
    }

    private String dayCode() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    private void ensureTodayKeys() {
        String expectedBase = todayKey(KEY_BASE_PREFIX);
        if (!expectedBase.equals(keyBaseToday)) {
            SharedPreferences prefs = appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
            long record = prefs.getLong(KEY_RECORD_STEPS, 0L);
            if (stepsToday > record) {
                prefs.edit()
                        .putLong(KEY_RECORD_STEPS, stepsToday)
                        .putString(KEY_RECORD_DAY, dayCode())
                        .apply();
            }
            keyBaseToday = expectedBase;
            keyTotalToday = todayKey(KEY_TOTAL_PREFIX);
            baseOffset = -1f;
            stepsToday = 0L;
            prefs.edit().remove(keyTotalToday).apply();
        }
    }

    private void saveToday(long steps) {
        appCtx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
                .putLong(keyTotalToday, steps)
                .apply();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
