package com.example.podovs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class ProfileFragment extends BottomSheetDialogFragment {

    // UI
    private ImageView ivAvatar;
    private TextView tvName;
    private TextView tvLevel;
    private ProgressBar pbXp;
    private TextView tvXpHint;

    private TextView tvStat1Title, tvStat1Value;
    private ImageButton btnEdit1;
    private TextView tvStat2Title, tvStat2Value;
    private ImageButton btnEdit2;

    // Repo / sesión
    private FirestoreRepo repo;
    private FirebaseFirestore db;
    private String uid = null;

    // Config
    private static final String PREFS_PROFILE = "profile_prefs";
    private static final String KEY_SLOT1 = "slot1_key";
    private static final String KEY_SLOT2 = "slot2_key";

    private static final int XP_CAP = 100;

    // (Opcional) offsets finos por cosmético. Y<0 sube.
    private static final Map<String, int[]> OFFSETS = new HashMap<>();
    static {
        // Si alguna vez quisieras subir/bajar algo:
        // OFFSETS.put("cos_id_7", new int[]{0, -2}); // ejemplo
    }

    // Métricas tarjetas
    private static final LinkedHashMap<String, String> METRIC_TITLES = new LinkedHashMap<>();
    static {
        METRIC_TITLES.put("usu_stats.km_total", "Km recorridos");
        METRIC_TITLES.put("usu_stats.objetos_comprados", "Objetos comprados");
        METRIC_TITLES.put("usu_stats.metas_diarias_total", "Metas diarias OK (total)");
        METRIC_TITLES.put("usu_stats.metas_semana_total", "Metas semanales OK (total)");
        METRIC_TITLES.put("usu_stats.mayor_pasos_dia", "Récord de pasos (día)");
        METRIC_TITLES.put("usu_stats.eventos_participados", "Eventos participados");
        METRIC_TITLES.put("usu_stats.mejor_posicion", "Mejor posición");
    }

    public ProfileFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        ivAvatar = v.findViewById(R.id.ivAvatarLarge);
        tvName   = v.findViewById(R.id.tvUserName);
        tvLevel  = v.findViewById(R.id.tvLevel);
        pbXp     = v.findViewById(R.id.pbXp);
        tvXpHint = v.findViewById(R.id.tvXpHint);

        tvStat1Title = v.findViewById(R.id.tvStat1Title);
        tvStat1Value = v.findViewById(R.id.tvStat1Value);
        btnEdit1     = v.findViewById(R.id.btnEdit1);

        tvStat2Title = v.findViewById(R.id.tvStat2Title);
        tvStat2Value = v.findViewById(R.id.tvStat2Value);
        btnEdit2     = v.findViewById(R.id.btnEdit2);

        ImageButton btnClose = v.findViewById(R.id.btnCloseSheet);
        btnClose.setOnClickListener(view -> dismiss());

        ivAvatar.setAdjustViewBounds(true);
        ivAvatar.setScaleType(ImageView.ScaleType.CENTER);
        ivAvatar.setImageResource(R.drawable.default_avatar);

        uid  = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE).getString("uid", null);
        repo = new FirestoreRepo();
        db   = FirebaseFirestore.getInstance();

        loadHeaderAndCards();

        btnEdit1.setOnClickListener(view -> showPickerForSlot(1));
        btnEdit2.setOnClickListener(view -> showPickerForSlot(2));
    }

    // ---------- Header ----------
    private void loadHeaderAndCards() {
        if (uid == null) return;

        repo.getUser(uid, (DocumentSnapshot snap) -> {
            if (snap == null || !snap.exists() || !isAdded()) return;

            String nombre = snap.getString("usu_nombre");
            int nivel = safeInt(snap.getLong("usu_nivel"), 1);
            tvName.setText(nombre != null ? nombre : "-");
            tvLevel.setText(String.format(Locale.getDefault(), "Nivel %d", nivel));

            long xp = getNestedLong(snap, "usu_stats.xp", 0L);
            pbXp.setMax(XP_CAP);
            pbXp.setProgress((int) Math.max(0, Math.min(xp, XP_CAP)));
            tvXpHint.setText(String.format(Locale.getDefault(), "%d / %d XP", xp, XP_CAP));

            refreshStatCardsWithSnapshot(snap);
            renderAvatarFromUserSnapshot(snap);
        }, e -> { /* no-op */ });
    }

    private void refreshStatCardsWithSnapshot(DocumentSnapshot snap) {
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE);
        String default1 = "usu_stats.km_total";
        String default2 = "usu_stats.objetos_comprados";

        String k1 = sp.getString(KEY_SLOT1, default1);
        String k2 = sp.getString(KEY_SLOT2, default2);
        if (TextUtils.equals(k1, k2)) k2 = default2;

        Map<String, Object> values = readAllMetricsFromSnapshot(snap);

        tvStat1Title.setText(titleFor(k1));
        tvStat1Value.setText(formatValue(k1, values.get(k1)));

        tvStat2Title.setText(titleFor(k2));
        tvStat2Value.setText(formatValue(k2, values.get(k2)));
    }

    private Map<String, Object> readAllMetricsFromSnapshot(DocumentSnapshot snap) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (String key : METRIC_TITLES.keySet()) {
            Object v = snap.get(key);
            if (v == null) {
                map.put(key, key.endsWith("km_total") ? 0.0 : 0L);
            } else {
                map.put(key, v);
            }
        }
        return map;
    }

    private String titleFor(String key) { String t = METRIC_TITLES.get(key); return (t == null) ? key : t; }
    private String formatValue(String key, Object value) {
        if (key.endsWith("km_total")) {
            double km = (value instanceof Number) ? ((Number) value).doubleValue() : 0d;
            return String.format(Locale.getDefault(), "%.2f", km);
        }
        if (key.endsWith("mejor_posicion")) {
            long pos = (value instanceof Number) ? ((Number) value).longValue() : 0L;
            return pos <= 0 ? "-" : String.valueOf(pos);
        }
        long v = (value instanceof Number) ? ((Number) value).longValue() : 0L;
        return String.format(Locale.getDefault(), "%,d", v);
    }

    private void showPickerForSlot(int slot) {
        final String[] keys = METRIC_TITLES.keySet().toArray(new String[0]);
        final String[] titles = METRIC_TITLES.values().toArray(new String[0]);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Elegí una estadística")
                .setItems(titles, (dialog, which) -> {
                    String chosenKey = keys[which];
                    SharedPreferences sp = requireContext().getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE);
                    if (slot == 1) sp.edit().putString(KEY_SLOT1, chosenKey).apply();
                    else           sp.edit().putString(KEY_SLOT2, chosenKey).apply();
                    loadHeaderAndCards();
                })
                .show();
    }

    // ===================== AVATAR =====================
    private void renderAvatarFromUserSnapshot(DocumentSnapshot snap) {
        String pielId     = asString(snap.get("usu_equipped.usu_piel"));
        String pantalonId = asString(snap.get("usu_equipped.usu_pantalon"));
        String remeraId   = asString(snap.get("usu_equipped.usu_remera"));
        String zapasId    = asString(snap.get("usu_equipped.usu_zapas"));
        String cabezaId   = asString(snap.get("usu_equipped.usu_cabeza"));

        if (pielId == null && pantalonId == null && remeraId == null && zapasId == null && cabezaId == null) {
            int fallback = getResIdByName("piel_startskin");
            if (fallback != 0 && isAdded()) ivAvatar.setImageResource(fallback);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(
                        requireContext().getSharedPreferences("session", Context.MODE_PRIVATE).getString("uid", ""))
                .collection("my_cosmetics")
                .get()
                .addOnSuccessListener(qs -> buildAvatarFromMyCosmetics(qs, pielId, pantalonId, remeraId, zapasId, cabezaId));
    }

    private void buildAvatarFromMyCosmetics(QuerySnapshot qs,
                                            @Nullable String pielId, @Nullable String pantalonId,
                                            @Nullable String remeraId, @Nullable String zapasId,
                                            @Nullable String cabezaId) {
        if (!isAdded()) return;

        // Obtener assets (pueden ser nombres o URLs) + offsets
        ArrayList<LayerReq> reqs = new ArrayList<>();
        addReq(qs, reqs, pielId);
        addReq(qs, reqs, pantalonId);
        addReq(qs, reqs, remeraId);
        addReq(qs, reqs, zapasId);
        addReq(qs, reqs, cabezaId);

        if (reqs.isEmpty()) {
            int def = getResIdByName("piel_startskin");
            if (def != 0) ivAvatar.setImageResource(def);
            return;
        }

        // Cargar drawables (local o URL) y compositar
        loadAllDrawables(reqs, layers -> composeAndShow(layers));
    }

    // ---------- carga/composición ----------
    private void composeAndShow(ArrayList<Layer> layers) {
        if (!isAdded() || layers.isEmpty()) return;

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

        final int targetHpx = dpToPx(96);
        int factor = Math.max(1, targetHpx / bh);
        Bitmap scaled = Bitmap.createScaledBitmap(base, bw * factor, bh * factor, false);

        ivAvatar.setAdjustViewBounds(true);
        ivAvatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ivAvatar.setImageBitmap(scaled);
    }

    private void loadAllDrawables(ArrayList<LayerReq> reqs, OnLayersReady cb) {
        ArrayList<Layer> result = new ArrayList<>();
        if (reqs.isEmpty()) { cb.onReady(result); return; }

        final int total = reqs.size();
        final int[] count = {0};

        for (LayerReq r : reqs) {
            loadDrawable(r.asset, new CustomTarget<Drawable>() {
                @Override public void onResourceReady(@NonNull Drawable res, @Nullable Transition<? super Drawable> t) {
                    result.add(new Layer(res, r.offX, r.offY));
                    if (++count[0] == total && isAdded()) cb.onReady(result);
                }
                @Override public void onLoadCleared(@Nullable Drawable placeholder) {
                    // no-op
                }
            });
        }
    }

    private void loadDrawable(@Nullable String asset, @NonNull CustomTarget<Drawable> target) {
        if (!isAdded()) return;
        if (asset == null) { target.onResourceReady(new EmptyDrawable(), null); return; }

        if (asset.startsWith("http")) {
            Glide.with(requireContext()).asDrawable().load(asset).into(target);
        } else {
            int resId = getResIdByName(asset);
            Drawable d = (resId != 0) ? ContextCompat.getDrawable(requireContext(), resId) : null;
            target.onResourceReady((d != null) ? d : new EmptyDrawable(), null);
        }
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

    // Transparente por si algo falla
    private static class EmptyDrawable extends Drawable {
        @Override public void draw(@NonNull Canvas canvas) {}
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSPARENT; }
        @Override public int getIntrinsicWidth() { return 32; }
        @Override public int getIntrinsicHeight() { return 48; }
    }

    // ---------- util de avatar ----------
    private void addReq(QuerySnapshot qs, ArrayList<LayerReq> out, @Nullable String cosId) {
        if (cosId == null) return;
        String asset = findAssetFor(qs, cosId);
        if (asset == null) return;
        int[] off = OFFSETS.getOrDefault(cosId, new int[]{0,0});
        out.add(new LayerReq(asset, off[0], off[1]));
    }

    @Nullable private String findAssetFor(QuerySnapshot qs, @Nullable String cosId) {
        if (cosId == null) return null;
        for (DocumentSnapshot d : qs.getDocuments()) {
            if (cosId.equals(d.getId())) {
                Object v = d.get("myc_cache.cos_asset");
                if (v instanceof String && !((String) v).isEmpty()) return (String) v; // puede ser URL o nombre
            }
        }
        return null;
    }

    private int dpToPx(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }
    private int getResIdByName(String name) { return getResources().getIdentifier(name, "drawable", requireContext().getPackageName()); }

    // --------- Helpers ---------
    private int safeInt(Long v, int def) { return (v == null) ? def : v.intValue(); }
    private long getNestedLong(DocumentSnapshot s, String path, long def) {
        Object v = s.get(path);
        return (v instanceof Number) ? ((Number) v).longValue() : def;
    }
    @Nullable private String asString(Object o) { return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null; }
}
