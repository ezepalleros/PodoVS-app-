package com.example.podovs;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class GoalsFragment extends DialogFragment {

    private static final String SP_CLAIM = "goals_claim";
    private static final double METROS_POR_PASO = 0.78;

    private DatabaseHelper db;
    private long userId;

    // Views
    private ProgressBar pbDaily, pbWeekly;
    private TextView tvDailyMeta, tvDailyProgress, tvDailyCoins, tvDailyState;
    private TextView tvWeeklyMeta, tvWeeklyProgress, tvWeeklyCoins, tvWeeklyState;
    private MaterialButton btnDailyClaim, btnWeeklyClaim;
    private ImageButton btnClose;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar);
        db = new DatabaseHelper(requireContext());
        SharedPreferences sp = requireActivity().getSharedPreferences("session", Context.MODE_PRIVATE);
        userId = sp.getLong("user_id", -1L);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_goals, container, false);

        // Bind
        pbDaily = v.findViewById(R.id.pbDaily);
        pbWeekly = v.findViewById(R.id.pbWeekly);
        tvDailyMeta = v.findViewById(R.id.tvDailyMeta);
        tvDailyProgress = v.findViewById(R.id.tvDailyProgress);
        tvDailyCoins = v.findViewById(R.id.tvDailyCoins);
        tvDailyState = v.findViewById(R.id.tvDailyState);
        tvWeeklyMeta = v.findViewById(R.id.tvWeeklyMeta);
        tvWeeklyProgress = v.findViewById(R.id.tvWeeklyProgress);
        tvWeeklyCoins = v.findViewById(R.id.tvWeeklyCoins);
        tvWeeklyState = v.findViewById(R.id.tvWeeklyState);
        btnDailyClaim = v.findViewById(R.id.btnDailyClaim);
        btnWeeklyClaim = v.findViewById(R.id.btnWeeklyClaim);
        btnClose = v.findViewById(R.id.btnClose);

        btnClose.setOnClickListener(view -> dismissAllowingStateLoss());
        btnDailyClaim.setOnClickListener(view -> claimDaily());
        btnWeeklyClaim.setOnClickListener(view -> claimWeekly());

        bindData();
        return v;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        d.setCanceledOnTouchOutside(true);
        return d;
    }

    // -------------------- Datos y UI --------------------
    private void bindData() {
        if (userId <= 0) return;

        // Metas persistidas en SQLite (estadisticas)
        int[] metas = db.getMetas(userId);
        int metaDaily  = metas[0];
        int metaWeekly = metas[1];

        // Progreso actual
        long stepsToday = StepsPrefs.todaySteps(requireContext());
        double kmSemana = db.getKmSemana(userId);
        long stepsWeek  = Math.max(0L, Math.round((kmSemana * 1000.0) / METROS_POR_PASO));

        int nivel = db.getNivel(userId);

        // ---- Diario ----
        boolean claimedD = alreadyClaimedToday();
        boolean reachedD = stepsToday >= metaDaily;

        tvDailyMeta.setText(String.format(Locale.getDefault(), "Meta diaria: %,d pasos", metaDaily));
        tvDailyProgress.setText(String.format(Locale.getDefault(), "%,d / %,d", stepsToday, metaDaily));
        pbDaily.setMax(metaDaily);
        pbDaily.setProgress((int)Math.min(stepsToday, Integer.MAX_VALUE));

        long rewardDailyPreview = (nivel == 1) ? 1000L : stepsToday;
        tvDailyCoins.setText(String.format(Locale.getDefault(), "Recompensa posible: %,d", rewardDailyPreview));

        btnDailyClaim.setEnabled(reachedD && !claimedD);
        btnDailyClaim.setAlpha(btnDailyClaim.isEnabled() ? 1f : 0.5f);

        if (claimedD) {
            tvDailyState.setText("Completada");
            tvDailyState.setTextColor(Color.parseColor("#7BE48F"));
        } else if (reachedD) {
            tvDailyState.setText("Listo para reclamar");
            tvDailyState.setTextColor(Color.parseColor("#6FE6B7"));
        } else {
            long faltan = Math.max(0, metaDaily - stepsToday);
            tvDailyState.setText("Faltan " + faltan + " pasos");
            tvDailyState.setTextColor(Color.parseColor("#A6A9B1"));
        }

        // ---- Semanal ----
        boolean claimedW = alreadyClaimedWeek();
        boolean reachedW = stepsWeek >= metaWeekly;

        tvWeeklyMeta.setText(String.format(Locale.getDefault(), "Meta semanal: %,d pasos", metaWeekly));
        tvWeeklyProgress.setText(String.format(Locale.getDefault(), "%,d / %,d", stepsWeek, metaWeekly));
        pbWeekly.setMax(metaWeekly);
        pbWeekly.setProgress((int)Math.min(stepsWeek, Integer.MAX_VALUE));

        long rewardWeeklyPreview = (nivel == 1) ? 10000L : stepsWeek;
        tvWeeklyCoins.setText(String.format(Locale.getDefault(), "Recompensa posible: %,d", rewardWeeklyPreview));

        btnWeeklyClaim.setEnabled(reachedW && !claimedW);
        btnWeeklyClaim.setAlpha(btnWeeklyClaim.isEnabled() ? 1f : 0.5f);

        if (claimedW) {
            tvWeeklyState.setText("Completada");
            tvWeeklyState.setTextColor(Color.parseColor("#7BE48F"));
        } else if (reachedW) {
            tvWeeklyState.setText("Listo para reclamar");
            tvWeeklyState.setTextColor(Color.parseColor("#6FE6B7"));
        } else {
            long faltan = Math.max(0, metaWeekly - stepsWeek);
            tvWeeklyState.setText("Faltan " + faltan + " pasos");
            tvWeeklyState.setTextColor(Color.parseColor("#A6A9B1"));
        }
    }

    private void claimDaily() {
        if (userId <= 0 || alreadyClaimedToday()) return;

        int metaDaily = db.getMetas(userId)[0];
        long stepsToday = StepsPrefs.todaySteps(requireContext());
        if (stepsToday < metaDaily) {
            Toast.makeText(requireContext(), "Todavía no alcanzaste la meta diaria.", Toast.LENGTH_SHORT).show();
            return;
        }

        int nivel = db.getNivel(userId);
        long coins = (nivel == 1) ? 1000L : stepsToday; // tu regla
        db.addSaldo(userId, coins);
        db.onDailyGoalReached(userId); // XP
        markClaimedToday(coins);
        Toast.makeText(requireContext(), "¡+" + coins + " monedas!", Toast.LENGTH_LONG).show();
        bindData();
    }

    private void claimWeekly() {
        if (userId <= 0 || alreadyClaimedWeek()) return;

        int metaWeekly = db.getMetas(userId)[1];
        double kmSemana = db.getKmSemana(userId);
        long stepsWeek = Math.max(0L, Math.round((kmSemana * 1000.0) / METROS_POR_PASO));
        if (stepsWeek < metaWeekly) {
            Toast.makeText(requireContext(), "Todavía no alcanzaste la meta semanal.", Toast.LENGTH_SHORT).show();
            return;
        }

        int nivel = db.getNivel(userId);
        long coins = (nivel == 1) ? 10000L : stepsWeek; // tu regla
        db.addSaldo(userId, coins);
        db.onWeeklyGoalReached(userId); // XP
        markClaimedWeek(coins);
        Toast.makeText(requireContext(), "¡+" + coins + " monedas!", Toast.LENGTH_LONG).show();
        bindData();
    }

    // ---------- Flags de Claim ----------
    private SharedPreferences prefs() { return requireContext().getSharedPreferences(SP_CLAIM, Context.MODE_PRIVATE); }
    private String ymd() { return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(System.currentTimeMillis()); }
    private String yearWeek() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + "-" + String.format(Locale.getDefault(), "%02d", c.get(Calendar.WEEK_OF_YEAR));
    }
    private boolean alreadyClaimedToday() { return ymd().equals(prefs().getString("last_daily_claim_"+userId, "")); }
    private boolean alreadyClaimedWeek()  { return yearWeek().equals(prefs().getString("last_weekly_claim_"+userId, "")); }
    private void markClaimedToday(long amount) {
        prefs().edit()
                .putString("last_daily_claim_"+userId, ymd())
                .putLong("last_daily_amount_"+userId, amount)
                .apply();
    }
    private void markClaimedWeek(long amount) {
        prefs().edit()
                .putString("last_weekly_claim_"+userId, yearWeek())
                .putLong("last_weekly_amount_"+userId, amount)
                .apply();
    }

    // --------- Pasos (guardados fuera de SQLite) ---------
    static class StepsPrefs {
        private static final String SP_STEPS = "steps_prefs";
        private static final String KEY_TOTAL_PREFIX = "total_";
        static long todaySteps(Context ctx) {
            String day = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(System.currentTimeMillis());
            return ctx.getSharedPreferences(SP_STEPS, Context.MODE_PRIVATE)
                    .getLong(KEY_TOTAL_PREFIX + day, 0L);
        }
    }
}
