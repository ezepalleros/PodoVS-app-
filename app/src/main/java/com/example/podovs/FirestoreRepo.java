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

    /** Crea el perfil con metas por defecto (1000/10000) y km_semana=0. */
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
        stats.put("km_hoy", 0.0);
        stats.put("km_semana", 0.0);
        stats.put("km_total", 0.0);
        stats.put("xp", 0L);
        stats.put("objetos_comprados", 0L);
        stats.put("meta_diaria_pasos", 1000L);
        stats.put("meta_semanal_pasos", 10000L);
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

    // ---------- Ranking ----------
    public Query rankingHoy() {
        return db.collection("users")
                .orderBy("usu_stats.km_hoy", Query.Direction.DESCENDING)
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

    // ---------- Stats básicos ----------
    public void setKmHoy(@NonNull String uid, double km,
                         @NonNull OnSuccessListener<Void> ok,
                         @NonNull OnFailureListener err) {
        userDoc(uid).update("usu_stats.km_hoy", km)
                .addOnSuccessListener(ok).addOnFailureListener(err);
    }
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

    // ---------- Recompensas ----------
    public void onDailyGoalReached(@NonNull String uid,
                                   @NonNull OnSuccessListener<Void> ok,
                                   @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("usu_saldo", FieldValue.increment(50));
            m.put("usu_stats.xp", FieldValue.increment(25));
            m.put("usu_stats.metas_diarias_cumplidas", FieldValue.increment(1));
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
            m.put("usu_stats.metas_semanales_cumplidas", FieldValue.increment(1));
            tr.update(userDoc(uid), m);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
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

    // ---------- Claims ----------
    public void claimDaily(@NonNull String uid, long coins,
                           @NonNull OnSuccessListener<Void> ok,
                           @NonNull OnFailureListener err) {
        db.runTransaction((Transaction.Function<Void>) tr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("usu_saldo", FieldValue.increment(coins));
            m.put("usu_stats.xp", FieldValue.increment(25));
            m.put("usu_stats.metas_diarias_cumplidas", FieldValue.increment(1));
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
            m.put("usu_stats.metas_semanales_cumplidas", FieldValue.increment(1));
            tr.update(userDoc(uid), m);
            return null;
        }).addOnSuccessListener(ok).addOnFailureListener(err);
    }
}
