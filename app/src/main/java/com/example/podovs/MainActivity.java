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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextView tvKmTotalBig;     // PASOS HOY (número grande)
    private TextView tvKmSemanaSmall;  // "Semana: xx.xx km"
    private ImageView ivAvatar;

    // Contador de monedas
    private TextView tvCoins;

    // >>> Firestore <<<
    private FirestoreRepo repo;
    private FirebaseFirestore db;
    private String uid = null;

    // >>> Steps <<<
    private StepsManager stepsManager;

    // ---- Rollover diario/semana (cache local de pasos) ----
    private static final double STEP_TO_KM = 0.0008; // 1 paso ~ 0.8 m
    private static final String PREFS = "podovs_prefs";
    private static final String KEY_PASOS_HOY = "pasos_hoy"; // guardado como LONG
    private static final String KEY_ULTIMO_DIA = "ultimo_dia";
    private static final String KEY_DIAS_CONTADOS = "dias_contados";

    // cache leída desde Firestore (para evitar lecturas extra en cada rollover)
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

        ivAvatar.setOnClickListener(v ->
                startActivity(new Intent(this, AvatarActivity.class)));

        // ==== Sesión Firebase ====
        SharedPreferences spSession = getSharedPreferences("session", MODE_PRIVATE);
        uid = spSession.getString("uid", null);
        if (uid == null || uid.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // ==== Repo Firestore ====
        repo = new FirestoreRepo();
        db   = FirebaseFirestore.getInstance();

        repo.addStarterPackIfMissing(uid, v -> {}, e -> {});
        // *** TODA la normalización de BDD la hace el repo (borra km_hoy y asegura usu_stats) ***
        repo.ensureStats(uid);

        // Inicializa clave de fecha si viene vacía
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.contains(KEY_ULTIMO_DIA)) {
            sp.edit().putString(KEY_ULTIMO_DIA, todayString()).apply();
        }

        // Listener del documento del usuario (saldo, km semana, equipamiento, etc.)
        repo.listenUser(uid, (snap, err) -> {
            if (err != null) {
                Log.w(TAG, "listenUser error: " + err.getMessage());
                return;
            }
            if (snap != null && snap.exists()) {
                // saldo
                Long saldo = snap.getLong("usu_saldo");
                if (saldo != null && tvCoins != null) {
                    tvCoins.setText(String.format(Locale.getDefault(), "%,d", saldo));
                }
                // km semana (dentro de usu_stats)
                Double kmSemana = getNestedDouble(snap, "usu_stats.km_semana");
                if (kmSemana != null) {
                    kmSemanaCache = kmSemana;
                    tvKmSemanaSmall.setText(
                            String.format(Locale.getDefault(), "Semana: %.2f km", kmSemanaCache)
                    );
                }
                // avatar segun equipamiento
                renderAvatarFromUserSnapshot(snap);
            }
        });

        // Mostrar pasos guardados (persistidos en prefs por el steps callback)
        long pasosGuardados = sp.getLong(KEY_PASOS_HOY, 0L);
        tvKmTotalBig.setText(String.valueOf(pasosGuardados));

        // StepsManager: SOLO local -> UI
        stepsManager = new StepsManager(this, /*db=*/null, /*userId=*/0L, (stepsToday, kmHoy) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit().putLong(KEY_PASOS_HOY, stepsToday).apply();
            tvKmTotalBig.setText(String.valueOf(stepsToday));
        });

        // --- Clicks de la fila superior ---
        findViewById(R.id.btnTopGoals).setOnClickListener(v -> openGoalsFragment());
        findViewById(R.id.btnTopStats).setOnClickListener(v -> openStatsFragment());
        findViewById(R.id.btnTopProfile).setOnClickListener(v -> openProfileSheet());
        findViewById(R.id.btnTopNotifications).setOnClickListener(v -> openNotificationsFragment());
        findViewById(R.id.btnTopOptions).setOnClickListener(v ->
                Toast.makeText(this, "Opciones (próximamente)", Toast.LENGTH_SHORT).show());

        // Si algún fragmento cambió monedas, Firestore nos actualizará por listener.
        getSupportFragmentManager().setFragmentResultListener(
                "coins_changed", this, (requestKey, bundle) -> { /* no-op */ }
        );

        requestRuntimePermissions();
        // Hacer rollover si cambió la fecha antes de mostrar
        maybeRunRollover();
        // Refrescar UI luego del posible rollover
        updateSmallWeekAndBigStepsFromStorage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeRunRollover();
        updateSmallWeekAndBigStepsFromStorage();
        secureStartSteps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        secureStopSteps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        secureStopSteps();
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

    private void openStatsFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,  android.R.anim.fade_out,
                        android.R.anim.fade_in,  android.R.anim.fade_out)
                .replace(R.id.root, new StatsFragment())
                .addToBackStack("stats")
                .commit();
    }

    private void openProfileSheet() {
        ProfileFragment sheet = new ProfileFragment();
        sheet.show(getSupportFragmentManager(), "profile_sheet");
    }

    private void openNotificationsFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,  android.R.anim.fade_out,
                        android.R.anim.fade_in,  android.R.anim.fade_out)
                .replace(R.id.root, new NotificationFragment())
                .addToBackStack("notifications")
                .commit();
    }

    private void requestRuntimePermissions() {
        ArrayList<String> req = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
            req.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            req.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!req.isEmpty()) {
            permsLauncher.launch(req.toArray(new String[0]));
        } else {
            secureStartSteps();
        }
    }

    // ====== Helpers de permisos/seguridad ======

    private boolean ensureARGranted() {
        return !(Build.VERSION.SDK_INT >= 29) ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void secureStartSteps() {
        if (stepsManager == null) return;
        if (!ensureARGranted()) return;
        try {
            stepsManager.start();
        } catch (SecurityException se) {
            Log.w(TAG, "SecurityException al iniciar StepsManager: " + se.getMessage());
        }
    }

    private void secureStopSteps() {
        if (stepsManager == null) return;
        try {
            stepsManager.stop();
        } catch (SecurityException se) {
            Log.w(TAG, "SecurityException al detener StepsManager: " + se.getMessage());
        }
    }

    // ==== Helpers de rollover y UI ====

    private String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    /** Rollover diario: suma km al semanal en Firestore y resetea pasos locales.
     *  También sincroniza 'mayor_pasos_dia' si se batió el récord local. */
    private void maybeRunRollover() {
        if (uid == null) return;

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String last = sp.getString(KEY_ULTIMO_DIA, "");
        String today = todayString();

        if (today.equals(last)) return; // mismo día

        long pasosHoy = sp.getLong(KEY_PASOS_HOY, 0L);
        double kmAgregar = pasosHoy * STEP_TO_KM;

        // Suma al semanal en Firestore usando cache local
        double nuevoSem = Math.max(0.0, kmSemanaCache + kmAgregar);
        repo.setKmSemana(uid, nuevoSem, v -> {}, e -> Log.w(TAG, "setKmSemana error: " + e.getMessage()));

        // récord (BDD -> repo)
        repo.updateMayorPasosDiaIfGreater(uid, pasosHoy);

        int diasContados = sp.getInt(KEY_DIAS_CONTADOS, 0) + 1;
        if (diasContados >= 7) {
            repo.setKmSemana(uid, 0.0, v -> {}, e -> Log.w(TAG, "reset semana error: " + e.getMessage()));
            kmSemanaCache = 0.0;
            diasContados = 0;
        }

        sp.edit()
                .putString(KEY_ULTIMO_DIA, today)
                .putLong(KEY_PASOS_HOY, 0L)
                .putInt(KEY_DIAS_CONTADOS, diasContados)
                .apply();

        // Refrescar UI local
        tvKmTotalBig.setText(String.valueOf(0));
        tvKmSemanaSmall.setText(String.format(Locale.getDefault(), "Semana: %.2f km", nuevoSem));
        kmSemanaCache = nuevoSem;
    }

    private void updateSmallWeekAndBigStepsFromStorage() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        long pasosHoy = sp.getLong(KEY_PASOS_HOY, 0L);
        tvKmTotalBig.setText(String.valueOf(pasosHoy));
        tvKmSemanaSmall.setText(String.format(Locale.getDefault(), "Semana: %.2f km", kmSemanaCache));
    }

    @Nullable
    private Double getNestedDouble(DocumentSnapshot snap, String dottedPath) {
        Object val = snap.get(dottedPath);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    // ===================== AVATAR =====================

    /** Lee los cos_id equipados del doc del usuario y arma el avatar. */
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

    /** Construye y setea el LayerDrawable respetando el orden de capas. */
    private void buildAvatarFromMyCosmetics(QuerySnapshot qs,
                                            @Nullable String pielId,
                                            @Nullable String pantalonId,
                                            @Nullable String remeraId,
                                            @Nullable String zapasId,
                                            @Nullable String cabezaId) {

        String pielAsset     = findAssetFor(qs, pielId);
        String pantAsset     = findAssetFor(qs, pantalonId);
        String remeraAsset   = findAssetFor(qs, remeraId);
        String zapasAsset    = findAssetFor(qs, zapasId);
        String cabezaAsset   = findAssetFor(qs, cabezaId);

        ArrayList<Drawable> layers = new ArrayList<>();
        addLayerIfExists(layers, pielAsset);      // base
        addLayerIfExists(layers, pantAsset);      // pantalón
        addLayerIfExists(layers, remeraAsset);    // remera
        addLayerIfExists(layers, zapasAsset);     // zapatillas
        addLayerIfExists(layers, cabezaAsset);    // gorra/sombrero

        if (layers.isEmpty()) {
            int def = getResIdByName("piel_startskin");
            if (def != 0) ivAvatar.setImageResource(def);
            return;
        }

        LayerDrawable ld = new LayerDrawable(layers.toArray(new Drawable[0]));
        ivAvatar.setImageDrawable(ld);
        ivAvatar.setAdjustViewBounds(true);
    }

    // ---- helpers avatar ----

    @Nullable
    private String asString(Object o) {
        return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null;
    }

    @Nullable
    private String findAssetFor(QuerySnapshot qs, @Nullable String cosId) {
        if (cosId == null) return null;
        DocumentSnapshot d = qs.getDocuments().stream()
                .filter(doc -> cosId.equals(doc.getId()))
                .findFirst().orElse(null);
        if (d == null) return null;
        Object v = d.get("myc_cache.cos_asset");
        return (v instanceof String && !((String) v).isEmpty()) ? (String) v : null;
    }

    private void addLayerIfExists(ArrayList<Drawable> layers, @Nullable String assetName) {
        if (assetName == null) return;
        int resId = getResIdByName(assetName);
        if (resId == 0) return;
        Drawable dr = ContextCompat.getDrawable(this, resId);
        if (dr != null) layers.add(dr);
    }

    private int getResIdByName(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }
}
