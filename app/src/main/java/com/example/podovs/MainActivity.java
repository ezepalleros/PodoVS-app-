package com.example.podovs;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
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

    // UI
    private TextView tvKmTotalBig;     // ahora: PASOS HOY (número grande)
    private TextView tvKmSemanaSmall;  // "Semana: xx.xx km"
    private ImageView ivAvatar;

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

        ivAvatar.setImageResource(R.drawable.default_avatar);

        db = new DatabaseHelper(this);

        // Sesión requerida
        SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
        userId = sp.getLong("user_id", -1L);
        if (userId <= 0) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Cargar km de la semana al iniciar
        tvKmSemanaSmall.setText(String.format(Locale.getDefault(), "Semana: %.2f km", getKmSemanaFromDb()));

        // Mostrar un valor inicial de pasos
        tvKmTotalBig.setText("0");

        // StepsManager: cada update refresca los pasos (número grande)
        // y re-lee km_semana desde la BDD por si cambió en otro flujo.
        stepsManager = new StepsManager(this, db, userId, (stepsToday, kmHoy) -> {
            tvKmTotalBig.setText(String.valueOf(stepsToday));
            tvKmSemanaSmall.setText(String.format(Locale.getDefault(), "Semana: %.2f km", getKmSemanaFromDb()));
        });

        requestRuntimePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    private double getKmSemanaFromDb() {
        double kmSemana = 0.0;
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT " + DatabaseHelper.COL_KM_SEMANA +
                        " FROM " + DatabaseHelper.TABLE_USUARIOS +
                        " WHERE " + DatabaseHelper.COL_ID + "=? LIMIT 1",
                new String[]{String.valueOf(userId)}
        );
        if (c != null) {
            if (c.moveToFirst()) kmSemana = c.getDouble(0);
            c.close();
        }
        return kmSemana;
    }

    // ---- Permisos ----
    private void requestRuntimePermissions() {
        boolean needAR = (Build.VERSION.SDK_INT >= 29) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED;

        if (needAR) {
            permsLauncher.launch(new String[]{Manifest.permission.ACTIVITY_RECOGNITION});
        } else {
            if (stepsManager != null) stepsManager.start();
        }
    }

    private boolean ensureARGranted() {
        return !(Build.VERSION.SDK_INT >= 29) ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        == PackageManager.PERMISSION_GRANTED;
    }
}
