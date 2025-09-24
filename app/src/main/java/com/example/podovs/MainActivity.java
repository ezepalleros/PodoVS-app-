package com.example.podovs;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvKmTotalBig;     // PASOS HOY (número grande)
    private TextView tvKmSemanaSmall;  // "Semana: xx.xx km"
    private ImageView ivAvatar;

    // Contador de monedas
    private TextView tvCoins;

    private DatabaseHelper db;
    private long userId = -1L;

    private StepsManager stepsManager;

    // ---- Rollover diario/semana ----
    private static final double STEP_TO_KM = 0.0008; // 1 paso ~ 0.8 m
    private static final String PREFS = "podovs_prefs";
    private static final String KEY_PASOS_HOY = "pasos_hoy"; // guardado como LONG
    private static final String KEY_ULTIMO_DIA = "ultimo_dia";
    private static final String KEY_DIAS_CONTADOS = "dias_contados";

    private final ActivityResultLauncher<String[]> permsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean arGranted = result.getOrDefault(Manifest.permission.ACTIVITY_RECOGNITION, false)
                        || Build.VERSION.SDK_INT < 29; // en <29 no existe
                if (arGranted) {
                    if (stepsManager != null) stepsManager.start();
                } else {
                    Toast.makeText(this, "Permiso de actividad física denegado.", Toast.LENGTH_LONG).show();
                }
                // POST_NOTIFICATIONS puede ser denegado sin bloquear nada crítico
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvKmTotalBig    = findViewById(R.id.tvKmTotalBig);
        tvKmSemanaSmall = findViewById(R.id.tvKmSemanaSmall);
        ivAvatar        = findViewById(R.id.ivAvatar);
        tvCoins         = findViewById(R.id.tvCoins);

        ivAvatar.setImageResource(R.drawable.default_avatar);

        db = new DatabaseHelper(this);

        SharedPreferences spSession = getSharedPreferences("session", MODE_PRIVATE);
        userId = spSession.getLong("user_id", -1L);
        if (userId <= 0) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Inicializa clave de fecha si viene vacía
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.contains(KEY_ULTIMO_DIA)) {
            sp.edit().putString(KEY_ULTIMO_DIA, todayString()).apply();
        }

        // Mostrar saldo inicial
        refreshCoins();

        tvKmSemanaSmall.setText(String.format(Locale.getDefault(),
                "Semana: %.2f km", db.getKmSemana(userId)));
        // Mostramos los pasos almacenados (si los hay) al entrar
        long pasosGuardados = sp.getLong(KEY_PASOS_HOY, 0L);
        tvKmTotalBig.setText(String.valueOf(pasosGuardados));

        stepsManager = new StepsManager(this, db, userId, (stepsToday, kmHoy) -> {
            // Guardamos pasos del día para poder hacer el rollover aunque la app se pause
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit().putLong(KEY_PASOS_HOY, stepsToday).apply();

            tvKmTotalBig.setText(String.valueOf(stepsToday));
            tvKmSemanaSmall.setText(String.format(Locale.getDefault(),
                    "Semana: %.2f km", db.getKmSemana(userId)));
        });

        // --- Clicks de la fila superior ---
        findViewById(R.id.btnTopGoals).setOnClickListener(v -> openGoalsFragment());
        findViewById(R.id.btnTopStats).setOnClickListener(v -> openStatsFragment());
        findViewById(R.id.btnTopProfile).setOnClickListener(v ->
                Toast.makeText(this, "Perfil (próximamente)", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnTopNotifications).setOnClickListener(v ->
                Toast.makeText(this, "Notificaciones (próximamente)", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnTopOptions).setOnClickListener(v ->
                Toast.makeText(this, "Opciones (próximamente)", Toast.LENGTH_SHORT).show());

        // Escuchar cuando GoalsFragment actualiza las monedas
        getSupportFragmentManager().setFragmentResultListener(
                "coins_changed", this, (requestKey, bundle) -> refreshCoins()
        );

        requestRuntimePermissions();
        // Hacer rollover si cambió la fecha antes de mostrar datos
        maybeRunRollover();
        // Refrescar UI luego del posible rollover
        updateSmallWeekAndBigStepsFromStorage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCoins(); // asegurar que el saldo esté al día
        maybeRunRollover(); // por si cambiamos de día mientras la app estaba en background
        updateSmallWeekAndBigStepsFromStorage();

        if (ensureARGranted() && stepsManager != null) stepsManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stepsManager != null) stepsManager.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stepsManager != null) stepsManager.stop();
    }

    private void openGoalsFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,  android.R.anim.fade_out,
                        android.R.anim.fade_in,  android.R.anim.fade_out)
                .replace(R.id.root, new GoalsFragment())
                .addToBackStack("goals")
                .commit();
    }

    private void openStatsFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,  android.R.anim.fade_out,
                        android.R.anim.fade_in,  android.R.anim.fade_out)
                .replace(R.id.root, new StatsFragment())
                .addToBackStack("stats")
                .commit();
    }

    private void requestRuntimePermissions() {
        ArrayList<String> req = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
            req.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            req.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!req.isEmpty()) {
            permsLauncher.launch(req.toArray(new String[0]));
        } else {
            if (stepsManager != null) stepsManager.start();
        }
    }

    private boolean ensureARGranted() {
        return !(Build.VERSION.SDK_INT >= 29) ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshCoins() {
        if (tvCoins != null && userId > 0) {
            long saldo = db.getSaldo(userId);
            tvCoins.setText(String.format(Locale.getDefault(), "%,d", saldo));
        }
    }

    // ==== Helpers de rollover y UI ====

    private String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void maybeRunRollover() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String last = sp.getString(KEY_ULTIMO_DIA, "");
        String today = todayString();

        if (today.equals(last)) return; // mismo día, nada que hacer

        long pasosHoy = sp.getLong(KEY_PASOS_HOY, 0L);
        double kmAgregar = pasosHoy * STEP_TO_KM;

        double kmSem = db.getKmSemana(userId);
        db.setKmSemana(userId, kmSem + kmAgregar);  // acumula al semanal
        db.setKmHoy(userId, 0);                     // limpia km del día

        int diasContados = sp.getInt(KEY_DIAS_CONTADOS, 0) + 1;
        if (diasContados >= 7) {
            db.setKmSemana(userId, 0);              // resetea semana
            diasContados = 0;
        }

        sp.edit()
                .putString(KEY_ULTIMO_DIA, today)
                .putLong(KEY_PASOS_HOY, 0L)              // resetea pasos del día (LONG)
                .putInt(KEY_DIAS_CONTADOS, diasContados)
                .apply();
    }

    private void updateSmallWeekAndBigStepsFromStorage() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        long pasosHoy = sp.getLong(KEY_PASOS_HOY, 0L);
        tvKmTotalBig.setText(String.valueOf(pasosHoy));
        tvKmSemanaSmall.setText(String.format(Locale.getDefault(),
                "Semana: %.2f km", db.getKmSemana(userId)));
    }
}
