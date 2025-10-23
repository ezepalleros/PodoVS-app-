package com.example.podovs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends BottomSheetDialogFragment {

    // UI
    private ImageView ivAvatar;
    private TextView tvName;
    private TextView tvLevel;
    private ProgressBar pbXp;
    private TextView tvXpHint;

    private TextView tvStat1Title, tvStat1Value;
    private ImageButton btnEdit1;
    private TextView tvStat2Title, tvStat2Value;
    private ImageButton btnEdit2;

    // Repo / sesión
    private FirestoreRepo repo;
    private FirebaseFirestore db;
    private String uid = null;

    // Config
    private static final String PREFS_PROFILE = "profile_prefs";
    private static final String KEY_SLOT1 = "slot1_key";
    private static final String KEY_SLOT2 = "slot2_key";
    private static final int XP_PER_LEVEL_BASE = 100; // debe coincidir con FirestoreRepo (100 * nivel)

    // Conjunto de métricas disponibles (clave Firestore -> título en UI)
    private static final LinkedHashMap<String, String> METRIC_TITLES = new LinkedHashMap<>();
    static {
        METRIC_TITLES.put("usu_stats.km_total", "Km recorridos");
        // ❌ quitado: usu_stats.km_semana
        METRIC_TITLES.put("usu_stats.objetos_comprados", "Objetos comprados");
        METRIC_TITLES.put("usu_stats.metas_diarias_total", "Metas diarias OK (total)");
        METRIC_TITLES.put("usu_stats.metas_semana_total", "Metas semanales OK (total)");
        METRIC_TITLES.put("usu_stats.mayor_pasos_dia", "Récord de pasos (día)");
        METRIC_TITLES.put("usu_stats.eventos_participados", "Eventos participados");
        METRIC_TITLES.put("usu_stats.mejor_posicion", "Mejor posición");
    }

    public ProfileFragment() {}

    @Nullable @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        ivAvatar = v.findViewById(R.id.ivAvatarLarge);
        tvName = v.findViewById(R.id.tvUserName);
        tvLevel = v.findViewById(R.id.tvLevel);
        pbXp = v.findViewById(R.id.pbXp);
        tvXpHint = v.findViewById(R.id.tvXpHint);

        tvStat1Title = v.findViewById(R.id.tvStat1Title);
        tvStat1Value = v.findViewById(R.id.tvStat1Value);
        btnEdit1 = v.findViewById(R.id.btnEdit1);

        tvStat2Title = v.findViewById(R.id.tvStat2Title);
        tvStat2Value = v.findViewById(R.id.tvStat2Value);
        btnEdit2 = v.findViewById(R.id.btnEdit2);

        ImageButton btnClose = v.findViewById(R.id.btnCloseSheet);
        btnClose.setOnClickListener(view -> dismiss());

        // Para que no distorsione el sprite
        ivAvatar.setAdjustViewBounds(true);
        ivAvatar.setScaleType(ImageView.ScaleType.CENTER);
        ivAvatar.setImageResource(R.drawable.default_avatar);

        uid = requireContext()
                .getSharedPreferences("session", Context.MODE_PRIVATE)
                .getString("uid", null);
        repo = new FirestoreRepo();
        db   = FirebaseFirestore.getInstance();

        // Cargar header (nombre/nivel/XP) y tarjetas + avatar
        loadHeaderAndCards();

        // Editar qué métrica se muestra en cada tarjeta
        btnEdit1.setOnClickListener(view -> showPickerForSlot(1));
        btnEdit2.setOnClickListener(view -> showPickerForSlot(2));
    }

    // ---------- Header (nombre, nivel, XP) + Tarjetas ----------
    private void loadHeaderAndCards() {
        if (uid == null) return;

        repo.getUser(uid, (DocumentSnapshot snap) -> {
            if (snap == null || !snap.exists() || !isAdded()) return;

            // Nombre y nivel
            String nombre = snap.getString("usu_nombre");
            int nivel = safeInt(snap.getLong("usu_nivel"), 1);

            tvName.setText(nombre != null ? nombre : "-");
            tvLevel.setText(String.format(Locale.getDefault(), "Nivel %d", nivel));

            // XP y barra
            long xp = getNestedLong(snap, "usu_stats.xp", 0L);
            int maxForLevel = XP_PER_LEVEL_BASE * Math.max(1, nivel);
            pbXp.setMax(maxForLevel);
            pbXp.setProgress((int) Math.max(0, Math.min(xp, maxForLevel)));
            tvXpHint.setText(String.format(Locale.getDefault(), "%d / %d XP", xp, maxForLevel));

            // Tarjetas
            refreshStatCardsWithSnapshot(snap);

            // Avatar (capas desde my_cosmetics)
            renderAvatarFromUserSnapshot(snap);

        }, e -> {
            // opcional: log/Toast
        });
    }

    private void refreshStatCardsWithSnapshot(DocumentSnapshot snap) {
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE);
        String default1 = "usu_stats.km_total";
        String default2 = "usu_stats.objetos_comprados";

        String k1 = sp.getString(KEY_SLOT1, default1);
        String k2 = sp.getString(KEY_SLOT2, default2);

        if (TextUtils.equals(k1, k2)) k2 = default2;

        Map<String, Object> values = readAllMetricsFromSnapshot(snap);

        tvStat1Title.setText(titleFor(k1));
        tvStat1Value.setText(formatValue(k1, values.get(k1)));

        tvStat2Title.setText(titleFor(k2));
        tvStat2Value.setText(formatValue(k2, values.get(k2)));
    }

    // Lee del snapshot todas las métricas que soportamos
    private Map<String, Object> readAllMetricsFromSnapshot(DocumentSnapshot snap) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (String key : METRIC_TITLES.keySet()) {
            Object v = snap.get(key);
            if (v == null) {
                if (key.endsWith("km_total")) {
                    map.put(key, 0.0);
                } else {
                    map.put(key, 0L);
                }
            } else {
                map.put(key, v);
            }
        }
        return map;
    }

    private String titleFor(String key) {
        String t = METRIC_TITLES.get(key);
        return (t == null) ? key : t;
    }

    private String formatValue(String key, Object value) {
        // Km totales como decimal
        if (key.endsWith("km_total")) {
            double km = (value instanceof Number) ? ((Number) value).doubleValue() : 0d;
            return String.format(Locale.getDefault(), "%.2f", km);
        }
        // Mejor posición: si 0 -> "-"
        if (key.endsWith("mejor_posicion")) {
            long pos = (value instanceof Number) ? ((Number) value).longValue() : 0L;
            return pos <= 0 ? "-" : String.valueOf(pos);
        }
        // Resto: números enteros con separadores
        long v = (value instanceof Number) ? ((Number) value).longValue() : 0L;
        return String.format(Locale.getDefault(), "%,d", v);
    }

    private void showPickerForSlot(int slot) {
        final String[] keys = METRIC_TITLES.keySet().toArray(new String[0]);
        final String[] titles = METRIC_TITLES.values().toArray(new String[0]);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Elegí una estadística")
                .setItems(titles, (dialog, which) -> {
                    String chosenKey = keys[which];
                    SharedPreferences sp = requireContext().getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE);
                    if (slot == 1) {
                        sp.edit().putString(KEY_SLOT1, chosenKey).apply();
                    } else {
                        sp.edit().putString(KEY_SLOT2, chosenKey).apply();
                    }
                    loadHeaderAndCards();
                })
                .show();
    }

    // ===================== AVATAR =====================

    /** Lee los cos_id equipados del doc y arma el avatar por capas. */
    private void renderAvatarFromUserSnapshot(DocumentSnapshot snap) {
        String pielId     = asString(snap.get("usu_equipped.usu_piel"));
        String pantalonId = asString(snap.get("usu_equipped.usu_pantalon"));
        String remeraId   = asString(snap.get("usu_equipped.usu_remera"));
        String zapasId    = asString(snap.get("usu_equipped.usu_zapas"));
        String cabezaId   = asString(snap.get("usu_equipped.usu_cabeza"));

        // Si no hay nada equipado, mostrar piel_startskin y salir
        if (pielId == null && pantalonId == null && remeraId == null
                && zapasId == null && cabezaId == null) {
            int fallback = getResIdByName("piel_startskin");
            if (fallback != 0 && isAdded()) ivAvatar.setImageResource(fallback);
            return;
        }

        db.collection("users").document(uid).collection("my_cosmetics")
                .get()
                .addOnSuccessListener(qs -> buildAvatarFromMyCosmetics(qs, pielId, pantalonId, remeraId, zapasId, cabezaId));
    }

    /** Construye y setea el LayerDrawable respetando el orden de capas. */
    private void buildAvatarFromMyCosmetics(QuerySnapshot qs,
                                            @Nullable String pielId,
                                            @Nullable String pantalonId,
                                            @Nullable String remeraId,
                                            @Nullable String zapasId,
                                            @Nullable String cabezaId) {
        if (!isAdded()) return;

        String pielAsset   = findAssetFor(qs, pielId);
        String pantAsset   = findAssetFor(qs, pantalonId);
        String remeraAsset = findAssetFor(qs, remeraId);
        String zapasAsset  = findAssetFor(qs, zapasId);
        String cabezaAsset = findAssetFor(qs, cabezaId);

        ArrayList<android.graphics.drawable.Drawable> layers = new ArrayList<>();
        addLayerIfExists(layers, pielAsset);
        addLayerIfExists(layers, pantAsset);
        addLayerIfExists(layers, remeraAsset);
        addLayerIfExists(layers, zapasAsset);
        addLayerIfExists(layers, cabezaAsset);

        if (layers.isEmpty()) {
            int def = getResIdByName("piel_startskin");
            if (def != 0) ivAvatar.setImageResource(def);
            return;
        }

        // 1) Tamaño base del sprite (fallback 32x48)
        int baseW = Math.max(1, layers.get(0).getIntrinsicWidth());
        int baseH = Math.max(1, layers.get(0).getIntrinsicHeight());
        if (baseW <= 1 || baseH <= 1) { baseW = 32; baseH = 48; }

        // 2) Componer todas las capas en un bitmap base sin escalado
        Bitmap baseBmp = Bitmap.createBitmap(baseW, baseH, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(baseBmp);
        for (android.graphics.drawable.Drawable d : layers) {
            d.setBounds(0, 0, baseW, baseH);
            d.draw(canvas);
        }

        // 3) Escalar con vecino más cercano a ~96dp de alto (factor entero)
        final int targetHpx = dpToPx(96);
        int factor = Math.max(1, targetHpx / baseH);

        int outW = baseW * factor;
        int outH = baseH * factor;
        Bitmap scaled = Bitmap.createScaledBitmap(baseBmp, outW, outH, /*filter=*/false);

        // 4) Mostrar centrado y sin recorte; NO tocar LayoutParams
        ivAvatar.setAdjustViewBounds(true);
        ivAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        ivAvatar.setImageBitmap(scaled);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Nullable
    private String asString(Object o) {
        return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null;
    }

    @Nullable
    private String findAssetFor(QuerySnapshot qs, @Nullable String cosId) {
        if (cosId == null) return null;
        for (DocumentSnapshot d : qs.getDocuments()) {
            if (cosId.equals(d.getId())) {
                Object v = d.get("myc_cache.cos_asset");
                if (v instanceof String && !((String) v).isEmpty()) return (String) v;
            }
        }
        return null;
    }

    private void addLayerIfExists(ArrayList<android.graphics.drawable.Drawable> layers, @Nullable String assetName) {
        if (assetName == null) return;
        int resId = getResIdByName(assetName);
        if (resId == 0) return;
        android.graphics.drawable.Drawable dr = ContextCompat.getDrawable(requireContext(), resId);
        if (dr != null) layers.add(dr);
    }

    private int getResIdByName(String name) {
        return getResources().getIdentifier(name, "drawable", requireContext().getPackageName());
    }

    // --------- Helpers ---------
    private int safeInt(Long v, int def) { return (v == null) ? def : v.intValue(); }
    private long getNestedLong(DocumentSnapshot s, String path, long def) {
        Object v = s.get(path);
        return (v instanceof Number) ? ((Number) v).longValue() : def;
    }
}
