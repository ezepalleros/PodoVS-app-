package com.example.podovs;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VersusActivity extends AppCompatActivity {

    private static final int MAX_ACTIVE_VS = 3;

    private FirebaseFirestore db;
    private FirestoreRepo repo;
    private String uid;

    private TextView tvAvailable;
    private LinearLayout containerActive;
    private LinearLayout containerRooms;
    private Button btnCreateRoom;

    private ListenerRegistration roomsListener;
    private ListenerRegistration versusListener;

    // listas en memoria
    private final List<VsRoom> myActive = new ArrayList<>();  // partidas en "versus"
    private final List<VsRoom> others   = new ArrayList<>();  // salas en "rooms"

    static class VsRoom {
        String id;
        String ownerId;
        boolean isPublic;
        String code;
        boolean isRace;      // true=carrera, false=maratón
        long targetSteps;    // solo carrera
        long days;           // solo maratón
        boolean finished;
        List<String> players = new ArrayList<>();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_versus);

        db = FirebaseFirestore.getInstance();
        repo = new FirestoreRepo();

        uid = getSharedPreferences("session", MODE_PRIVATE).getString("uid", null);
        if (uid == null || uid.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvAvailable = findViewById(R.id.tvVsAvailable);
        containerActive = findViewById(R.id.containerActive);
        containerRooms = findViewById(R.id.containerRooms);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);

        // bottom bar
        ImageButton btnHome = findViewById(R.id.btnHome);
        ImageButton btnShop = findViewById(R.id.btnShop);
        ImageButton btnVs   = findViewById(R.id.btnVs);
        ImageButton btnEvt  = findViewById(R.id.btnEvents);
        ImageButton btnLb   = findViewById(R.id.btnLeaderboards);

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        btnShop.setOnClickListener(v -> {
            startActivity(new Intent(this, ShopActivity.class));
            finish();
        });

        // refrescar pantalla
        btnVs.setOnClickListener(v -> {
            startActivity(new Intent(this, VersusActivity.class));
            finish();
        });

        btnEvt.setOnClickListener(v ->
                Toast.makeText(this, "Eventos próximamente", Toast.LENGTH_SHORT).show());
        btnLb.setOnClickListener(v ->
                Toast.makeText(this, "Rankings próximamente", Toast.LENGTH_SHORT).show());

        // 👉 ahora abre CreatorFragment
        btnCreateRoom.setOnClickListener(v -> {
            CreatorFragment frag = CreatorFragment.newInstance(uid);
            frag.show(getSupportFragmentManager(), "creator_room");
        });

        // YA NO llamamos a ensureTestRoomForUser.
        startVersusListener();
        startRoomsListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomsListener != null) roomsListener.remove();
        if (versusListener != null) versusListener.remove();
    }

    // ---------- LISTENERS ----------

    private void startRoomsListener() {
        if (roomsListener != null) roomsListener.remove();

        roomsListener = db.collection("rooms")
                .whereEqualTo("roo_finished", false)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    rebuildRoomsFromSnapshot(qs);
                    renderRooms();
                });
    }

    private void startVersusListener() {
        if (versusListener != null) versusListener.remove();

        // solo partidas donde yo participo
        versusListener = db.collection("versus")
                .whereArrayContains("ver_players", uid)
                .whereEqualTo("ver_finished", false)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    rebuildVersusFromSnapshot(qs);
                    renderRooms();
                });
    }

    private void rebuildRoomsFromSnapshot(QuerySnapshot qs) {
        others.clear();

        for (DocumentSnapshot d : qs.getDocuments()) {
            VsRoom r = new VsRoom();
            r.id = d.getId();
            r.ownerId = asString(d.get("roo_user"));

            Boolean pub = d.getBoolean("roo_public");
            r.isPublic = pub != null && pub;

            r.code = asString(d.get("roo_code"));

            Boolean typeB = d.getBoolean("roo_type");
            r.isRace = typeB != null && typeB;

            Object tSteps = d.get("roo_targetSteps");
            if (tSteps instanceof Number) r.targetSteps = ((Number) tSteps).longValue();

            Object dDays = d.get("roo_days");
            if (dDays instanceof Number) r.days = ((Number) dDays).longValue();

            Boolean finished = d.getBoolean("roo_finished");
            r.finished = finished != null && finished;

            Object playersRaw = d.get("roo_players");
            if (playersRaw instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> rawList = (List<Object>) playersRaw;
                for (Object o : rawList) {
                    if (o instanceof String) r.players.add((String) o);
                }
            }

            if (r.finished) continue;
            others.add(r);
        }
    }

    private void rebuildVersusFromSnapshot(QuerySnapshot qs) {
        myActive.clear();

        for (DocumentSnapshot d : qs.getDocuments()) {
            VsRoom r = new VsRoom();
            r.id = d.getId();
            r.ownerId = asString(d.get("ver_owner"));
            Boolean typeB = d.getBoolean("ver_type");
            r.isRace = typeB != null && typeB;
            Object tSteps = d.get("ver_targetSteps");
            if (tSteps instanceof Number) r.targetSteps = ((Number) tSteps).longValue();
            Object dDays = d.get("ver_days");
            if (dDays instanceof Number) r.days = ((Number) dDays).longValue();
            Boolean finished = d.getBoolean("ver_finished");
            r.finished = finished != null && finished;

            Object playersRaw = d.get("ver_players");
            if (playersRaw instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> rawList = (List<Object>) playersRaw;
                for (Object o : rawList) {
                    if (o instanceof String) r.players.add((String) o);
                }
            }

            if (r.finished) continue;
            myActive.add(r);
        }
    }

    // ---------- RENDER ----------

    private void renderRooms() {
        int used = myActive.size();
        int remaining = MAX_ACTIVE_VS - used;
        if (remaining < 0) remaining = 0;

        tvAvailable.setText(
                String.format(Locale.getDefault(),
                        "Versus disponibles: %d / %d", remaining, MAX_ACTIVE_VS));

        containerActive.removeAllViews();
        containerRooms.removeAllViews();

        // partidas activas (colección "versus")
        if (myActive.isEmpty()) {
            containerActive.addView(makeSimpleText("No tenés versus en progreso."));
        } else {
            for (VsRoom r : myActive) {
                containerActive.addView(makeActiveCard(r));
            }
        }

        // salas disponibles (colección "rooms")
        if (others.isEmpty()) {
            containerRooms.addView(makeSimpleText("No hay salas disponibles por ahora."));
        } else {
            for (VsRoom r : others) {
                containerRooms.addView(makeJoinableCard(r));
            }
        }

        boolean canCreate = remaining > 0;
        btnCreateRoom.setEnabled(canCreate);
        btnCreateRoom.setAlpha(canCreate ? 1f : 0.5f);
    }

    private View makeSimpleText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xFF4B5563);
        tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        return tv;
    }

    private View makeActiveCard(VsRoom r) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        root.setLayoutParams(lp);
        root.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Versus en progreso");
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(0xFF111827);
        tvTitle.setPadding(0, 0, 0, dp(4));

        TextView tvInfo = new TextView(this);
        tvInfo.setText(formatRoomInfo(r));
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(0xFF4B5563);

        Button btn = new Button(this);
        btn.setText("Ver estado");
        btn.setAllCaps(false);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF6366F1));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36));
        lpBtn.topMargin = dp(8);
        btn.setLayoutParams(lpBtn);
        btn.setOnClickListener(v ->
                Toast.makeText(this, "El fragmento de estado se hará después.", Toast.LENGTH_SHORT).show());

        root.addView(tvTitle);
        root.addView(tvInfo);
        root.addView(btn);
        return root;
    }

    private View makeJoinableCard(VsRoom r) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        root.setLayoutParams(lp);
        root.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        TextView tvTitle = new TextView(this);
        String priv = r.isPublic ? "Pública" : "Privada";
        tvTitle.setText(String.format(Locale.getDefault(), "Sala %s", priv));
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(0xFF111827);
        tvTitle.setPadding(0, 0, 0, dp(4));

        TextView tvInfo = new TextView(this);
        tvInfo.setText(formatRoomInfo(r));
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(0xFF4B5563);

        Button btn = new Button(this);

        // 👉 si soy el dueño, no puedo unirme
        if (uid.equals(r.ownerId)) {
            btn.setText("Esperando rival…");
            btn.setEnabled(false);
            btn.setAlpha(0.6f);
        } else {
            btn.setText("Unirse");
            btn.setEnabled(true);
            btn.setAlpha(1f);
            btn.setOnClickListener(v -> joinRoom(r));
        }

        btn.setAllCaps(false);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF10B981));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36));
        lpBtn.topMargin = dp(8);
        btn.setLayoutParams(lpBtn);

        root.addView(tvTitle);
        root.addView(tvInfo);
        root.addView(btn);
        return root;
    }

    // ---------- JOIN ----------

    private void joinRoom(VsRoom r) {
        if (uid.equals(r.ownerId)) {
            Toast.makeText(this, "No podés unirte a tu propia sala.", Toast.LENGTH_SHORT).show();
            return;
        }

        repo.joinRoomAndStartMatch(r.id, uid,
                vsId -> Toast.makeText(this, "Te uniste a la sala. Partida creada.", Toast.LENGTH_SHORT).show(),
                e -> {
                    String msg = (e != null && e.getMessage() != null)
                            ? e.getMessage()
                            : "Error al unirte a la sala.";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
    }

    // ---------- UTIL ----------

    private String formatRoomInfo(VsRoom r) {
        String mode = r.isRace ? "Carrera" : "Maratón";
        String objetivo;
        if (r.isRace) {
            objetivo = (r.targetSteps > 0)
                    ? String.format(Locale.getDefault(), "%,d pasos", r.targetSteps)
                    : "meta sorpresa";
        } else {
            objetivo = (r.days > 0)
                    ? String.format(Locale.getDefault(), "%d días", r.days)
                    : "duración sorpresa";
        }

        String rivalId = r.ownerId;
        if (r.players.contains(uid) && r.players.size() > 1) {
            for (String p : r.players) {
                if (!uid.equals(p)) { rivalId = p; break; }
            }
        }

        String rivalLabel;
        if (TextUtils.isEmpty(rivalId)) rivalLabel = "Desconocido";
        else if (rivalId.equals(uid)) rivalLabel = "Vos";
        else rivalLabel = "@" + rivalId.substring(0, Math.min(6, rivalId.length()));

        return "Rival: " + rivalLabel +
                "\nModo: " + mode +
                "\nObjetivo: " + objetivo;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private String asString(Object o) {
        return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null;
    }
}
