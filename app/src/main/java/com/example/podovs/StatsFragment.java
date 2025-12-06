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
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;

public class StatsFragment extends Fragment {

    private TextView tvKmTotales;
    private TextView tvCarrerasGanadas;
    private TextView tvObjetosComprados;
    private TextView tvEventosParticipados;
    private TextView tvMejorPosicionMensual;
    private TextView tvMetasDiariasOk;
    private TextView tvMetasSemanalesOk;

    private FirestoreRepo repo;
    private String uid = null;
    private ListenerRegistration userListener;

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

        tvKmTotales = v.findViewById(R.id.tvKmTotales);
        tvCarrerasGanadas = v.findViewById(R.id.tvCarrerasGanadas);
        tvObjetosComprados = v.findViewById(R.id.tvObjetosComprados);
        tvEventosParticipados = v.findViewById(R.id.tvEventosParticipados);
        tvMejorPosicionMensual = v.findViewById(R.id.tvMejorPosicionMensual);
        tvMetasDiariasOk = v.findViewById(R.id.tvMetasDiariasOk);
        tvMetasSemanalesOk = v.findViewById(R.id.tvMetasSemanalesOk);

        uid = requireContext()
                .getSharedPreferences("session", Context.MODE_PRIVATE)
                .getString("uid", null);
        repo = new FirestoreRepo();

        if (uid == null) {
            Toast.makeText(requireContext(), "Sesión inválida. Iniciá sesión nuevamente.", Toast.LENGTH_LONG).show();
            requireActivity().onBackPressed();
            return;
        }

        userListener = repo.listenUser(uid, userEventListener);

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
        if (uid != null) {
            repo.getUser(uid, this::bindStatsFromSnapshot, e -> { /* no-op */ });
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

    private final EventListener<DocumentSnapshot> userEventListener = (snap, err) -> {
        if (!isAdded()) return;
        if (err != null) return;
        if (snap == null || !snap.exists()) return;
        bindStatsFromSnapshot(snap);
    };

    private void bindStatsFromSnapshot(DocumentSnapshot snap) {
        if (!isAdded()) return;

        double kmTotales = getDouble(snap, "usu_stats.km_total", 0.0);
        tvKmTotales.setText(String.format(Locale.getDefault(), "%.2f km", kmTotales));

        long objetosComprados = getLong(snap, "usu_stats.objetos_comprados", 0L);
        tvObjetosComprados.setText(String.valueOf(objetosComprados));

        long metasDiariasOk = getLong(snap, "usu_stats.metas_diarias_total", 0L);
        long metasSemanalesOk = getLong(snap, "usu_stats.metas_semana_total", 0L);
        tvMetasDiariasOk.setText(String.valueOf(metasDiariasOk));
        tvMetasSemanalesOk.setText(String.valueOf(metasSemanalesOk));

        long carrerasGanadas = getLong(snap, "usu_stats.carreras_ganadas", 0L);
        tvCarrerasGanadas.setText(String.valueOf(carrerasGanadas));

        long eventosParticipados = getLong(snap, "usu_stats.eventos_participados", 0L);
        tvEventosParticipados.setText(String.valueOf(eventosParticipados));

        Long mejorPos = getLongNullable(snap, "usu_stats.mejor_posicion");
        tvMejorPosicionMensual.setText((mejorPos == null || mejorPos == 0L) ? "-" : String.valueOf(mejorPos));
    }

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
