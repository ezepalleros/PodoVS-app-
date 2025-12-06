package com.example.podovs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AvatarActivity extends AppCompatActivity {

    private ImageView ivPreview;
    private RecyclerView rv;
    private Button btnCatPiel, btnCatCabeza, btnCatRemera, btnCatPantalon, btnCatZapas;
    private Button btnConfirm, btnCancel;

    private FirebaseFirestore db;
    private String uid;

    private final List<Cosmetic> all = new ArrayList<>();
    private final Map<String, String> selectedByTipo = new HashMap<>();
    private String currentTipo = "piel";

    private static final Map<String, String> TIPO_TO_USER_FIELD = new HashMap<>();

    static {
        TIPO_TO_USER_FIELD.put("piel", "usu_equipped.usu_piel");
        TIPO_TO_USER_FIELD.put("cabeza", "usu_equipped.usu_cabeza");
        TIPO_TO_USER_FIELD.put("remera", "usu_equipped.usu_remera");
        TIPO_TO_USER_FIELD.put("pantalon", "usu_equipped.usu_pantalon");
        TIPO_TO_USER_FIELD.put("zapatillas", "usu_equipped.usu_zapas");
    }

    private static final Map<String, int[]> OFFSETS = new HashMap<>();

    static {
        OFFSETS.put("cos_id_7", new int[]{0, -6});
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar);

        ivPreview = findViewById(R.id.ivPreview);
        rv = findViewById(R.id.rvItems);
        btnCatPiel = findViewById(R.id.btnCatPiel);
        btnCatCabeza = findViewById(R.id.btnCatCabeza);
        btnCatRemera = findViewById(R.id.btnCatRemera);
        btnCatPantalon = findViewById(R.id.btnCatPantalon);
        btnCatZapas = findViewById(R.id.btnCatZapas);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel = findViewById(R.id.btnCancel);

        rv.setLayoutManager(new GridLayoutManager(this, 4));

        db = FirebaseFirestore.getInstance();
        uid = getSharedPreferences("session", MODE_PRIVATE).getString("uid", null);
        if (uid == null) {
            finish();
            return;
        }

        loadData();

        btnCatPiel.setOnClickListener(v -> {
            currentTipo = "piel";
            refreshGrid();
        });
        btnCatCabeza.setOnClickListener(v -> {
            currentTipo = "cabeza";
            refreshGrid();
        });
        btnCatRemera.setOnClickListener(v -> {
            currentTipo = "remera";
            refreshGrid();
        });
        btnCatPantalon.setOnClickListener(v -> {
            currentTipo = "pantalon";
            refreshGrid();
        });
        btnCatZapas.setOnClickListener(v -> {
            currentTipo = "zapatillas";
            refreshGrid();
        });

        btnConfirm.setOnClickListener(v -> saveSelection());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void loadData() {
        Tasks.whenAllSuccess(
                db.collection("users").document(uid).get(),
                db.collection("users").document(uid).collection("my_cosmetics").get()
        ).addOnSuccessListener(list -> {
            DocumentSnapshot user = (DocumentSnapshot) list.get(0);
            QuerySnapshot inv = (QuerySnapshot) list.get(1);

            selectedByTipo.put("piel", asString(user.get("usu_equipped.usu_piel")));
            selectedByTipo.put("cabeza", asString(user.get("usu_equipped.usu_cabeza")));
            selectedByTipo.put("remera", asString(user.get("usu_equipped.usu_remera")));
            selectedByTipo.put("pantalon", asString(user.get("usu_equipped.usu_pantalon")));
            selectedByTipo.put("zapatillas", asString(user.get("usu_equipped.usu_zapas")));

            all.clear();
            for (DocumentSnapshot d : inv.getDocuments()) {
                String id = d.getId();
                Map<String, Object> cache = (Map<String, Object>) d.get("myc_cache");
                String tipo = cache == null ? null : asString(cache.get("cos_tipo"));
                String asset = cache == null ? null : asString(cache.get("cos_asset"));
                String aType = cache == null ? null : asString(cache.get("cos_assetType"));
                boolean eq = Boolean.TRUE.equals(d.getBoolean("myc_equipped"));
                all.add(new Cosmetic(id, tipo, asset, aType, eq));
            }

            refreshGrid();
            renderPreview();
        }).addOnFailureListener(e -> finish());
    }

    private void refreshGrid() {
        List<Cosmetic> filtered = new ArrayList<>();
        if (!"piel".equals(currentTipo)) filtered.add(Cosmetic.none(currentTipo));
        for (Cosmetic c : all) if (currentTipo.equalsIgnoreCase(c.tipo)) filtered.add(c);

        rv.setAdapter(new CosAdapter(
                filtered,
                currentTipo,
                selectedByTipo.get(currentTipo),
                clicked -> {
                    selectedByTipo.put(currentTipo, clicked.id);
                    renderPreview();
                    rv.getAdapter().notifyDataSetChanged();
                }
        ));
    }

    private void renderPreview() {
        String selPiel = selectedByTipo.get("piel");
        String selZap = selectedByTipo.get("zapatillas");
        String selPan = selectedByTipo.get("pantalon");
        String selRem = selectedByTipo.get("remera");
        String selCab = selectedByTipo.get("cabeza");

        List<LayerReq> reqs = new ArrayList<>();
        addReq(reqs, selPiel);
        addReq(reqs, selZap);
        addReq(reqs, selPan);
        addReq(reqs, selRem);
        addReq(reqs, selCab);

        if (reqs.isEmpty()) {
            ivPreview.setImageDrawable(null);
            return;
        }
        loadAllDrawables(reqs, this::composeAndShow);
    }

    private void composeAndShow(List<Layer> layers) {
        if (layers.isEmpty()) {
            ivPreview.setImageDrawable(null);
            return;
        }

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

        int targetH = dpToPx(180);
        int factor = Math.max(1, targetH / bh);
        Bitmap scaled = Bitmap.createScaledBitmap(base, bw * factor, bh * factor, /*filter=*/false);

        ivPreview.setAdjustViewBounds(true);
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ivPreview.setImageBitmap(scaled);
    }

    // ======= SAVE =======
    private void saveSelection() {
        WriteBatch batch = db.batch();

        Map<String, Object> upd = new HashMap<>();
        for (Map.Entry<String, String> e : selectedByTipo.entrySet()) {
            String tipo = e.getKey();
            String field = TIPO_TO_USER_FIELD.get(tipo);
            if (field != null) upd.put(field, e.getValue());
        }
        batch.update(db.collection("users").document(uid), upd);

        // 2) myc_equipped true/false por categoría
        for (String tipo : TIPO_TO_USER_FIELD.keySet()) {
            String chosenId = selectedByTipo.get(tipo);
            for (Cosmetic c : all) {
                if (!tipo.equals(c.tipo)) continue;
                boolean shouldBeTrue = (chosenId != null) && chosenId.equals(c.id);
                Map<String, Object> m = new HashMap<>();
                m.put("myc_equipped", shouldBeTrue);
                batch.set(
                        db.collection("users").document(uid)
                                .collection("my_cosmetics").document(c.id),
                        m, SetOptions.merge()
                );
            }
        }

        batch.commit()
                .addOnSuccessListener(v -> finish())
                .addOnFailureListener(e -> finish());
    }

    // ---------- helpers de carga y modelo ----------
    private void addReq(List<LayerReq> out, @Nullable String cosId) {
        if (cosId == null) return;
        for (Cosmetic c : all) {
            if (cosId.equals(c.id)) {
                int[] off = OFFSETS.getOrDefault(c.id, new int[]{0, 0});
                out.add(new LayerReq(c.asset, c.assetType, off[0], off[1]));
                break;
            }
        }
    }

    private void loadAllDrawables(List<LayerReq> reqs, OnLayersReady cb) {
        final Layer[] slots = new Layer[reqs.size()];
        final int total = reqs.size();
        if (total == 0) {
            cb.onReady(new ArrayList<>());
            return;
        }

        final int[] count = {0};
        for (int i = 0; i < reqs.size(); i++) {
            final int idx = i;
            final LayerReq r = reqs.get(i);

            loadDrawable(r.asset, r.assetType, new CustomTarget<Drawable>() {
                @Override
                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                    slots[idx] = new Layer(resource, r.offX, r.offY);
                    if (++count[0] == total) {
                        List<Layer> out = new ArrayList<>();
                        for (Layer l : slots) if (l != null) out.add(l);
                        cb.onReady(out);
                    }
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                    if (++count[0] == total) {
                        List<Layer> out = new ArrayList<>();
                        for (Layer l : slots) if (l != null) out.add(l);
                        cb.onReady(out);
                    }
                }

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                    if (++count[0] == total) {
                        List<Layer> out = new ArrayList<>();
                        for (Layer l : slots) if (l != null) out.add(l);
                        cb.onReady(out);
                    }
                }
            });
        }
    }

    private void loadDrawable(@Nullable String asset, @Nullable String assetType,
                              @NonNull CustomTarget<Drawable> target) {
        if (asset == null) {
            target.onResourceReady(new EmptyDrawable(), null);
            return;
        }

        if ("cloudinary".equalsIgnoreCase(assetType)) {
            String cloud = getString(R.string.cloudinary_cloud_name);
            String url = asset.startsWith("http")
                    ? asset
                    : "https://res.cloudinary.com/" + cloud + "/image/upload/" + asset + (asset.contains(".") ? "" : ".png");
            Glide.with(this).asDrawable().load(url).into(target);
            return;
        }

        if (asset.startsWith("http://") || asset.startsWith("https://")) {
            Glide.with(this).asDrawable().load(asset).into(target);
            return;
        }

        int resId = getResources().getIdentifier(asset, "drawable", getPackageName());
        Drawable d = (resId != 0) ? ContextCompat.getDrawable(this, resId) : null;
        target.onResourceReady(d != null ? d : new EmptyDrawable(), null);
    }

    private interface OnLayersReady {
        void onReady(List<Layer> layers);
    }

    private static class LayerReq {
        final String asset;
        final String assetType;
        final int offX;
        final int offY;

        LayerReq(String a, String t, int x, int y) {
            asset = a;
            assetType = t;
            offX = x;
            offY = y;
        }
    }

    private static class Layer {
        final Drawable drawable;
        final int offX;
        final int offY;

        Layer(Drawable d, int x, int y) {
            drawable = d;
            offX = x;
            offY = y;
        }
    }

    // Drawable transparente de 32x48
    private static class EmptyDrawable extends Drawable {
        @Override
        public void draw(@NonNull Canvas canvas) {
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 32;
        }

        @Override
        public int getIntrinsicHeight() {
            return 48;
        }
    }

    @Nullable
    private String asString(Object o) {
        return (o instanceof String && !((String) o).isEmpty()) ? (String) o : null;
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics()));
    }

    // ====== MODELOS + ADAPTER ======
    private static class Cosmetic {
        final String id;
        final String tipo;
        final String asset;
        final String assetType;
        final boolean equipped;

        Cosmetic(String id, String tipo, String asset, String assetType, boolean eq) {
            this.id = id;
            this.tipo = tipo;
            this.asset = asset;
            this.assetType = assetType;
            this.equipped = eq;
        }

        static Cosmetic none(String tipo) {
            return new Cosmetic(null, tipo, null, null, false);
        }
    }

    private class CosAdapter extends RecyclerView.Adapter<CosAdapter.Holder> {
        interface OnPick {
            void onPick(Cosmetic c);
        }

        final List<Cosmetic> data;
        final String tipo;
        final String selectedId;
        final OnPick onPick;

        CosAdapter(List<Cosmetic> data, String tipo, String selectedId, OnPick onPick) {
            this.data = data;
            this.tipo = tipo;
            this.selectedId = selectedId;
            this.onPick = onPick;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
            ImageView iv = new ImageView(p.getContext());
            int pad = dp(p.getContext(), 6);
            iv.setPadding(pad, pad, pad, pad);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(p.getContext(), 78)));
            return new Holder(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int i) {
            Context ctx = h.itemView.getContext();
            Cosmetic c = data.get(i);

            if (c.id == null) {
                h.iv.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            } else if ("cloudinary".equalsIgnoreCase(c.assetType)) {
                String cloud = getString(R.string.cloudinary_cloud_name);
                String url = (c.asset != null && (c.asset.startsWith("http://") || c.asset.startsWith("https://")))
                        ? c.asset
                        : "https://res.cloudinary.com/" + cloud + "/image/upload/" + c.asset + (c.asset != null && c.asset.contains(".") ? "" : ".png");
                Glide.with(ctx).load(url).into(h.iv);
            } else if (c.asset != null && (c.asset.startsWith("http://") || c.asset.startsWith("https://"))) {
                Glide.with(ctx).load(c.asset).into(h.iv);
            } else {
                int res = ctx.getResources().getIdentifier(c.asset, "drawable", ctx.getPackageName());
                if (res != 0) h.iv.setImageResource(res);
                else h.iv.setImageDrawable(null);
            }

            boolean sel = (selectedId == null && c.id == null) ||
                    (selectedId != null && selectedId.equals(c.id));
            h.iv.setBackgroundColor(sel ? 0x332196F3 : 0x00000000);
            h.itemView.setOnClickListener(v -> onPick.onPick(c));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        int dp(Context c, int d) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, d,
                    c.getResources().getDisplayMetrics()));
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView iv;

            Holder(@NonNull View itemView) {
                super(itemView);
                iv = (ImageView) itemView;
            }
        }
    }
}
