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
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
    public static final long WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    public static final int MAX_ACTIVE_VERSUS = 3;

    private static final long VS_WIN_COINS_BASE = 0L;

    private static final String[] STARTER_IDS = {
            "cos_id_1", "cos_id_2", "cos_id_3", "cos_id_4", "cos_id_5", "cos_id_6", "cos_id_7",
            "cos_id_13", "cos_id_14", "cos_id_15"
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

    // rooms / versus / rankings
    private CollectionReference roomsCol() {
        return db.collection("rooms");
    }

    private CollectionReference versusCol() {
        return db.collection("versus");
    }

    private CollectionReference rankingsCol() {
        return db.collection("rankings");
    }

    private DocumentReference versusDoc(@NonNull String id) {
        return versusCol().document(id);
    }

    private long currentWeekKey() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        long mondayStart = cal.getTimeInMillis();
        return mondayStart / WEEK_MS;
    }

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
    public FirebaseUser currentUser() {
        return auth.getCurrentUser();
    }

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
        stats.put("km_total", 0.0);
        stats.put("xp", 0L);
        stats.put("objetos_comprados", 0L);
        stats.put("meta_diaria_pasos", 1000L);
        stats.put("meta_semanal_pasos", 10000L);
        stats.put("carreras_ganadas", 0L);
        stats.put("eventos_participados", 0L);
        stats.put("mejor_posicion", 0L);
        stats.put("mayor_pasos_dia", 0L);
        stats.put("metas_diarias_total", 0L);
        stats.put("metas_semana_total", 0L);
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

    public void ensureStats(@NonNull String uid) {
        DocumentReference ref = userDoc(uid);
        ref.get().addOnSuccessListener(snap -> {
            Map<String, Object> up = new HashMap<>();
            // limpiar legados
            up.put("usu_stats.km_hoy", FieldValue.delete());
            up.put("usu_stats.metas_diarias_cumplidas", FieldValue.delete());
            up.put("usu_stats.metas_semanales_cumplidas", FieldValue.delete());
            // asegurar actuales
            ensureMissingDouble(snap, up, "usu_stats.km_semana", 0.0d);
            ensureMissingDouble(snap, up, "usu_stats.km_total", 0.0d);
            ensureMissingLong(snap, up, "usu_stats.xp", 0L);
            ensureMissingLong(snap, up, "usu_stats.objetos_comprados", 0L);
            ensureMissingLong(snap, up, "usu_stats.meta_diaria_pasos", 1000L);
            ensureMissingLong(snap, up, "usu_stats.meta_semanal_pasos", 10000L);
            ensureMissingLong(snap, up, "usu_stats.carreras_ganadas", 0L);
            ensureMissingLong(snap, up, "usu_stats.eventos_participados", 0L);
            ensureMissingLong(snap, up, "usu_stats.mejor_posicion", 0L);
            ensureMissingLong(snap, up, "usu_stats.mayor_pasos_dia", 0L);
            ensureMissingLong(snap, up, "usu_stats.metas_diarias_total", 0L);
            ensureMissingLong(snap, up, "usu_stats.metas_semana_total", 0L);
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

    private String todayCode() {
        return new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                .format(new java.util.Date());
    }

    public void addKmDelta(@NonNull String uid, double kmDelta,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        if (kmDelta <= 0) {
            ok.onSuccess(null);
            return;
        }
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
            up.put("usu_stats.km_total", nuevoTot);
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
        if (d.contains("medio") || d.contains("normal")) return 0.2;
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
            long diaria = dailyFor(nivel, dificultad);
            long semanal = weeklyFor(nivel, dificultad);
            Map<String, Object> m = new HashMap<>();
            m.put("usu_difi", dificultad.toLowerCase(Locale.ROOT));
            m.put("usu_stats.meta_diaria_pasos", diaria);
            m.put("usu_stats.meta_semanal_pasos", semanal);
            tr.update(ref, m);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
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
            long diaria = dailyFor(nivel, nuevaDifi);
            long semanal = weeklyFor(nivel, nuevaDifi);

            Map<String, Object> up = new HashMap<>();
            up.put("usu_difi", nuevaDifi.toLowerCase(Locale.ROOT));
            up.put("usu_stats.meta_diaria_pasos", diaria);
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
            Map<String, Object> up = new HashMap<>();
            up.put("usu_nivel", 1);
            up.put("usu_stats.meta_diaria_pasos", 1000L);
            up.put("usu_stats.meta_semanal_pasos", 10000L);
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
            while (nuevoXp >= XP_PER_LEVEL) {
                nuevoXp -= XP_PER_LEVEL;
                nuevoNivel++;
                leveleo = true;
            }

            Map<String, Object> up = new HashMap<>();
            if (nuevoXp != xp) up.put("usu_stats.xp", nuevoXp);
            if (nuevoNivel != nivel) up.put("usu_nivel", nuevoNivel);
            if (leveleo) {
                up.put("usu_stats.meta_diaria_pasos", dailyFor(nuevoNivel, dif));
                up.put("usu_stats.meta_semanal_pasos", weeklyFor(nuevoNivel, dif));
            }

            if (!up.isEmpty()) tr.update(ref, up);
            return null;
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
            while (nuevoXp >= XP_PER_LEVEL) {
                nuevoXp -= XP_PER_LEVEL;
                nuevoNivel++;
                leveleo = true;
            }

            Map<String, Object> up = new HashMap<>();
            up.put("usu_saldo", nuevoSaldo);
            up.put("usu_nivel", nuevoNivel);
            up.put("usu_stats.xp", nuevoXp);
            if (incDailyCounter) up.put("usu_stats.metas_diarias_total", FieldValue.increment(1));
            if (incWeeklyCounter) up.put("usu_stats.metas_semana_total", FieldValue.increment(1));

            if (leveleo) {
                up.put("usu_stats.meta_diaria_pasos", dailyFor(nuevoNivel, dif));
                up.put("usu_stats.meta_semanal_pasos", weeklyFor(nuevoNivel, dif));
            }

            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void claimDaily(@NonNull String uid, long coins, @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        grantReward(uid, coins, 25, true, false, ok, err);
    }

    public void claimWeekly(@NonNull String uid, long coins, @NonNull OnSuccessListener<Void> ok,
                            @NonNull OnFailureListener err) {
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
            while (nuevoXp >= XP_PER_LEVEL) {
                nuevoXp -= XP_PER_LEVEL;
                nuevoNivel++;
                leveleo = true;
            }

            Map<String, Object> up = new HashMap<>();
            up.put("usu_saldo", nuevoSaldo);
            up.put("usu_stats.xp", nuevoXp);
            up.put("usu_nivel", nuevoNivel);
            up.put("usu_stats.metas_semana_total", FieldValue.increment(1));
            up.put("usu_stats.km_semana", 0.0);
            up.put("usu_stats.week_started_at", System.currentTimeMillis());
            if (leveleo) {
                up.put("usu_stats.meta_diaria_pasos", dailyFor(nuevoNivel, dif));
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

    private long stepsFromProg(Map<String, Object> progMap, String pid) {
        if (progMap == null) return 0L;
        Object val = progMap.get(pid);
        if (val instanceof Map) {
            Object stObj = ((Map<?, ?>) val).get("steps");
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

    public ListenerRegistration listenUser(@NonNull String uid,
                                           @NonNull EventListener<DocumentSnapshot> listener) {
        return userDoc(uid).addSnapshotListener(listener);
    }

    // ---- Constantes de cofres ----
    public static final int CHEST_T1 = 1;
    public static final int CHEST_T2 = 2;
    public static final int CHEST_T3 = 3;

    @IntDef({CHEST_T1, CHEST_T2, CHEST_T3})
    public @interface ChestTier {
    }

    /**
     * Compra transaccional garantizando alta en my_cosmetics.
     */
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

            Map<String, Object> cache = new HashMap<>();
            cache.put("cos_asset", cos.getString("cos_asset"));
            cache.put("cos_assetType", cos.getString("cos_assetType"));
            cache.put("cos_nombre", cos.getString("cos_nombre"));
            cache.put("cos_tipo", cos.getString("cos_tipo"));

            Map<String, Object> sub = new HashMap<>();
            sub.put("myc_cache", cache);
            sub.put("myc_obtenido", FieldValue.serverTimestamp());
            sub.put("myc_equipped", false);
            tr.set(inventoryDoc(uid, cosId), sub);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
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

                    int[] raceTargets = {10000, 20000, 30000};
                    int[] marathonDays = {3, 4, 5};
                    Random r = new Random();

                    long targetSteps = isRace ? raceTargets[r.nextInt(raceTargets.length)] : 0;
                    long days = isRace ? 0 : marathonDays[r.nextInt(marathonDays.length)];

                    Map<String, Object> data = new HashMap<>();
                    data.put("roo_user", ownerUid);
                    data.put("roo_public", isPublic);
                    data.put("roo_code", isPublic ? "" :
                            (code == null ? "" : code.toUpperCase(Locale.US)));
                    data.put("roo_createdAt", FieldValue.serverTimestamp());
                    data.put("roo_type", isRace);
                    data.put("roo_targetSteps", targetSteps);
                    data.put("roo_days", days);

                    List<String> players = new ArrayList<>();
                    players.add(ownerUid);
                    data.put("roo_players", players);

                    data.put("roo_finished", false);

                    roomsCol().add(data)
                            .addOnSuccessListener(doc -> ok.onSuccess(null))
                            .addOnFailureListener(err);
                })
                .addOnFailureListener(err);
    }

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

            if (!isPublic && !normalizedStored.isEmpty()
                    && !normalizedStored.equals(normalizedInput)) {
                throw new IllegalStateException("Código incorrecto.");
            }

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

            List<String> vsPlayers = new ArrayList<>(players);
            vsPlayers.add(joinerUid);

            Map<String, Object> vsData = new HashMap<>();
            vsData.put("ver_owner", ownerUid);
            vsData.put("ver_players", vsPlayers);
            Boolean typeB = room.getBoolean("roo_type");
            vsData.put("ver_type", typeB != null && typeB);
            vsData.put("ver_targetSteps", room.get("roo_targetSteps"));
            vsData.put("ver_days", room.get("roo_days"));
            vsData.put("ver_createdAt", FieldValue.serverTimestamp());
            vsData.put("ver_finished", false);
            String todayCode = todayCode();

            Map<String, Object> progress = new HashMap<>();
            for (String pUid : vsPlayers) {
                Map<String, Object> p = new HashMap<>();
                p.put("steps", 0L);
                p.put("deviceTotal", 0L);
                p.put("joinedAt", FieldValue.serverTimestamp());
                p.put("lastUpdate", FieldValue.serverTimestamp());
                p.put("dayCode", todayCode);
                progress.put(pUid, p);
            }
            vsData.put("ver_progress", progress);

            DocumentReference vsRef = versusCol().document();
            tr.set(vsRef, vsData);

            tr.delete(roomRef);

            return vsRef.getId();
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ---------- PROGRESO DE VERSUS / GANADOR + RECOMPENSAS ----------

    void updateVersusSteps(@NonNull String versusId,
                           @NonNull String uid,
                           long stepsToday,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener er) {

        final DocumentReference vsRef = versusDoc(versusId);
        final String todayCode = todayCode();

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot snap = transaction.get(vsRef);
                    if (!snap.exists()) return null;

                    // ¿es un versus de evento cooperativo?
                    Boolean isEventB = snap.getBoolean("ver_isEvent");
                    boolean isEvent = isEventB != null && isEventB;

                    // Leer progreso previo de este jugador
                    Object raw = snap.get("ver_progress." + uid);
                    long prevDevice = 0L;
                    long prevSteps = 0L;
                    String prevDay = null;

                    if (raw instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) raw;
                        Object dv = m.get("deviceTotal");
                        Object st = m.get("steps");
                        Object dy = m.get("dayCode");

                        if (dv instanceof Number) prevDevice = ((Number) dv).longValue();
                        if (st instanceof Number) prevSteps = ((Number) st).longValue();
                        if (dy instanceof String) prevDay = (String) dy;
                    }

                    long inc;
                    if (prevDay == null || !todayCode.equals(prevDay)) {
                        inc = stepsToday;
                    } else {
                        inc = stepsToday - prevDevice;
                    }
                    if (inc < 0L) inc = 0L;

                    long newSteps = prevSteps + inc;
                    long now = System.currentTimeMillis();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("ver_progress." + uid + ".steps", newSteps);
                    updates.put("ver_progress." + uid + ".deviceTotal", stepsToday);
                    updates.put("ver_progress." + uid + ".lastUpdate", now);
                    updates.put("ver_progress." + uid + ".dayCode", todayCode);

                    Boolean finishedFlag = snap.getBoolean("ver_finished");
                    boolean alreadyFinished = finishedFlag != null && finishedFlag;
                    if (alreadyFinished) {
                        transaction.update(vsRef, updates);
                        return null;
                    }

                    List<String> players = new ArrayList<>();
                    Object rawPlayers = snap.get("ver_players");
                    if (rawPlayers instanceof List) {
                        for (Object o : (List<?>) rawPlayers) {
                            if (o instanceof String) players.add((String) o);
                        }
                    }

                    Map<String, Object> progMap = null;
                    Object progObj = snap.get("ver_progress");
                    if (progObj instanceof Map) {
                        //noinspection unchecked
                        progMap = (Map<String, Object>) progObj;
                    }

                    if (isEvent) {
                        long targetSteps = 0L;
                        Object tObj = snap.get("ver_targetSteps");
                        if (tObj instanceof Number) targetSteps = ((Number) tObj).longValue();

                        long totalGroup = 0L;
                        if (progMap != null) {
                            for (Map.Entry<String, Object> e : progMap.entrySet()) {
                                String pid = e.getKey();
                                long sVal;
                                if (pid.equals(uid)) {
                                    sVal = newSteps;
                                } else {
                                    sVal = stepsFromProg(progMap, pid);
                                }
                                totalGroup += sVal;
                            }
                        } else {
                            totalGroup = newSteps;
                        }

                        boolean finishedEvent = targetSteps > 0L && totalGroup >= targetSteps;
                        if (finishedEvent) {
                            updates.put("ver_finished", true);
                            updates.put("ver_finishedAt", now);
                            updates.put("ver_totalSteps", totalGroup);

                            Long rewardCoinsL = snap.getLong("ver_rewardCoins");
                            long rewardCoins = rewardCoinsL == null ? 0L : rewardCoinsL;

                            if (!players.isEmpty() && rewardCoins > 0L) {
                                for (String pid : players) {
                                    Map<String, Object> upUser = new HashMap<>();
                                    upUser.put("usu_saldo", FieldValue.increment(rewardCoins));
                                    upUser.put("usu_stats.eventos_participados", FieldValue.increment(1));
                                    transaction.update(userDoc(pid), upUser);
                                }
                            }

                            // La room puede haber sido borrada al crear el versus cooperativo
                            String roomId = snap.getString("ver_roomId");
                            if (roomId != null && !roomId.isEmpty()) {
                                DocumentReference roomRef = roomsCol().document(roomId);
                                DocumentSnapshot roomSnap = transaction.get(roomRef);
                                if (roomSnap.exists()) {
                                    transaction.update(roomRef, "roo_finished", true);
                                }
                            }
                        }

                        transaction.update(vsRef, updates);
                        return null;
                    }

                    boolean isRace = Boolean.TRUE.equals(snap.getBoolean("ver_type"));

                    long targetSteps = 0L;
                    Object tObj2 = snap.get("ver_targetSteps");
                    if (tObj2 instanceof Number) targetSteps = ((Number) tObj2).longValue();

                    long days = 0L;
                    Object dObj = snap.get("ver_days");
                    if (dObj instanceof Number) days = ((Number) dObj).longValue();

                    long createdAtMs = 0L;
                    Object cObj = snap.get("ver_createdAt");
                    if (cObj instanceof Timestamp) {
                        createdAtMs = ((Timestamp) cObj).toDate().getTime();
                    } else if (cObj instanceof Number) {
                        createdAtMs = ((Number) cObj).longValue();
                    }

                    boolean closeNow = false;
                    String winnerUid = null;
                    String loserUid = null;

                    if (isRace) {
                        if (targetSteps > 0L && newSteps >= targetSteps) {
                            closeNow = true;
                            winnerUid = uid;

                            if (players.size() >= 2) {
                                for (String p : players) {
                                    if (!p.equals(uid)) {
                                        loserUid = p;
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        if (days > 0L && createdAtMs > 0L) {
                            long limitMs = createdAtMs + days * 24L * 60L * 60L * 1000L;
                            if (now >= limitMs) {
                                if (players.size() == 1) {
                                    winnerUid = players.get(0);
                                    closeNow = true;
                                } else if (players.size() >= 2) {
                                    String p0 = players.get(0);
                                    String p1 = players.get(1);

                                    long s0 = p0.equals(uid) ? newSteps : stepsFromProg(progMap, p0);
                                    long s1 = p1.equals(uid) ? newSteps : stepsFromProg(progMap, p1);

                                    if (s0 >= s1) {
                                        winnerUid = p0;
                                        loserUid = p1;
                                    } else {
                                        winnerUid = p1;
                                        loserUid = p0;
                                    }
                                    closeNow = true;
                                }
                            }
                        }
                    }

                    if (closeNow && winnerUid != null) {
                        updates.put("ver_finished", true);
                        updates.put("ver_finishedAt", now);
                        updates.put("ver_winner", winnerUid);

                        long winnerSteps = winnerUid.equals(uid) ? newSteps : stepsFromProg(progMap, winnerUid);
                        long winnerBase;
                        if (isRace && targetSteps > 0L) {
                            winnerBase = Math.max(winnerSteps, targetSteps);
                        } else {
                            winnerBase = winnerSteps;
                        }
                        long winnerCoins = winnerBase * 2L + VS_WIN_COINS_BASE;

                        Map<String, Object> winUp = new HashMap<>();
                        winUp.put("usu_saldo", FieldValue.increment(winnerCoins));
                        winUp.put("usu_stats.carreras_ganadas", FieldValue.increment(1));
                        transaction.update(userDoc(winnerUid), winUp);

                        // Perdedor
                        if (loserUid != null) {
                            long loserSteps = loserUid.equals(uid) ? newSteps : stepsFromProg(progMap, loserUid);
                            long loserCoins = loserSteps / 2L;
                            if (loserCoins > 0L) {
                                transaction.update(userDoc(loserUid),
                                        "usu_saldo", FieldValue.increment(loserCoins));
                            }
                        }
                    }

                    transaction.update(vsRef, updates);
                    return null;
                })
                .addOnSuccessListener(ok)
                .addOnFailureListener(er);
    }

    void updateVersusStepsQuiet(@NonNull String versusId,
                                @NonNull String uid,
                                long stepsToday) {

        updateVersusSteps(
                versusId,
                uid,
                stepsToday,
                v -> {
                },
                e -> {
                }
        );
    }

    // ========= RANKINGS SEMANALES =========

    public static class WeeklyRankingRow {
        public final String uid;
        public final String nombre;
        public final double kmSemana;
        public final long stepsWeek;
        public final int position;
        public final long coins;

        public WeeklyRankingRow(String uid,
                                String nombre,
                                double kmSemana,
                                long stepsWeek,
                                int position,
                                long coins) {
            this.uid = uid;
            this.nombre = nombre;
            this.kmSemana = kmSemana;
            this.stepsWeek = stepsWeek;
            this.position = position;
            this.coins = coins;
        }
    }

    public static class WeeklyRankingResult {
        public final List<WeeklyRankingRow> rows;
        public final boolean rewardsApplied;
        public final long weekKey;

        public WeeklyRankingResult(List<WeeklyRankingRow> rows,
                                   boolean rewardsApplied,
                                   long weekKey) {
            this.rows = rows;
            this.rewardsApplied = rewardsApplied;
            this.weekKey = weekKey;
        }
    }

    public void loadWeeklyRankingForUser(@NonNull String uid,
                                         @NonNull OnSuccessListener<WeeklyRankingResult> ok,
                                         @NonNull OnFailureListener err) {

        long weekKey = currentWeekKey();

        cleanupOldRankingsForUser(uid, weekKey);

        rankingsCol()
                .whereEqualTo("ran_weekKey", weekKey)
                .whereArrayContains("ran_players", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        DocumentSnapshot doc = qs.getDocuments().get(0);
                        buildWeeklyRows(doc, weekKey, ok, err);
                    } else {
                        // Todavía no está en ninguna tabla: lo asignamos a una
                        assignUserToWeeklyRanking(uid, weekKey,
                                doc -> buildWeeklyRows(doc, weekKey, ok, err),
                                err);
                    }
                })
                .addOnFailureListener(err);
    }

    private void cleanupOldRankingsForUser(@NonNull String uid, long currentWeekKey) {
        rankingsCol()
                .whereArrayContains("ran_players", uid)
                .whereLessThan("ran_weekKey", currentWeekKey)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        applyWeeklyRewardsAndDelete(d);
                    }
                });
    }

    private void applyWeeklyRewardsAndDelete(@NonNull DocumentSnapshot rankingDoc) {
        Object rawPlayers = rankingDoc.get("ran_players");
        List<String> players = new ArrayList<>();
        if (rawPlayers instanceof List) {
            for (Object o : (List<?>) rawPlayers) {
                if (o instanceof String) players.add((String) o);
            }
        }
        if (players.isEmpty()) {
            rankingDoc.getReference().delete();
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String pid : players) {
            tasks.add(userDoc(pid).get());
        }

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<WeeklyRankingRow> tmp = new ArrayList<>();

                    for (Object o : results) {
                        DocumentSnapshot s = (DocumentSnapshot) o;
                        String pid = s.getId();

                        Double kmD = null;
                        Object vKm = s.get("usu_stats.km_semana");
                        if (vKm instanceof Number) kmD = ((Number) vKm).doubleValue();
                        double km = kmD == null ? 0.0 : kmD;
                        long stepsWeek = Math.round(km * 1000.0);

                        String nombre = s.getString("usu_nombre");
                        if (nombre == null || nombre.trim().isEmpty()) {
                            String shortId = pid;
                            if (shortId.length() > 6) shortId = shortId.substring(0, 6);
                            nombre = "@" + shortId;
                        }

                        tmp.add(new WeeklyRankingRow(pid, nombre, km, stepsWeek, 0, 0L));
                    }

                    Collections.sort(tmp, (a, b) ->
                            Double.compare(b.kmSemana, a.kmSemana));

                    List<WeeklyRankingRow> rows = new ArrayList<>();
                    for (int i = 0; i < tmp.size(); i++) {
                        WeeklyRankingRow base = tmp.get(i);
                        int pos = i + 1;
                        long coins;
                        if (pos == 1) coins = base.stepsWeek * 3L;
                        else if (pos == 2) coins = base.stepsWeek * 2L;
                        else if (pos == 3) coins = base.stepsWeek;
                        else coins = 0L;

                        rows.add(new WeeklyRankingRow(
                                base.uid,
                                base.nombre,
                                base.kmSemana,
                                base.stepsWeek,
                                pos,
                                coins
                        ));
                    }

                    WriteBatch batch = db.batch();
                    for (WeeklyRankingRow r : rows) {
                        if (r.coins > 0L) {
                            batch.update(userDoc(r.uid),
                                    "usu_saldo", FieldValue.increment(r.coins));
                        }
                    }
                    batch.delete(rankingDoc.getReference());
                    batch.commit();
                });
    }

    public void updateChosenStatsForUser(@NonNull String uid,
                                         @Nullable String slot1Key,
                                         @Nullable String slot2Key) {
        Map<String, Object> up = new HashMap<>();
        if (slot1Key != null && !slot1Key.trim().isEmpty()) {
            up.put("usu_chosen1Key", slot1Key);
        }
        if (slot2Key != null && !slot2Key.trim().isEmpty()) {
            up.put("usu_chosen2Key", slot2Key);
        }
        if (up.isEmpty()) return;
        userDoc(uid).update(up);
    }

    private void assignUserToWeeklyRanking(@NonNull String uid,
                                           long weekKey,
                                           @NonNull OnSuccessListener<DocumentSnapshot> ok,
                                           @NonNull OnFailureListener err) {

        rankingsCol()
                .whereEqualTo("ran_weekKey", weekKey)
                .whereEqualTo("ran_finished", false)
                .get()
                .addOnSuccessListener(qs -> {
                    List<DocumentSnapshot> candidates = new ArrayList<>();
                    HashSet<String> usedPlayers = new HashSet<>();

                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Object raw = d.get("ran_players");
                        if (raw instanceof List) {
                            for (Object o : (List<?>) raw) {
                                if (o instanceof String) {
                                    usedPlayers.add((String) o);
                                }
                            }
                        }

                        List<String> players = new ArrayList<>();
                        Object rawPlayers = d.get("ran_players");
                        if (rawPlayers instanceof List) {
                            for (Object o : (List<?>) rawPlayers) {
                                if (o instanceof String) players.add((String) o);
                            }
                        }
                        if (!players.contains(uid) && players.size() < 5) {
                            candidates.add(d);
                        }
                    }

                    if (!candidates.isEmpty()) {
                        DocumentSnapshot chosen = candidates.get(new Random().nextInt(candidates.size()));
                        DocumentReference ref = chosen.getReference();
                        ref.update("ran_players", FieldValue.arrayUnion(uid))
                                .addOnSuccessListener(v ->
                                        ref.get().addOnSuccessListener(ok).addOnFailureListener(err))
                                .addOnFailureListener(err);
                    } else {
                        usedPlayers.add(uid);

                        db.collection("users").get()
                                .addOnSuccessListener(userQs -> {
                                    List<DocumentSnapshot> users = userQs.getDocuments();
                                    Collections.shuffle(users, new Random());

                                    List<String> players = new ArrayList<>();
                                    players.add(uid);

                                    for (DocumentSnapshot uSnap : users) {
                                        String pid = uSnap.getId();
                                        if (usedPlayers.contains(pid)) continue;
                                        if (pid.equals(uid)) continue;
                                        Boolean susp = uSnap.getBoolean("usu_suspendido");
                                        if (susp != null && susp) continue;

                                        players.add(pid);
                                        usedPlayers.add(pid);
                                        if (players.size() >= 5) break;
                                    }

                                    Map<String, Object> data = new HashMap<>();
                                    data.put("ran_weekKey", weekKey);
                                    data.put("ran_createdAt", System.currentTimeMillis());
                                    data.put("ran_finished", false);
                                    data.put("ran_rewardsApplied", false);
                                    data.put("ran_players", players);

                                    rankingsCol().add(data)
                                            .addOnSuccessListener(ref ->
                                                    ref.get().addOnSuccessListener(ok).addOnFailureListener(err))
                                            .addOnFailureListener(err);
                                })
                                .addOnFailureListener(err);
                    }
                })
                .addOnFailureListener(err);
    }

    private void buildWeeklyRows(@NonNull DocumentSnapshot rankingDoc,
                                 long weekKey,
                                 @NonNull OnSuccessListener<WeeklyRankingResult> ok,
                                 @NonNull OnFailureListener err) {

        Object rawPlayers = rankingDoc.get("ran_players");
        List<String> players = new ArrayList<>();
        if (rawPlayers instanceof List) {
            for (Object o : (List<?>) rawPlayers) {
                if (o instanceof String) players.add((String) o);
            }
        }

        if (players.isEmpty()) {
            ok.onSuccess(new WeeklyRankingResult(
                    new ArrayList<>(),
                    false,
                    weekKey
            ));
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String pid : players) {
            tasks.add(userDoc(pid).get());
        }

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<WeeklyRankingRow> tmp = new ArrayList<>();

                    for (Object o : results) {
                        DocumentSnapshot s = (DocumentSnapshot) o;
                        String pid = s.getId();
                        Double kmD = null;
                        Object vKm = s.get("usu_stats.km_semana");
                        if (vKm instanceof Number) kmD = ((Number) vKm).doubleValue();
                        double km = kmD == null ? 0.0 : kmD;
                        long stepsWeek = Math.round(km * 1000.0);

                        String nombre = s.getString("usu_nombre");
                        if (nombre == null || nombre.trim().isEmpty()) {
                            String shortId = pid;
                            if (shortId.length() > 6) shortId = shortId.substring(0, 6);
                            nombre = "@" + shortId;
                        }

                        tmp.add(new WeeklyRankingRow(pid, nombre, km, stepsWeek, 0, 0L));
                    }

                    Collections.sort(tmp, (a, b) ->
                            Double.compare(b.kmSemana, a.kmSemana));

                    List<WeeklyRankingRow> rows = new ArrayList<>();
                    for (int i = 0; i < tmp.size(); i++) {
                        WeeklyRankingRow base = tmp.get(i);
                        int pos = i + 1;
                        long coins;
                        if (pos == 1) coins = base.stepsWeek * 3L;
                        else if (pos == 2) coins = base.stepsWeek * 2L;
                        else if (pos == 3) coins = base.stepsWeek;
                        else coins = 0L;

                        rows.add(new WeeklyRankingRow(
                                base.uid,
                                base.nombre,
                                base.kmSemana,
                                base.stepsWeek,
                                pos,
                                coins
                        ));
                    }

                    ok.onSuccess(new WeeklyRankingResult(rows, false, weekKey));
                })
                .addOnFailureListener(err);
    }
}
