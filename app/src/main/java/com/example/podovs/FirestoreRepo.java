package com.example.podovs;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class FirestoreRepo {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    private static final int XP_PER_LEVEL = 100;

    // -------- Starter pack --------
    private static final String[] STARTER_IDS = {
            "cos_id_1","cos_id_2","cos_id_3","cos_id_4","cos_id_5","cos_id_6","cos_id_7"
    };
    private static final String DEFAULT_SKIN_ID = "cos_id_2";

    public FirestoreRepo() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    // ---------- Paths ----------
    private DocumentReference userDoc(@NonNull String uid) { return db.collection("users").document(uid); }
    private DocumentReference cosmeticDoc(@NonNull String cosId) { return db.collection("cosmetics").document(cosId); }
    private DocumentReference inventoryDoc(@NonNull String uid, @NonNull String cosId) {
        return userDoc(uid).collection("my_cosmetics").document(cosId);
    }

    // ---------- Auth ----------
    public void signIn(@NonNull String email, @NonNull String password,
                       @NonNull OnSuccessListener<AuthResult> ok,
                       @NonNull OnFailureListener err) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(ok).addOnFailureListener(err);
    }
    public void createUser(@NonNull String email, @NonNull String password,
                           @NonNull OnSuccessListener<AuthResult> ok,
                           @NonNull OnFailureListener err) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(ok).addOnFailureListener(err);
    }
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
        data.put("usu_difi", dif.toLowerCase());

        Map<String, Object> stats = new HashMap<>();
        stats.put("km_semana", 0.0);
        stats.put("km_total",  0.0);
        stats.put("xp", 0L);
        stats.put("objetos_comprados", 0L);
        // placeholders (se recalculan abajo en transacción por dif+nivel)
        stats.put("meta_diaria_pasos",  1000L);
        stats.put("meta_semanal_pasos", 10000L);

        stats.put("carreras_ganadas",      0L);
        stats.put("eventos_participados",  0L);
        stats.put("mejor_posicion",        0L);
        stats.put("mayor_pasos_dia",       0L);
        stats.put("metas_diarias_total",   0L);
        stats.put("metas_semana_total",    0L);

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

    /** Garantiza campos y borra legados. */
    public void ensureStats(@NonNull String uid) {
        DocumentReference ref = userDoc(uid);
        ref.get().addOnSuccessListener(snap -> {
            Map<String, Object> up = new HashMap<>();
            // eliminar legados
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
            if (!up.isEmpty()) ref.update(up);
        });
    }

    // ---------- Ranking ----------
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
            long saldo = snap.getLong("usu_saldo") == null ? 0L : snap.getLong("usu_saldo");
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
        userDoc(uid).update("usu_stats.km_semana", km)
                .addOnSuccessListener(ok).addOnFailureListener(err);
    }
    public void setKmTotal(@NonNull String uid, double km,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        userDoc(uid).update("usu_stats.km_total", km)
                .addOnSuccessListener(ok).addOnFailureListener(err);
    }
    public void addKmDelta(@NonNull String uid, double kmDelta,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        if (kmDelta <= 0) { ok.onSuccess(null); return; }
        DocumentReference ref = userDoc(uid);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot s = tr.get(ref);
            double curSem = (s.getDouble("usu_stats.km_semana") == null) ? 0.0 : s.getDouble("usu_stats.km_semana");
            double curTot = (s.getDouble("usu_stats.km_total")  == null) ? 0.0 : s.getDouble("usu_stats.km_total");
            Map<String, Object> up = new HashMap<>();
            up.put("usu_stats.km_semana", Math.max(0.0, curSem + kmDelta));
            up.put("usu_stats.km_total",  Math.max(0.0, curTot + kmDelta));
            tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }
    public void updateMayorPasosDiaIfGreater(@NonNull String uid, long candidate) {
        DocumentReference ref = userDoc(uid);
        db.runTransaction(tr -> {
            DocumentSnapshot s = tr.get(ref);
            Object v = s.get("usu_stats.mayor_pasos_dia");
            long cur = (v instanceof Number) ? ((Number) v).longValue() : 0L;
            if (candidate > cur) tr.update(ref, "usu_stats.mayor_pasos_dia", candidate);
            return null;
        });
    }

    // ---------- Dificultad / Metas ----------
    private double difMultiplier(@NonNull String dif) {
        String d = dif.toLowerCase();
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

    /** Sincroniza metas usando **nivel actual** + dificultad dada. */
    public void syncGoalsWithDifficulty(@NonNull String uid,
                                        @NonNull String dificultad,
                                        @NonNull OnSuccessListener<Void> ok,
                                        @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);
            int nivel = (s.getLong("usu_nivel") == null) ? 1 : s.getLong("usu_nivel").intValue();
            long diaria  = dailyFor(nivel, dificultad);
            long semanal = weeklyFor(nivel, dificultad);

            Map<String,Object> m = new HashMap<>();
            m.put("usu_difi", dificultad.toLowerCase());
            m.put("usu_stats.meta_diaria_pasos",  diaria);
            m.put("usu_stats.meta_semanal_pasos", semanal);
            tr.update(ref, m);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    /** Cambia dificultad y actualiza metas en un solo paso (nivel-consistente). */
    public void setDifficulty(@NonNull String uid, @NonNull String nuevaDifi,
                              @NonNull OnSuccessListener<Void> ok,
                              @NonNull OnFailureListener err) {
        syncGoalsWithDifficulty(uid, nuevaDifi, ok, err);
    }

    /** Recalcula metas para el usuario actual leyendo su nivel y dificultad guardada. */
    public void forceRecalcGoals(@NonNull String uid,
                                 @NonNull OnSuccessListener<Void> ok,
                                 @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);
            int nivel = (s.getLong("usu_nivel") == null) ? 1 : s.getLong("usu_nivel").intValue();
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

    // ---------- XP / Nivel (100 XP por nivel SIEMPRE) ----------
    public void normalizeXpLevel(@NonNull String uid,
                                 @NonNull OnSuccessListener<Void> ok,
                                 @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);
            long xp = (s.getLong("usu_stats.xp") == null) ? 0L : s.getLong("usu_stats.xp");
            int nivel = (s.getLong("usu_nivel") == null) ? 1 : s.getLong("usu_nivel").intValue();
            String dif = (s.getString("usu_difi") == null) ? "facil" : s.getString("usu_difi");

            long nuevoXp = Math.max(0L, xp);
            int nuevoNivel = Math.max(1, nivel);
            boolean leveleo = false;
            while (nuevoXp >= XP_PER_LEVEL) { nuevoXp -= XP_PER_LEVEL; nuevoNivel++; leveleo = true; }

            Map<String,Object> up = new HashMap<>();
            if (nuevoXp != xp)    up.put("usu_stats.xp", nuevoXp);
            if (nuevoNivel != nivel) up.put("usu_nivel", nuevoNivel);

            // ajuste de metas cuando cambió el nivel
            if (leveleo) {
                up.put("usu_stats.meta_diaria_pasos",  dailyFor(nuevoNivel, dif));
                up.put("usu_stats.meta_semanal_pasos", weeklyFor(nuevoNivel, dif));
            }

            if (!up.isEmpty()) tr.update(ref, up);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    /** Suma XP y si levelea, recalcula metas con fórmula (base × mult × nivel). */
    public void addXpAndMaybeLevelUp(@NonNull String uid, int deltaXp,
                                     @NonNull OnSuccessListener<Integer> ok,
                                     @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Integer>) tr -> {
            DocumentReference ref = userDoc(uid);
            DocumentSnapshot s = tr.get(ref);

            long xp = (s.getLong("usu_stats.xp") == null) ? 0L : s.getLong("usu_stats.xp");
            int nivel = (s.getLong("usu_nivel") == null) ? 1 : s.getLong("usu_nivel").intValue();
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

            long saldo = (s.getLong("usu_saldo") == null) ? 0L : s.getLong("usu_saldo");
            long nuevoSaldo = saldo + coinsDelta;
            if (nuevoSaldo < 0) throw new IllegalStateException("Saldo insuficiente");

            long xp = (s.getLong("usu_stats.xp") == null) ? 0L : s.getLong("usu_stats.xp");
            int nivel = (s.getLong("usu_nivel") == null) ? 1 : s.getLong("usu_nivel").intValue();
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
        grantReward(uid, coins, 90, false, true, ok, err);
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
                WriteBatch batch = db.batch();
                batch.set(
                        inventoryDoc(uid, DEFAULT_SKIN_ID),
                        new HashMap<String, Object>() {{ put("myc_equipped", true); }},
                        SetOptions.merge()
                );
                batch.update(userDoc(uid), "usu_equipped.usu_piel", DEFAULT_SKIN_ID);
                batch.commit().addOnSuccessListener(ok).addOnFailureListener(err);
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

                batch.update(userDoc(uid), "usu_equipped.usu_piel", DEFAULT_SKIN_ID);

                if (!missing.contains(DEFAULT_SKIN_ID)) {
                    batch.set(inventoryDoc(uid, DEFAULT_SKIN_ID),
                            new HashMap<String, Object>() {{ put("myc_equipped", true); }},
                            SetOptions.merge());
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

            inventoryDoc(uid, cosId).set(data)
                    .addOnSuccessListener(ok).addOnFailureListener(err);
        }).addOnFailureListener(err);
    }

    public void equip(@NonNull String uid, @NonNull String cosId, @NonNull String tipo,
                      @NonNull OnSuccessListener<Void> ok, @NonNull OnFailureListener err) {
        Map<String, Object> m = new HashMap<>();
        switch (tipo.toLowerCase()) {
            case "cabeza" -> m.put("usu_equipped.usu_cabeza", cosId);
            case "remera" -> m.put("usu_equipped.usu_remera", cosId);
            case "pantalon" -> m.put("usu_equipped.usu_pantalon", cosId);
            case "zapatillas" -> m.put("usu_equipped.usu_zapas", cosId);
            case "piel" -> m.put("usu_equipped.usu_piel", cosId);
        }
        userDoc(uid).update(m).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public ListenerRegistration listenUser(@NonNull String uid,
                                           @NonNull EventListener<DocumentSnapshot> listener) {
        return userDoc(uid).addSnapshotListener(listener);
    }
}
