package com.example.podovs;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RankingActivity extends AppCompatActivity {

    private FirestoreRepo repo;
    private String uid;

    private LinearLayout containerWeekly;
    private LinearLayout containerGlobal;
    private TextView tvWeeklySubtitle;

    // Timer semana
    private final Handler weeklyHandler = new Handler(Looper.getMainLooper());
    private Runnable weeklyCountdownRunnable;
    private long weeklyEndMillis = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        repo = new FirestoreRepo();
        uid = getSharedPreferences("session", MODE_PRIVATE).getString("uid", null);
        if (uid == null || uid.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        containerWeekly = findViewById(R.id.containerWeeklyRanking);
        containerGlobal = findViewById(R.id.containerGlobalRanking);
        tvWeeklySubtitle = findViewById(R.id.tvWeeklySubtitle);

        setupBottomNav();
        loadWeeklyRanking();
        loadGlobalRanking();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopWeeklyCountdown();
    }

    private void setupBottomNav() {
        ImageButton btnHome = findViewById(R.id.btnHome);
        ImageButton btnShop = findViewById(R.id.btnShop);
        ImageButton btnVs   = findViewById(R.id.btnVs);
        ImageButton btnEvt  = findViewById(R.id.btnEvents);
        ImageButton btnLb   = findViewById(R.id.btnLeaderboards);

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        btnShop.setOnClickListener(v -> {
            startActivity(new Intent(this, ShopActivity.class));
            finish();
        });
        btnVs.setOnClickListener(v -> {
            startActivity(new Intent(this, VersusActivity.class));
            finish();
        });
        btnEvt.setOnClickListener(v -> {
            startActivity(new Intent(this, EventActivity.class));
            finish();
        });
    }

    // ---------- abrir perfil desde ranking ----------
    private void openUserProfile(@NonNull String otherUid, @NonNull String displayName) {
        if (otherUid.equals(uid)) {
            new ProfileFragment().show(getSupportFragmentManager(), "profile_self");
        } else {
            ProfileFragment f = ProfileFragment.newInstanceForUser(otherUid, displayName, false);
            f.show(getSupportFragmentManager(), "profile_other");
        }
    }

    // ---------- RANKING SEMANAL (GRUPO DE HASTA 5) ----------

    private void loadWeeklyRanking() {
        containerWeekly.removeAllViews();
        containerWeekly.addView(makeSimpleText("Cargando tabla semanal..."));
        tvWeeklySubtitle.setText("Cargando...");

        repo.loadWeeklyRankingForUser(uid,
                result -> {
                    containerWeekly.removeAllViews();

                    if (result.rows.isEmpty()) {
                        stopWeeklyCountdown();
                        tvWeeklySubtitle.setText("Buscando tu tabla semanal...");
                        containerWeekly.addView(makeSimpleText(
                                "Todavía no hay jugadores asignados a tu tabla."));
                        return;
                    }

                    startWeeklyCountdown();

                    for (FirestoreRepo.WeeklyRankingRow row : result.rows) {
                        containerWeekly.addView(makeWeeklyRow(row));
                    }
                },
                e -> {
                    stopWeeklyCountdown();
                    containerWeekly.removeAllViews();
                    containerWeekly.addView(makeSimpleText("Error al cargar ranking: " + e.getMessage()));
                    tvWeeklySubtitle.setText("Error al cargar la tabla semanal.");
                });
    }

     private void startWeeklyCountdown() {
        stopWeeklyCountdown();

        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int daysUntilMonday = (Calendar.MONDAY - dow + 7) % 7;
        if (daysUntilMonday == 0) {
            daysUntilMonday = 7;
        }
        cal.add(Calendar.DAY_OF_MONTH, daysUntilMonday);
        weeklyEndMillis = cal.getTimeInMillis();

        weeklyCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long remaining = weeklyEndMillis - now;

                if (remaining <= 0) {
                    tvWeeklySubtitle.setText("Actualizando nueva semana...");
                    stopWeeklyCountdown();
                    loadWeeklyRanking();
                    return;
                }

                long seconds = remaining / 1000;
                long days = seconds / (24 * 60 * 60);
                seconds %= (24 * 60 * 60);
                long hours = seconds / 3600;
                seconds %= 3600;
                long minutes = seconds / 60;
                seconds %= 60;

                String base = "";
                String countdown = String.format(Locale.getDefault(),
                        " Termina en %dd %02d:%02d:%02d",
                        days, hours, minutes, seconds);

                tvWeeklySubtitle.setText(base + countdown);
                weeklyHandler.postDelayed(this, 1000);
            }
        };
        weeklyHandler.post(weeklyCountdownRunnable);
    }

    private void stopWeeklyCountdown() {
        if (weeklyCountdownRunnable != null) {
            weeklyHandler.removeCallbacks(weeklyCountdownRunnable);
            weeklyCountdownRunnable = null;
        }
    }

    private View makeWeeklyRow(@NonNull FirestoreRepo.WeeklyRankingRow row) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.bottomMargin = dp(6);
        card.setLayoutParams(lpCard);
        card.setRadius(dp(10));
        card.setCardElevation(dp(1));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(0xFFFFFFFF);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(root);

        TextView tvPos = new TextView(this);
        tvPos.setText("#" + row.position);
        tvPos.setTextSize(14);
        tvPos.setTextColor(0xFF111827);
        tvPos.setPadding(0, 0, dp(8), 0);

        TextView tvName = new TextView(this);
        tvName.setText(row.nombre);
        tvName.setTextSize(14);
        tvName.setTextColor(row.uid.equals(uid) ? 0xFFB91C1C : 0xFF111827);

        tvName.setOnClickListener(v ->
                openUserProfile(row.uid, row.nombre));

        TextView tvStats = new TextView(this);
        tvStats.setTextSize(13);
        tvStats.setTextColor(0xFF4B5563);
        tvStats.setGravity(Gravity.END);

        String kmText = String.format(Locale.getDefault(), "%.2f km", row.kmSemana);
        String coinsText;
        if (row.coins > 0) {
            coinsText = String.format(Locale.getDefault(), " · premio: %,d", row.coins);
        } else {
            coinsText = "";
        }
        tvStats.setText(kmText + coinsText);

        LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(lpName);

        root.addView(tvPos);
        root.addView(tvName);
        root.addView(tvStats);

        return card;
    }

    // ---------- RANKING GLOBAL (TOP 5 KM_TOTAL) ----------

    private void loadGlobalRanking() {
        containerGlobal.removeAllViews();
        containerGlobal.addView(makeSimpleText("Cargando top global..."));

        FirebaseFirestore.getInstance()
                .collection("users")
                .orderBy("usu_stats.km_total", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(qs -> {
                    containerGlobal.removeAllViews();
                    if (qs.isEmpty()) {
                        containerGlobal.addView(makeSimpleText("Todavía no hay datos suficientes."));
                        return;
                    }
                    List<DocumentSnapshot> docs = qs.getDocuments();
                    for (int i = 0; i < docs.size(); i++) {
                        containerGlobal.addView(makeGlobalRow(i, docs.get(i)));
                    }
                })
                .addOnFailureListener(e -> {
                    containerGlobal.removeAllViews();
                    containerGlobal.addView(makeSimpleText(
                            "Error al cargar top global: " + e.getMessage()));
                });
    }

    private View makeGlobalRow(int index,
                               @NonNull DocumentSnapshot snap) {

        int pos = index + 1;
        String nombre = snap.getString("usu_nombre");
        if (TextUtils.isEmpty(nombre)) {
            String id = snap.getId();
            if (id.length() > 6) id = id.substring(0, 6);
            nombre = "@" + id;
        }

        Double kmD = null;
        Object vKm = snap.get("usu_stats.km_total");
        if (vKm instanceof Number) kmD = ((Number) vKm).doubleValue();
        double km = kmD == null ? 0.0 : kmD;

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.bottomMargin = dp(6);
        card.setLayoutParams(lpCard);
        card.setRadius(dp(10));
        card.setCardElevation(dp(1));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(0xFFFFFFFF);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(root);

        TextView tvPos = new TextView(this);
        tvPos.setText("#" + pos);
        tvPos.setTextSize(14);
        tvPos.setTextColor(0xFF111827);
        tvPos.setPadding(0, 0, dp(8), 0);

        TextView tvName = new TextView(this);
        tvName.setText(nombre);
        tvName.setTextSize(14);
        tvName.setTextColor(0xFF111827);

        String otherUid = snap.getId();
        String finalNombre = nombre;
        tvName.setOnClickListener(v ->
                openUserProfile(otherUid, finalNombre));

        TextView tvKm = new TextView(this);
        tvKm.setTextSize(13);
        tvKm.setTextColor(0xFF4B5563);
        tvKm.setGravity(Gravity.END);
        tvKm.setText(String.format(Locale.getDefault(), "%.2f km", km));

        LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(lpName);

        root.addView(tvPos);
        root.addView(tvName);
        root.addView(tvKm);

        return card;
    }

    // ---------- UTILS ----------

    private View makeSimpleText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xFF4B5563);
        tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        return tv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
