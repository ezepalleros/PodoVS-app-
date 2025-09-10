package com.example.podovs;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvPasosHoy, tvKmHoy, tvKmSemana, tvBienvenido;
    private StepCounterHelper stepHelper;
    private DatabaseHelper db;
    private long userId;
    private static final double METROS_POR_PASO = 0.78;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvPasosHoy   = findViewById(R.id.tvPasosHoy);
        tvKmHoy      = findViewById(R.id.tvKmHoy);
        tvKmSemana   = findViewById(R.id.tvKmSemana);
        tvBienvenido = findViewById(R.id.tvBienvenido);

        db = new DatabaseHelper(this);

        // Recuperá userId/nombre de tu sesión (ya lo hacías)
        var sp = getSharedPreferences("session", MODE_PRIVATE);
        userId = sp.getLong("user_id", -1L);
        String nombre = sp.getString("user_name", "Usuario");
        tvBienvenido.setText("Bienvenido, " + nombre);

        // Helper de pasos en vivo
        stepHelper = new StepCounterHelper(this, stepsToday -> {
            tvPasosHoy.setText("Pasos hoy: " + stepsToday);

            // actualizar km hoy y semana (semana: por ahora solo reflejamos hoy como demo)
            double kmHoy = Math.round((stepsToday * METROS_POR_PASO / 1000.0) * 100.0) / 100.0;

            // Leemos lo que ya tenías para semana y sumamos hoy (simple)
            var c = db.getReadableDatabase().rawQuery(
                    "SELECT " + DatabaseHelper.COL_KM_SEMANA + " FROM " + DatabaseHelper.TABLE_USUARIOS +
                            " WHERE " + DatabaseHelper.COL_ID + "=? LIMIT 1",
                    new String[]{String.valueOf(userId)}
            );
            double kmSemana = 0;
            if (c != null && c.moveToFirst()) {
                kmSemana = c.getDouble(0);
                c.close();
            }

            // Guardar km de hoy y de semana (semana = base anterior + hoy calculado)
            db.getWritableDatabase().execSQL(
                    "UPDATE " + DatabaseHelper.TABLE_USUARIOS +
                            " SET " + DatabaseHelper.COL_KM_HOY + " = ?, " +
                            DatabaseHelper.COL_KM_SEMANA + " = ? " +
                            " WHERE " + DatabaseHelper.COL_ID + " = ?",
                    new Object[]{kmHoy, Math.max(kmSemana, kmHoy), userId} // simple: semana >= hoy
            );

            tvKmHoy.setText(String.format(java.util.Locale.getDefault(),
                    "Kilómetros hoy: %.2f km", kmHoy));
            tvKmSemana.setText(String.format(java.util.Locale.getDefault(),
                    "Kilómetros esta semana: %.2f km", Math.max(kmSemana, kmHoy)));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = stepHelper.register();
        if (!ok) {
            Toast.makeText(this, "Este dispositivo no tiene sensor de pasos.", Toast.LENGTH_LONG).show();
        } else {
            // pintar último valor guardado por si tarda el primer evento
            tvPasosHoy.setText("Pasos hoy: " + stepHelper.getLastSavedToday());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stepHelper.unregister();
    }
}
