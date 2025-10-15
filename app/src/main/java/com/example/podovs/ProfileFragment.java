package com.example.podovs;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.DocumentSnapshot;

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
    private String uid = null;

    // Config
    private static final String PREFS_PROFILE = "profile_prefs";
    private static final String KEY_SLOT1 = "slot1_key";
    private static final String KEY_SLOT2 = "slot2_key";
    private static final int XP_PER_LEVEL_BASE = 100; // debe coincidir con FirestoreRepo (100 * nivel)

    // Conjunto de métricas disponibles (clave Firestore -> título en UI)
    // Claves son paths de Firestore dentro del doc users/{uid}
    private static final LinkedHashMap<String, String> METRIC_TITLES = new LinkedHashMap<>();
    static {
        METRIC_TITLES.put("usu_stats.km_total", "Km recorridos");
        METRIC_TITLES.put("usu_stats.objetos_comprados", "Objetos comprados");
        METRIC_TITLES.put("usu_stats.metas_diarias_cumplidas", "Metas diarias OK");
        METRIC_TITLES.put("usu_stats.metas_semanales_cumplidas", "Metas semanales OK");
        METRIC_TITLES.put("usu_stats.km_semana", "Km esta semana");
        METRIC_TITLES.put("usu_stats.km_hoy", "Km hoy");
        // Podés agregar más cuando existan en Firestore (p. ej., carreras, eventos, etc.)
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

        ivAvatar.setImageResource(R.drawable.default_avatar);

        uid = requireContext()
                .getSharedPreferences("session", Context.MODE_PRIVATE)
                .getString("uid", null);
        repo = new FirestoreRepo();

        // Cargar header y tarjetas
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

        }, e -> {
            // Podés loguear o mostrar un toast si querés
        });
    }

    private void refreshStatCardsWithSnapshot(DocumentSnapshot snap) {
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE);
        String default1 = "usu_stats.km_total";
        String default2 = "usu_stats.objetos_comprados";

        String k1 = sp.getString(KEY_SLOT1, default1);
        String k2 = sp.getString(KEY_SLOT2, default2);

        // Evitar que ambas tarjetas muestren lo mismo en el primer inicio
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
                // defaults razonables
                if (key.endsWith("km_total") || key.endsWith("km_semana") || key.endsWith("km_hoy")) {
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
        if (key.endsWith("km_total") || key.endsWith("km_semana") || key.endsWith("km_hoy")) {
            double km = (value instanceof Number) ? ((Number) value).doubleValue() : 0d;
            return String.format(Locale.getDefault(), "%.2f", km);
        }
        // restantes: enteros
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
                    // Recargar solo las tarjetas (con los últimos datos)
                    loadHeaderAndCards();
                })
                .show();
    }

    // --------- Helpers ---------
    private int safeInt(Long v, int def) { return (v == null) ? def : v.intValue(); }
    private long getNestedLong(DocumentSnapshot s, String path, long def) {
        Object v = s.get(path);
        return (v instanceof Number) ? ((Number) v).longValue() : def;
    }
}
