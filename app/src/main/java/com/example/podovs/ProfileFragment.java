package com.example.podovs;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
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

    // Data
    private DatabaseHelper db;
    private long userId = -1L;

    // Config
    private static final String PREFS_PROFILE = "profile_prefs";
    private static final String KEY_SLOT1 = "slot1_key";
    private static final String KEY_SLOT2 = "slot2_key";
    private static final int XP_PER_LEVEL = 100; // debe coincidir con DatabaseHelper

    // Conjunto de métricas disponibles (clave -> título en UI)
    // Las claves se usan para guardar preferencia de qué mostrar.
    private static final LinkedHashMap<String, String> METRIC_TITLES = new LinkedHashMap<>();
    static {
        METRIC_TITLES.put(DatabaseHelper.COL_ST_KM_TOTAL, "Km recorridos");
        METRIC_TITLES.put(DatabaseHelper.COL_ST_CARRERAS_GANADAS, "Carreras ganadas");
        METRIC_TITLES.put(DatabaseHelper.COL_ST_OBJ_COMPRADOS, "Objetos comprados");
        METRIC_TITLES.put(DatabaseHelper.COL_ST_EVENTOS_PART, "Eventos participados");
        METRIC_TITLES.put(DatabaseHelper.COL_ST_MEJOR_POS_MENSUAL, "Mejor posición mensual");
        METRIC_TITLES.put(DatabaseHelper.COL_ST_METAS_DIARIAS_OK, "Metas diarias OK");
        METRIC_TITLES.put(DatabaseHelper.COL_ST_METAS_SEMANALES_OK, "Metas semanales OK");
        METRIC_TITLES.put(DatabaseHelper.COL_ST_PASOS_TOTALES, "Pasos totales");
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

        db = new DatabaseHelper(requireContext());
        userId = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
                .getLong("user_id", -1L);

        // Cargar nombre/level/xp
        loadHeader();

        // Cargar y mostrar dos tarjetas configurables
        refreshStatCards();

        // Editar qué métrica se muestra en cada tarjeta
        btnEdit1.setOnClickListener(view -> showPickerForSlot(1));
        btnEdit2.setOnClickListener(view -> showPickerForSlot(2));
    }

    // ---------- Header (nombre, nivel, XP) ----------
    private void loadHeader() {
        // nombre + nivel
        Cursor cu = db.getReadableDatabase().rawQuery(
                "SELECT " + DatabaseHelper.COL_NOMBRE + ", " + DatabaseHelper.COL_NIVEL +
                        " FROM " + DatabaseHelper.TABLE_USUARIOS + " WHERE " + DatabaseHelper.COL_ID + "=? LIMIT 1",
                new String[]{ String.valueOf(userId) }
        );
        String nombre = "-";
        int nivel = 1;
        try {
            if (cu.moveToFirst()) {
                nombre = cu.isNull(0) ? "-" : cu.getString(0);
                nivel = cu.isNull(1) ? 1 : cu.getInt(1);
            }
        } finally { cu.close(); }

        tvName.setText(nombre);
        tvLevel.setText(String.format(Locale.getDefault(), "Nivel %d", nivel));

        // XP actual
        Cursor cxp = db.getReadableDatabase().rawQuery(
                "SELECT s." + DatabaseHelper.COL_ST_XP +
                        " FROM " + DatabaseHelper.TABLE_USUARIOS + " u " +
                        "JOIN " + DatabaseHelper.TABLE_STATS + " s ON s." + DatabaseHelper.COL_ST_ID + " = u." + DatabaseHelper.COL_STATS_FK + " " +
                        "WHERE u." + DatabaseHelper.COL_ID + "=? LIMIT 1",
                new String[]{ String.valueOf(userId) }
        );
        int xp = 0;
        try {
            if (cxp.moveToFirst()) xp = cxp.isNull(0) ? 0 : cxp.getInt(0);
        } finally { cxp.close(); }

        pbXp.setMax(XP_PER_LEVEL);
        pbXp.setProgress(Math.max(0, Math.min(xp, XP_PER_LEVEL)));
        tvXpHint.setText(String.format(Locale.getDefault(), "%d / %d XP", xp, XP_PER_LEVEL));
    }

    // ---------- Tarjetas configurables ----------
    private void refreshStatCards() {
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE);
        String k1 = sp.getString(KEY_SLOT1, DatabaseHelper.COL_ST_KM_TOTAL);
        String k2 = sp.getString(KEY_SLOT2, DatabaseHelper.COL_ST_CARRERAS_GANADAS);

        // Evitar que ambas tarjetas muestren lo mismo en el primer inicio
        if (TextUtils.equals(k1, k2)) k2 = DatabaseHelper.COL_ST_CARRERAS_GANADAS;

        Map<String, Object> values = readAllMetrics();

        tvStat1Title.setText(titleFor(k1));
        tvStat1Value.setText(formatValue(k1, values.get(k1)));

        tvStat2Title.setText(titleFor(k2));
        tvStat2Value.setText(formatValue(k2, values.get(k2)));
    }

    private Map<String, Object> readAllMetrics() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        String sql = "SELECT " +
                "s." + DatabaseHelper.COL_ST_KM_TOTAL + "," +
                "s." + DatabaseHelper.COL_ST_CARRERAS_GANADAS + "," +
                "s." + DatabaseHelper.COL_ST_OBJ_COMPRADOS + "," +
                "s." + DatabaseHelper.COL_ST_EVENTOS_PART + "," +
                "s." + DatabaseHelper.COL_ST_MEJOR_POS_MENSUAL + "," +
                "s." + DatabaseHelper.COL_ST_METAS_DIARIAS_OK + "," +
                "s." + DatabaseHelper.COL_ST_METAS_SEMANALES_OK + "," +
                "s." + DatabaseHelper.COL_ST_PASOS_TOTALES +
                " FROM " + DatabaseHelper.TABLE_USUARIOS + " u " +
                "JOIN " + DatabaseHelper.TABLE_STATS + " s ON s." + DatabaseHelper.COL_ST_ID + " = u." + DatabaseHelper.COL_STATS_FK + " " +
                "WHERE u." + DatabaseHelper.COL_ID + "=? LIMIT 1";

        Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{ String.valueOf(userId) });
        try {
            if (c.moveToFirst()) {
                map.put(DatabaseHelper.COL_ST_KM_TOTAL, c.isNull(0) ? 0d : c.getDouble(0));
                map.put(DatabaseHelper.COL_ST_CARRERAS_GANADAS, c.isNull(1) ? 0 : c.getInt(1));
                map.put(DatabaseHelper.COL_ST_OBJ_COMPRADOS, c.isNull(2) ? 0 : c.getInt(2));
                map.put(DatabaseHelper.COL_ST_EVENTOS_PART, c.isNull(3) ? 0 : c.getInt(3));
                map.put(DatabaseHelper.COL_ST_MEJOR_POS_MENSUAL, c.isNull(4) ? null : c.getInt(4));
                map.put(DatabaseHelper.COL_ST_METAS_DIARIAS_OK, c.isNull(5) ? 0 : c.getInt(5));
                map.put(DatabaseHelper.COL_ST_METAS_SEMANALES_OK, c.isNull(6) ? 0 : c.getInt(6));
                map.put(DatabaseHelper.COL_ST_PASOS_TOTALES, c.isNull(7) ? 0 : c.getInt(7));
            }
        } finally { c.close(); }
        return map;
    }

    private String titleFor(String key) {
        String t = METRIC_TITLES.get(key);
        return (t == null) ? key : t;
    }

    private String formatValue(String key, Object value) {
        if (key.equals(DatabaseHelper.COL_ST_KM_TOTAL)) {
            double km = value instanceof Number ? ((Number) value).doubleValue() : 0d;
            return String.format(Locale.getDefault(), "%.2f", km);
        }
        if (key.equals(DatabaseHelper.COL_ST_MEJOR_POS_MENSUAL)) {
            if (value == null) return "-";
            return String.valueOf(((Number) value).intValue());
        }
        // restantes: números enteros
        int v = (value instanceof Number) ? ((Number) value).intValue() : 0;
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
                    refreshStatCards();
                })
                .show();
    }
}
