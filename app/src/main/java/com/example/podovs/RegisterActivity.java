package com.example.podovs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    private EditText etNombre, etEmail, etPassword;
    private Spinner spDificultad;
    private Button btnCrearCuenta;
    private TextView tvDifDetalles, tvDifTitulo; // nuevos
    private View dotSelected;                     // chip color seleccionado
    private DatabaseHelper db;

    // Colores (verde, amarillo, rojo)
    private static final int COL_EASY   = 0xFF6FE6B7; // verde PodoVS
    private static final int COL_MEDIUM = 0xFFFFC107; // amarillo
    private static final int COL_HARD   = 0xFFF44336; // rojo

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etNombre       = findViewById(R.id.etNombre);
        etEmail        = findViewById(R.id.etEmail);
        etPassword     = findViewById(R.id.etPassword);
        spDificultad   = findViewById(R.id.spDificultad);
        btnCrearCuenta = findViewById(R.id.btnCrearCuenta);

        tvDifDetalles  = findViewById(R.id.tvDifDetalles);
        tvDifTitulo    = findViewById(R.id.tvDifTitulo);
        dotSelected    = findViewById(R.id.dotSelected);

        db = new DatabaseHelper(this);

        // opciones dificultad (valores válidos para la CHECK constraint)
        String[] difs = new String[]{"bajo", "medio", "alto"};

        // Adapter con color en ítem seleccionado y dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, difs) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                tintText((TextView) v, getItem(position));
                return v;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                tintText((TextView) v, getItem(position));
                return v;
            }
            private void tintText(TextView tv, String value) {
                if (tv == null || value == null) return;
                tv.setText(value);
                tv.setAllCaps(false);
                switch (value) {
                    case "bajo":  tv.setTextColor(COL_EASY);   break;
                    case "medio": tv.setTextColor(COL_MEDIUM); break;
                    case "alto":  tv.setTextColor(COL_HARD);   break;
                }
            }
        };
        spDificultad.setAdapter(adapter);

        // Actualiza leyenda y dot al seleccionar
        spDificultad.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String dif = (String) parent.getItemAtPosition(pos);
                applyDifficultyExplainer(dif);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });

        btnCrearCuenta.setOnClickListener(v -> registrar());
    }

    private void applyDifficultyExplainer(String dif) {
        // Título con color
        int color;
        String titulo;
        String detalle;
        switch (dif) {
            case "bajo":
                color = COL_EASY;
                titulo = "Fácil (verde)";
                // En tu DB: factor(bajo)=1.1  | medio=1.2 | alto=1.3
                detalle =
                        "• Metas diarias/semanales más bajas (crecen más lento: factor 1.1 por nivel).\n" +
                                "• Ideal si recién empezás o venís de sedentarismo.\n" +
                                "• Recomendado: \"Elegí esta si estás retomando la actividad o querés ir de a poco\".";
                break;
            case "alto":
                color = COL_HARD;
                titulo = "Difícil (rojo)";
                detalle =
                        "• Metas diarias/semanales más altas (crecen más rápido: factor 1.3 por nivel).\n" +
                                "• Requiere constancia y volumen de pasos más exigente.\n" +
                                "• Recomendado: \"Elegí esta si sos un atleta apasionado o ya caminás/corrés a diario\".";
                break;
            default:
                color = COL_MEDIUM;
                titulo = "Medio (amarillo)";
                detalle =
                        "• Metas balanceadas (crecen a ritmo normal: factor 1.2 por nivel).\n" +
                                "• Buen equilibrio entre desafío y alcanzabilidad.\n" +
                                "• Recomendado: \"Elegí esta si ya tenés algo de actividad y querés mantener la motivación\".";
                break;
        }
        tvDifTitulo.setText(titulo);
        tvDifTitulo.setTextColor(color);
        tvDifDetalles.setText(detalle);
        if (dotSelected != null) dotSelected.setBackgroundColor(color);
    }

    private void registrar() {
        String nombre = etNombre.getText().toString().trim();
        String email  = etEmail.getText().toString().trim();
        String pass   = etPassword.getText().toString().trim();
        String dif    = (String) spDificultad.getSelectedItem();

        if (nombre.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Completá nombre, email y contraseña.", Toast.LENGTH_SHORT).show();
            return;
        }

        long userId = db.registrarUsuario(nombre, email, pass, dif, 0 /*saldo inicial*/);
        if (userId == -2) {
            Toast.makeText(this, "Ese email ya está registrado.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userId <= 0) {
            Toast.makeText(this, "No se pudo crear la cuenta.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Guardar sesión y entrar
        SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
        sp.edit()
                .putLong("user_id", userId)
                .putString("user_name", nombre)
                .apply();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
