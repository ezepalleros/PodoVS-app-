package com.example.podovs;

import android.content.Context;
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

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

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

    // Firestore
    private FirestoreRepo repo;
    private String uid = null;
    private ListenerRegistration userListener;

    // Para convertir km -> pasos si no existe el campo explícito
    private static final double METROS_POR_PASO = 0.78;

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

        uid = requireContext()
                .getSharedPreferences("session", Context.MODE_PRIVATE)
                .getString("uid", null);
        repo = new FirestoreRepo();

        if (uid == null) {
            Toast.makeText(requireContext(), "Sesión inválida. Iniciá sesión nuevamente.", Toast.LENGTH_LONG).show();
            requireActivity().onBackPressed();
            return;
        }

        // Escuchar actualizaciones en tiempo real del usuario
        userListener = repo.listenUser(uid, userEventListener);

        // Por compatibilidad con otros flujos que disparan refresh manual
        getParentFragmentManager().setFragmentResultListener(
                "stats_refresh",
                this,
                (requestKey, bundle) -> repo.getUser(uid, this::bindStatsFromSnapshot,
                        e -> Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show())
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        // Forzar una lectura única para inicializar rápido (por si el listener tarda en llegar)
        if (uid != null) {
            repo.getUser(uid, this::bindStatsFromSnapshot,
                    e -> { /* no-op */ });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    // --- Listener de Firestore ---
    private final EventListener<DocumentSnapshot> userEventListener = (snap, err) -> {
        if (!isAdded()) return;
        if (err != null) return;
        if (snap == null || !snap.exists()) return;
        bindStatsFromSnapshot(snap);
    };

    // --- Vincular datos a la UI ---
    private void bindStatsFromSnapshot(DocumentSnapshot snap) {
        if (!isAdded()) return;

        // km totales
        double kmTotales = getDouble(snap, "usu_stats.km_total", 0.0);
        tvKmTotales.setText(String.format(Locale.getDefault(), "%.2f km", kmTotales));

        // pasos totales (si no existe campo, lo derivamos de km_total)
        long pasosTotales = getLong(snap, "usu_stats.pasos_totales",
                Math.max(0L, Math.round((kmTotales * 1000.0) / METROS_POR_PASO)));
        tvPasosTotales.setText(String.format(Locale.getDefault(), "%,d", pasosTotales));

        // objetos comprados
        long objetosComprados = getLong(snap, "usu_stats.objetos_comprados", 0L);
        tvObjetosComprados.setText(String.valueOf(objetosComprados));

        // metas cumplidas
        long metasDiariasOk = getLong(snap, "usu_stats.metas_diarias_cumplidas", 0L);
        long metasSemanalesOk = getLong(snap, "usu_stats.metas_semanales_cumplidas", 0L);
        tvMetasDiariasOk.setText(String.valueOf(metasDiariasOk));
        tvMetasSemanalesOk.setText(String.valueOf(metasSemanalesOk));

        // carreras ganadas (si no lo tenés aún en Firestore, quedará 0)
        long carrerasGanadas = getLong(snap, "usu_stats.carreras_ganadas", 0L);
        tvCarrerasGanadas.setText(String.valueOf(carrerasGanadas));

        // eventos participados (igual que arriba: default 0)
        long eventosParticipados = getLong(snap, "usu_stats.eventos_participados", 0L);
        tvEventosParticipados.setText(String.valueOf(eventosParticipados));

        // mejor posición mensual (nullable)
        Long mejorPos = getLongNullable(snap, "usu_stats.mejor_pos_mensual");
        tvMejorPosicionMensual.setText(mejorPos == null ? "-" : String.valueOf(mejorPos));
    }

    // --- Helpers para leer del snapshot ---
    private long getLong(DocumentSnapshot s, String path, long def) {
        Object v = s.get(path);
        return (v instanceof Number) ? ((Number) v).longValue() : def;
    }

    @Nullable
    private Long getLongNullable(DocumentSnapshot s, String path) {
        Object v = s.get(path);
        return (v instanceof Number) ? ((Number) v).longValue() : null;
    }

    private double getDouble(DocumentSnapshot s, String path, double def) {
        Object v = s.get(path);
        return (v instanceof Number) ? ((Number) v).doubleValue() : def;
    }
}
