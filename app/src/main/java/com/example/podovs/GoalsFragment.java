package com.example.podovs;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
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
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class GoalsFragment extends DialogFragment {

    private static final String SP_CLAIM = "goals_claim";
    private static final double METROS_POR_PASO = 0.78;
    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    private FirestoreRepo repo;
    private String uid;

    private ProgressBar pbDaily, pbWeekly;
    private TextView tvDailyMeta, tvDailyProgress, tvDailyCoins, tvDailyState;
    private TextView tvWeeklyMeta, tvWeeklyProgress, tvWeeklyCoins, tvWeeklyState;
    private MaterialButton btnDailyClaim, btnWeeklyClaim;

    // overlays de bloqueo
    private View overlayDailyLock, overlayWeeklyLock;
    private TextView tvDailyLock, tvWeeklyLock;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar);
        repo = new FirestoreRepo();
        SharedPreferences sp = requireActivity()
                .getSharedPreferences("session", Context.MODE_PRIVATE);
        uid = sp.getString("uid", null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_goals, container, false);

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

        overlayDailyLock = v.findViewById(R.id.overlayDailyLock);
        overlayWeeklyLock = v.findViewById(R.id.overlayWeeklyLock);
        tvDailyLock = v.findViewById(R.id.tvDailyLock);
        tvWeeklyLock = v.findViewById(R.id.tvWeeklyLock);

        ImageButton btnClose = v.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(view -> dismissAllowingStateLoss());

        btnDailyClaim.setOnClickListener(view -> claimDaily());
        btnWeeklyClaim.setOnClickListener(view -> claimWeekly());

        bindData();
        return v;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        d.setCanceledOnTouchOutside(true);
        return d;
    }

    private void bindData() {
        if (uid == null || !isAdded()) return;

        repo.getUser(uid, snap -> {
            if (snap == null || !snap.exists() || !isAdded()) return;

            long metaDaily = getNestedLong(snap, "usu_stats.meta_diaria_pasos", 8000L);
            long metaWeekly = getNestedLong(snap, "usu_stats.meta_semanal_pasos", 56000L);

            long stepsTodayRaw = StepsPrefs.todaySteps(requireContext());

            double kmSemana = getNestedDouble(snap, "usu_stats.km_semana", 0.0);
            long stepsWeekRaw = Math.max(0L,
                    Math.round((kmSemana * 1000.0) / METROS_POR_PASO));

            long weekStart = getNestedLong(snap, "usu_stats.week_started_at",
                    System.currentTimeMillis());
            long msElapsed = System.currentTimeMillis() - weekStart;
            long msLeftWeekWindow = Math.max(0L, WEEK_MS - msElapsed);
            boolean weekWindowReady = (msLeftWeekWindow == 0L);

            // ---------- DIARIA ----------
            boolean claimedD = alreadyClaimedToday();
            long stepsTodayUi = claimedD ? metaDaily : stepsTodayRaw;
            boolean reachedD = stepsTodayRaw >= metaDaily;

            tvDailyMeta.setText(String.format(Locale.getDefault(),
                    "Meta diaria: %,d pasos", metaDaily));
            tvDailyProgress.setText(String.format(Locale.getDefault(),
                    "%,d / %,d", stepsTodayUi, metaDaily));
            pbDaily.setMax(safeInt(metaDaily));
            pbDaily.setProgress((int) Math.min(stepsTodayUi, Integer.MAX_VALUE));
            tvDailyCoins.setText(String.format(Locale.getDefault(),
                    "Recompensa posible: %,d", metaDaily));

            btnDailyClaim.setEnabled(reachedD && !claimedD);
            btnDailyClaim.setAlpha(btnDailyClaim.isEnabled() ? 1f : 0.5f);

            if (claimedD) {
                long msDailyLeft = msUntilEndOfDay();
                tvDailyState.setText("Meta diaria completada");
                tvDailyState.setTextColor(Color.parseColor("#7BE48F"));
                showDailyLock(true, msDailyLeft);
            } else if (reachedD) {
                tvDailyState.setText("Listo para reclamar");
                tvDailyState.setTextColor(Color.parseColor("#6FE6B7"));
                showDailyLock(false, 0L);
            } else {
                long faltan = Math.max(0, metaDaily - stepsTodayRaw);
                tvDailyState.setText("Faltan " + faltan + " pasos");
                tvDailyState.setTextColor(Color.parseColor("#A6A9B1"));
                showDailyLock(false, 0L);
            }

            // ---------- SEMANAL ----------
            boolean claimedW = alreadyClaimedWeek();
            long stepsWeekUi = claimedW ? metaWeekly : stepsWeekRaw;
            boolean reachedW = stepsWeekRaw >= metaWeekly;

            tvWeeklyMeta.setText(String.format(Locale.getDefault(),
                    "Meta semanal: %,d pasos", metaWeekly));
            tvWeeklyProgress.setText(String.format(Locale.getDefault(),
                    "%,d / %,d", stepsWeekUi, metaWeekly));
            pbWeekly.setMax(safeInt(metaWeekly));
            pbWeekly.setProgress((int) Math.min(stepsWeekUi, Integer.MAX_VALUE));
            tvWeeklyCoins.setText(String.format(Locale.getDefault(),
                    "Recompensa posible: %,d", metaWeekly));

            boolean canClaimWeekly = weekWindowReady && reachedW && !claimedW;

            btnWeeklyClaim.setEnabled(canClaimWeekly);
            btnWeeklyClaim.setAlpha(canClaimWeekly ? 1f : 0.5f);

            if (claimedW) {
                long lockMs = msUntilWeeklyAvailable();
                tvWeeklyState.setText("Meta semanal completada");
                tvWeeklyState.setTextColor(Color.parseColor("#7BE48F"));
                showWeeklyLock(true, lockMs);
            } else if (!weekWindowReady) {
                long hrs = msLeftWeekWindow / (60L * 60L * 1000L);
                long days = hrs / 24;
                long remH = hrs % 24;
                tvWeeklyState.setText("Se habilita en " + days + "d " + remH + "h");
                tvWeeklyState.setTextColor(Color.parseColor("#A6A9B1"));
                showWeeklyLock(false, 0L);
            } else if (reachedW) {
                tvWeeklyState.setText("Listo para reclamar");
                tvWeeklyState.setTextColor(Color.parseColor("#6FE6B7"));
                showWeeklyLock(false, 0L);
            } else {
                long faltan = Math.max(0, metaWeekly - stepsWeekRaw);
                tvWeeklyState.setText("Faltan " + faltan + " pasos");
                tvWeeklyState.setTextColor(Color.parseColor("#A6A9B1"));
                showWeeklyLock(false, 0L);
            }

        }, e -> {
            if (isAdded()) {
                Toast.makeText(requireContext(),
                        "Error cargando metas: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void claimDaily() {
        if (uid == null || alreadyClaimedToday()) return;

        repo.getUser(uid, snap -> {
            long metaDaily = getNestedLong(snap, "usu_stats.meta_diaria_pasos", 8000L);
            long stepsToday = StepsPrefs.todaySteps(requireContext());
            if (stepsToday < metaDaily) {
                Toast.makeText(requireContext(),
                        "Todavía no alcanzaste la meta diaria.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            long coins = metaDaily;
            repo.claimDaily(uid, coins,
                    v -> {
                        markClaimedToday(coins);
                        getParentFragmentManager()
                                .setFragmentResult("coins_changed", new Bundle());
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "¡+" + coins + " monedas!",
                                    Toast.LENGTH_LONG).show();
                            bindData();
                        }
                    },
                    e -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "No se pudo reclamar: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }, e -> {
            if (isAdded()) {
                Toast.makeText(requireContext(),
                        "Error verificando meta diaria.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void claimWeekly() {
        if (uid == null || alreadyClaimedWeek()) return;

        repo.getUser(uid, snap -> {
            long metaWeekly = getNestedLong(snap,
                    "usu_stats.meta_semanal_pasos", 56000L);
            double kmSemana = getNestedDouble(snap, "usu_stats.km_semana", 0.0);
            long stepsWeek = Math.max(0L,
                    Math.round((kmSemana * 1000.0) / METROS_POR_PASO));

            long weekStart = getNestedLong(snap, "usu_stats.week_started_at",
                    System.currentTimeMillis());
            boolean windowReady =
                    System.currentTimeMillis() - weekStart >= WEEK_MS;

            if (!windowReady) {
                Toast.makeText(requireContext(),
                        "Aún no pasaron 7 días desde el inicio de la semana.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (stepsWeek < metaWeekly) {
                Toast.makeText(requireContext(),
                        "Todavía no alcanzaste la meta semanal.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            long coins = metaWeekly;
            repo.claimWeekly(uid, coins,
                    v -> {
                        markClaimedWeek(coins);
                        getParentFragmentManager()
                                .setFragmentResult("coins_changed", new Bundle());
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "¡+" + coins + " monedas!",
                                    Toast.LENGTH_LONG).show();
                            bindData();
                        }
                    },
                    e -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "No se pudo reclamar: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }, e -> {
            if (isAdded()) {
                Toast.makeText(requireContext(),
                        "Error verificando meta semanal.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- Overlays ----------

    private void showDailyLock(boolean show, long msLeft) {
        if (!isAdded() || overlayDailyLock == null) return;
        if (show) {
            overlayDailyLock.setVisibility(View.VISIBLE);
            if (tvDailyLock != null) {
                tvDailyLock.setText("Faltan " + formatDuration(msLeft));
            }
        } else {
            overlayDailyLock.setVisibility(View.GONE);
        }
    }

    private void showWeeklyLock(boolean show, long msLeft) {
        if (!isAdded() || overlayWeeklyLock == null) return;
        if (show) {
            overlayWeeklyLock.setVisibility(View.VISIBLE);
            if (tvWeeklyLock != null) {
                tvWeeklyLock.setText("Faltan " + formatDuration(msLeft));
            }
        } else {
            overlayWeeklyLock.setVisibility(View.GONE);
        }
    }

    // ---------- flags de claim ----------

    private SharedPreferences prefs() {
        return requireContext()
                .getSharedPreferences(SP_CLAIM, Context.MODE_PRIVATE);
    }

    private String ymd() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(System.currentTimeMillis());
    }

    private boolean alreadyClaimedToday() {
        return ymd().equals(
                prefs().getString("last_daily_claim_" + uid, ""));
    }

    private boolean alreadyClaimedWeek() {
        long last = prefs().getLong("last_weekly_claim_ts_" + uid, 0L);
        if (last == 0L) return false;
        long now = System.currentTimeMillis();
        return now - last < WEEK_MS;
    }

    private long msUntilWeeklyAvailable() {
        long last = prefs().getLong("last_weekly_claim_ts_" + uid, 0L);
        if (last == 0L) return 0L;
        long end = last + WEEK_MS;
        long now = System.currentTimeMillis();
        return Math.max(0L, end - now);
    }

    private void markClaimedToday(long amount) {
        prefs().edit()
                .putString("last_daily_claim_" + uid, ymd())
                .putLong("last_daily_amount_" + uid, amount)
                .apply();
    }

    private void markClaimedWeek(long amount) {
        prefs().edit()
                .putLong("last_weekly_claim_ts_" + uid,
                        System.currentTimeMillis())
                .putLong("last_weekly_amount_" + uid, amount)
                .apply();
    }

    // ---------- helpers de tiempo ----------

    private long msUntilEndOfDay() {
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, 1);
        long end = c.getTimeInMillis();
        return Math.max(0L, end - now);
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "0 min";
        long totalMinutes = ms / (60L * 1000L);
        long days = totalMinutes / (60L * 24L);
        long hours = (totalMinutes % (60L * 24L)) / 60L;
        long mins = totalMinutes % 60L;

        if (days > 0) {
            return String.format(Locale.getDefault(),
                    "%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format(Locale.getDefault(),
                    "%dh %02dm", hours, mins);
        } else {
            return String.format(Locale.getDefault(),
                    "%d min", Math.max(1, mins));
        }
    }

    // ---------- otros helpers ----------

    static class StepsPrefs {
        private static final String SP_STEPS = "steps_prefs";
        private static final String KEY_TOTAL_PREFIX = "total_";

        static long todaySteps(Context ctx) {
            String day = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    .format(System.currentTimeMillis());
            return ctx.getSharedPreferences(SP_STEPS, Context.MODE_PRIVATE)
                    .getLong(KEY_TOTAL_PREFIX + day, 0L);
        }
    }

    private int safeInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private long getNestedLong(DocumentSnapshot s, String path, long def) {
        Object v = s.get(path);
        return (v instanceof Number) ? ((Number) v).longValue() : def;
    }

    private double getNestedDouble(DocumentSnapshot s, String path, double def) {
        Object v = s.get(path);
        return (v instanceof Number) ? ((Number) v).doubleValue() : def;
    }
}
