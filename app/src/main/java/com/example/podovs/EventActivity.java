package com.example.podovs;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class EventActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String uid;

    // Evento activo
    private String currentEventId = null;

    // UI evento
    private TextView tvEventTitle;
    private TextView tvEventGoal;
    private TextView tvEventReward;
    private TextView tvEventCountdown;
    private ImageView ivEventBoss;

    // UI salas
    private LinearLayout containerMyEventRoom;
    private LinearLayout containerOtherEventRooms;
    private SwitchMaterial swEventRoomPublic;
    private MaterialButton btnEventCreateRoom;

    // Listeners
    private ListenerRegistration eventListener;
    private ListenerRegistration roomsListener;
    private ListenerRegistration coopVsListener;

    // Estado de salas
    private EventRoom myRoom = null;
    private final List<EventRoom> otherRooms = new ArrayList<>();

    // Versus cooperativo (evento)
    private CoopVersusInfo myCoopVersus = null;

    // -------- Salas de evento --------
    static class EventRoom {
        String id;
        String ownerId;
        boolean isPublic;
        String code;
        boolean finished;
        List<String> players = new ArrayList<>();
    }

    // -------- Versus cooperativo de evento --------
    static class CoopVersusInfo {
        String id;
        long targetSteps;
        long rewardCoins;
        boolean finished;
        List<String> players = new ArrayList<>();
        Map<String, Long> stepsByPlayer = new HashMap<>();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event);

        db = FirebaseFirestore.getInstance();

        uid = getSharedPreferences("session", MODE_PRIVATE).getString("uid", null);
        if (uid == null || uid.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Bind UI
        tvEventTitle      = findViewById(R.id.tvEventTitle);
        tvEventGoal       = findViewById(R.id.tvEventGoal);
        tvEventReward     = findViewById(R.id.tvEventReward);
        tvEventCountdown  = findViewById(R.id.tvEventCountdown);
        ivEventBoss       = findViewById(R.id.ivEventBoss);

        containerMyEventRoom      = findViewById(R.id.containerMyEventRoom);
        containerOtherEventRooms  = findViewById(R.id.containerOtherEventRooms);
        swEventRoomPublic         = findViewById(R.id.swEventRoomPublic);
        btnEventCreateRoom        = findViewById(R.id.btnEventCreateRoom);

        // Bottom bar
        ImageButton btnHome         = findViewById(R.id.btnHome);
        ImageButton btnShop         = findViewById(R.id.btnShop);
        ImageButton btnVs           = findViewById(R.id.btnVs);
        ImageButton btnEvents       = findViewById(R.id.btnEvents);
        ImageButton btnLeaderboards = findViewById(R.id.btnLeaderboards);

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        btnShop.setOnClickListener(v -> {
            startActivity(new Intent(this, ShopActivity.class));
            finish();
        });
        btnVs.setOnClickListener(v -> {
            startActivity(new Intent(this, VersusActivity.class));
            finish();
        });
        btnEvents.setOnClickListener(v -> {
            // ya estás acá
        });
        btnLeaderboards.setOnClickListener(v -> {
            startActivity(new Intent(this, RankingActivity.class));
            finish();
        });

        btnEventCreateRoom.setOnClickListener(v -> createEventRoom());

        startActiveEventListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventListener != null) eventListener.remove();
        if (roomsListener != null) roomsListener.remove();
        if (coopVsListener != null) coopVsListener.remove();
    }

    // =========================================================
    //  LISTENER DEL EVENTO ACTIVO
    // =========================================================
    private void startActiveEventListener() {
        if (eventListener != null) eventListener.remove();

        eventListener = db.collection("events")
                .orderBy("ev_startAt", Query.Direction.DESCENDING)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;

                    if (qs.isEmpty()) {
                        showNoEvent();
                        return;
                    }

                    long now = System.currentTimeMillis();
                    DocumentSnapshot active = null;

                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Timestamp tsStart = d.getTimestamp("ev_startAt");
                        Timestamp tsEnd   = d.getTimestamp("ev_endAt");

                        long startMs = tsStart != null ? tsStart.toDate().getTime() : Long.MIN_VALUE;
                        long endMs   = tsEnd   != null ? tsEnd.toDate().getTime()   : Long.MAX_VALUE;

                        if (now >= startMs && now <= endMs) {
                            active = d;
                            break;
                        }
                    }

                    if (active == null) {
                        showNoEvent();
                    } else {
                        handleEventDoc(active);
                    }
                });
    }

    private void handleEventDoc(@NonNull DocumentSnapshot evt) {
        currentEventId = evt.getId();

        String name = evt.getString("ev_title");
        if (TextUtils.isEmpty(name)) name = "Evento especial";
        tvEventTitle.setText(name);

        Long goalSteps = null;
        Object goalObj = evt.get("ev_targetSteps");
        if (goalObj instanceof Number) goalSteps = ((Number) goalObj).longValue();
        long goal = (goalSteps == null) ? 0L : goalSteps;
        if (goal > 0) {
            tvEventGoal.setText(String.format(
                    Locale.getDefault(),
                    "Meta del evento: %,d pasos", goal));
        } else {
            tvEventGoal.setText("Meta del evento: -");
        }

        Long rewardCoins = null;
        Object rewObj = evt.get("ev_rewardCoins");
        if (rewObj instanceof Number) rewardCoins = ((Number) rewObj).longValue();
        long reward = (rewardCoins == null) ? 0L : rewardCoins;
        if (reward > 0) {
            tvEventReward.setText(String.format(
                    Locale.getDefault(),
                    "Recompensa: %,d coins por jugador", reward));
        } else {
            tvEventReward.setText("Recompensa: -");
        }

        Object endObj = evt.get("ev_endAt");
        if (endObj instanceof Timestamp) {
            long diff = ((Timestamp) endObj).toDate().getTime() - System.currentTimeMillis();
            if (diff <= 0) {
                tvEventCountdown.setText("Tiempo restante: finalizado");
            } else {
                long diffSec = diff / 1000L;
                long days = diffSec / (24L * 3600L);
                long hours = (diffSec % (24L * 3600L)) / 3600L;
                tvEventCountdown.setText(
                        String.format(Locale.getDefault(),
                                "Tiempo restante: %dd %dh", days, hours));
            }
        } else {
            tvEventCountdown.setText("Tiempo restante: -");
        }

        String bossImg = evt.getString("ev_bossImg");
        if (!TextUtils.isEmpty(bossImg)) {
            Glide.with(this)
                    .load(bossImg)
                    .placeholder(R.drawable.default_avatar)
                    .into(ivEventBoss);
        } else {
            ivEventBoss.setImageResource(R.drawable.default_avatar);
        }

        startRoomsListenerForEvent(currentEventId);
        startCoopVersusListenerForEvent(currentEventId);
        updateCreateButtonState();
    }

    private void showNoEvent() {
        currentEventId = null;
        tvEventTitle.setText("Sin evento activo");
        tvEventGoal.setText("Meta del evento: -");
        tvEventReward.setText("Recompensa: -");
        tvEventCountdown.setText("Tiempo restante: -");
        ivEventBoss.setImageResource(R.drawable.default_avatar);

        if (roomsListener != null) {
            roomsListener.remove();
            roomsListener = null;
        }
        if (coopVsListener != null) {
            coopVsListener.remove();
            coopVsListener = null;
        }

        myRoom = null;
        otherRooms.clear();
        myCoopVersus = null;
        renderRooms();
        updateCreateButtonState();
    }

    // =================== LISTENERS DE ROOMS / VERSUS ===================

    private void startRoomsListenerForEvent(@NonNull String eventId) {
        if (roomsListener != null) roomsListener.remove();

        roomsListener = db.collection("rooms")
                .whereEqualTo("roo_eventId", eventId)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    rebuildEventRoomsFromSnapshot(qs);
                    renderRooms();
                    updateCreateButtonState();
                });
    }

    private void startCoopVersusListenerForEvent(@NonNull String eventId) {
        if (coopVsListener != null) coopVsListener.remove();

        coopVsListener = db.collection("versus")
                .whereEqualTo("ver_isEvent", true)
                .whereEqualTo("ver_eventId", eventId)
                .whereArrayContains("ver_players", uid)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    rebuildCoopVersusFromSnapshot(qs);
                    renderRooms();
                    updateCreateButtonState();
                });
    }

    private void rebuildEventRoomsFromSnapshot(@NonNull QuerySnapshot qs) {
        myRoom = null;
        otherRooms.clear();

        for (DocumentSnapshot d : qs.getDocuments()) {
            EventRoom r = new EventRoom();
            r.id = d.getId();
            r.ownerId = asString(d.get("roo_user"));

            Boolean pub = d.getBoolean("roo_public");
            r.isPublic = pub != null && pub;

            r.code = asString(d.get("roo_code"));

            Boolean fin = d.getBoolean("roo_finished");
            r.finished = fin != null && fin;

            if (r.finished) {
                continue;
            }

            Object playersRaw = d.get("roo_players");
            if (playersRaw instanceof List) {
                for (Object o : (List<?>) playersRaw) {
                    if (o instanceof String) {
                        r.players.add((String) o);
                    }
                }
            }

            boolean containsMe = r.players.contains(uid);
            if (containsMe && myRoom == null) {
                myRoom = r; // UNA sola sala por usuario
            } else if (!containsMe) {
                otherRooms.add(r);
            }

            // Si la sala llegó a 4 jugadores, intentamos crear el VS cooperativo
            if (r.players.size() >= 4) {
                maybeCreateCoopVersus(r);
            }
        }
    }

    private void rebuildCoopVersusFromSnapshot(@NonNull QuerySnapshot qs) {
        myCoopVersus = null;

        if (qs.isEmpty()) return;

        // Si por algún motivo hubiera más de uno, agarramos el más nuevo
        DocumentSnapshot chosen = qs.getDocuments().get(0);
        long bestCreated = getCreatedAtMillis(chosen);

        for (DocumentSnapshot d : qs.getDocuments()) {
            long c = getCreatedAtMillis(d);
            if (c > bestCreated) {
                bestCreated = c;
                chosen = d;
            }
        }

        CoopVersusInfo info = new CoopVersusInfo();
        info.id = chosen.getId();

        Boolean finB = chosen.getBoolean("ver_finished");
        info.finished = finB != null && finB;

        Object tObj = chosen.get("ver_targetSteps");
        if (tObj instanceof Number) info.targetSteps = ((Number) tObj).longValue();

        Object rObj = chosen.get("ver_rewardCoins");
        if (rObj instanceof Number) info.rewardCoins = ((Number) rObj).longValue();

        Object playersRaw = chosen.get("ver_players");
        if (playersRaw instanceof List) {
            for (Object o : (List<?>) playersRaw) {
                if (o instanceof String) info.players.add((String) o);
            }
        }

        Object progObj = chosen.get("ver_progress");
        if (progObj instanceof Map) {
            Map<?,?> m = (Map<?,?>) progObj;
            for (String pid : info.players) {
                long steps = 0L;
                Object v = m.get(pid);
                if (v instanceof Map) {
                    Object st = ((Map<?,?>) v).get("steps");
                    if (st instanceof Number) steps = ((Number) st).longValue();
                }
                info.stepsByPlayer.put(pid, steps);
            }
        }

        myCoopVersus = info;
    }

    private long getCreatedAtMillis(@NonNull DocumentSnapshot d) {
        Object cObj = d.get("ver_createdAt");
        if (cObj instanceof Timestamp) {
            return ((Timestamp) cObj).toDate().getTime();
        } else if (cObj instanceof Number) {
            return ((Number) cObj).longValue();
        }
        return 0L;
    }

    // Crea el versus cooperativo para una sala llena (4 jugadores) si aún no existe
    private void maybeCreateCoopVersus(@NonNull EventRoom room) {
        if (currentEventId == null) return;

        final String eventId = currentEventId;
        final String roomId  = room.id;

        db.runTransaction(tr -> {
            DocumentSnapshot roomSnap =
                    tr.get(db.collection("rooms").document(roomId));

            if (!roomSnap.exists()) return null;

            Boolean finished  = roomSnap.getBoolean("roo_finished");
            Boolean vsCreated = roomSnap.getBoolean("roo_vsCreated");

            // si la sala ya está terminada o ya tiene VS creado, no hacemos nada
            if (finished != null && finished) return null;
            if (vsCreated != null && vsCreated) return null;

            // jugadores actuales de la sala
            List<String> players = new ArrayList<>();
            Object rawPlayers = roomSnap.get("roo_players");
            if (rawPlayers instanceof List) {
                for (Object o : (List<?>) rawPlayers) {
                    if (o instanceof String) players.add((String) o);
                }
            }

            // solo creamos el VS cuando hay 4 jugadores
            if (players.size() < 4) return null;

            // leemos el evento
            DocumentSnapshot eventSnap =
                    tr.get(db.collection("events").document(eventId));
            if (!eventSnap.exists()) return null;

            long targetSteps = 0L;
            Object tObj = eventSnap.get("ev_targetSteps");
            if (tObj instanceof Number) targetSteps = ((Number) tObj).longValue();

            long rewardCoins = 0L;
            Object rObj = eventSnap.get("ev_rewardCoins");
            if (rObj instanceof Number) rewardCoins = ((Number) rObj).longValue();

            String ownerUid = roomSnap.getString("roo_user");
            if ((ownerUid == null || ownerUid.isEmpty()) && !players.isEmpty()) {
                ownerUid = players.get(0);
            }

            Map<String, Object> vsData = new HashMap<>();
            vsData.put("ver_owner", ownerUid);
            vsData.put("ver_players", players);
            vsData.put("ver_type", false); // no importa para coop
            vsData.put("ver_targetSteps", targetSteps);
            vsData.put("ver_days", 0L);
            vsData.put("ver_createdAt", FieldValue.serverTimestamp());
            vsData.put("ver_finished", false);
            vsData.put("ver_isEvent", true);
            vsData.put("ver_eventId", eventId);
            vsData.put("ver_rewardCoins", rewardCoins);
            vsData.put("ver_roomId", roomId);

            String today = todayCode();
            Map<String, Object> progress = new HashMap<>();
            for (String pid : players) {
                Map<String, Object> p = new HashMap<>();
                p.put("steps", 0L);
                p.put("deviceTotal", 0L);
                p.put("joinedAt", FieldValue.serverTimestamp());
                p.put("lastUpdate", FieldValue.serverTimestamp());
                p.put("dayCode", today);
                progress.put(pid, p);
            }
            vsData.put("ver_progress", progress);

            com.google.firebase.firestore.DocumentReference vsRef =
                    db.collection("versus").document();
            tr.set(vsRef, vsData);

            // BORRAMOS la room cuando se crea el versus
            tr.delete(roomSnap.getReference());

            return null;
        });
    }

    // =================== RENDER DE UI ===================

    private void renderRooms() {
        containerMyEventRoom.removeAllViews();
        containerOtherEventRooms.removeAllViews();

        // Prioridad: si ya hay versus coop, mostramos eso
        if (myCoopVersus != null) {
            containerMyEventRoom.addView(makeCoopVersusCard(myCoopVersus));

            containerOtherEventRooms.addView(makeSimpleText(
                    "Ya estás participando del evento con tu equipo."
            ));
            return;
        }

        // Si no hay versus, mostramos sala (si existe)
        if (myRoom == null) {
            containerMyEventRoom.addView(makeSimpleText(
                    "Todavía no estás en ninguna sala del evento."
            ));
        } else {
            containerMyEventRoom.addView(makeMyRoomCard(myRoom));
        }

        if (otherRooms.isEmpty()) {
            containerOtherEventRooms.addView(makeSimpleText(
                    "No hay otras salas disponibles por ahora."
            ));
        } else {
            for (EventRoom r : otherRooms) {
                containerOtherEventRooms.addView(makeOtherRoomCard(r));
            }
        }
    }

    private void updateCreateButtonState() {
        boolean canCreate = currentEventId != null
                && myRoom == null
                && myCoopVersus == null; // si ya tenés equipo, no creás sala
        btnEventCreateRoom.setEnabled(canCreate);
        btnEventCreateRoom.setAlpha(canCreate ? 1f : 0.5f);
    }

    private void createEventRoom() {
        if (currentEventId == null) {
            Toast.makeText(this, "No hay evento activo.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (myRoom != null) {
            Toast.makeText(this, "Ya estás en una sala del evento.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (myCoopVersus != null) {
            Toast.makeText(this, "Ya estás participando del evento.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isPublic = swEventRoomPublic.isChecked();

        // Para salas privadas generamos un código de 4 dígitos.
        String code;
        if (!isPublic) {
            int num = new Random().nextInt(10_000); // 0..9999
            code = String.format(Locale.getDefault(), "%04d", num);
        } else {
            code = "";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("roo_user", uid);
        data.put("roo_eventId", currentEventId);
        data.put("roo_public", isPublic);
        data.put("roo_code", code);
        data.put("roo_createdAt", FieldValue.serverTimestamp());
        data.put("roo_finished", false);
        data.put("roo_vsCreated", false);

        List<String> players = new ArrayList<>();
        players.add(uid);
        data.put("roo_players", players);

        db.collection("rooms")
                .add(data)
                .addOnSuccessListener(doc -> {
                    if (!isPublic && !TextUtils.isEmpty(code)) {
                        Toast.makeText(this,
                                "Sala privada creada. Código: " + code,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Sala creada.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al crear sala.", Toast.LENGTH_SHORT).show());
    }

    // Join público
    private void joinEventRoom(@NonNull EventRoom r) {
        joinEventRoom(r, null);
    }

    private void joinEventRoom(@NonNull EventRoom r, @Nullable String inputCode) {
        if (currentEventId == null) {
            Toast.makeText(this, "No hay evento activo.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (myRoom != null) {
            Toast.makeText(this, "Ya estás en una sala del evento.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (myCoopVersus != null) {
            Toast.makeText(this, "Ya estás participando del evento.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("rooms").document(r.id)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        Toast.makeText(this, "La sala ya no existe.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Boolean pub = snap.getBoolean("roo_public");
                    boolean isPublic = pub == null || pub;

                    if (!isPublic) {
                        String stored = snap.getString("roo_code");
                        String normalizedStored = stored == null ? "" : stored.trim();
                        String normalizedInput  = inputCode == null ? "" : inputCode.trim();

                        if (normalizedStored.isEmpty()) {
                            Toast.makeText(this,
                                    "Esta sala privada no tiene código configurado.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (!normalizedStored.equals(normalizedInput)) {
                            Toast.makeText(this,
                                    "Código incorrecto.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    List<String> players = new ArrayList<>();
                    Object raw = snap.get("roo_players");
                    if (raw instanceof List) {
                        for (Object o : (List<?>) raw) {
                            if (o instanceof String) players.add((String) o);
                        }
                    }
                    if (players.contains(uid)) {
                        Toast.makeText(this, "Ya estás en esta sala.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (players.size() >= 4) {
                        Toast.makeText(this, "La sala está llena.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    db.collection("rooms").document(r.id)
                            .update("roo_players", FieldValue.arrayUnion(uid))
                            .addOnSuccessListener(v ->
                                    Toast.makeText(this, "Te uniste a la sala.", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Error al unirse a la sala.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al cargar sala.", Toast.LENGTH_SHORT).show());
    }

    private void leaveMyRoom(@NonNull EventRoom r) {
        db.collection("rooms").document(r.id)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        Toast.makeText(this, "La sala ya no existe.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<String> players = new ArrayList<>();
                    Object raw = snap.get("roo_players");
                    if (raw instanceof List) {
                        for (Object o : (List<?>) raw) {
                            if (o instanceof String) players.add((String) o);
                        }
                    }

                    // Si soy el único jugador, borro la sala
                    if (players.size() <= 1) {
                        db.collection("rooms").document(r.id)
                                .delete()
                                .addOnSuccessListener(v ->
                                        Toast.makeText(this, "Sala eliminada.", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error al eliminar sala.", Toast.LENGTH_SHORT).show());
                    } else {
                        // Sino, solo me saco de la lista
                        db.collection("rooms").document(r.id)
                                .update("roo_players", FieldValue.arrayRemove(uid))
                                .addOnSuccessListener(v ->
                                        Toast.makeText(this, "Saliste de la sala.", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error al salir de la sala.", Toast.LENGTH_SHORT).show());
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al cargar sala.", Toast.LENGTH_SHORT).show());
    }

    private View makeSimpleText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xFF4B5563);
        tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        return tv;
    }

    private View makeMyRoomCard(@NonNull EventRoom r) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.bottomMargin = dp(12);
        card.setLayoutParams(lpCard);
        card.setRadius(dp(18));
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setUseCompatPadding(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(root);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Tu sala del evento");
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFF111827);
        tvTitle.setPadding(0, 0, 0, dp(4));

        TextView tvInfo = new TextView(this);
        int count = (r.players == null) ? 0 : r.players.size();
        String tipo = r.isPublic ? "Pública" : "Privada";
        StringBuilder info = new StringBuilder();
        info.append(String.format(Locale.getDefault(),
                "Jugadores: %d / 4\nTipo: %s", count, tipo));
        if (!r.isPublic && !TextUtils.isEmpty(r.code)) {
            info.append(String.format(Locale.getDefault(),
                    "\nCódigo: %s", r.code));
        }
        tvInfo.setText(info.toString());
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(0xFF4B5563);

        MaterialButton btnLeave = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnLeave.setText("Salir de la sala");
        btnLeave.setAllCaps(false);
        btnLeave.setStrokeWidth(dp(1));
        btnLeave.setStrokeColor(
                android.content.res.ColorStateList.valueOf(0xFFDC2626));
        btnLeave.setTextColor(0xFFDC2626);
        btnLeave.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        lpBtn.topMargin = dp(8);
        btnLeave.setLayoutParams(lpBtn);
        btnLeave.setOnClickListener(v -> leaveMyRoom(r));

        root.addView(tvTitle);
        root.addView(tvInfo);
        root.addView(btnLeave);

        return card;
    }

    private View makeCoopVersusCard(@NonNull CoopVersusInfo vs) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.bottomMargin = dp(12);
        card.setLayoutParams(lpCard);
        card.setRadius(dp(18));
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setUseCompatPadding(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(root);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Tu equipo del evento");
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFF111827);
        tvTitle.setPadding(0, 0, 0, dp(4));

        long total = 0L;
        for (Long v : vs.stepsByPlayer.values()) {
            if (v != null) total += v;
        }

        TextView tvInfo = new TextView(this);
        String linea;
        if (vs.targetSteps > 0) {
            linea = String.format(Locale.getDefault(),
                    "Progreso grupal: %,d / %,d pasos",
                    total, vs.targetSteps);
        } else {
            linea = String.format(Locale.getDefault(),
                    "Progreso grupal: %,d pasos", total);
        }
        if (vs.finished) {
            linea += "\nEstado: COMPLETADO";
        } else {
            linea += "\nEstado: En curso";
        }
        tvInfo.setText(linea);
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(0xFF4B5563);
        tvInfo.setPadding(0, 0, 0, dp(8));

        root.addView(tvTitle);
        root.addView(tvInfo);

        // Aportes individuales
        for (String pid : vs.players) {
            long steps = 0L;
            if (vs.stepsByPlayer.containsKey(pid) && vs.stepsByPlayer.get(pid) != null) {
                steps = vs.stepsByPlayer.get(pid);
            }

            TextView tvRow = new TextView(this);
            tvRow.setText(String.format(Locale.getDefault(),
                    "%s · %,d pasos",
                    buildHandle(pid), steps));
            tvRow.setTextSize(13);
            tvRow.setTextColor(0xFF111827);
            tvRow.setPadding(0, dp(2), 0, dp(2));
            root.addView(tvRow);
        }

        return card;
    }

    private View makeOtherRoomCard(@NonNull EventRoom r) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.bottomMargin = dp(12);
        card.setLayoutParams(lpCard);
        card.setRadius(dp(18));
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setUseCompatPadding(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(root);

        TextView tvTitle = new TextView(this);
        String ownerHandle = (r.ownerId == null)
                ? "Sala"
                : "Sala de " + buildHandle(r.ownerId);
        tvTitle.setText(ownerHandle);
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(0xFF111827);
        tvTitle.setPadding(0, 0, 0, dp(4));

        TextView tvInfo = new TextView(this);
        int count = (r.players == null) ? 0 : r.players.size();
        String tipo = r.isPublic ? "Pública" : "Privada";
        tvInfo.setText(String.format(Locale.getDefault(),
                "Jugadores: %d / 4\nTipo: %s", count, tipo));
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(0xFF4B5563);

        MaterialButton btnJoin = new MaterialButton(this);
        btnJoin.setAllCaps(false);
        btnJoin.setCornerRadius(dp(18));
        btnJoin.setTextColor(0xFFFFFFFF);
        btnJoin.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF10B981));

        if (r.isPublic) {
            btnJoin.setText("Unirse");
            btnJoin.setEnabled(true);
            btnJoin.setAlpha(1f);
            btnJoin.setOnClickListener(v -> joinEventRoom(r));
        } else {
            btnJoin.setText("Unirse con código");
            btnJoin.setEnabled(true);
            btnJoin.setAlpha(1f);
            btnJoin.setOnClickListener(v -> showCodeDialogAndJoin(r));
        }

        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        lpBtn.topMargin = dp(8);
        btnJoin.setLayoutParams(lpBtn);

        root.addView(tvTitle);
        root.addView(tvInfo);
        root.addView(btnJoin);

        return card;
    }

    private void showCodeDialogAndJoin(@NonNull EventRoom r) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ingresar código");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Código de 4 dígitos");
        input.setPadding(dp(16), dp(8), dp(16), dp(8));

        builder.setView(input);
        builder.setPositiveButton("Unirse", (dialog, which) -> {
            String code = input.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "Ingresá el código.", Toast.LENGTH_SHORT).show();
                return;
            }
            joinEventRoom(r, code);
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // yyyyMMdd del día actual (mismo formato que FirestoreRepo)
    private String todayCode() {
        return new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                .format(new java.util.Date());
    }

    @Nullable
    private static String asString(Object o) {
        return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null;
    }

    @NonNull
    private String buildHandle(@NonNull String ownerId) {
        String shortId = ownerId;
        if (ownerId.length() > 6) shortId = ownerId.substring(0, 6);
        return "@" + shortId;
    }
}
