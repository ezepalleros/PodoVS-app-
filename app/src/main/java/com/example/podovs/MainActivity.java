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

    private final ActivityResultLauncher<String[]> permsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean arGranted = result.getOrDefault(Manifest.permission.ACTIVITY_RECOGNITION, false);
                if (arGranted) {
                    if (stepsManager != null) stepsManager.start();
                } else {
                    Toast.makeText(this, "Permiso de actividad física denegado.", Toast.LENGTH_LONG).show();
                }
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

        SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
        userId = sp.getLong("user_id", -1L);
        if (userId <= 0) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Mostrar saldo inicial
        refreshCoins();

        tvKmSemanaSmall.setText(String.format(Locale.getDefault(),
                "Semana: %.2f km", db.getKmSemana(userId)));
        tvKmTotalBig.setText("0");

        stepsManager = new StepsManager(this, db, userId, (stepsToday, kmHoy) -> {
            tvKmTotalBig.setText(String.valueOf(stepsToday));
            tvKmSemanaSmall.setText(String.format(Locale.getDefault(),
                    "Semana: %.2f km", db.getKmSemana(userId)));
        });

        // --- Clicks de la fila superior ---
        findViewById(R.id.btnTopGoals).setOnClickListener(v -> openGoalsFragment());
        findViewById(R.id.btnTopStats).setOnClickListener(v ->
                Toast.makeText(this, "Stats (próximamente)", Toast.LENGTH_SHORT).show());
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCoins(); // asegurar que el saldo esté al día
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

    private void requestRuntimePermissions() {
        boolean needAR = (Build.VERSION.SDK_INT >= 29) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED;

        if (needAR) {
            permsLauncher.launch(new String[]{ Manifest.permission.ACTIVITY_RECOGNITION });
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
}
