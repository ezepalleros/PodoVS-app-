package com.example.podovs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

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

    // Inventario cacheado
    private final List<Cosmetic> all = new ArrayList<>();
    // Selección actual por tipo (piel/cabeza/remera/pantalon/zapatillas)
    private final Map<String, String> selectedByTipo = new HashMap<>();
    // Tipo visible en la grilla
    private String currentTipo = "piel";

    // Tipos soportados -> key en users.usu_equipped
    private static final Map<String, String> TIPO_TO_USER_FIELD = new HashMap<>();
    static {
        TIPO_TO_USER_FIELD.put("piel", "usu_equipped.usu_piel");
        TIPO_TO_USER_FIELD.put("cabeza", "usu_equipped.usu_cabeza");
        TIPO_TO_USER_FIELD.put("remera", "usu_equipped.usu_remera");
        TIPO_TO_USER_FIELD.put("pantalon", "usu_equipped.usu_pantalon");
        TIPO_TO_USER_FIELD.put("zapatillas", "usu_equipped.usu_zapas");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar);

        ivPreview = findViewById(R.id.ivPreview);
        rv        = findViewById(R.id.rvItems);
        btnCatPiel     = findViewById(R.id.btnCatPiel);
        btnCatCabeza   = findViewById(R.id.btnCatCabeza);
        btnCatRemera   = findViewById(R.id.btnCatRemera);
        btnCatPantalon = findViewById(R.id.btnCatPantalon);
        btnCatZapas    = findViewById(R.id.btnCatZapas);
        btnConfirm     = findViewById(R.id.btnConfirm);
        btnCancel      = findViewById(R.id.btnCancel);

        rv.setLayoutManager(new GridLayoutManager(this, 4));

        db  = FirebaseFirestore.getInstance();
        uid = getSharedPreferences("session", MODE_PRIVATE).getString("uid", null);
        if (uid == null) { finish(); return; }

        // Carga user + inventario, inicializa selección y arma preview
        loadData();

        // Tabs
        btnCatPiel.setOnClickListener(v -> { currentTipo = "piel"; refreshGrid(); });
        btnCatCabeza.setOnClickListener(v -> { currentTipo = "cabeza"; refreshGrid(); });
        btnCatRemera.setOnClickListener(v -> { currentTipo = "remera"; refreshGrid(); });
        btnCatPantalon.setOnClickListener(v -> { currentTipo = "pantalon"; refreshGrid(); });
        btnCatZapas.setOnClickListener(v -> { currentTipo = "zapatillas"; refreshGrid(); });

        btnConfirm.setOnClickListener(v -> saveSelection());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void loadData() {
        // 1) User para saber equipados actuales
        Tasks.whenAllSuccess(
                db.collection("users").document(uid).get(),
                db.collection("users").document(uid).collection("my_cosmetics").get()
        ).addOnSuccessListener(list -> {
            DocumentSnapshot user = (DocumentSnapshot) list.get(0);
            QuerySnapshot inv     = (QuerySnapshot)    list.get(1);

            // set selección actual desde el user
            selectedByTipo.put("piel",        asString(user.get("usu_equipped.usu_piel")));
            selectedByTipo.put("cabeza",      asString(user.get("usu_equipped.usu_cabeza")));
            selectedByTipo.put("remera",      asString(user.get("usu_equipped.usu_remera")));
            selectedByTipo.put("pantalon",    asString(user.get("usu_equipped.usu_pantalon")));
            selectedByTipo.put("zapatillas",  asString(user.get("usu_equipped.usu_zapas")));

            // Inventario
            all.clear();
            for (DocumentSnapshot d : inv.getDocuments()) {
                String id     = d.getId();
                String tipo   = asString(d.get("myc_cache.cos_tipo"));
                String asset  = asString(d.get("myc_cache.cos_asset"));
                boolean eq    = Boolean.TRUE.equals(d.getBoolean("myc_equipped"));
                all.add(new Cosmetic(id, tipo, asset, eq));
            }

            refreshGrid();
            renderPreview();
        }).addOnFailureListener(e -> finish());
    }

    private void refreshGrid() {
        // Construye lista filtrada por tipo + item "Ninguno" (id null)
        List<Cosmetic> filtered = new ArrayList<>();
        // "Ninguno" solo para categorías que no son piel
        if (!"piel".equals(currentTipo)) {
            filtered.add(Cosmetic.none(currentTipo));
        }
        for (Cosmetic c : all) {
            if (currentTipo.equalsIgnoreCase(c.tipo)) filtered.add(c);
        }
        rv.setAdapter(new CosAdapter(filtered, currentTipo, selectedByTipo.get(currentTipo),
                (clicked) -> {
                    // actualizar selección y preview
                    selectedByTipo.put(currentTipo, clicked.id); // puede ser null (Ninguno)
                    renderPreview();
                    rv.getAdapter().notifyDataSetChanged();
                }));
    }

    private void renderPreview() {
        // assets por tipo a partir de la selección actual
        String pielAsset  = assetForSelected("piel");
        String panAsset   = assetForSelected("pantalon");
        String remAsset   = assetForSelected("remera");
        String zapAsset   = assetForSelected("zapatillas");
        String cabAsset   = assetForSelected("cabeza");

        ArrayList<Drawable> layers = new ArrayList<>();
        addLayerIfExists(layers, pielAsset);
        addLayerIfExists(layers, panAsset);
        addLayerIfExists(layers, remAsset);
        addLayerIfExists(layers, zapAsset);
        addLayerIfExists(layers, cabAsset);

        if (layers.isEmpty()) { ivPreview.setImageDrawable(null); return; }

        // tamaño base del sprite
        int bw = Math.max(1, layers.get(0).getIntrinsicWidth());
        int bh = Math.max(1, layers.get(0).getIntrinsicHeight());
        if (bw <= 1 || bh <= 1) { bw = 32; bh = 48; }

        Bitmap base = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(base);
        for (Drawable d : layers) { d.setBounds(0,0,bw,bh); d.draw(c); }

        // escalar "pixel perfect" a un alto objetivo
        int targetH = dpToPx(180);       // tamaño cómodo para previsualización
        int factor  = Math.max(1, targetH / bh);
        Bitmap scaled = Bitmap.createScaledBitmap(base, bw*factor, bh*factor, /*filter=*/false);

        ivPreview.setAdjustViewBounds(true);
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ivPreview.setImageBitmap(scaled);
    }

    private void saveSelection() {
        WriteBatch batch = db.batch();

        // 1) actualizar campos del usuario
        Map<String, Object> upd = new HashMap<>();
        for (Map.Entry<String,String> e : selectedByTipo.entrySet()) {
            String tipo = e.getKey();
            String field = TIPO_TO_USER_FIELD.get(tipo);
            if (field != null) upd.put(field, e.getValue()); // puede ser null
        }
        batch.update(db.collection("users").document(uid), upd);

        // 2) setear myc_equipped true/false por categoría
        for (String tipo : TIPO_TO_USER_FIELD.keySet()) {
            String chosenId = selectedByTipo.get(tipo); // puede ser null
            for (Cosmetic c : all) {
                if (!tipo.equals(c.tipo)) continue;
                boolean shouldBeTrue = (chosenId != null) && chosenId.equals(c.id);
                Map<String, Object> m = new HashMap<>();
                m.put("myc_equipped", shouldBeTrue);
                batch.set(db.collection("users").document(uid)
                                .collection("my_cosmetics").document(c.id),
                        m, com.google.firebase.firestore.SetOptions.merge());
            }
        }

        batch.commit()
                .addOnSuccessListener(v -> finish())
                .addOnFailureListener(e -> finish());
    }



    // ------- util ----------
    @Nullable private String asString(Object o) {
        return (o instanceof String && !((String)o).isEmpty()) ? (String) o : null;
    }
    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics()));
    }
    private void addLayerIfExists(List<Drawable> layers, @Nullable String asset) {
        if (asset == null) return;
        int resId = getResources().getIdentifier(asset, "drawable", getPackageName());
        if (resId == 0) return;
        Drawable d = ContextCompat.getDrawable(this, resId);
        if (d != null) layers.add(d);
    }
    @Nullable private String assetForSelected(String tipo) {
        String sel = selectedByTipo.get(tipo);
        if (sel == null) return null;
        for (Cosmetic c : all) if (sel.equals(c.id)) return c.asset;
        return null;
    }

    // ------- modelos + adapter ----------
    private static class Cosmetic {
        final String id;      // doc id (p.ej., cos_id_2) o null si "ninguno"
        final String tipo;    // piel/cabeza/remera/pantalon/zapatillas
        final String asset;   // nombre de drawable
        final boolean equipped;
        Cosmetic(String id, String tipo, String asset, boolean eq) {
            this.id=id; this.tipo=tipo; this.asset=asset; this.equipped=eq;
        }
        static Cosmetic none(String tipo) { return new Cosmetic(null, tipo, null, false); }
    }

    private static class CosAdapter extends RecyclerView.Adapter<CosAdapter.Holder> {
        interface OnPick { void onPick(Cosmetic c); }

        final List<Cosmetic> data;
        final String tipo;
        final String selectedId; // puede ser null
        final OnPick onPick;

        CosAdapter(List<Cosmetic> data, String tipo, String selectedId, OnPick onPick) {
            this.data = data; this.tipo = tipo; this.selectedId = selectedId; this.onPick = onPick;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
            ImageView iv = new ImageView(p.getContext());
            int pad = dp(p.getContext(), 6);
            iv.setPadding(pad,pad,pad,pad);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(p.getContext(), 78)));
            return new Holder(iv);
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int i) {
            Context ctx = h.itemView.getContext();
            Cosmetic c = data.get(i);
            if (c.id == null) {
                // "Ninguno" -> ícono vacío
                h.iv.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            } else {
                int res = ctx.getResources().getIdentifier(c.asset, "drawable", ctx.getPackageName());
                if (res != 0) h.iv.setImageResource(res); else h.iv.setImageDrawable(null);
            }
            // borde de selección
            boolean sel = (selectedId == null && c.id == null) ||
                    (selectedId != null && selectedId.equals(c.id));
            h.iv.setBackgroundColor(sel ? 0x332196F3 : 0x00000000);

            h.itemView.setOnClickListener(v -> onPick.onPick(c));
        }

        @Override public int getItemCount() { return data.size(); }

        static int dp(Context c, int d) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, d,
                    c.getResources().getDisplayMetrics()));
        }
        static class Holder extends RecyclerView.ViewHolder {
            ImageView iv; Holder(@NonNull View itemView) { super(itemView); iv = (ImageView) itemView; }
        }
    }
}
