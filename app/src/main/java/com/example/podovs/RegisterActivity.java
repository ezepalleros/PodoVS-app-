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

import com.google.firebase.auth.AuthResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private EditText etNombre, etEmail, etPassword;
    private Spinner spDificultad;
    private Button btnCrearCuenta;
    private TextView tvDifDetalles, tvDifTitulo;
    private View dotSelected;

    private FirestoreRepo repo;

    // Colores
    private static final int COL_EASY   = 0xFF6FE6B7;
    private static final int COL_MEDIUM = 0xFFFFC107;
    private static final int COL_HARD   = 0xFFF44336;

    private String onboardingUid = null;

    private static final String PREFS_GLOBAL = "podovs_global";
    private static final String KEY_LAST_UID = "last_uid";
    private static final String PREFS_PREFIX = "podovs_prefs_";
    private static final String KEY_PASOS_HOY = "pasos_hoy";
    private static final String KEY_ULTIMO_DIA = "ultimo_dia";
    private static final String KEY_DIAS_CONTADOS = "dias_contados";
    private static final String KEY_FIRST_LOGIN_DONE = "first_login_done";

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

        repo = new FirestoreRepo();

        onboardingUid = getIntent().getStringExtra("onboarding_uid");
        if (onboardingUid != null) {
            etEmail.setEnabled(false);
            etPassword.setEnabled(false);
            etEmail.setHint("Email ya verificado");
            etPassword.setHint("Sesión iniciada");
        }

        String[] difs = new String[]{"bajo", "medio", "alto"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, difs) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                tint((TextView) v, getItem(position)); return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                tint((TextView) v, getItem(position)); return v;
            }
            private void tint(TextView tv, String value) {
                if (tv == null) return;
                tv.setText(value);
                switch (value) {
                    case "bajo":  tv.setTextColor(COL_EASY); break;
                    case "alto":  tv.setTextColor(COL_HARD); break;
                    default:      tv.setTextColor(COL_MEDIUM); break;
                }
            }
        };
        spDificultad.setAdapter(adapter);
        spDificultad.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                applyDifficultyExplainer((String) parent.getItemAtPosition(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        btnCrearCuenta.setOnClickListener(v -> registrar());
    }

    private void applyDifficultyExplainer(String dif) {
        int color; String titulo; String detalle;
        switch (dif) {
            case "bajo":
                color = COL_EASY; titulo = "Fácil (verde)";
                detalle = "Metas más bajas, crecimiento 1.1x por nivel.";
                break;
            case "alto":
                color = COL_HARD; titulo = "Difícil (rojo)";
                detalle = "Metas más altas, crecimiento 1.3x por nivel.";
                break;
            default:
                color = COL_MEDIUM; titulo = "Medio (amarillo)";
                detalle = "Equilibrado, crecimiento 1.2x por nivel.";
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

        if (nombre.isEmpty()) {
            Toast.makeText(this, "Indicá tu nombre.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCrearCuenta.setEnabled(false);

        if (onboardingUid != null) {
            repo.initUserProfile(onboardingUid, nombre, dif,
                    v -> repo.addStarterPackIfMissing(
                            onboardingUid,
                            vv -> {
                                initLocalPrefsFor(onboardingUid); // evita heredar pasos
                                saveSessionAndGo(onboardingUid, nombre);
                            },
                            e2 -> { btnCrearCuenta.setEnabled(true);
                                Toast.makeText(this, "Starter pack: " + e2.getMessage(), Toast.LENGTH_LONG).show(); }
                    ),
                    e -> {
                        btnCrearCuenta.setEnabled(true);
                        Toast.makeText(this, "No se pudo inicializar el perfil: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
            );
        } else {
            if (email.isEmpty() || pass.isEmpty()) {
                btnCrearCuenta.setEnabled(true);
                Toast.makeText(this, "Completá email y contraseña.", Toast.LENGTH_SHORT).show();
                return;
            }
            repo.createUser(email, pass,
                    (AuthResult r) -> {
                        String uid = (r.getUser() != null) ? r.getUser().getUid() : null;
                        if (uid == null) {
                            btnCrearCuenta.setEnabled(true);
                            Toast.makeText(this, "No se obtuvo UID del usuario.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        repo.initUserProfile(uid, nombre, dif,
                                v -> repo.addStarterPackIfMissing(
                                        uid,
                                        vv -> {
                                            initLocalPrefsFor(uid); // configura prefs por usuario en 0
                                            saveSessionAndGo(uid, nombre);
                                        },
                                        e2 -> { btnCrearCuenta.setEnabled(true);
                                            Toast.makeText(this, "Starter pack: " + e2.getMessage(), Toast.LENGTH_LONG).show(); }
                                ),
                                e -> {
                                    btnCrearCuenta.setEnabled(true);
                                    Toast.makeText(this, "No se pudo crear el perfil: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                        );
                    },
                    e -> {
                        btnCrearCuenta.setEnabled(true);
                        Toast.makeText(this, "Registro fallido: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
            );
        }
    }

    private void initLocalPrefsFor(String uid) {
        getSharedPreferences(PREFS_GLOBAL, MODE_PRIVATE)
                .edit().putString(KEY_LAST_UID, uid).apply();

        SharedPreferences up = getSharedPreferences(PREFS_PREFIX + uid, MODE_PRIVATE);
        up.edit()
                .putString(KEY_ULTIMO_DIA, todayString())
                .putLong(KEY_PASOS_HOY, 0L)
                .putInt(KEY_DIAS_CONTADOS, 0)
                .putBoolean(KEY_FIRST_LOGIN_DONE, true)
                .apply();
    }

    private String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void saveSessionAndGo(String uid, String nombre) {
        SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
        sp.edit().putString("uid", uid).putString("user_name", nombre).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
