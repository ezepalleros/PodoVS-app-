package com.example.podovs;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // ==== UI ====
    private TextView tvKmTotalBig;
    private ImageView ivAvatar;
    private TextView tvCoins;
    private TextView tvKmSemanaSmall;

    // ==== Firestore / Repo ====
    private FirestoreRepo repo;
    private FirebaseFirestore db;
    private String uid = null;

    // ==== Steps ====
    private StepsManager stepsManager;

    // ==== Prefs / flags ====
    private static final double STEP_TO_KM = 0.0008;

    private static final String PREFS_GLOBAL = "podovs_global";
    private static final String KEY_LAST_UID = "last_uid";

    private static final String PREFS_PREFIX = "podovs_prefs_";
    private static final String KEY_PASOS_HOY = "pasos_hoy";
    private static final String KEY_ULTIMO_DIA = "ultimo_dia";
    private static final String KEY_DIAS_CONTADOS = "dias_contados";
    private static final String KEY_FIRST_LOGIN_DONE = "first_login_done";

    // notificaciones
    private static final String KEY_LAST_LEVEL = "last_seen_level";
    private static final String KEY_NOTIF_DAILY_PREFIX = "notif_daily_";   // + yyyymmdd
    private static final String KEY_NOTIF_WEEKLY_PREFIX = "notif_week_";   // + cycle string

    private SharedPreferences userPrefs() { return getSharedPreferences(PREFS_PREFIX + uid, MODE_PRIVATE); }

    private double kmSemanaCache = 0.0;

    private final ActivityResultLauncher<String[]> permsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean arGranted = result.getOrDefault(Manifest.permission.ACTIVITY_RECOGNITION, false)
                        || Build.VERSION.SDK_INT < 29;
                if (arGranted) {
                    secureStartSteps();
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

        if (tvKmSemanaSmall != null) tvKmSemanaSmall.setVisibility(View.GONE);

        ivAvatar.setOnClickListener(v -> startActivity(new Intent(this, AvatarActivity.class)));

        SharedPreferences spSession = getSharedPreferences("session", MODE_PRIVATE);
        uid = spSession.getString("uid", null);
        if (uid == null || uid.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        ensurePerUserPrefsInitialized();

        repo = new FirestoreRepo();
        db   = FirebaseFirestore.getInstance();

        repo.addStarterPackIfMissing(uid, v -> {}, e -> {});
        repo.ensureStats(uid);
        repo.normalizeXpLevel(uid, v -> {}, e -> Log.w(TAG, "normalizeXpLevel: " + e.getMessage()));

        repo.listenUser(uid, (snap, err) -> {
            if (err != null) {
                Log.w(TAG, "listenUser error: " + err.getMessage());
                return;
            }
            if (snap != null && snap.exists()) {
                Long saldo = snap.getLong("usu_saldo");
                if (saldo != null && tvCoins != null) tvCoins.setText(String.format(Locale.getDefault(), "%,d", saldo));

                Double kmSemana = getNestedDouble(snap, "usu_stats.km_semana");
                if (kmSemana != null) kmSemanaCache = kmSemana;

                // -------- notificaciones: level up --------
                int newLevel = (snap.getLong("usu_nivel") == null) ? 1 : snap.getLong("usu_nivel").intValue();
                int lastLevel = userPrefs().getInt(KEY_LAST_LEVEL, 1);
                if (newLevel > lastLevel) {
                    NotificationHelper.showLevelUp(this, newLevel);
                    userPrefs().edit().putInt(KEY_LAST_LEVEL, newLevel).apply();
                } else if (!userPrefs().contains(KEY_LAST_LEVEL)) {
                    userPrefs().edit().putInt(KEY_LAST_LEVEL, newLevel).apply();
                }

                // -------- notificaciones: metas disponibles --------
                long metaDaily  = (snap.getLong("usu_stats.meta_diaria_pasos")  == null) ? 8000L  : snap.getLong("usu_stats.meta_diaria_pasos");
                long metaWeekly = (snap.getLong("usu_stats.meta_semanal_pasos") == null) ? 56000L : snap.getLong("usu_stats.meta_semanal_pasos");

                long stepsToday = userPrefs().getLong(KEY_PASOS_HOY, 0L);
                if (stepsToday >= metaDaily) {
                    String todayKey = todayYMD();
                    if (!userPrefs().getBoolean(KEY_NOTIF_DAILY_PREFIX + todayKey, false)) {
                        NotificationHelper.showGoalClaimAvailable(this, "diaria");
                        userPrefs().edit().putBoolean(KEY_NOTIF_DAILY_PREFIX + todayKey, true).apply();
                    }
                }

                long weekStart = (snap.getLong("usu_stats.week_started_at") == null) ? System.currentTimeMillis() : snap.getLong("usu_stats.week_started_at");
                boolean windowReady = System.currentTimeMillis() - weekStart >= 7L * 24L * 60L * 60L * 1000L;

                long stepsWeek = (long) Math.max(0, Math.round((kmSemanaCache * 1000.0) / 0.78)); // ≈ pasos
                if (windowReady && stepsWeek >= metaWeekly) {
                    String cycle = weeklyCycleString(weekStart);
                    if (!userPrefs().getBoolean(KEY_NOTIF_WEEKLY_PREFIX + cycle, false)) {
                        NotificationHelper.showGoalClaimAvailable(this, "semanal");
                        userPrefs().edit().putBoolean(KEY_NOTIF_WEEKLY_PREFIX + cycle, true).apply();
                    }
                }

                renderAvatarFromUserSnapshot(snap);
            }
        });

        long pasosGuardados = userPrefs().getLong(KEY_PASOS_HOY, 0L);
        tvKmTotalBig.setText(String.valueOf(pasosGuardados));

        stepsManager = new StepsManager(this, null, 0L, (stepsToday, kmHoy) -> {
            userPrefs().edit().putLong(KEY_PASOS_HOY, stepsToday).apply();
            tvKmTotalBig.setText(String.valueOf(stepsToday));
        });

        findViewById(R.id.btnTopGoals).setOnClickListener(v -> openGoalsFragment());
        findViewById(R.id.btnTopStats).setOnClickListener(v -> openStatsFragment());
        findViewById(R.id.btnTopProfile).setOnClickListener(v -> openProfileSheet());
        findViewById(R.id.btnTopNotifications).setOnClickListener(v -> openNotificationsFragment());
        findViewById(R.id.btnTopOptions).setOnClickListener(v -> openOptionsFragment());

        // Botón (tarjeta) de tienda en el main
        findViewById(R.id.btnShop).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ShopActivity.class)));

        // === Bottom bar: navegación a Versus y otros ===
        ImageButton btnHome = findViewById(R.id.btnHome);
        ImageButton btnVs   = findViewById(R.id.btnVs);
        ImageButton btnEvt  = findViewById(R.id.btnEvents);
        ImageButton btnLb   = findViewById(R.id.btnLeaderboards);

        if (btnHome != null) {
            btnHome.setOnClickListener(v ->
                    Toast.makeText(this, "Ya estás en inicio", Toast.LENGTH_SHORT).show());
        }

        if (btnVs != null) {
            btnVs.setOnClickListener(v -> {
                startActivity(new Intent(this, VersusActivity.class));
                finish();
            });
        }

        if (btnEvt != null) {
            btnEvt.setOnClickListener(v ->
                    Toast.makeText(this, "Eventos próximamente", Toast.LENGTH_SHORT).show());
        }

        if (btnLb != null) {
            btnLb.setOnClickListener(v ->
                    Toast.makeText(this, "Rankings próximamente", Toast.LENGTH_SHORT).show());
        }

        getSupportFragmentManager().setFragmentResultListener("coins_changed", this, (requestKey, bundle) -> {});

        requestRuntimePermissions();

        if (userPrefs().getBoolean(KEY_FIRST_LOGIN_DONE, false)) {
            maybeRunRollover();
        }
        updateBigStepsFromStorage();
    }

    @Override protected void onResume() {
        super.onResume();
        if (userPrefs().getBoolean(KEY_FIRST_LOGIN_DONE, false)) maybeRunRollover();
        updateBigStepsFromStorage();
        secureStartSteps();
    }

    @Override protected void onPause() {
        super.onPause();
        secureStopSteps();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        secureStopSteps();
    }

    // ==== Prefs por usuario ====
    private void ensurePerUserPrefsInitialized() {
        SharedPreferences global = getSharedPreferences(PREFS_GLOBAL, MODE_PRIVATE);
        String lastUid = global.getString(KEY_LAST_UID, null);
        if (!uid.equals(lastUid)) {
            SharedPreferences up = userPrefs();
            up.edit()
                    .putString(KEY_ULTIMO_DIA, todayString())
                    .putLong(KEY_PASOS_HOY, 0L)
                    .putInt(KEY_DIAS_CONTADOS, 0)
                    .putBoolean(KEY_FIRST_LOGIN_DONE, true)
                    .putInt(KEY_LAST_LEVEL, 1)
                    .apply();
            global.edit().putString(KEY_LAST_UID, uid).apply();
        } else {
            SharedPreferences up = userPrefs();
            if (!up.contains(KEY_ULTIMO_DIA)) up.edit().putString(KEY_ULTIMO_DIA, todayString()).apply();
            if (!up.contains(KEY_FIRST_LOGIN_DONE)) up.edit().putBoolean(KEY_FIRST_LOGIN_DONE, true).apply();
        }
    }

    private void openGoalsFragment() {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.root, new GoalsFragment())
                .addToBackStack("goals")
                .commit();
    }
    private void openStatsFragment() {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.root, new StatsFragment())
                .addToBackStack("stats")
                .commit();
    }
    private void openProfileSheet() { new ProfileFragment().show(getSupportFragmentManager(), "profile_sheet"); }
    private void openNotificationsFragment() {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.root, new NotificationFragment())
                .addToBackStack("notifications")
                .commit();
    }
    private void openOptionsFragment() {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.root, new OptionsFragment())
                .addToBackStack("options")
                .commit();
    }

    // ====== Permisos ======
    private void requestRuntimePermissions() {
        ArrayList<String> req = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) req.add(Manifest.permission.ACTIVITY_RECOGNITION);
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) req.add(Manifest.permission.POST_NOTIFICATIONS);

        if (!req.isEmpty()) permsLauncher.launch(req.toArray(new String[0]));
        else secureStartSteps();
    }
    private boolean ensureARGranted() {
        return !(Build.VERSION.SDK_INT >= 29) ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        == PackageManager.PERMISSION_GRANTED;
    }
    private void secureStartSteps() { if (stepsManager != null && ensureARGranted()) try { stepsManager.start(); } catch (SecurityException ignored) {} }
    private void secureStopSteps()  { if (stepsManager != null) try { stepsManager.stop(); } catch (SecurityException ignored) {} }

    // ==== Rollover y UI ====
    private String todayString() { return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()); }
    private String todayYMD()    { return new SimpleDateFormat("yyyyMMdd",  Locale.getDefault()).format(new Date()); }
    private String weeklyCycleString(long weekStartMs) {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(weekStartMs));
    }

    private void maybeRunRollover() {
        if (uid == null) return;

        SharedPreferences sp = userPrefs();
        String last = sp.getString(KEY_ULTIMO_DIA, "");
        String today = todayString();

        if (today.equals(last)) return;

        long pasosHoy = sp.getLong(KEY_PASOS_HOY, 0L);
        double kmAgregar = pasosHoy * STEP_TO_KM;

        repo.addKmDelta(uid, kmAgregar, v -> {}, e -> Log.w(TAG, "addKmDelta error: " + e.getMessage()));

        repo.updateMayorPasosDiaIfGreater(uid, pasosHoy);

        int diasContados = sp.getInt(KEY_DIAS_CONTADOS, 0) + 1;
        if (diasContados >= 7) {
            repo.setKmSemana(uid, 0.0, v -> {}, e -> Log.w(TAG, "reset semana error"));
            kmSemanaCache = 0.0;
            diasContados = 0;
        } else {
            kmSemanaCache = Math.max(0.0, kmSemanaCache + kmAgregar);
        }

        sp.edit().putString(KEY_ULTIMO_DIA, today).putLong(KEY_PASOS_HOY, 0L).putInt(KEY_DIAS_CONTADOS, diasContados).apply();
        tvKmTotalBig.setText(String.valueOf(0));
    }

    private void updateBigStepsFromStorage() {
        long pasosHoy = userPrefs().getLong(KEY_PASOS_HOY, 0L);
        tvKmTotalBig.setText(String.valueOf(pasosHoy));
    }

    @Nullable private Double getNestedDouble(DocumentSnapshot snap, String dottedPath) {
        Object val = snap.get(dottedPath);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    // ===================== AVATAR =====================
    private void renderAvatarFromUserSnapshot(DocumentSnapshot snap) {
        String pielId     = asString(snap.get("usu_equipped.usu_piel"));
        String pantalonId = asString(snap.get("usu_equipped.usu_pantalon"));
        String remeraId   = asString(snap.get("usu_equipped.usu_remera"));
        String zapasId    = asString(snap.get("usu_equipped.usu_zapas"));
        String cabezaId   = asString(snap.get("usu_equipped.usu_cabeza"));

        if (pielId == null && pantalonId == null && remeraId == null
                && zapasId == null && cabezaId == null) {
            int fallback = getResIdByName("piel_startskin");
            if (fallback != 0) ivAvatar.setImageResource(fallback);
            return;
        }

        db.collection("users").document(uid).collection("my_cosmetics")
                .get()
                .addOnSuccessListener(qs -> buildAvatarFromMyCosmetics(qs, pielId, pantalonId, remeraId, zapasId, cabezaId))
                .addOnFailureListener(e -> Log.w(TAG, "my_cosmetics get() error: " + e.getMessage()));
    }

    private static class LayerRequest { final String type; final String asset;
        LayerRequest(String t, String a) { type = t; asset = a; } }

    @Nullable private LayerRequest fromCache(QuerySnapshot qs, @Nullable String cosId) {
        if (cosId == null) return null;
        DocumentSnapshot d = null;
        for (DocumentSnapshot doc : qs) { if (cosId.equals(doc.getId())) { d = doc; break; } }
        if (d == null) return null;
        Object at = d.get("myc_cache.cos_assetType");
        Object av = d.get("myc_cache.cos_asset");
        String type  = (at instanceof String && !((String) at).isEmpty()) ? (String) at : null;
        String asset = (av instanceof String && !((String) av).isEmpty()) ? (String) av : null;
        if (asset == null) return null;
        return new LayerRequest(type, asset);
    }

    private boolean isRemote(@Nullable LayerRequest r) {
        return r != null && r.asset != null &&
                ("cloudinary".equalsIgnoreCase(r.type)
                        || r.asset.startsWith("http://")
                        || r.asset.startsWith("https://"));
    }

    private void buildAvatarFromMyCosmetics(QuerySnapshot qs,
                                            @Nullable String pielId,
                                            @Nullable String pantalonId,
                                            @Nullable String remeraId,
                                            @Nullable String zapasId,
                                            @Nullable String cabezaId) {

        LayerRequest[] reqs = new LayerRequest[] {
                fromCache(qs, pielId),
                fromCache(qs, zapasId),
                fromCache(qs, pantalonId),
                fromCache(qs, remeraId),
                fromCache(qs, cabezaId)
        };

        boolean anyRemote = false;
        for (LayerRequest r : reqs) if (isRemote(r)) { anyRemote = true; break; }

        if (!anyRemote) {
            ArrayList<Drawable> layers = new ArrayList<>();
            for (LayerRequest r : reqs) if (r != null && r.asset != null) addLayerIfExists(layers, r.asset);
            if (layers.isEmpty()) {
                int def = getResIdByName("piel_startskin");
                if (def != 0) ivAvatar.setImageResource(def);
                return;
            }
            LayerDrawable ld = new LayerDrawable(layers.toArray(new Drawable[0]));
            ivAvatar.setImageDrawable(ld);
            ivAvatar.setAdjustViewBounds(true);
            return;
        }

        loadLayersAsync(reqs, drawables -> {
            if (drawables.isEmpty()) {
                int def = getResIdByName("piel_startskin");
                if (def != 0) ivAvatar.setImageResource(def);
                return;
            }
            LayerDrawable ld = new LayerDrawable(drawables.toArray(new Drawable[0]));
            ivAvatar.setImageDrawable(ld);
            ivAvatar.setAdjustViewBounds(true);
        });
    }

    private interface LayersReady { void onReady(ArrayList<Drawable> layers); }

    private void loadLayersAsync(LayerRequest[] reqs, LayersReady cb) {
        final Drawable[] slots = new Drawable[reqs.length];
        final int total = reqs.length;
        final int[] done = {0};

        for (int i = 0; i < reqs.length; i++) {
            final int idx = i;
            final LayerRequest r = reqs[i];

            if (r == null || r.asset == null) { done[0]++; maybeFinishOrdered(slots, done[0], total, cb); continue; }

            if (!isRemote(r)) {
                int resId = getResIdByName(r.asset);
                Drawable dr = (resId != 0) ? ContextCompat.getDrawable(this, resId) : null;
                slots[idx] = dr;
                done[0]++; maybeFinishOrdered(slots, done[0], total, cb);
            } else {
                String url = (r.asset.startsWith("http://") || r.asset.startsWith("https://"))
                        ? r.asset
                        : "https://res.cloudinary.com/" + getString(R.string.cloudinary_cloud_name) + "/image/upload/" + r.asset + (r.asset.contains(".") ? "" : ".png");

                Glide.with(this).asDrawable().load(url).into(new CustomTarget<Drawable>() {
                    @Override public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        slots[idx] = resource;
                        done[0]++; maybeFinishOrdered(slots, done[0], total, cb);
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) { done[0]++; maybeFinishOrdered(slots, done[0], total, cb); }
                    @Override public void onLoadFailed(@Nullable Drawable errorDrawable) { done[0]++; maybeFinishOrdered(slots, done[0], total, cb); }
                });
            }
        }
    }

    private void maybeFinishOrdered(Drawable[] slots, int done, int total, LayersReady cb) {
        if (done >= total) {
            ArrayList<Drawable> out = new ArrayList<>();
            for (Drawable d : slots) if (d != null) out.add(d);
            cb.onReady(out);
        }
    }

    @Nullable private String asString(Object o) { return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null; }
    private void addLayerIfExists(ArrayList<Drawable> layers, @Nullable String assetName) {
        if (assetName == null) return;
        int resId = getResIdByName(assetName);
        if (resId == 0) return;
        Drawable dr = ContextCompat.getDrawable(this, resId);
        if (dr != null) layers.add(dr);
    }
    private int getResIdByName(String name) { return getResources().getIdentifier(name, "drawable", getPackageName()); }
}
