package com.example.podovs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

public class OptionsFragment extends Fragment {

    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    private FirestoreRepo repo;
    private String uid;

    private TextView tvCurrentDif;
    private TextView tvCooldownInfo;
    private TextView tvResetInfo;
    private Button btnDifBajo, btnDifMedio, btnDifAlto;
    private Button btnResetProgress;
    private Button btnLogout;
    private ImageButton btnClose;

    private int resetClicks = 0;

    public OptionsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new FirestoreRepo();

        Context ctx = requireContext();
        SharedPreferences sp = ctx.getSharedPreferences("session", Context.MODE_PRIVATE);
        uid = sp.getString("uid", null);

        if (uid == null || uid.isEmpty()) {
            // sin sesión -> mandar a login
            startActivity(new Intent(ctx, LoginActivity.class));
            requireActivity().finish();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Modal similar a notificaciones
        return inflater.inflate(R.layout.fragment_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvCurrentDif     = view.findViewById(R.id.tvCurrentDif);
        tvCooldownInfo   = view.findViewById(R.id.tvCooldownInfo);
        tvResetInfo      = view.findViewById(R.id.tvResetInfo);
        btnDifBajo       = view.findViewById(R.id.btnDifBajo);
        btnDifMedio      = view.findViewById(R.id.btnDifMedio);
        btnDifAlto       = view.findViewById(R.id.btnDifAlto);
        btnResetProgress = view.findViewById(R.id.btnResetProgress);
        btnLogout        = view.findViewById(R.id.btnLogout);
        btnClose         = view.findViewById(R.id.btnClose);

        // Colores ya vienen desde el XML; solo seteamos listeners
        loadCurrentData();
        updateResetHint(); // "Tocá 3 veces..."

        btnDifBajo.setOnClickListener(v -> changeDifficulty("bajo"));
        btnDifMedio.setOnClickListener(v -> changeDifficulty("medio"));
        btnDifAlto.setOnClickListener(v -> changeDifficulty("alto"));

        btnResetProgress.setOnClickListener(v -> handleResetClick());
        btnLogout.setOnClickListener(v -> logout());
        btnClose.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    // ====== Datos de usuario / dificultad ======

    private void loadCurrentData() {
        if (uid == null) return;
        repo.getUser(uid,
                (DocumentSnapshot snap) -> {
                    if (!snap.exists()) return;

                    String dif = snap.getString("usu_difi");
                    if (dif == null) dif = "bajo";
                    tvCurrentDif.setText("Dificultad actual: " + dif);

                    styleSelectedDifficulty(dif);

                    Long lastChange = null;
                    Object v = snap.get("usu_difi_changed_at");
                    if (v instanceof Number) lastChange = ((Number) v).longValue();

                    long now = System.currentTimeMillis();
                    if (lastChange == null || now - lastChange >= WEEK_MS) {
                        tvCooldownInfo.setText("Podés cambiar la dificultad ahora.");
                        enableDiffButtons(true);
                    } else {
                        long remainingMs = WEEK_MS - (now - lastChange);
                        long days = (long) Math.ceil(remainingMs / (24.0 * 60 * 60 * 1000));
                        tvCooldownInfo.setText("Podrás cambiar la dificultad en " + days + " día(s).");
                        enableDiffButtons(false);
                    }
                },
                e -> Toast.makeText(getContext(), "Error cargando opciones: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void enableDiffButtons(boolean enable) {
        btnDifBajo.setEnabled(enable);
        btnDifMedio.setEnabled(enable);
        btnDifAlto.setEnabled(enable);

        float alpha = enable ? 1f : 0.4f;
        btnDifBajo.setAlpha(alpha);
        btnDifMedio.setAlpha(alpha);
        btnDifAlto.setAlpha(alpha);
    }

    private void styleSelectedDifficulty(String dif) {
        // resaltamos el botón seleccionado
        btnDifBajo.setAlpha(0.7f);
        btnDifMedio.setAlpha(0.7f);
        btnDifAlto.setAlpha(0.7f);

        if ("bajo".equalsIgnoreCase(dif))      btnDifBajo.setAlpha(1f);
        else if ("medio".equalsIgnoreCase(dif))btnDifMedio.setAlpha(1f);
        else if ("alto".equalsIgnoreCase(dif)) btnDifAlto.setAlpha(1f);
    }

    private void changeDifficulty(String nuevaDifi) {
        if (uid == null) return;

        repo.changeDifficultyWithCooldown(uid, nuevaDifi,
                v -> {
                    Toast.makeText(getContext(), "Dificultad cambiada a " + nuevaDifi + ".", Toast.LENGTH_SHORT).show();
                    loadCurrentData(); // refresca texto, estilos y cooldown
                },
                e -> Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    // ====== Reset de nivel y metas ======

    private void updateResetHint() {
        int remaining = 3 - resetClicks;
        if (remaining >= 3) {
            tvResetInfo.setText("Tocá 3 veces para reiniciar nivel y metas.");
        } else if (remaining > 0) {
            tvResetInfo.setText("Faltan " + remaining + " toque(s) para reiniciar.");
        } else {
            tvResetInfo.setText("Reiniciando nivel y metas...");
        }
    }

    private void handleResetClick() {
        resetClicks++;
        if (resetClicks < 3) {
            updateResetHint();
            return;
        }

        // disparar reset
        resetClicks = 0;
        updateResetHint(); // "Reiniciando nivel y metas..."

        if (uid == null) return;
        repo.resetLevelAndGoals(uid,
                v -> tvResetInfo.setText("Listo: ahora sos nivel 1 y tus metas vuelven a 1000/10000."),
                e -> tvResetInfo.setText("Error al reiniciar: " + e.getMessage())
        );
    }

    // ====== Logout ======

    private void logout() {
        Context ctx = requireContext();
        // Cerrar sesión Firebase
        FirebaseAuth.getInstance().signOut();

        // Limpiar session prefs
        SharedPreferences sp = ctx.getSharedPreferences("session", Context.MODE_PRIVATE);
        sp.edit().clear().apply();

        Intent i = new Intent(ctx, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        requireActivity().finish();
    }
}
