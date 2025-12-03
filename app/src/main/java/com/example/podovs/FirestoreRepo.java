package com.example.podovs;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class FirestoreRepo {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    private static final int XP_PER_LEVEL = 100;
    private static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    public static final int MAX_ACTIVE_VERSUS = 3;

    private static final String[] STARTER_IDS = {
            "cos_id_1","cos_id_2","cos_id_3","cos_id_4","cos_id_5","cos_id_6","cos_id_7",
            "cos_id_13","cos_id_14","cos_id_15"
    };
    private static final String DEFAULT_SKIN_ID = "cos_id_2";

    public FirestoreRepo() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    private DocumentReference userDoc(@NonNull String uid) {
        return db.collection("users").document(uid);
    }
    private DocumentReference cosmeticDoc(@NonNull String cosId) {
        return db.collection("cosmetics").document(cosId);
    }
    private DocumentReference inventoryDoc(@NonNull String uid, @NonNull String cosId) {
        return userDoc(uid).collection("my_cosmetics").document(cosId);
    }

    // rooms / versus
    private CollectionReference roomsCol() { return db.collection("rooms"); }
    private CollectionReference versusCol() { return db.collection("versus"); }
    private DocumentReference versusDoc(@NonNull String id) { return versusCol().document(id); }

    // ---------- Auth ----------
    public void signIn(@NonNull String email, @NonNull String password,
                       @NonNull OnSuccessListener<AuthResult> ok,
                       @NonNull OnFailureListener err) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(ok)
                .addOnFailureListener(err);
    }

    public void createUser(@NonNull String email, @NonNull String password,
                           @NonNull OnSuccessListener<AuthResult> ok,
                           @NonNull OnFailureListener err) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(ok)
                .addOnFailureListener(err);
    }

    @Nullable
    public FirebaseUser currentUser() { return auth.getCurrentUser(); }

    // ---------- User ----------
    public void getUser(@NonNull String uid,
                        @NonNull OnSuccessListener<DocumentSnapshot> ok,
                        @NonNull OnFailureListener err) {
        userDoc(uid).get().addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void initUserProfile(@NonNull String uid,
                                @NonNull String nombre,
                                @NonNull String dif,
                                @NonNull OnSuccessListener<Void> ok,
                                @NonNull OnFailureListener err) {
        Map<String, Object> data = new HashMap<>();
        data.put("usu_nombre", nombre);
        data.put("usu_saldo", 0L);
        data.put("usu_nivel", 1);
        data.put("usu_difi", dif.toLowerCase(Locale.ROOT));
        data.put("usu_rol", "user");
        data.put("usu_suspendido", false);

        Map<String, Object> stats = new HashMap<>();
        stats.put("km_semana", 0.0);
        stats.put("km_total",  0.0);
        stats.put("xp", 0L);
        stats.put("objetos_comprados", 0L);
        stats.put("meta_diaria_pasos",  1000L);
        stats.put("meta_semanal_pasos", 10000L);
        stats.put("carreras_ganadas",      0L);
        stats.put("eventos_participados",  0L);
        stats.put("mejor_posicion",        0L);
        stats.put("mayor_pasos_dia",       0L);
        stats.put("metas_diarias_total",   0L);
        stats.put("metas_semana_total",    0L);
        stats.put("week_started_at", System.currentTimeMillis());

        data.put("usu_stats", stats);

        Map<String, Object> eq = new HashMap<>();
        eq.put("usu_cabeza", null);
        eq.put("usu_remera", null);
        eq.put("usu_pantalon", null);
        eq.put("usu_zapas", null);
        eq.put("usu_piel", null);
        data.put("usu_equipped", eq);

        userDoc(uid).set(data)
                .addOnSuccessListener(v -> syncGoalsWithDifficulty(uid, dif, ok, err))
                .addOnFailureListener(err);
    }

    /** Garantiza campos, corrige legados y fuerza invariante km_total >= km_semana. */
    public void ensureStats(@NonNull String uid) {
        DocumentReference ref = userDoc(uid);
        ref.get().addOnSuccessListener(snap -> {
            Map<String, Object> up = new HashMap<>();
            // limpiar legados
            up.put("usu_stats.km_hoy", FieldValue.delete());
            up.put("usu_stats.metas_diarias_cumplidas", FieldValue.delete());
            up.put("usu_stats.metas_semanales_cumplidas", FieldValue.delete());
            // asegurar actuales
            ensureMissingDouble(snap, up, "usu_stats.km_semana",          0.0d);
            ensureMissingDouble(snap, up, "usu_stats.km_total",           0.0d);
            ensureMissingLong  (snap, up, "usu_stats.xp",                 0L);
            ensureMissingLong  (snap, up, "usu_stats.objetos_comprados",  0L);
            ensureMissingLong  (snap, up, "usu_stats.meta_diaria_pasos",  1000L);
            ensureMissingLong  (snap, up, "usu_stats.meta_semanal_pasos", 10000L);
            ensureMissingLong  (snap, up, "usu_stats.carreras_ganadas",     0L);
            ensureMissingLong  (snap, up, "usu_stats.eventos_participados", 0L);
            ensureMissingLong  (snap, up, "usu_stats.mejor_posicion",       0L);
            ensureMissingLong  (snap, up, "usu_stats.mayor_pasos_dia",      0L);
            ensureMissingLong  (snap, up, "usu_stats.metas_diarias_total",  0L);
            ensureMissingLong  (snap, up, "usu_stats.metas_semana_total",   0L);
            // inicio de semana
            if (!(snap.get("usu_stats.week_started_at") instanceof Number)) {
                up.put("usu_stats.week_started_at", System.currentTimeMillis());
            }
            if (!up.isEmpty()) ref.update(up);

            db.runTransaction((Transaction.Function<Void>) tr -> {
                DocumentSnapshot s = tr.get(ref);
                Double semD = s.getDouble("usu_stats.km_semana");
                Double totD = s.getDouble("usu_stats.km_total");
                double sem = semD == null ? 0.0 : semD;
                double tot = totD == null ? 0.0 : totD;
                if (sem > tot) tr.update(ref, "usu_stats.km_total", sem);
                return null;
            });
        });
    }

    public Query rankingHoy() {
        return db.collection("users")
                .orderBy("usu_stats.mayor_pasos_dia", Query.Direction.DESCENDING)
                .limit(50);
    }
    public Query rankingSemana() {
        return db.collection("users")
                .orderBy("usu_stats.km_semana", Query.Direction.DESCENDING)
                .limit(50);
    }

    // ---------- Saldo ----------
    public void addSaldo(@NonNull String uid, long delta,
                         @NonNull OnSuccessListener<Void> ok,
                         @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot snap = tr.get(userDoc(uid));
            Long saldoL = snap.getLong("usu_saldo");
            long saldo = saldoL == null ? 0L : saldoL;
            long nuevo = saldo + delta;
            if (nuevo < 0) throw new IllegalStateException("Saldo insuficiente");
            tr.update(userDoc(uid), "usu_saldo", nuevo);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ---------- Distancias ----------
    public void setKmSemana(@NonNull String uid, double km,
                            @NonNull OnSuccessListener<Void> ok,
                            @NonNull OnFailureListener err) {
        DocumentReference ref = userDoc(uid);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot s = tr.get(ref);
            Double totD = s.getDouble("usu_stats.km_total");
            double tot = totD == null ? 0.0 : totD;
            double nuevoSem = Math.max(0.0, km);
            double nuevoTot = Math.max(tot, nuevoSem);
            Map<String, Object> up = new HashMap<>();
            up.put("usu_stats.km_semana", nuevoSem);
            up.put("usu_stats.km_total", nuevoTot);
            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void setKmTotal(@NonNull String uid, double km,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        DocumentReference ref = userDoc(uid);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot s = tr.get(ref);
            Double semD = s.getDouble("usu_stats.km_semana");
            double sem = semD == null ? 0.0 : semD;
            double nuevoTot = Math.max(0.0, km);
            if (nuevoTot < sem) nuevoTot = sem;
            tr.update(ref, "usu_stats.km_total", nuevoTot);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void addKmDelta(@NonNull String uid, double kmDelta,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        if (kmDelta <= 0) { ok.onSuccess(null); return; }
        DocumentReference ref = userDoc(uid);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot s = tr.get(ref);
            Double curSemD = s.getDouble("usu_stats.km_semana");
            Double curTotD = s.getDouble("usu_stats.km_total");
            double curSem = curSemD == null ? 0.0 : curSemD;
            double curTot = curTotD == null ? 0.0 : curTotD;
            double nuevoSem = Math.max(0.0, curSem + kmDelta);
            double nuevoTot = Math.max(0.0, curTot + kmDelta);
            if (nuevoTot < nuevoSem) nuevoTot = nuevoSem;
            Map<String, Object> up = new HashMap<>();
            up.put("usu_stats.km_semana", nuevoSem);
            up.put("usu_stats.km_total",  nuevoTot);
            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void updateMayorPasosDiaIfGreater(@NonNull String uid, long candidate) {
        DocumentReference ref = userDoc(uid);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot s = tr.get(ref);
            Object v = s.get("usu_stats.mayor_pasos_dia");
            long cur = (v instanceof Number) ? ((Number) v).longValue() : 0L;
            if (candidate > cur) tr.update(ref, "usu_stats.mayor_pasos_dia", candidate);
            return null;
        });
    }

    // ---------- Dificultad / Metas ----------
    private double difMultiplier(@NonNull String dif) {
        String d = dif.toLowerCase(Locale.ROOT);
        if (d.contains("dificil") || d.contains("difícil") || d.contains("alto")) return 0.3;
        if (d.contains("medio")  || d.contains("normal")) return 0.2;
        return 0.1;
    }

    private long dailyFor(int nivel, String dif) {
        int n = Math.max(1, nivel);
        double mult = difMultiplier(dif);
        long base = 1000L;
        return Math.round(base * (1.0 + mult * Math.sqrt(n - 1)));
    }

    private long weeklyFor(int nivel, String dif) {
        int n = Math.max(1, nivel);
        double mult = difMultiplier(dif);
        long base = 10000L;
        return Math.round(base * (1.0 + mult * Math.sqrt(n - 1)));
    }

    public void syncGoalsWithDifficulty(@NonNull String uid,
                                        @NonNull String dificultad,
                                        @NonNull OnSuccessListener<Void> ok,
                                        @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);
            Long lvlL = s.getLong("usu_nivel");
            int nivel = (lvlL == null) ? 1 : lvlL.intValue();
            long diaria  = dailyFor(nivel, dificultad);
            long semanal = weeklyFor(nivel, dificultad);
            Map<String,Object> m = new HashMap<>();
            m.put("usu_difi", dificultad.toLowerCase(Locale.ROOT));
            m.put("usu_stats.meta_diaria_pasos",  diaria);
            m.put("usu_stats.meta_semanal_pasos", semanal);
            tr.update(ref, m);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void setDifficulty(@NonNull String uid, @NonNull String nuevaDifi,
                              @NonNull OnSuccessListener<Void> ok,
                              @NonNull OnFailureListener err) {
        syncGoalsWithDifficulty(uid, nuevaDifi, ok, err);
    }

    public void changeDifficultyWithCooldown(@NonNull String uid, @NonNull String nuevaDifi,
                                             @NonNull OnSuccessListener<Void> ok,
                                             @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            long now = System.currentTimeMillis();
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);

            Long lastChange = null;
            Object v = s.get("usu_difi_changed_at");
            if (v instanceof Number) lastChange = ((Number) v).longValue();

            if (lastChange != null && now - lastChange < WEEK_MS) {
                throw new IllegalStateException("Solo podés cambiar la dificultad una vez por semana.");
            }

            Long lvlL = s.getLong("usu_nivel");
            int nivel = (lvlL == null) ? 1 : lvlL.intValue();
            long diaria  = dailyFor(nivel, nuevaDifi);
            long semanal = weeklyFor(nivel, nuevaDifi);

            Map<String,Object> up = new HashMap<>();
            up.put("usu_difi", nuevaDifi.toLowerCase(Locale.ROOT));
            up.put("usu_stats.meta_diaria_pasos",  diaria);
            up.put("usu_stats.meta_semanal_pasos", semanal);
            up.put("usu_difi_changed_at", now);

            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void resetLevelAndGoals(@NonNull String uid,
                                   @NonNull OnSuccessListener<Void> ok,
                                   @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            Map<String,Object> up = new HashMap<>();
            up.put("usu_nivel", 1);
            up.put("usu_stats.meta_diaria_pasos", 1000L);
            up.put("usu_stats.meta_semanal_pasos", 10000L);
            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void forceRecalcGoals(@NonNull String uid,
                                 @NonNull OnSuccessListener<Void> ok,
                                 @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);
            Long lvlL = s.getLong("usu_nivel");
            int nivel = (lvlL == null) ? 1 : lvlL.intValue();
            String dif = (s.getString("usu_difi") == null) ? "facil" : s.getString("usu_difi");
            long diaria  = dailyFor(nivel, dif);
            long semanal = weeklyFor(nivel, dif);
            Map<String,Object> up = new HashMap<>();
            up.put("usu_stats.meta_diaria_pasos",  diaria);
            up.put("usu_stats.meta_semanal_pasos", semanal);
            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ---------- XP / Nivel ----------
    public void normalizeXpLevel(@NonNull String uid,
                                 @NonNull OnSuccessListener<Void> ok,
                                 @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);
            Long xpL = s.getLong("usu_stats.xp");
            long xp = (xpL == null) ? 0L : xpL;
            Long lvlL = s.getLong("usu_nivel");
            int nivel = (lvlL == null) ? 1 : lvlL.intValue();
            String dif = (s.getString("usu_difi") == null) ? "facil" : s.getString("usu_difi");

            long nuevoXp = Math.max(0L, xp);
            int nuevoNivel = Math.max(1, nivel);
            boolean leveleo = false;
            while (nuevoXp >= XP_PER_LEVEL) { nuevoXp -= XP_PER_LEVEL; nuevoNivel++; leveleo = true; }

            Map<String,Object> up = new HashMap<>();
            if (nuevoXp != xp)    up.put("usu_stats.xp", nuevoXp);
            if (nuevoNivel != nivel) up.put("usu_nivel", nuevoNivel);
            if (leveleo) {
                up.put("usu_stats.meta_diaria_pasos",  dailyFor(nuevoNivel, dif));
                up.put("usu_stats.meta_semanal_pasos", weeklyFor(nuevoNivel, dif));
            }

            if (!up.isEmpty()) tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void addXpAndMaybeLevelUp(@NonNull String uid, int deltaXp,
                                     @NonNull OnSuccessListener<Integer> ok,
                                     @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Integer>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);

            Long xpL = s.getLong("usu_stats.xp");
            long xp = (xpL == null) ? 0L : xpL;
            Long lvlL = s.getLong("usu_nivel");
            int nivel = (lvlL == null) ? 1 : lvlL.intValue();
            String dif = (s.getString("usu_difi") == null) ? "facil" : s.getString("usu_difi");

            long nuevoXp = Math.max(0, xp + deltaXp);
            int nuevoNivel = nivel;
            boolean leveleo = false;
            while (nuevoXp >= XP_PER_LEVEL) { nuevoXp -= XP_PER_LEVEL; nuevoNivel++; leveleo = true; }

            Map<String, Object> up = new HashMap<>();
            up.put("usu_nivel", nuevoNivel);
            up.put("usu_stats.xp", nuevoXp);
            if (leveleo) {
                up.put("usu_stats.meta_diaria_pasos",  dailyFor(nuevoNivel, dif));
                up.put("usu_stats.meta_semanal_pasos", weeklyFor(nuevoNivel, dif));
            }

            tr.update(ref, up);
            return nuevoNivel;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ---------- Recompensas / Logros ----------
    private void grantReward(@NonNull String uid,
                             long coinsDelta,
                             int xpDelta,
                             boolean incDailyCounter,
                             boolean incWeeklyCounter,
                             @NonNull OnSuccessListener<Void> ok,
                             @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);

            Long saldoL = s.getLong("usu_saldo");
            long saldo = (saldoL == null) ? 0L : saldoL;
            long nuevoSaldo = saldo + coinsDelta;
            if (nuevoSaldo < 0) throw new IllegalStateException("Saldo insuficiente");

            Long xpL = s.getLong("usu_stats.xp");
            long xp = (xpL == null) ? 0L : xpL;
            Long lvlL = s.getLong("usu_nivel");
            int nivel = (lvlL == null) ? 1 : lvlL.intValue();
            String dif = (s.getString("usu_difi") == null) ? "facil" : s.getString("usu_difi");

            long nuevoXp = Math.max(0, xp + xpDelta);
            int nuevoNivel = nivel;
            boolean leveleo = false;
            while (nuevoXp >= XP_PER_LEVEL) { nuevoXp -= XP_PER_LEVEL; nuevoNivel++; leveleo = true; }

            Map<String, Object> up = new HashMap<>();
            up.put("usu_saldo", nuevoSaldo);
            up.put("usu_nivel", nuevoNivel);
            up.put("usu_stats.xp", nuevoXp);
            if (incDailyCounter)  up.put("usu_stats.metas_diarias_total", FieldValue.increment(1));
            if (incWeeklyCounter) up.put("usu_stats.metas_semana_total",  FieldValue.increment(1));

            if (leveleo) {
                up.put("usu_stats.meta_diaria_pasos",  dailyFor(nuevoNivel, dif));
                up.put("usu_stats.meta_semanal_pasos", weeklyFor(nuevoNivel, dif));
            }

            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void onDailyGoalReached(@NonNull String uid, @NonNull OnSuccessListener<Void> ok,
                                   @NonNull OnFailureListener err) {
        grantReward(uid, 50, 25, true, false, ok, err);
    }

    public void onWeeklyGoalReached(@NonNull String uid, @NonNull OnSuccessListener<Void> ok,
                                    @NonNull OnFailureListener err) {
        grantReward(uid, 250, 90, false, true, ok, err);
    }

    public void claimDaily(@NonNull String uid, long coins, @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        grantReward(uid, coins, 25, true, false, ok, err);
    }

    public void claimWeekly(@NonNull String uid, long coins, @NonNull OnSuccessListener<Void> ok,
                            @NonNull OnFailureListener err) {
        // Reinicia ciclo semanal al reclamar
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);

            Long saldoL = s.getLong("usu_saldo");
            long saldo = (saldoL == null) ? 0L : saldoL;
            long nuevoSaldo = saldo + coins;

            Long xpL = s.getLong("usu_stats.xp");
            long xp = (xpL == null) ? 0L : xpL;
            long nuevoXp = Math.max(0, xp + 90); // XP semanal
            Long lvlL = s.getLong("usu_nivel");
            int nivel = (lvlL == null) ? 1 : lvlL.intValue();
            String dif = (s.getString("usu_difi") == null) ? "facil" : s.getString("usu_difi");

            int nuevoNivel = nivel;
            boolean leveleo = false;
            while (nuevoXp >= XP_PER_LEVEL) { nuevoXp -= XP_PER_LEVEL; nuevoNivel++; leveleo = true; }

            Map<String,Object> up = new HashMap<>();
            up.put("usu_saldo", nuevoSaldo);
            up.put("usu_stats.xp", nuevoXp);
            up.put("usu_nivel", nuevoNivel);
            up.put("usu_stats.metas_semana_total", FieldValue.increment(1));
            up.put("usu_stats.km_semana", 0.0);
            up.put("usu_stats.week_started_at", System.currentTimeMillis());
            if (leveleo) {
                up.put("usu_stats.meta_diaria_pasos",  dailyFor(nuevoNivel, dif));
                up.put("usu_stats.meta_semanal_pasos", weeklyFor(nuevoNivel, dif));
            }
            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ===== Helpers =====
    private void ensureMissingLong(DocumentSnapshot s, Map<String, Object> up,
                                   String dotted, long def) {
        Object v = s.get(dotted);
        if (!(v instanceof Number)) up.put(dotted, def);
    }
    private void ensureMissingDouble(DocumentSnapshot s, Map<String, Object> up,
                                     String dotted, double def) {
        Object v = s.get(dotted);
        if (!(v instanceof Number)) up.put(dotted, def);
    }

    private long stepsFromProg(Map<String,Object> progMap, String pid) {
        if (progMap == null) return 0L;
        Object val = progMap.get(pid);
        if (val instanceof Map) {
            Object stObj = ((Map<?,?>) val).get("steps");
            if (stObj instanceof Number) {
                long st = ((Number) stObj).longValue();
                return Math.max(0L, st);
            }
        }
        return 0L;
    }

    // ---------- Inventario / Cosméticos ----------
    public void addStarterPackIfMissing(@NonNull String uid,
                                        @NonNull OnSuccessListener<Void> ok,
                                        @NonNull OnFailureListener err) {
        CollectionReference myCos = userDoc(uid).collection("my_cosmetics");
        myCos.get().addOnSuccessListener(qs -> {
            HashSet<String> have = new HashSet<>();
            for (DocumentSnapshot d : qs.getDocuments()) have.add(d.getId());

            HashSet<String> missing = new HashSet<>();
            for (String id : STARTER_IDS) if (!have.contains(id)) missing.add(id);

            // IMPORTANTE: si no falta nada, NO volvemos a equipar la skin por defecto.
            if (missing.isEmpty()) {
                ok.onSuccess(null);
                return;
            }

            List<Task<DocumentSnapshot>> fetches = new ArrayList<>();
            for (String id : missing) fetches.add(cosmeticDoc(id).get());

            Tasks.whenAllSuccess(fetches).addOnSuccessListener(results -> {
                WriteBatch batch = db.batch();
                for (Object o : results) {
                    DocumentSnapshot cos = (DocumentSnapshot) o;
                    String cosId = cos.getId();

                    Map<String, Object> cache = new HashMap<>();
                    cache.put("cos_asset", cos.getString("cos_asset"));
                    cache.put("cos_assetType", cos.getString("cos_assetType"));
                    cache.put("cos_nombre", cos.getString("cos_nombre"));
                    cache.put("cos_tipo", cos.getString("cos_tipo"));

                    Map<String, Object> sub = new HashMap<>();
                    sub.put("myc_cache", cache);
                    sub.put("myc_obtenido", FieldValue.serverTimestamp());
                    sub.put("myc_equipped", DEFAULT_SKIN_ID.equals(cosId));

                    batch.set(inventoryDoc(uid, cosId), sub);
                }

                // al crear el starter pack por primera vez sí equipamos la skin default
                batch.update(userDoc(uid), "usu_equipped.usu_piel", DEFAULT_SKIN_ID);

                if (!missing.contains(DEFAULT_SKIN_ID)) {
                    Map<String, Object> extra = new HashMap<>();
                    extra.put("myc_equipped", true);
                    batch.set(inventoryDoc(uid, DEFAULT_SKIN_ID), extra, SetOptions.merge());
                }

                batch.commit().addOnSuccessListener(ok).addOnFailureListener(err);
            }).addOnFailureListener(err);

        }).addOnFailureListener(err);
    }

    public void addToInventory(@NonNull String uid, @NonNull String cosId, boolean usando,
                               @NonNull OnSuccessListener<Void> ok, @NonNull OnFailureListener err) {
        Map<String, Object> data = new HashMap<>();
        data.put("myc_equipped", usando);
        data.put("myc_obtenido", FieldValue.serverTimestamp());

        cosmeticDoc(cosId).get().addOnSuccessListener(s -> {
            Map<String, Object> cache = new HashMap<>();
            cache.put("cos_asset", s.getString("cos_asset"));
            cache.put("cos_assetType", s.getString("cos_assetType"));
            cache.put("cos_nombre", s.getString("cos_nombre"));
            cache.put("cos_tipo", s.getString("cos_tipo"));
            data.put("myc_cache", cache);

            inventoryDoc(uid, cosId).set(data).addOnSuccessListener(ok).addOnFailureListener(err);
        }).addOnFailureListener(err);
    }

    public void equip(@NonNull String uid, @NonNull String cosId, @NonNull String tipo,
                      @NonNull OnSuccessListener<Void> ok, @NonNull OnFailureListener err) {
        Map<String, Object> m = new HashMap<>();
        switch (tipo.toLowerCase(Locale.ROOT)) {
            case "cabeza":
                m.put("usu_equipped.usu_cabeza", cosId);
                break;
            case "remera":
                m.put("usu_equipped.usu_remera", cosId);
                break;
            case "pantalon":
                m.put("usu_equipped.usu_pantalon", cosId);
                break;
            case "zapatillas":
                m.put("usu_equipped.usu_zapas", cosId);
                break;
            case "piel":
                m.put("usu_equipped.usu_piel", cosId);
                break;
        }
        userDoc(uid).update(m).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public ListenerRegistration listenUser(@NonNull String uid,
                                           @NonNull EventListener<DocumentSnapshot> listener) {
        return userDoc(uid).addSnapshotListener(listener);
    }

    // ---- Constantes de cofres ----
    public static final int CHEST_T1 = 1;
    public static final int CHEST_T2 = 2;
    public static final int CHEST_T3 = 3;

    @IntDef({CHEST_T1, CHEST_T2, CHEST_T3})
    public @interface ChestTier {}

    public Task<List<DocumentSnapshot>> fetchShopPool() {
        return db.collection("cosmetics")
                .whereEqualTo("cos_tienda", true)
                .whereEqualTo("cos_activo", true)
                .get().continueWith(t -> t.getResult().getDocuments());
    }

    public Task<List<DocumentSnapshot>> fetchEventCosmetics() {
        return db.collection("cosmetics")
                .whereEqualTo("cos_evento", true)
                .whereEqualTo("cos_activo", true)
                .get().continueWith(t -> t.getResult().getDocuments());
    }

    public Task<List<String>> fetchAllInventoryIds(@NonNull String uid) {
        return userDoc(uid).collection("my_cosmetics").get()
                .continueWith(t -> {
                    List<String> out = new ArrayList<>();
                    for (DocumentSnapshot d : t.getResult().getDocuments()) out.add(d.getId());
                    return out;
                });
    }

    /** Compra transaccional garantizando alta en my_cosmetics. */
    public void buyCosmetic(@NonNull String uid, @NonNull String cosId, long price,
                            @NonNull OnSuccessListener<Void> ok, @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot u = tr.get(userDoc(uid));

            Boolean susp = u.getBoolean("usu_suspendido");
            if (susp != null && susp) throw new IllegalStateException("Usuario suspendido.");

            Long saldoL = u.getLong("usu_saldo");
            long saldo = (saldoL == null) ? 0L : saldoL;
            if (saldo < price) throw new IllegalStateException("Saldo insuficiente.");

            DocumentSnapshot inv = tr.get(inventoryDoc(uid, cosId));
            if (inv.exists()) throw new IllegalStateException("Ya lo tenés.");

            DocumentSnapshot cos = tr.get(cosmeticDoc(cosId));
            if (!Boolean.TRUE.equals(cos.getBoolean("cos_activo")))
                throw new IllegalStateException("No disponible.");

            tr.update(userDoc(uid), "usu_saldo", saldo - price,
                    "usu_stats.objetos_comprados", FieldValue.increment(1));

            Map<String,Object> cache = new HashMap<>();
            cache.put("cos_asset", cos.getString("cos_asset"));
            cache.put("cos_assetType", cos.getString("cos_assetType"));
            cache.put("cos_nombre", cos.getString("cos_nombre"));
            cache.put("cos_tipo", cos.getString("cos_tipo"));

            Map<String,Object> sub = new HashMap<>();
            sub.put("myc_cache", cache);
            sub.put("myc_obtenido", FieldValue.serverTimestamp());
            sub.put("myc_equipped", false);
            tr.set(inventoryDoc(uid, cosId), sub);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ---- Cofres ----
    public static class ChestResult {
        public final boolean duplicated;
        public final String cosmeticId;
        public final String cosmeticName;
        public final long refundGranted;
        ChestResult(boolean duplicated, String cosmeticId, String cosmeticName, long refundGranted) {
            this.duplicated = duplicated;
            this.cosmeticId = cosmeticId;
            this.cosmeticName = cosmeticName;
            this.refundGranted = refundGranted;
        }
    }

    public void openChest(@NonNull String uid, @ChestTier int tier,
                          @NonNull OnSuccessListener<ChestResult> ok,
                          @NonNull OnFailureListener err) {

        long chestCost, prizePrice, refund;
        if (tier == CHEST_T1) { chestCost = 10_000; prizePrice = 20_000; refund = 5_000; }
        else if (tier == CHEST_T2) { chestCost = 25_000; prizePrice = 50_000; refund = 12_000; }
        else { chestCost = 50_000; prizePrice = 100_000; refund = 25_000; }

        db.collection("cosmetics")
                .whereEqualTo("cos_precio", prizePrice)
                .whereEqualTo("cos_activo", true)
                .get()
                .addOnSuccessListener(qs -> {
                    List<DocumentSnapshot> all = qs.getDocuments();
                    if (all.isEmpty()) {
                        err.onFailure(new IllegalStateException("Sin premios disponibles."));
                        return;
                    }

                    DocumentSnapshot chosen = all.get(new Random().nextInt(all.size()));
                    String cosId = chosen.getId();
                    String cosName = chosen.getString("cos_nombre");

                    db.runTransaction((Transaction.Function<ChestResult>) tr -> {
                        DocumentSnapshot u = tr.get(userDoc(uid));

                        Boolean susp = u.getBoolean("usu_suspendido");
                        if (susp != null && susp) throw new IllegalStateException("Usuario suspendido.");

                        Long saldoL = u.getLong("usu_saldo");
                        long saldo = (saldoL == null) ? 0L : saldoL;
                        if (saldo < chestCost) throw new IllegalStateException("Saldo insuficiente.");

                        DocumentSnapshot inv = tr.get(inventoryDoc(uid, cosId));
                        if (inv.exists()) {
                            long nuevo = (saldo - chestCost) + refund;
                            if (nuevo < 0) nuevo = 0;
                            tr.update(userDoc(uid), "usu_saldo", nuevo);
                            return new ChestResult(true, cosId, cosName == null ? "-" : cosName, refund);
                        } else {
                            tr.update(userDoc(uid),
                                    "usu_saldo", saldo - chestCost,
                                    "usu_stats.objetos_comprados", FieldValue.increment(1));

                            Map<String,Object> cache = new HashMap<>();
                            cache.put("cos_asset", chosen.getString("cos_asset"));
                            cache.put("cos_assetType", chosen.getString("cos_assetType"));
                            cache.put("cos_nombre", cosName);
                            cache.put("cos_tipo", chosen.getString("cos_tipo"));

                            Map<String,Object> sub = new HashMap<>();
                            sub.put("myc_cache", cache);
                            sub.put("myc_obtenido", FieldValue.serverTimestamp());
                            sub.put("myc_equipped", false);
                            tr.set(inventoryDoc(uid, cosId), sub);

                            return new ChestResult(false, cosId, cosName == null ? "-" : cosName, 0L);
                        }
                    }).addOnSuccessListener(ok).addOnFailureListener(err);
                })
                .addOnFailureListener(err);
    }

    // ========= CONTADOR DE VERSUS / ROOMS ACTIVOS =========
    public Task<Integer> countActiveVsAndRooms(@NonNull String uid) {
        Task<QuerySnapshot> tVs = versusCol()
                .whereArrayContains("ver_players", uid)
                .whereEqualTo("ver_finished", false)
                .get();

        Task<QuerySnapshot> tRooms = roomsCol()
                .whereEqualTo("roo_user", uid)
                .whereEqualTo("roo_finished", false)
                .get();

        return Tasks.whenAllSuccess(tVs, tRooms)
                .continueWith(task -> {
                    List<?> res = task.getResult();
                    QuerySnapshot vsQs = (QuerySnapshot) res.get(0);
                    QuerySnapshot rmQs = (QuerySnapshot) res.get(1);
                    return vsQs.size() + rmQs.size();
                });
    }

    // =================== ROOMS (VS) ===================
    public void createRoomFromOptions(@NonNull String ownerUid,
                                      boolean isPublic,
                                      boolean isRace,
                                      @Nullable String code,
                                      @NonNull OnSuccessListener<Void> ok,
                                      @NonNull OnFailureListener err) {

        countActiveVsAndRooms(ownerUid)
                .addOnSuccessListener(count -> {
                    if (count >= MAX_ACTIVE_VERSUS) {
                        err.onFailure(new IllegalStateException("Ya tenés el máximo de versus activos."));
                        return;
                    }

                    int[] raceTargets   = {30000, 40000, 50000};
                    int[] marathonDays  = {3, 4, 5};
                    Random r = new Random();

                    long targetSteps = isRace ? raceTargets[r.nextInt(raceTargets.length)] : 0;
                    long days        = isRace ? 0 : marathonDays[r.nextInt(marathonDays.length)];

                    Map<String,Object> data = new HashMap<>();
                    data.put("roo_user", ownerUid);
                    data.put("roo_public", isPublic);
                    data.put("roo_code", isPublic ? "" :
                            (code == null ? "" : code.toUpperCase(Locale.US)));
                    data.put("roo_createdAt", FieldValue.serverTimestamp());
                    data.put("roo_type", isRace);           // true=carrera, false=maratón
                    data.put("roo_targetSteps", targetSteps);
                    data.put("roo_days", days);

                    List<String> players = new ArrayList<>();
                    players.add(ownerUid);                  // el creador también está en la sala
                    data.put("roo_players", players);

                    data.put("roo_finished", false);

                    roomsCol().add(data)
                            .addOnSuccessListener(doc -> ok.onSuccess(null))
                            .addOnFailureListener(err);
                })
                .addOnFailureListener(err);
    }

    /**
     * Elimina una sala SOLO si el uid coincide con el creador.
     */
    public void deleteRoomIfOwner(@NonNull String roomId,
                                  @NonNull String uid,
                                  @NonNull OnSuccessListener<Void> ok,
                                  @NonNull OnFailureListener err) {

        DocumentReference roomRef = roomsCol().document(roomId);

        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot room = tr.get(roomRef);
            if (!room.exists()) {
                throw new IllegalStateException("La sala ya no existe.");
            }

            String owner = room.getString("roo_user");
            if (owner == null || !owner.equals(uid)) {
                throw new IllegalStateException("Solo el creador puede borrar la sala.");
            }

            tr.delete(roomRef);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    /**
     * Unirse a una sala (con o sin código).
     *
     * @param roomId    id del documento en "rooms"
     * @param joinerUid uid del que se une
     * @param codeInput código que escribió el usuario (null para públicas)
     */
    public void joinRoomAndStartMatch(@NonNull String roomId,
                                      @NonNull String joinerUid,
                                      @Nullable String codeInput,
                                      @NonNull OnSuccessListener<String> ok,
                                      @NonNull OnFailureListener err) {

        countActiveVsAndRooms(joinerUid)
                .addOnSuccessListener(count -> {
                    if (count >= MAX_ACTIVE_VERSUS) {
                        err.onFailure(new IllegalStateException("Ya tenés el máximo de versus activos."));
                        return;
                    }
                    doJoinRoomAndStartMatch(roomId, joinerUid, codeInput, ok, err);
                })
                .addOnFailureListener(err);
    }

    private void doJoinRoomAndStartMatch(@NonNull String roomId,
                                         @NonNull String joinerUid,
                                         @Nullable String codeInput,
                                         @NonNull OnSuccessListener<String> ok,
                                         @NonNull OnFailureListener err) {

        DocumentReference roomRef = roomsCol().document(roomId);

        db.runTransaction((Transaction.Function<String>) tr -> {
            DocumentSnapshot room = tr.get(roomRef);
            if (!room.exists()) {
                throw new IllegalStateException("La sala ya no existe.");
            }

            Boolean finished = room.getBoolean("roo_finished");
            if (finished != null && finished) {
                throw new IllegalStateException("La sala está cerrada.");
            }

            Boolean isPublic = room.getBoolean("roo_public");
            if (isPublic == null) isPublic = true;

            String storedCode = room.getString("roo_code");
            String normalizedStored = storedCode == null ? "" :
                    storedCode.trim().toUpperCase(Locale.US);
            String normalizedInput = codeInput == null ? "" :
                    codeInput.trim().toUpperCase(Locale.US);

            // si es privada y hay código, debe coincidir
            if (!isPublic && !normalizedStored.isEmpty()
                    && !normalizedStored.equals(normalizedInput)) {
                throw new IllegalStateException("Código incorrecto.");
            }

            // jugadores en la sala (normalmente solo el owner)
            List<String> players = new ArrayList<>();
            Object rawPlayers = room.get("roo_players");
            if (rawPlayers instanceof List) {
                for (Object o : (List<?>) rawPlayers) {
                    if (o instanceof String) players.add((String) o);
                }
            }

            if (players.contains(joinerUid)) {
                throw new IllegalStateException("Ya estás en esta sala.");
            }
            if (players.size() >= 2) {
                throw new IllegalStateException("La sala ya tiene dos jugadores.");
            }

            String ownerUid = room.getString("roo_user");
            if (ownerUid == null || ownerUid.isEmpty()) {
                throw new IllegalStateException("Sala inválida.");
            }

            // construimos los datos del versus
            List<String> vsPlayers = new ArrayList<>(players);
            vsPlayers.add(joinerUid);

            Map<String,Object> vsData = new HashMap<>();
            vsData.put("ver_owner", ownerUid);
            vsData.put("ver_players", vsPlayers);
            Boolean typeB = room.getBoolean("roo_type");
            vsData.put("ver_type", typeB != null && typeB);
            vsData.put("ver_targetSteps", room.get("roo_targetSteps"));
            vsData.put("ver_days", room.get("roo_days"));
            vsData.put("ver_createdAt", FieldValue.serverTimestamp());
            vsData.put("ver_finished", false);

            Map<String,Object> progress = new HashMap<>();
            for (String pUid : vsPlayers) {
                Map<String,Object> p = new HashMap<>();
                p.put("steps", 0L);
                p.put("deviceTotal", 0L);
                p.put("joinedAt", FieldValue.serverTimestamp());
                p.put("lastUpdate", FieldValue.serverTimestamp());
                progress.put(pUid, p);
            }
            vsData.put("ver_progress", progress);

            DocumentReference vsRef = versusCol().document();
            tr.set(vsRef, vsData);

            // borramos la sala de espera
            tr.delete(roomRef);

            return vsRef.getId();
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ---------- PROGRESO DE VERSUS / GANADOR + RECOMPENSAS ----------
    /**
     * stepsToday debe ser el TOTAL de pasos del día actual que devuelve la API
     * (no el delta). Este método calcula el delta de forma segura, lo suma al
     * progreso del versus y, si corresponde, marca ganador y reparte recompensas.
     */
    public void updateVersusSteps(@NonNull String versusId,
                                  @NonNull String uid,
                                  long stepsToday,
                                  @NonNull OnSuccessListener<Void> ok,
                                  @NonNull OnFailureListener err) {

        final long stepsTotal = Math.max(0L, stepsToday);
        final DocumentReference vsRef = versusDoc(versusId);

        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot vs = tr.get(vsRef);

            if (!vs.exists()) {
                throw new IllegalStateException("La partida ya no existe.");
            }

            Boolean finishedB = vs.getBoolean("ver_finished");
            if (finishedB != null && finishedB) {
                // ya finalizada, no tocar nada
                return null;
            }

            Object progRaw = vs.get("ver_progress");
            if (!(progRaw instanceof Map)) {
                throw new IllegalStateException("Datos de progreso inválidos.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> progMap = new HashMap<>((Map<String, Object>) progRaw);

            Object userProgRaw = progMap.get(uid);
            if (!(userProgRaw instanceof Map)) {
                throw new IllegalStateException("No estás en esta partida.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> userProg = new HashMap<>((Map<String, Object>) userProgRaw);

            // pasos acumulados actualmente en el versus para este jugador
            long storedSteps = stepsFromProg(progMap, uid);

            // último total de pasos de dispositivo que guardamos
            Long lastDeviceTotal = null;
            Object devObj = userProg.get("deviceTotal");
            if (devObj instanceof Number) {
                lastDeviceTotal = ((Number) devObj).longValue();
            }

            long delta;
            if (lastDeviceTotal == null) {
                // primera vez: contamos todo lo del día
                delta = stepsTotal;
            } else if (stepsTotal >= lastDeviceTotal) {
                // contador subió normal
                delta = stepsTotal - lastDeviceTotal;
            } else {
                // contador se reseteó (medianoche/reinicio)
                delta = stepsTotal;
            }

            if (delta < 0L) delta = 0L;

            long newStoredSteps = storedSteps + delta;
            if (newStoredSteps < 0L) newStoredSteps = 0L;

            long now = System.currentTimeMillis();

            Map<String,Object> updates = new HashMap<>();
            updates.put("ver_progress." + uid + ".steps", newStoredSteps);
            updates.put("ver_progress." + uid + ".deviceTotal", stepsTotal);
            updates.put("ver_progress." + uid + ".lastUpdate", now);

            // snapshot de pasos acumulados de todos para usar después (ganador / recompensas)
            Map<String,Long> stepsSnapshot = new HashMap<>();
            for (String pid : progMap.keySet()) {
                if (pid.equals(uid)) {
                    stepsSnapshot.put(pid, newStoredSteps);
                } else {
                    stepsSnapshot.put(pid, stepsFromProg(progMap, pid));
                }
            }

            boolean isRace = Boolean.TRUE.equals(vs.getBoolean("ver_type"));
            Long targetL = vs.getLong("ver_targetSteps");
            long targetSteps = targetL != null ? targetL : 0L;
            Long daysL = vs.getLong("ver_days");
            long days = daysL != null ? daysL : 0L;

            boolean shouldFinish = false;
            String winnerUid = null;
            long winnerSteps = 0L;

            if (isRace && targetSteps > 0 && newStoredSteps >= targetSteps) {
                // Carrera: el que llega primero a la meta gana
                shouldFinish = true;
                winnerUid = uid;
                winnerSteps = newStoredSteps;
            } else if (!isRace && days > 0) {
                // Maratón: verificar si ya terminó el periodo
                Timestamp createdTs = vs.getTimestamp("ver_createdAt");
                if (createdTs != null) {
                    long endMs = createdTs.toDate().getTime()
                            + days * 24L * 60L * 60L * 1000L;
                    if (now >= endMs) {
                        shouldFinish = true;

                        // buscar el jugador con más pasos acumulados
                        for (Map.Entry<String,Long> e : stepsSnapshot.entrySet()) {
                            String pid = e.getKey();
                            Long stL = e.getValue();
                            long st = stL == null ? 0L : stL;
                            if (winnerUid == null || st > winnerSteps) {
                                winnerUid = pid;
                                winnerSteps = st;
                            }
                        }
                    }
                }
            }

            if (shouldFinish) {
                updates.put("ver_finished", true);
                updates.put("ver_finishedAt", now);
                if (winnerUid != null) {
                    updates.put("ver_winnerUid", winnerUid);
                    updates.put("ver_winnerSteps", winnerSteps);
                }

                // aplicamos update al doc de versus
                tr.update(vsRef, updates);

                // calcular recompensas
                List<String> players = new ArrayList<>();
                Object playersRaw = vs.get("ver_players");
                if (playersRaw instanceof List) {
                    for (Object o : (List<?>) playersRaw) {
                        if (o instanceof String) players.add((String) o);
                    }
                }

                if (!players.isEmpty()) {
                    for (String pid : players) {
                        Long stL = stepsSnapshot.get(pid);
                        long st = stL == null ? 0L : stL;
                        if (st < 0) st = 0;

                        long coins;
                        if (winnerUid != null) {
                            if (pid.equals(winnerUid)) {
                                // ganador: doble de sus pasos
                                coins = st * 2L;
                            } else {
                                // perdedor: mitad de sus pasos
                                coins = st / 2L;
                            }
                        } else {
                            // empate (sin ganador): todos reciben mitad de sus pasos
                            coins = st / 2L;
                        }

                        if (coins > 0) {
                            tr.update(userDoc(pid),
                                    "usu_saldo", FieldValue.increment(coins));
                        }

                        // carreras_ganadas +1 solo para el ganador
                        if (winnerUid != null && pid.equals(winnerUid)) {
                            tr.update(userDoc(pid),
                                    "usu_stats.carreras_ganadas", FieldValue.increment(1));
                        }
                    }
                }

            } else {
                // solo actualiza progreso, no termina el match
                tr.update(vsRef, updates);
            }

            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    /** Versión silenciosa para usar desde UI cuando no hace falta manejar callbacks. */
    public void updateVersusStepsQuiet(@NonNull String versusId,
                                       @NonNull String uid,
                                       long stepsToday) {
        updateVersusSteps(versusId, uid, stepsToday, v -> {}, e -> {});
    }
}
