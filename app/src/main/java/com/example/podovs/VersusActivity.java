package com.example.podovs;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class VersusActivity extends AppCompatActivity {

    private static final int MAX_ACTIVE_VS = FirestoreRepo.MAX_ACTIVE_VERSUS;

    private FirebaseFirestore db;
    private FirestoreRepo repo;
    private String uid;

    private TextView tvAvailable;
    private LinearLayout containerActive;
    private LinearLayout containerRooms;
    private MaterialButton btnCreateRoom;

    private ImageView ivEnergy1;
    private ImageView ivEnergy2;
    private ImageView ivEnergy3;

    private ListenerRegistration roomsListener;
    private ListenerRegistration versusListener;

    private final List<VsRoom> myActive = new ArrayList<>();
    private final List<VsRoom> others   = new ArrayList<>();
    private final Map<String, OwnerInfo> ownersCache = new HashMap<>();

    static class VsRoom {
        String id;
        String ownerId;
        boolean isPublic;
        String code;
        boolean isRace;
        long targetSteps;
        long days;
        boolean finished;
        List<String> players = new ArrayList<>();

        String ownerName;
        Map<String, Object> ownerEquipped;
    }

    static class OwnerInfo {
        final String name;
        final Map<String, Object> equipped;
        OwnerInfo(String name, Map<String, Object> equipped) {
            this.name = name;
            this.equipped = equipped;
        }
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

        tvAvailable     = findViewById(R.id.tvVsAvailable);
        containerActive = findViewById(R.id.containerActive);
        containerRooms  = findViewById(R.id.containerRooms);
        btnCreateRoom   = findViewById(R.id.btnCreateRoom);

        ivEnergy1 = findViewById(R.id.ivEnergy1);
        ivEnergy2 = findViewById(R.id.ivEnergy2);
        ivEnergy3 = findViewById(R.id.ivEnergy3);

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
        btnVs.setOnClickListener(v -> {
            startActivity(new Intent(this, VersusActivity.class));
            finish();
        });
        btnEvt.setOnClickListener(v -> {
            startActivity(new Intent(this, EventActivity.class));
            finish();
        });
        btnLb.setOnClickListener(v -> {
            startActivity(new Intent(this, RankingActivity.class));
            finish();
        });

        btnCreateRoom.setOnClickListener(v -> {
            CreatorFragment frag = CreatorFragment.newInstance(uid);
            frag.show(getSupportFragmentManager(), "creator_room");
        });

        startVersusListener();
        startRoomsListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomsListener != null) roomsListener.remove();
        if (versusListener != null) versusListener.remove();
    }

    // LISTENERS

    private void startRoomsListener() {
        if (roomsListener != null) roomsListener.remove();

        roomsListener = db.collection("rooms")
                .whereEqualTo("roo_finished", false)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    rebuildRoomsFromSnapshot(qs);
                    fetchOwnersForRooms();
                });
    }

    private void startVersusListener() {
        if (versusListener != null) versusListener.remove();

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

            if (d.get("roo_eventId") != null) {
                continue;
            }

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
                List<?> rawList = (List<?>) playersRaw;
                for (Object o : rawList) if (o instanceof String) r.players.add((String) o);
            }

            if (!r.finished) others.add(r);
        }
    }

    private void rebuildVersusFromSnapshot(QuerySnapshot qs) {
        myActive.clear();
        if (qs == null) return;

        for (DocumentSnapshot d : qs.getDocuments()) {

            Boolean isEventB = d.getBoolean("ver_isEvent");
            if (isEventB != null && isEventB) {
                continue;
            }

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
                List<?> rawList = (List<?>) playersRaw;
                for (Object o : rawList)
                    if (o instanceof String) r.players.add((String) o);
            }

            if (!r.finished) myActive.add(r);
        }
    }


    private void fetchOwnersForRooms() {
        HashSet<String> missing = new HashSet<>();
        for (VsRoom r : others) {
            if (r.ownerId != null && !ownersCache.containsKey(r.ownerId)) {
                missing.add(r.ownerId);
            }
        }
        if (missing.isEmpty()) {
            applyOwnerInfoToRooms();
            renderRooms();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(missing.size());
        for (String ownerId : missing) {
            db.collection("users").document(ownerId)
                    .get()
                    .addOnSuccessListener(snap -> {
                        String name = snap.getString("usu_nombre");
                        if (name == null || name.trim().isEmpty()) {
                            name = buildHandle(ownerId);
                        }
                        Object eqRaw = snap.get("usu_equipped");
                        Map<String, Object> equipped = null;
                        if (eqRaw instanceof Map) {
                            //noinspection unchecked
                            equipped = (Map<String, Object>) eqRaw;
                        }
                        ownersCache.put(ownerId, new OwnerInfo(name, equipped));

                        if (remaining.decrementAndGet() == 0) {
                            applyOwnerInfoToRooms();
                            renderRooms();
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (remaining.decrementAndGet() == 0) {
                            applyOwnerInfoToRooms();
                            renderRooms();
                        }
                    });
        }
    }

    private void applyOwnerInfoToRooms() {
        for (VsRoom r : others) {
            if (r.ownerId == null) continue;
            OwnerInfo info = ownersCache.get(r.ownerId);
            if (info != null) {
                r.ownerName = info.name;
                r.ownerEquipped = info.equipped;
            }
        }
    }

    // RENDER

    private void renderRooms() {
        int used = myActive.size();
        int remaining = MAX_ACTIVE_VS - used;
        if (remaining < 0) remaining = 0;

        tvAvailable.setText(
                String.format(Locale.getDefault(),
                        "Versus disponibles: %d / %d", remaining, MAX_ACTIVE_VS));

        updateEnergyIcons(remaining);

        containerActive.removeAllViews();
        containerRooms.removeAllViews();

        if (myActive.isEmpty()) {
            containerActive.addView(makeSimpleText("No tenés versus en progreso."));
        } else {
            for (VsRoom r : myActive) {
                containerActive.addView(makeActiveCard(r));
            }
        }

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

    private void updateEnergyIcons(int remaining) {
        int full  = R.drawable.energy_empty;
        int spent = R.drawable.energy_charged;

        ImageView[] arr = {ivEnergy1, ivEnergy2, ivEnergy3};
        for (int i = 0; i < arr.length; i++) {
            if (remaining > i) {
                arr[i].setImageResource(full);
            } else {
                arr[i].setImageResource(spent);
            }
        }
    }

    private View makeSimpleText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xFF4B5563);
        tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        return tv;
    }

    // CARD VS ACTIVO

    private View makeActiveCard(VsRoom r) {
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

        LinearLayout avatarsRow = new LinearLayout(this);
        avatarsRow.setOrientation(LinearLayout.HORIZONTAL);
        avatarsRow.setGravity(Gravity.CENTER_HORIZONTAL);
        avatarsRow.setPadding(0, 0, 0, dp(8));
        avatarsRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        String rivalId = r.ownerId;
        if (r.players.contains(uid) && r.players.size() > 1) {
            for (String p : r.players) {
                if (!uid.equals(p)) {
                    rivalId = p;
                    break;
                }
            }
        }

        ImageView myAvatarView    = createMiniAvatarView(48);
        ImageView rivalAvatarView = createMiniAvatarView(48);

        AvatarMiniRenderer.renderInto(this, db, myAvatarView, uid, null);
        if (!TextUtils.isEmpty(rivalId)) {
            AvatarMiniRenderer.renderInto(this, db, rivalAvatarView, rivalId, null);
        }

        LinearLayout vsContainer = new LinearLayout(this);
        vsContainer.setOrientation(LinearLayout.HORIZONTAL);
        vsContainer.setGravity(Gravity.CENTER_VERTICAL);
        vsContainer.addView(myAvatarView);

        TextView tvVs = new TextView(this);
        tvVs.setText("VS");
        tvVs.setTextSize(16);
        tvVs.setTextColor(0xFF6B7280);
        tvVs.setPadding(dp(8), 0, dp(8), 0);

        vsContainer.addView(tvVs);
        vsContainer.addView(rivalAvatarView);

        avatarsRow.addView(vsContainer);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Versus en progreso");
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(0xFF111827);
        tvTitle.setPadding(0, 0, 0, dp(4));

        TextView tvInfo = new TextView(this);
        tvInfo.setText(formatActiveInfo(r));
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(0xFF4B5563);

        MaterialButton btn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText("Ver estado");
        btn.setAllCaps(false);
        btn.setStrokeWidth(dp(1));
        btn.setStrokeColor(
                android.content.res.ColorStateList.valueOf(0xFF6366F1));
        btn.setTextColor(0xFF6366F1);
        btn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFFFFFFF));

        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        lpBtn.topMargin = dp(8);
        btn.setLayoutParams(lpBtn);
        btn.setOnClickListener(v -> {
            VsInfoFragment frag = VsInfoFragment.newInstance(r.id, uid);
            frag.show(getSupportFragmentManager(), "vs_info");
        });

        root.addView(avatarsRow);
        root.addView(tvTitle);
        root.addView(tvInfo);
        root.addView(btn);
        return card;
    }

    // CARD ROOM JOINABLE

    private View makeJoinableCard(VsRoom r) {
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

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatarView = createMiniAvatarView(40);
        AvatarMiniRenderer.renderInto(this, db, avatarView, r.ownerId, r.ownerEquipped);

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String displayName;
        if (!TextUtils.isEmpty(r.ownerName)) {
            displayName = r.ownerName;
        } else if (!TextUtils.isEmpty(r.ownerId)) {
            displayName = buildHandle(r.ownerId);
        } else {
            displayName = "Jugador misterioso";
        }

        TextView tvName = new TextView(this);
        tvName.setText(displayName);
        tvName.setTextSize(15);
        tvName.setTextColor(0xFF111827);

        String handle = (r.ownerId == null) ? "" : buildHandle(r.ownerId);
        TextView tvHandle = new TextView(this);
        tvHandle.setText(handle);
        tvHandle.setTextSize(12);
        tvHandle.setTextColor(0xFF9CA3AF);

        nameCol.addView(tvName);
        nameCol.addView(tvHandle);

        TextView tvLock = new TextView(this);
        tvLock.setTextSize(18);
        tvLock.setPadding(dp(4), 0, 0, 0);
        tvLock.setText(r.isPublic ? "" : "🔒");

        topRow.addView(avatarView);
        topRow.addView(nameCol);
        topRow.addView(tvLock);

        // botón de cerrar sala sólo para el dueño
        TextView tvClose = new TextView(this);
        tvClose.setText("✕");
        tvClose.setTextSize(18);
        tvClose.setTextColor(0xFF9CA3AF);
        tvClose.setPadding(dp(8), 0, 0, 0);
        topRow.addView(tvClose);

        if (uid.equals(r.ownerId)) {
            tvClose.setVisibility(View.VISIBLE);
            tvClose.setOnClickListener(v -> {
                db.collection("rooms").document(r.id)
                        .delete()
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Error al borrar sala", Toast.LENGTH_SHORT).show());
            });
        } else {
            tvClose.setVisibility(View.GONE);
        }

        TextView tvInfo = new TextView(this);
        tvInfo.setText(formatModeAndGoal(r));
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(0xFF4B5563);
        tvInfo.setPadding(0, dp(4), 0, 0);

        MaterialButton btn = new MaterialButton(this);
        btn.setAllCaps(false);
        btn.setCornerRadius(dp(18));
        btn.setIconPadding(dp(6));
        btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF10B981));
        btn.setIconResource(R.drawable.energy_charged);

        if (uid.equals(r.ownerId)) {
            btn.setText("Esperando rival...");
            btn.setEnabled(false);
            btn.setAlpha(0.6f);
            btn.setIcon(null);
        } else {
            btn.setText(r.isPublic ? "Unirse" : "Unirse con código");
            btn.setEnabled(true);
            btn.setAlpha(1f);

            if (r.isPublic) {
                btn.setOnClickListener(v -> joinRoom(r));
            } else {
                btn.setOnClickListener(v -> {
                    CodeFragment f = CodeFragment.newInstance(r.id, uid);
                    f.show(getSupportFragmentManager(), "code_room");
                });
            }
        }

        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        lpBtn.topMargin = dp(8);
        btn.setLayoutParams(lpBtn);

        root.addView(topRow);
        root.addView(tvInfo);
        root.addView(btn);

        return card;
    }

    private ImageView createMiniAvatarView(int sizeDp) {
        ImageView avatar = new ImageView(this);
        LinearLayout.LayoutParams lpAvatar =
                new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        lpAvatar.rightMargin = dp(8);
        avatar.setLayoutParams(lpAvatar);
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        avatar.setImageResource(R.drawable.default_avatar);
        return avatar;
    }

    // JOIN

    private void joinRoom(VsRoom r) {
        if (uid.equals(r.ownerId)) return;

        new FirestoreRepo().joinRoomAndStartMatch(
                r.id,
                uid,
                null,
                vsId -> {},
                e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    public void joinRoomWithCode(@NonNull String roomId, @NonNull String code) {
        if (TextUtils.isEmpty(roomId) || TextUtils.isEmpty(code)) return;

        new FirestoreRepo().joinRoomAndStartMatch(
                roomId,
                uid,
                code,
                vsId -> {},
                e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    // MINI AVATAR RENDERER

    private static class AvatarMiniRenderer {
        private static final Map<String, int[]> OFFSETS = new HashMap<>();
        private static final Map<String, Bitmap> AVATAR_CACHE = new HashMap<>();
        static { }

        static void renderInto(@NonNull VersusActivity act,
                               @NonNull FirebaseFirestore db,
                               @NonNull ImageView target,
                               @Nullable String userId,
                               @Nullable Map<String, Object> equipped) {
            if (TextUtils.isEmpty(userId)) return;

            Bitmap cached = AVATAR_CACHE.get(userId);
            if (cached != null) {
                target.setAdjustViewBounds(true);
                target.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                target.setImageBitmap(cached);
                return;
            }

            if (equipped != null) {
                renderWithEquipped(act, db, target, userId, equipped);
            } else {
                db.collection("users").document(userId).get()
                        .addOnSuccessListener(snap -> {
                            if (snap == null || !snap.exists()
                                    || act.isFinishing() || act.isDestroyed()) return;
                            Object eqRaw = snap.get("usu_equipped");
                            Map<String, Object> eq = null;
                            if (eqRaw instanceof Map) {
                                //noinspection unchecked
                                eq = (Map<String, Object>) eqRaw;
                            }
                            renderWithEquipped(act, db, target, userId, eq);
                        });
            }
        }

        private static void renderWithEquipped(@NonNull VersusActivity act,
                                               @NonNull FirebaseFirestore db,
                                               @NonNull ImageView target,
                                               @NonNull String userId,
                                               @Nullable Map<String, Object> eq) {

            String pielId     = asString(eq != null ? eq.get("usu_piel")      : null);
            String pantalonId = asString(eq != null ? eq.get("usu_pantalon")  : null);
            String remeraId   = asString(eq != null ? eq.get("usu_remera")    : null);
            String zapasId    = asString(eq != null ? eq.get("usu_zapas")     : null);
            String cabezaId   = asString(eq != null ? eq.get("usu_cabeza")    : null);

            if (pielId == null && pantalonId == null && remeraId == null &&
                    zapasId == null && cabezaId == null) {
                int fallback = getResIdByName(act, "piel_startskin");
                if (fallback != 0) target.setImageResource(fallback);
                else target.setImageResource(R.drawable.default_avatar);
                return;
            }

            db.collection("users").document(userId)
                    .collection("my_cosmetics")
                    .get()
                    .addOnSuccessListener(qs ->
                            buildAvatarFromMyCosmetics(act, target, qs,
                                    pielId, pantalonId, remeraId, zapasId, cabezaId, userId));
        }

        private static void buildAvatarFromMyCosmetics(@NonNull VersusActivity act,
                                                       @NonNull ImageView target,
                                                       @NonNull QuerySnapshot qs,
                                                       @Nullable String pielId, @Nullable String pantalonId,
                                                       @Nullable String remeraId, @Nullable String zapasId,
                                                       @Nullable String cabezaId,
                                                       @NonNull String userId) {
            if (act.isFinishing() || act.isDestroyed()) return;

            ArrayList<LayerReq> reqs = new ArrayList<>();
            addReq(qs, reqs, pielId);
            addReq(qs, reqs, zapasId);
            addReq(qs, reqs, pantalonId);
            addReq(qs, reqs, remeraId);
            addReq(qs, reqs, cabezaId);

            if (reqs.isEmpty()) {
                int def = getResIdByName(act, "piel_startskin");
                if (def != 0) target.setImageResource(def);
                else target.setImageResource(R.drawable.default_avatar);
                return;
            }

            loadAllDrawables(act, reqs, layers -> composeAndShowMini(act, target, layers, userId));
        }

        private interface OnLayersReady { void onReady(ArrayList<Layer> layers); }

        private static class LayerReq {
            final String asset; final int offX; final int offY;
            LayerReq(String a, int x, int y) { asset=a; offX=x; offY=y; }
        }

        private static class Layer {
            final Drawable drawable; final int offX; final int offY;
            Layer(Drawable d, int x, int y) { drawable=d; offX=x; offY=y; }
        }

        private static void loadAllDrawables(@NonNull VersusActivity act,
                                             @NonNull ArrayList<LayerReq> reqs,
                                             @NonNull OnLayersReady cb) {
            ArrayList<Layer> result = new ArrayList<>();
            if (reqs.isEmpty()) { cb.onReady(result); return; }

            final int total = reqs.size();
            final int[] count = {0};

            for (LayerReq r : reqs) {
                loadDrawable(act, r.asset, new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable res,
                                                @Nullable Transition<? super Drawable> transition) {
                        result.add(new Layer(res, r.offX, r.offY));
                        if (++count[0] == total && !act.isFinishing() && !act.isDestroyed()) {
                            cb.onReady(result);
                        }
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
            }
        }

        private static void loadDrawable(@NonNull VersusActivity act,
                                         @Nullable String asset,
                                         @NonNull CustomTarget<Drawable> target) {
            if (asset == null) {
                target.onResourceReady(new EmptyDrawable(), null);
                return;
            }
            if (asset.startsWith("http")) {
                Glide.with(act.getApplicationContext())
                        .asDrawable()
                        .load(asset)
                        .into(target);
            } else {
                int resId = getResIdByName(act, asset);
                Drawable d = (resId != 0)
                        ? ContextCompat.getDrawable(act, resId)
                        : null;
                target.onResourceReady((d != null) ? d : new EmptyDrawable(), null);
            }
        }

        private static void composeAndShowMini(@NonNull VersusActivity act,
                                               @NonNull ImageView target,
                                               @NonNull ArrayList<Layer> layers,
                                               @NonNull String userId) {
            if (layers.isEmpty() || act.isFinishing() || act.isDestroyed()) return;

            int bw = Math.max(32, layers.get(0).drawable.getIntrinsicWidth());
            int bh = Math.max(48, layers.get(0).drawable.getIntrinsicHeight());

            Bitmap base = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(base);

            for (Layer l : layers) {
                Drawable d = l.drawable;
                d.setBounds(0, 0, bw, bh);
                canvas.save();
                canvas.translate(l.offX, l.offY);
                d.draw(canvas);
                canvas.restore();
            }

            int targetHpx = dpToPx(act, 48);
            int factor = Math.max(1, targetHpx / bh);
            Bitmap scaled = Bitmap.createScaledBitmap(base, bw * factor, bh * factor, false);

            AVATAR_CACHE.put(userId, scaled);

            target.setAdjustViewBounds(true);
            target.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            target.setImageBitmap(scaled);
        }

        private static class EmptyDrawable extends Drawable {
            @Override public void draw(@NonNull Canvas canvas) {}
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSPARENT; }
            @Override public int getIntrinsicWidth() { return 32; }
            @Override public int getIntrinsicHeight() { return 48; }
        }

        private static void addReq(QuerySnapshot qs, ArrayList<LayerReq> out, @Nullable String cosId) {
            if (cosId == null) return;
            String asset = findAssetFor(qs, cosId);
            if (asset == null) return;
            int[] off = OFFSETS.getOrDefault(cosId, new int[]{0,0});
            out.add(new LayerReq(asset, off[0], off[1]));
        }

        @Nullable
        private static String findAssetFor(QuerySnapshot qs, @Nullable String cosId) {
            if (cosId == null) return null;
            for (DocumentSnapshot d : qs.getDocuments()) {
                if (cosId.equals(d.getId())) {
                    Object v = d.get("myc_cache.cos_asset");
                    if (v instanceof String && !((String) v).isEmpty()) {
                        return (String) v;
                    }
                }
            }
            return null;
        }

        private static int getResIdByName(@NonNull VersusActivity act, @NonNull String name) {
            return act.getResources().getIdentifier(
                    name, "drawable", act.getPackageName());
        }

        private static int dpToPx(@NonNull VersusActivity act, int dp) {
            return Math.round(dp * act.getResources().getDisplayMetrics().density);
        }

        @Nullable
        private static String asString(Object o) {
            return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null;
        }
    }

    // UTILS

    private String formatActiveInfo(VsRoom r) {
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
        else rivalLabel = buildHandle(rivalId);

        return "Rival: " + rivalLabel +
                "\nModo: " + mode +
                "\nObjetivo: " + objetivo;
    }

    private String formatModeAndGoal(VsRoom r) {
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
        return "Modo: " + mode + "\nObjetivo: " + objetivo;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static String asString(Object o) {
        return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null;
    }

    private String buildHandle(@NonNull String ownerId) {
        String shortId = ownerId;
        if (ownerId.length() > 6) shortId = ownerId.substring(0, 6);
        return "@" + shortId;
    }
}
