package com.example.podovs;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VsInfoFragment extends DialogFragment {

    private static final String ARG_VS_ID = "vs_id";
    private static final String ARG_UID = "uid";

    public static VsInfoFragment newInstance(@NonNull String vsId,
                                             @NonNull String uid) {
        VsInfoFragment f = new VsInfoFragment();
        Bundle b = new Bundle();
        b.putString(ARG_VS_ID, vsId);
        b.putString(ARG_UID, uid);
        f.setArguments(b);
        return f;
    }

    private String vsId;
    private String myUid;

    private FirebaseFirestore db;
    private ListenerRegistration registration;

    private View rootOverlay;
    private MaterialCardView card;
    private TextView tvTitle, tvSubtitle, tvTarget, tvTime, tvWinner;
    private LinearLayout containerPlayers;
    private MaterialButton btnClose;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vsinfo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vsId = getArguments() != null ? getArguments().getString(ARG_VS_ID) : null;
        myUid = getArguments() != null ? getArguments().getString(ARG_UID) : null;
        db = FirebaseFirestore.getInstance();

        rootOverlay = view.findViewById(R.id.rootVsInfoOverlay);
        card = view.findViewById(R.id.cardVsInfo);
        tvTitle = view.findViewById(R.id.tvVsInfoTitle);
        tvSubtitle = view.findViewById(R.id.tvVsInfoSubtitle);
        tvTarget = view.findViewById(R.id.tvVsInfoTarget);
        tvTime = view.findViewById(R.id.tvVsInfoTime);
        tvWinner = view.findViewById(R.id.tvVsInfoWinner);
        containerPlayers = view.findViewById(R.id.containerVsPlayers);
        btnClose = view.findViewById(R.id.btnCloseVsInfo);
        View btnCloseIcon = view.findViewById(R.id.btnCloseIcon);

        rootOverlay.setOnClickListener(v -> dismiss());
        card.setOnClickListener(v -> {
        });
        btnClose.setOnClickListener(v -> dismiss());
        btnCloseIcon.setOnClickListener(v -> dismiss());

        if (!TextUtils.isEmpty(vsId)) {
            registration = db.collection("versus").document(vsId)
                    .addSnapshotListener((snap, e) -> {
                        if (e != null || snap == null || !snap.exists()) {
                            return;
                        }
                        bindSnapshot(snap);
                    });
        }
    }

    private void bindSnapshot(@NonNull DocumentSnapshot snap) {
        Boolean typeB = snap.getBoolean("ver_type");
        boolean isRace = typeB != null && typeB;

        Object playersRaw = snap.get("ver_players");
        List<String> players = playersRaw instanceof List ? (List<String>) playersRaw : null;
        if (players == null) return;

        String title = isRace ? "Carrera 1 vs 1" : "Maratón 1 vs 1";
        tvTitle.setText(title);

        String owner = snap.getString("ver_owner");
        if (myUid != null && myUid.equals(owner)) {
            tvSubtitle.setText("Sos el creador de este versus");
        } else {
            tvSubtitle.setText("Estás participando en este versus");
        }

        Long targetSteps = snap.getLong("ver_targetSteps");
        Long days = null;
        Object dObj = snap.get("ver_days");
        if (dObj instanceof Number) days = ((Number) dObj).longValue();

        if (isRace) {
            String objetivo = (targetSteps != null && targetSteps > 0)
                    ? String.format(Locale.getDefault(), "Meta: %,d pasos", targetSteps)
                    : "Meta: sorpresa";
            tvTarget.setText(objetivo);
        } else {
            String objetivo = (days != null && days > 0)
                    ? String.format(Locale.getDefault(), "Duración: %d días", days)
                    : "Duración: indefinida";
            tvTarget.setText(objetivo);
        }

        Timestamp createdAt = snap.getTimestamp("ver_createdAt");
        boolean finished = snap.getBoolean("ver_finished") != null &&
                snap.getBoolean("ver_finished");

        if (createdAt != null && days != null && days > 0) {
            long start = createdAt.toDate().getTime();
            long end = start + TimeUnit.DAYS.toMillis(days);
            long now = System.currentTimeMillis();
            long diff = end - now;

            if (diff <= 0) {
                tvTime.setText("El desafío ya terminó.");
            } else {
                long d = TimeUnit.MILLISECONDS.toDays(diff);
                long h = TimeUnit.MILLISECONDS.toHours(diff - TimeUnit.DAYS.toMillis(d));
                tvTime.setText(
                        String.format(Locale.getDefault(),
                                "Faltan %d d %d h", d, h));
            }
        } else if (isRace && targetSteps != null && targetSteps > 0) {
            tvTime.setText("Termina cuando alguien llegue a la meta.");
        } else {
            tvTime.setText("");
        }

        String winnerId = snap.getString("ver_winnerUid");
        if (finished && !TextUtils.isEmpty(winnerId)) {
            String label = winnerId.equals(myUid) ? "Ganaste este versus" :
                    "Ganador: @" + winnerId.substring(0, Math.min(6, winnerId.length()));
            tvWinner.setText(label);
            tvWinner.setVisibility(View.VISIBLE);
        } else if (finished) {
            tvWinner.setText("Versus finalizado");
            tvWinner.setVisibility(View.VISIBLE);
        } else {
            tvWinner.setVisibility(View.GONE);
        }

        Object progressRaw = snap.get("ver_progress");
        Map<String, Map<String, Object>> progress = null;
        if (progressRaw instanceof Map) {
            progress = (Map<String, Map<String, Object>>) progressRaw;
        }
        if (progress == null) progress = new HashMap<>();

        containerPlayers.removeAllViews();

        for (String pUid : players) {
            Map<String, Object> pData = progress.get(pUid);
            long steps = 0L;
            if (pData != null && pData.get("steps") instanceof Number) {
                steps = ((Number) pData.get("steps")).longValue();
            }
            containerPlayers.addView(makePlayerRow(pUid, steps, targetSteps));
        }
    }

    private View makePlayerRow(String playerUid, long steps, @Nullable Long targetSteps) {
        if (getContext() == null) return new View(getContext());

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (4 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(lp);

        String shortId = "@" + playerUid.substring(0, Math.min(6, playerUid.length()));
        String name = shortId;
        if (playerUid.equals(myUid)) {
            name = shortId + " (vos)";
        }

        TextView tvLine = new TextView(getContext());
        tvLine.setText(
                String.format(Locale.getDefault(), "%s   %,d pasos", name, steps));
        tvLine.setTextSize(14);
        tvLine.setTextColor(0xFFE5E7EB);

        ProgressBar pb = new ProgressBar(getContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setMinimumHeight((int) (6 * getResources().getDisplayMetrics().density));
        pb.setScaleY(1.2f);
        pb.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0x33111111));
        pb.setProgressTintList(
                android.content.res.ColorStateList.valueOf(0xFF818CF8));

        int progress = 0;
        if (targetSteps != null && targetSteps > 0) {
            progress = (int) Math.min(100,
                    Math.round(steps * 100f / targetSteps));
        }
        pb.setProgress(progress);

        row.addView(tvLine);
        row.addView(pb);

        return row;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) registration.remove();
    }
}
