package com.example.podovs;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Locale;

public class StatsFragment extends Fragment {

    private TextView tvPasosTotales;
    private TextView tvKmTotales;
    private TextView tvCarrerasGanadas;
    private TextView tvObjetosComprados;
    private TextView tvEventosParticipados;
    private TextView tvMejorPosicionMensual;
    private TextView tvMetasDiariasOk;
    private TextView tvMetasSemanalesOk;

    private DatabaseHelper db;
    private long userId = -1L;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        ((TextView) v.findViewById(R.id.tvTitle)).setText("Estadísticas");
        ImageButton btnClose = v.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(b -> requireActivity()
                .getSupportFragmentManager()
                .popBackStack());

        tvPasosTotales         = v.findViewById(R.id.tvPasosTotales);
        tvKmTotales            = v.findViewById(R.id.tvKmTotales);
        tvCarrerasGanadas      = v.findViewById(R.id.tvCarrerasGanadas);
        tvObjetosComprados     = v.findViewById(R.id.tvObjetosComprados);
        tvEventosParticipados  = v.findViewById(R.id.tvEventosParticipados);
        tvMejorPosicionMensual = v.findViewById(R.id.tvMejorPosicionMensual);
        tvMetasDiariasOk       = v.findViewById(R.id.tvMetasDiariasOk);
        tvMetasSemanalesOk     = v.findViewById(R.id.tvMetasSemanalesOk);

        db = new DatabaseHelper(requireContext());
        userId = requireContext().getSharedPreferences("session", requireContext().MODE_PRIVATE)
                .getLong("user_id", -1L);

        if (userId <= 0) {
            Toast.makeText(requireContext(), "Sesión inválida. Iniciá sesión nuevamente.", Toast.LENGTH_LONG).show();
            requireActivity().onBackPressed();
            return;
        }

        // Escuchar eventos de actualización cuando se reclaman metas
        getParentFragmentManager().setFragmentResultListener(
                "stats_refresh",
                this,
                (requestKey, bundle) -> loadStats()
        );

        loadStats();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Por si volvemos desde GoalsFragment sin evento explícito
        loadStats();
    }

    private void loadStats() {
        String sql = "SELECT " +
                "s." + DatabaseHelper.COL_ST_PASOS_TOTALES + ", " +
                "s." + DatabaseHelper.COL_ST_KM_TOTAL + ", " +
                "s." + DatabaseHelper.COL_ST_CARRERAS_GANADAS + ", " +
                "s." + DatabaseHelper.COL_ST_OBJ_COMPRADOS + ", " +
                "s." + DatabaseHelper.COL_ST_EVENTOS_PART + ", " +
                "s." + DatabaseHelper.COL_ST_MEJOR_POS_MENSUAL + ", " +
                "s." + DatabaseHelper.COL_ST_METAS_DIARIAS_OK + ", " +
                "s." + DatabaseHelper.COL_ST_METAS_SEMANALES_OK +
                " FROM " + DatabaseHelper.TABLE_USUARIOS + " u " +
                "JOIN " + DatabaseHelper.TABLE_STATS + " s ON s." + DatabaseHelper.COL_ST_ID + " = u." + DatabaseHelper.COL_STATS_FK + " " +
                "WHERE u." + DatabaseHelper.COL_ID + "=? LIMIT 1";

        Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(userId)});
        try {
            if (c.moveToFirst()) {
                int pasosTotales        = safeGetInt(c, 0);
                double kmTotales        = safeGetDouble(c, 1);
                int carrerasGanadas     = safeGetInt(c, 2);
                int objetosComprados    = safeGetInt(c, 3);
                int eventosParticipados = safeGetInt(c, 4);
                Integer mejorPosMensual = safeGetNullableInt(c, 5);
                int metasDiariasOk      = safeGetInt(c, 6);
                int metasSemanalesOk    = safeGetInt(c, 7);

                tvPasosTotales.setText(String.format(Locale.getDefault(), "%,d", pasosTotales));
                tvKmTotales.setText(String.format(Locale.getDefault(), "%.2f km", kmTotales));
                tvCarrerasGanadas.setText(String.valueOf(carrerasGanadas));
                tvObjetosComprados.setText(String.valueOf(objetosComprados));
                tvEventosParticipados.setText(String.valueOf(eventosParticipados));
                tvMejorPosicionMensual.setText(mejorPosMensual == null ? "-" : String.valueOf(mejorPosMensual));
                tvMetasDiariasOk.setText(String.valueOf(metasDiariasOk));
                tvMetasSemanalesOk.setText(String.valueOf(metasSemanalesOk));
            } else {
                Toast.makeText(requireContext(), "No se encontraron estadísticas.", Toast.LENGTH_LONG).show();
            }
        } finally {
            c.close();
        }
    }

    private int safeGetInt(Cursor c, int idx) {
        return c.isNull(idx) ? 0 : c.getInt(idx);
    }

    private double safeGetDouble(Cursor c, int idx) {
        return c.isNull(idx) ? 0.0 : c.getDouble(idx);
    }

    private Integer safeGetNullableInt(Cursor c, int idx) {
        return c.isNull(idx) ? null : c.getInt(idx);
    }
}
