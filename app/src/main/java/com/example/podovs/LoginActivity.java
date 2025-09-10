package com.example.podovs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnIngresar;
    private DatabaseHelper db; // está en el mismo paquete com.example.podovs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login); // tu XML con etEmail, etPassword, btnIngresar

        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        btnIngresar = findViewById(R.id.btnIngresar);
        db          = new DatabaseHelper(this);

        btnIngresar.setOnClickListener(v -> intentarLogin());
    }

    private void intentarLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Por favor, completá email y contraseña.", Toast.LENGTH_SHORT).show();
            return;
        }

        Cursor c = db.login(email, pass);
        if (c != null && c.moveToFirst()) {
            long userId   = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
            String nombre = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_NOMBRE));
            c.close();

            // Guardar sesión para MainActivity
            SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
            sp.edit()
                    .putLong("user_id", userId)
                    .putString("user_name", nombre)
                    .apply();

            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            if (c != null) c.close();
            Toast.makeText(this, "Email o contraseña incorrectos.", Toast.LENGTH_SHORT).show();
        }
    }
}
