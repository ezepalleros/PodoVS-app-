package com.example.podovs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnIngresar;
    private TextView tvRegister;

    private FirestoreRepo repo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        btnIngresar = findViewById(R.id.btnIngresar);
        tvRegister  = findViewById(R.id.tvRegister);

        repo = new FirestoreRepo();

        btnIngresar.setOnClickListener(v -> intentarLogin());

        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );
    }

    private void intentarLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Completá email y contraseña.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnIngresar.setEnabled(false);

        repo.signIn(email, pass,
                (AuthResult res) -> {
                    FirebaseUser fu = repo.currentUser();
                    if (fu == null) {
                        btnIngresar.setEnabled(true);
                        Toast.makeText(this, "No se pudo iniciar sesión.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = fu.getUid();
                    // Traer (o crear) el documento del usuario y continuar
                    repo.getUser(uid,
                            (DocumentSnapshot snap) -> {
                                if (snap.exists()) {
                                    continueToMain(uid, snap.getString("usu_nombre"));
                                } else {
                                    // Onboarding para inicializar perfil
                                    Intent i = new Intent(this, RegisterActivity.class);
                                    i.putExtra("onboarding_uid", uid);
                                    startActivity(i);
                                    finish();
                                }
                            },
                            e -> {
                                btnIngresar.setEnabled(true);
                                Toast.makeText(this, "Error obteniendo usuario: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                    );
                },
                e -> {
                    btnIngresar.setEnabled(true);
                    Toast.makeText(this, "Login fallido: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
        );
    }

    private void continueToMain(String uid, @Nullable String nombre) {
        SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
        sp.edit()
                .putString("uid", uid)
                .putString("user_name", (nombre != null && !nombre.isEmpty()) ? nombre : "Jugador")
                .apply();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
