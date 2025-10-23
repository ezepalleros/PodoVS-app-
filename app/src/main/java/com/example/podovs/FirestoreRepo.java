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

    // -------- Starter pack --------
    private static final String[] STARTER_IDS = {
            "cos_id_1", // cabeza_bwcap
            "cos_id_2", // piel_startskin  (default equip)
            "cos_id_3", // piel_deepebony
            "cos_id_4", // torso_greenshirt
            "cos_id_5", // pierna_bluejeans
            "cos_id_6"  // pies_comicallylong
    };
    private static final String DEFAULT_SKIN_ID = "cos_id_2"; // equip por defecto

    public FirestoreRepo() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    // ---------- Paths ----------
    private DocumentReference userDoc(@NonNull String uid) {
        return db.collection("users").document(uid);
    }
    private DocumentReference cosmeticDoc(@NonNull String cosId) {
        return db.collection("cosmetics").document(cosId);
    }
    private DocumentReference inventoryDoc(@NonNull String uid, @NonNull String cosId) {
        return userDoc(uid).collection("my_cosmetics").document(cosId);
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

    public FirebaseUser currentUser() { return auth.getCurrentUser(); }

    // ---------- User ----------
    public void getUser(@NonNull String uid,
                        @NonNull OnSuccessListener<DocumentSnapshot> ok,
                        @NonNull OnFailureListener err) {
        userDoc(uid).get().addOnSuccessListener(ok).addOnFailureListener(err);
    }

    /** Crea el perfil con metas por defecto (1000/10000) y esquema de usu_stats sin km_hoy. */
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
        // Distancias
        stats.put("km_semana", 0.0);
        stats.put("km_total",  0.0);
        // Progresión
        stats.put("xp", 0L);
        stats.put("objetos_comprados", 0L);
        // Metas configurables
        stats.put("meta_diaria_pasos",  1000L);
        stats.put("meta_semanal_pasos", 10000L);
        // NUEVOS ACUMULADORES / RÉCORDS
        stats.put("carreras_ganadas",      0L);
        stats.put("eventos_participados",  0L);
        stats.put("mejor_posicion",        0L); // si manejás “mejor puesto” como número (1 es mejor)
        stats.put("mayor_pasos_dia",       0L);
        stats.put("metas_diarias_total",   0L);
        stats.put("metas_semana_total",    0L);

        data.put("usu_stats", stats);

        Map<String, Object> eq = new HashMap<>();
        eq.put("usu_cabeza", null);
        eq.put("usu_remera", null);
        eq.put("usu_pantalon", null);
        eq.put("usu_zapas", null);
        eq.put("usu_piel", null); // se setea luego por starter pack
        data.put("usu_equipped", eq);

        userDoc(uid).set(data)
                .addOnSuccessListener(ok)
                .addOnFailureListener(err);
    }

    /** Garantiza el esquema de usu_stats, agrega faltantes y ELIMINA km_hoy si existiera. */
    public void ensureStats(@NonNull String uid) {
        DocumentReference ref = userDoc(uid);
        ref.get().addOnSuccessListener(snap -> {
            Map<String, Object> up = new HashMap<>();

            // ---- Borrar legados dentro de usu_stats ----
            up.put("usu_stats.km_hoy", FieldValue.delete());                  // legacy
            up.put("usu_stats.metas_diarias_cumplidas", FieldValue.delete()); // legacy
            up.put("usu_stats.metas_semanales_cumplidas", FieldValue.delete());// legacy

            // ---- Asegurar campos actuales ----
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
    /** “Ranking hoy” ahora muestra por récord personal (mayor_pasos_dia) ya que no hay km_hoy. */
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

    // ---------- Stats (sin km_hoy) ----------
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

    /** Actualiza mayor_pasos_dia sólo si el candidato es mayor al actual. */
    public void updateMayorPasosDiaIfGreater(@NonNull String uid, long candidate) {
        DocumentReference ref = userDoc(uid);
        db.runTransaction(tr -> {
            DocumentSnapshot s = tr.get(ref);
            Object v = s.get("usu_stats.mayor_pasos_dia");
            long cur = (v instanceof Number) ? ((Number) v).longValue() : 0L;
            if (candidate > cur) {
                tr.update(ref, "usu_stats.mayor_pasos_dia", candidate); // <-- update (no set)
            }
            return null;
        });
    }

    public void addKmDelta(@NonNull String uid, double kmDelta,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        if (kmDelta <= 0) { ok.onSuccess(null); return; }

        DocumentReference ref = userDoc(uid);
        db.runTransaction((Transaction.Function<Void>) tr -> {   // <-- fuerza TResult = Void
                    DocumentSnapshot s = tr.get(ref);

                    double curSem = 0.0, curTot = 0.0;
                    Object vs = s.get("usu_stats.km_semana");
                    Object vt = s.get("usu_stats.km_total");
                    if (vs instanceof Number) curSem = ((Number) vs).doubleValue();
                    if (vt instanceof Number) curTot = ((Number) vt).doubleValue();

                    double newSem = Math.max(0.0, curSem + kmDelta);
                    double newTot = Math.max(0.0, curTot + kmDelta);

                    Map<String, Object> up = new HashMap<>();
                    up.put("usu_stats.km_semana", newSem);
                    up.put("usu_stats.km_total",  newTot);
                    tr.update(ref, up);
                    return null; // Void
                })
                .addOnSuccessListener(unused -> ok.onSuccess(null)) // <-- adapta Void
                .addOnFailureListener(err);
    }

    // ---------- Metas ----------
    public void recalcularMetas(@NonNull String uid, int nivel, @NonNull String dif,
                                @NonNull OnSuccessListener<Void> ok,
                                @NonNull OnFailureListener err) {
        int baseDiaria = switch (dif.toLowerCase()) {
            case "dificil", "difícil", "alto" -> 12000;
            case "medio", "normal" -> 8000;
            default -> 5000;
        };
        int baseSemanal = baseDiaria * 7;

        long diaria = Math.round(baseDiaria * (1.0 + (nivel * 0.05)));
        long semanal = Math.round(baseSemanal * (1.0 + (nivel * 0.05)));

        Map<String, Object> m = new HashMap<>();
        m.put("usu_nivel", nivel);
        m.put("usu_difi", dif.toLowerCase());
        m.put("usu_stats.meta_diaria_pasos", diaria);
        m.put("usu_stats.meta_semanal_pasos", semanal);

        userDoc(uid).update(m).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ---------- XP / Nivel ----------
    public void addXpAndMaybeLevelUp(@NonNull String uid, int deltaXp,
                                     @NonNull OnSuccessListener<Integer> ok,
                                     @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Integer>) tr -> {
            DocumentSnapshot s = tr.get(userDoc(uid));
            long xp = s.getLong("usu_stats.xp") == null ? 0L : s.getLong("usu_stats.xp");
            int nivel = s.getLong("usu_nivel") == null ? 1 : s.getLong("usu_nivel").intValue();
            long nuevoXp = Math.max(0, xp + deltaXp);
            int nuevoNivel = nivel;
            while (nuevoXp >= (long) (100L * nuevoNivel)) {
                nuevoXp -= 100L * nuevoNivel;
                nuevoNivel++;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("usu_nivel", nuevoNivel);
            m.put("usu_stats.xp", nuevoXp);
            tr.update(userDoc(uid), m);
            return nuevoNivel;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    // ---------- Recompensas / Logros ----------
    public void onDailyGoalReached(@NonNull String uid,
                                   @NonNull OnSuccessListener<Void> ok,
                                   @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("usu_saldo", FieldValue.increment(50));
            m.put("usu_stats.xp", FieldValue.increment(25));
            // nuevo contador total (reemplaza metas_diarias_cumplidas)
            m.put("usu_stats.metas_diarias_total", FieldValue.increment(1));
            tr.update(userDoc(uid), m);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void onWeeklyGoalReached(@NonNull String uid,
                                    @NonNull OnSuccessListener<Void> ok,
                                    @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("usu_saldo", FieldValue.increment(250));
            m.put("usu_stats.xp", FieldValue.increment(100));
            // nuevo contador total (reemplaza metas_semanales_cumplidas)
            m.put("usu_stats.metas_semana_total", FieldValue.increment(1));
            tr.update(userDoc(uid), m);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    /** Conviene exponer helpers para actualizar estos nuevos acumuladores: */
    public void addCarreraGanada(@NonNull String uid,
                                 @NonNull OnSuccessListener<Void> ok,
                                 @NonNull OnFailureListener err) {
        userDoc(uid).update("usu_stats.carreras_ganadas", FieldValue.increment(1))
                .addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void addEventoParticipado(@NonNull String uid,
                                     @NonNull OnSuccessListener<Void> ok,
                                     @NonNull OnFailureListener err) {
        userDoc(uid).update("usu_stats.eventos_participados", FieldValue.increment(1))
                .addOnSuccessListener(ok).addOnFailureListener(err);
    }

    /** Guarda la mejor posición alcanzada si es menor (1 es mejor). */
    public void updateMejorPosicionIfBetter(@NonNull String uid, long posicion) {
        DocumentReference ref = userDoc(uid);
        db.runTransaction(tr -> {
            DocumentSnapshot s = tr.get(ref);
            Object v = s.get("usu_stats.mejor_posicion");
            long cur = (v instanceof Number) ? ((Number) v).longValue() : 0L;
            // si cur==0 significa sin dato, o si posicion < cur mejora
            if (cur == 0L || (posicion > 0 && posicion < cur)) {
                Map<String, Object> m = new HashMap<>();
                m.put("usu_stats.mejor_posicion", posicion);
                tr.set(ref, m, SetOptions.merge());
            }
            return null;
        });
    }

    // ---------- Inventario / Cosméticos ----------
    /** Crea los 6 items del starter pack si faltan y equipa cos_id_2 como piel. */
    public void addStarterPackIfMissing(@NonNull String uid,
                                        @NonNull OnSuccessListener<Void> ok,
                                        @NonNull OnFailureListener err) {

        CollectionReference myCos = userDoc(uid).collection("my_cosmetics");

        // 1) ¿Qué ya tiene?
        myCos.get().addOnSuccessListener(qs -> {
            HashSet<String> have = new HashSet<>();
            for (DocumentSnapshot d : qs.getDocuments()) have.add(d.getId());

            // 2) Calcular faltantes
            HashSet<String> missing = new HashSet<>();
            for (String id : STARTER_IDS) if (!have.contains(id)) missing.add(id);

            // Si no falta ninguno, igual marcamos equip por si quedó desmarcado
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

            // 3) Leer metadata de cosmetics para los faltantes
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
                    sub.put("myc_equipped", DEFAULT_SKIN_ID.equals(cosId)); // solo la piel default en true

                    batch.set(inventoryDoc(uid, cosId), sub);
                }

                // Asegurar equip del usuario a la piel default
                batch.update(userDoc(uid), "usu_equipped.usu_piel", DEFAULT_SKIN_ID);

                // Si cos_id_2 ya existía, asegurar myc_equipped=true
                if (!missing.contains(DEFAULT_SKIN_ID)) {
                    batch.set(
                            inventoryDoc(uid, DEFAULT_SKIN_ID),
                            new HashMap<String, Object>() {{ put("myc_equipped", true); }},
                            SetOptions.merge()
                    );
                }

                batch.commit().addOnSuccessListener(ok).addOnFailureListener(err);
            }).addOnFailureListener(err);

        }).addOnFailureListener(err);
    }

    /** Agrega un cosmético “normal” al inventario del usuario. */
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

    // ---------- Live listener ----------
    public ListenerRegistration listenUser(@NonNull String uid,
                                           @NonNull EventListener<DocumentSnapshot> listener) {
        return userDoc(uid).addSnapshotListener(listener);
    }



    // ---------- Claims / Recompensas rápidas ----------
    public void claimDaily(@NonNull String uid, long coins,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("usu_saldo", FieldValue.increment(coins));
            m.put("usu_stats.xp", FieldValue.increment(25));
            m.put("usu_stats.metas_diarias_total", FieldValue.increment(1));
            tr.update(userDoc(uid), m);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }

    public void claimWeekly(@NonNull String uid, long coins,
                            @NonNull OnSuccessListener<Void> ok,
                            @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("usu_saldo", FieldValue.increment(coins));
            m.put("usu_stats.xp", FieldValue.increment(100));
            m.put("usu_stats.metas_semana_total", FieldValue.increment(1));
            tr.update(userDoc(uid), m);
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
}
