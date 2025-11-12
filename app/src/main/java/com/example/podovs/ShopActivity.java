package com.example.podovs;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tienda en una sola Activity + activity_shop.xml
 * - Rotación REAL cada 3 horas (estable dentro del mismo tramo)
 * - Selección determinística por usuario (seed = slot^uidHash)
 * - Cuenta regresiva visible junto a "Ofertas"
 * - Fondos animados "lluvia" por sección
 */
public class ShopActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirestoreRepo repo;
    private String uid;

    private TextView tvCoins;
    private LinearLayout rowDailyShop;
    private LinearLayout rowChests;
    private GridLayout gridEventos;

    // Header ofertas
    private TextView tvRotationTitle;
    private TextView tvRotationTimer;

    // Contenedores de cada sección para la lluvia
    private ViewGroup sectionRotation;
    private ViewGroup sectionChests;
    private ViewGroup sectionEvents;

    // ======= Cofres =======
    private static final int PRICE_BRONZE = 10000;   // premios 20k
    private static final int PRICE_SILVER = 25000;   // premios 50k
    private static final int PRICE_GOLD   = 50000;   // premios 100k

    private static final int POOL_BRONZE  = 20000;
    private static final int POOL_SILVER  = 50000;
    private static final int POOL_GOLD    = 100000;

    // ======= Paleta directa (sin R.color) =======
    private static final int COL_BG_CARD     = Color.parseColor("#F3F4F6"); // gris 050
    private static final int COL_TEXT_DARK   = Color.parseColor("#111827"); // gris 900
    private static final int COL_TEXT_MEDIUM = Color.parseColor("#4B5563"); // gris 700
    private static final int COL_BTN_LILAC   = Color.parseColor("#8B5CF6"); // lilac 500
    private static final int COL_BTN_DISABLED= Color.parseColor("#9CA3AF"); // gris 400

    // ======= Rotación 3h (ticker) =======
    private long lastSeed = -1L;
    private Handler handler;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateCountdown();
            long s = computedSeed();        // seed actual (3h ^ uid)
            if (s != lastSeed) {
                lastSeed = s;
                loadDailyShop();            // cambia automáticamente sin salir de la activity
            }
            handler.postDelayed(this, 1000);
        }
    };

    // ======= Prefs (persistir la selección del tramo actual) =======
    private static final String PREF_SHOP  = "shop_rotation_local";
    private static final String KEY_SEED   = "rot_seed";
    private static final String KEY_IDS    = "rot_ids"; // csv 3 ids

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shop);

        db   = FirebaseFirestore.getInstance();
        repo = new FirestoreRepo();

        SharedPreferences sp = getSharedPreferences("session", MODE_PRIVATE);
        uid = sp.getString("uid", null);
        if (uid == null) { finish(); return; }

        tvCoins         = findViewById(R.id.tvCoinsShop);
        rowDailyShop    = findViewById(R.id.rowDailyShop);
        rowChests       = findViewById(R.id.rowChests);
        gridEventos     = findViewById(R.id.gridEventos);
        tvRotationTitle = findViewById(R.id.tvRotationTitle);
        tvRotationTimer = findViewById(R.id.tvRotationTimer);

        // Secciones para el fondo animado
        sectionRotation = findViewById(R.id.sectionRotation);
        sectionChests   = findViewById(R.id.sectionChests);
        sectionEvents   = findViewById(R.id.sectionEvents);

        // Lluvia (verde, amarillo, azul)
        attachRain(sectionRotation, R.drawable.icon_bag,   0xFFE6F7EC);
        attachRain(sectionChests,   R.drawable.icon_chest, 0xFFFFF4CC);
        attachRain(sectionEvents,   R.drawable.icon_event, 0xFFE5F0FF);

        // Saldo en vivo
        repo.listenUser(uid, (snap, err) -> {
            if (err != null || snap == null || !snap.exists()) return;
            Long saldo = snap.getLong("usu_saldo");
            if (saldo != null) tvCoins.setText(String.format(Locale.getDefault(), "%,d", saldo));
        });

        // Cargar secciones
        lastSeed = computedSeed();
        loadDailyShop();
        setupChests();
        loadEventos();

        // Bottom bar
        ImageButton btnHome = findViewById(R.id.btnHome);
        ImageButton btnShop = findViewById(R.id.btnShop);
        ImageButton btnVs   = findViewById(R.id.btnVs);
        ImageButton btnEvt  = findViewById(R.id.btnEvents);
        ImageButton btnLb   = findViewById(R.id.btnLeaderboards);

        btnHome.setOnClickListener(v -> { startActivity(new Intent(this, MainActivity.class)); finish(); });
        btnShop.setOnClickListener(v -> Toast.makeText(this, "Estás en la tienda", Toast.LENGTH_SHORT).show());
        btnVs.setOnClickListener(v -> safeNavigate("com.example.podovs.VersusActivity", "VS próximamente"));
        btnEvt.setOnClickListener(v -> safeNavigate("com.example.podovs.EventsActivity", "Eventos próximamente"));
        btnLb.setOnClickListener(v -> safeNavigate("com.example.podovs.LeaderboardsActivity", "Tablas próximamente"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (handler == null) handler = new Handler();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) handler.removeCallbacks(ticker);
    }

    // ==================== Rotación ====================
    private static final long SLOT_MS = 3L * 60L * 60L * 1000L;

    private long rotationSeed() { return System.currentTimeMillis() / SLOT_MS; }

    /** Semilla por usuario (local, distinta para cada uid) */
    private long computedSeed() {
        long slot = rotationSeed();
        long userHash = uid == null ? 0 : uid.hashCode();
        return slot ^ userHash;
    }

    /** Actualiza el contador hh:mm:ss y el texto de cabecera */
    private void updateCountdown() {
        long now = System.currentTimeMillis();
        long next = ((now / SLOT_MS) + 1) * SLOT_MS;
        long remain = Math.max(0, next - now);

        long h = remain / 3_600_000;
        long m = (remain % 3_600_000) / 60_000;
        long s = (remain % 60_000) / 1000;

        if (tvRotationTitle != null) tvRotationTitle.setText("Ofertas (rota cada 3 hs)");
        if (tvRotationTimer != null) tvRotationTimer.setText(String.format(Locale.getDefault(), "%01d:%02d:%02d", h, m, s));
    }

    private void safeNavigate(String className, String msgIfMissing) {
        try { startActivity(new Intent(this, Class.forName(className))); }
        catch (ClassNotFoundException e) { Toast.makeText(this, msgIfMissing, Toast.LENGTH_SHORT).show(); }
    }

    // ==================== Ofertas (3) ====================
    private void loadDailyShop() {
        // 1) Intentar leer ids persistidos para el seed actual
        SharedPreferences p = getSharedPreferences(PREF_SHOP + "_" + uid, MODE_PRIVATE);
        long storedSeed = p.getLong(KEY_SEED, -1);
        String csv = p.getString(KEY_IDS, null);

        if (storedSeed == lastSeed && !TextUtils.isEmpty(csv)) {
            String[] ids = csv.split(",");
            fetchOffersByIds(ids); // lectura directa por id (sin whereIn)
            return;
        }

        // 2) Generar determinísticamente la selección (sin ORDER BY en Firestore para evitar índice)
        db.collection("cosmetics")
                .whereEqualTo("cos_tienda", true)
                .whereEqualTo("cos_activo", true)
                .get()
                .addOnSuccessListener(qs -> {
                    List<DocumentSnapshot> all = new ArrayList<>(qs.getDocuments());
                    if (all.isEmpty()) return;

                    // Orden estable local (por id) para que el seed sea reproducible
                    Collections.sort(all, Comparator.comparing(DocumentSnapshot::getId));

                    SecureRandom rnd = new SecureRandom(longToBytes(lastSeed));
                    Set<Integer> picked = new HashSet<>();
                    ArrayList<String> chosenIds = new ArrayList<>();

                    while (picked.size() < Math.min(3, all.size())) {
                        int idx = rnd.nextInt(all.size());
                        if (picked.add(idx)) chosenIds.add(all.get(idx).getId());
                    }

                    // Persistir para NO cambiar hasta el próximo tramo
                    p.edit()
                            .putLong(KEY_SEED, lastSeed)
                            .putString(KEY_IDS, TextUtils.join(",", chosenIds))
                            .apply();

                    fetchOffersByIds(chosenIds.toArray(new String[0]));
                })
                .addOnFailureListener(e -> {
                    // Si falla la consulta, limpiar fila para no dejarla vacía permanentemente
                    rowDailyShop.removeAllViews();
                });
    }

    /** Carga EXACTAMENTE los docs indicados, manteniendo el orden, y asegura 3 cards. */
    private void fetchOffersByIds(String[] ids) {
        rowDailyShop.removeAllViews();

        // limpiar ids vacíos y limitar a 3
        ArrayList<String> clean = new ArrayList<>();
        for (String s : ids == null ? new String[0] : ids) {
            if (!TextUtils.isEmpty(s)) clean.add(s.trim());
        }
        if (clean.isEmpty()) return;
        if (clean.size() > 3) clean = new ArrayList<>(clean.subList(0, 3));

        // Lee por id individual y respeta el orden original
        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (String id : clean) {
            reads.add(db.collection("cosmetics").document(id).get());
        }
        final ArrayList<String> finalClean = new ArrayList<>(clean);
        Tasks.whenAllSuccess(reads).addOnSuccessListener(list -> {
            for (int i = 0; i < finalClean.size(); i++) {
                DocumentSnapshot d = (DocumentSnapshot) list.get(i);
                if (d != null && d.exists()) {
                    rowDailyShop.addView(makeCosmeticCard(d, true));
                }
            }
            // si por algún motivo no llegaron 3, rellena con cualquiera activa de tienda
            if (rowDailyShop.getChildCount() < 3) {
                final int faltanInicial = 3 - rowDailyShop.getChildCount();
                AtomicInteger faltan = new AtomicInteger(faltanInicial);
                db.collection("cosmetics")
                        .whereEqualTo("cos_tienda", true)
                        .whereEqualTo("cos_activo", true)
                        .get()
                        .addOnSuccessListener(qs -> {
                            for (DocumentSnapshot d : qs) {
                                if (finalClean.contains(d.getId())) continue; // evitar duplicados
                                rowDailyShop.addView(makeCosmeticCard(d, true));
                                if (faltan.decrementAndGet() <= 0) break;
                            }
                        });
            }
        }).addOnFailureListener(e -> {
            // Si falla, limpia prefs para reintentar en el próximo tick
            SharedPreferences p = getSharedPreferences(PREF_SHOP + "_" + uid, MODE_PRIVATE);
            p.edit().clear().apply();
        });
    }

    private byte[] longToBytes(long l) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) { b[i] = (byte) (l & 0xFF); l >>= 8; }
        return b;
    }

    // ==================== Cofres (3) ====================
    private void setupChests() {
        rowChests.removeAllViews();
        rowChests.addView(makeChestCard(PRICE_BRONZE, POOL_BRONZE));
        rowChests.addView(makeChestCard(PRICE_SILVER, POOL_SILVER));
        rowChests.addView(makeChestCard(PRICE_GOLD,   POOL_GOLD));
    }

    private View makeChestCard(int price, int poolPrice) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        root.setLayoutParams(lp);

        CardView card = new CardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(96));
        card.setLayoutParams(lpCard);
        card.setCardBackgroundColor(COL_BG_CARD);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setAdjustViewBounds(true);
        iv.setImageResource(R.drawable.pixel_chest);
        card.addView(iv);

        TextView tvPrice = new TextView(this);
        tvPrice.setText(String.format(Locale.getDefault(), "%,d", price));
        tvPrice.setTypeface(Typeface.DEFAULT_BOLD);
        tvPrice.setTextColor(COL_TEXT_DARK);
        tvPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvPrice.setPadding(0, dp(6), 0, 0);

        TextView tvSub = new TextView(this);
        tvSub.setText("Premios de " + String.format(Locale.getDefault(), "%,d", poolPrice));
        tvSub.setTextColor(COL_TEXT_MEDIUM);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        Button btn = new Button(this);
        btn.setText("Abrir");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackgroundTintList(ColorStateList.valueOf(COL_BTN_LILAC));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        lpBtn.setMargins(0, dp(8), 0, 0);
        btn.setLayoutParams(lpBtn);
        btn.setOnClickListener(v -> openChest(price, poolPrice));

        root.addView(card);
        root.addView(tvPrice);
        root.addView(tvSub);
        root.addView(btn);
        return root;
    }

    private void openChest(int chestPrice, int prizePrice) {
        // 1) Verificar saldo y descontar PRIMERO
        repo.getUser(uid, user -> {
            long saldo = user.getLong("usu_saldo") == null ? 0L : user.getLong("usu_saldo");
            if (saldo < chestPrice) { Toast.makeText(this, "Saldo insuficiente.", Toast.LENGTH_SHORT).show(); return; }

            repo.addSaldo(uid, -chestPrice, v1 -> {
                // 2) Elegir premio del pool
                db.collection("cosmetics")
                        .whereEqualTo("cos_activo", true)
                        .whereEqualTo("cos_precio", prizePrice)
                        .get()
                        .addOnSuccessListener(qs -> {
                            List<DocumentSnapshot> pool = qs.getDocuments();
                            if (pool.isEmpty()) {
                                repo.addSaldo(uid, chestPrice, vv -> {}, ee -> {});
                                Toast.makeText(this, "Sin premios disponibles.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            SecureRandom rnd = new SecureRandom();
                            DocumentSnapshot prize = pool.get(rnd.nextInt(pool.size()));
                            String cosId = prize.getId();

                            // 3) ¿Repetido?
                            db.collection("users").document(uid)
                                    .collection("my_cosmetics").document(cosId)
                                    .get().addOnSuccessListener(s -> {
                                        boolean already = s.exists();
                                        if (already) {
                                            int refund = chestPrice / 2;
                                            repo.addSaldo(uid, refund,
                                                    v2 -> {
                                                        showNewItemDialog(false, prize);
                                                        Toast.makeText(this, "Repetido. Devolución de " + refund, Toast.LENGTH_SHORT).show();
                                                    },
                                                    e2 -> Toast.makeText(this, "Error devolución.", Toast.LENGTH_SHORT).show());
                                        } else {
                                            // 4) Entregar
                                            repo.addToInventory(uid, cosId, false,
                                                    v2 -> {
                                                        showNewItemDialog(true, prize);
                                                        Toast.makeText(this, "¡Nuevo cosmético!", Toast.LENGTH_SHORT).show();
                                                    },
                                                    e2 -> {
                                                        repo.addSaldo(uid, chestPrice, v3 -> {}, e3 -> {});
                                                        Toast.makeText(this, "Error al entregar.", Toast.LENGTH_LONG).show();
                                                    });
                                        }
                                    });
                        })
                        .addOnFailureListener(e -> {
                            repo.addSaldo(uid, chestPrice, vv -> {}, ee -> {});
                            Toast.makeText(this, "Error cargando premios.", Toast.LENGTH_SHORT).show();
                        });
            }, e -> Toast.makeText(this, "Error al descontar saldo.", Toast.LENGTH_SHORT).show());

        }, e -> Toast.makeText(this, "Error usuario.", Toast.LENGTH_SHORT).show());
    }

    // ==================== Eventos ====================
    private void loadEventos() {
        db.collection("cosmetics")
                .whereEqualTo("cos_evento", true)
                .whereEqualTo("cos_activo", true)
                .get()
                .addOnSuccessListener(qs -> {
                    gridEventos.removeAllViews();
                    gridEventos.setColumnCount(2);

                    List<Task<DocumentSnapshot>> reads = new ArrayList<>();
                    for (DocumentSnapshot d : qs) {
                        reads.add(db.collection("users").document(uid)
                                .collection("my_cosmetics").document(d.getId()).get());
                    }
                    Tasks.whenAllSuccess(reads).addOnSuccessListener(res -> {
                        ArrayList<String> owned = new ArrayList<>();
                        for (Object o : res) {
                            DocumentSnapshot s = (DocumentSnapshot) o;
                            if (s.exists()) owned.add(s.getId());
                        }
                        for (DocumentSnapshot d : qs) {
                            gridEventos.addView(makeCosmeticCardEvento(d, owned.contains(d.getId())));
                        }
                    });
                });
    }

    // ==================== Cards ====================
    private View makeCosmeticCard(DocumentSnapshot d, boolean big) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        root.setLayoutParams(lp);

        CardView imgCard = new CardView(this);
        imgCard.setRadius(dp(16));
        imgCard.setCardBackgroundColor(COL_BG_CARD);
        imgCard.setCardElevation(dp(2));
        LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, big ? dp(96) : dp(92));
        imgCard.setLayoutParams(lpImg);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setAdjustViewBounds(true);
        imgCard.addView(iv);

        TextView name = new TextView(this);
        name.setTextColor(COL_TEXT_DARK);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dp(6), 0, 0);

        TextView price = new TextView(this);
        price.setTextColor(COL_TEXT_MEDIUM);
        price.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        Button btn = new Button(this);
        btn.setText("Comprar");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackgroundTintList(ColorStateList.valueOf(COL_BTN_LILAC));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        lpBtn.setMargins(0, dp(8), 0, 0);
        btn.setLayoutParams(lpBtn);

        String cosId   = d.getId();
        String cosName = d.getString("cos_nombre") == null ? "" : d.getString("cos_nombre");
        Long   cosPrice= d.getLong("cos_precio");
        String asset   = d.getString("cos_asset");
        String aType   = d.getString("cos_assetType");
        String tipo    = d.getString("cos_tipo");

        name.setText(cosName);
        price.setText(cosPrice == null ? "-" : String.format(Locale.getDefault(), "%,d", cosPrice));

        if ("cloudinary".equalsIgnoreCase(aType)) Glide.with(this).load(asset).error(android.R.drawable.ic_menu_report_image).into(iv);
        else {
            int resId = (!TextUtils.isEmpty(asset)) ? getResources().getIdentifier(asset, "drawable", getPackageName()) : 0;
            iv.setImageResource(resId != 0 ? resId : android.R.drawable.ic_menu_report_image);
        }

        db.collection("users").document(uid).collection("my_cosmetics").document(cosId)
                .get().addOnSuccessListener(s -> {
                    if (s.exists()) {
                        btn.setEnabled(false);
                        btn.setText("Ya lo tenés");
                        btn.setBackgroundTintList(ColorStateList.valueOf(COL_BTN_DISABLED));
                    }
                });

        btn.setOnClickListener(v -> tryBuy(cosId, tipo, cosPrice == null ? 0L : cosPrice));

        root.addView(imgCard);
        root.addView(name);
        root.addView(price);
        root.addView(btn);
        return root;
    }

    private View makeCosmeticCardEvento(DocumentSnapshot d, boolean owned) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GridLayout.LayoutParams gl = new GridLayout.LayoutParams();
        gl.width = 0;
        gl.height = GridLayout.LayoutParams.WRAP_CONTENT;
        gl.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gl.setMargins(dp(6), dp(6), dp(6), dp(6));
        root.setLayoutParams(gl);

        CardView imgCard = new CardView(this);
        imgCard.setRadius(dp(16));
        imgCard.setCardBackgroundColor(COL_BG_CARD);
        imgCard.setCardElevation(dp(2));
        LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(92));
        imgCard.setLayoutParams(lpImg);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setAdjustViewBounds(true);
        imgCard.addView(iv);

        TextView name = new TextView(this);
        name.setTextColor(COL_TEXT_DARK);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setPadding(0, dp(6), 0, 0);

        TextView price = new TextView(this);
        price.setTextColor(COL_TEXT_MEDIUM);
        price.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        Button btn = new Button(this);
        btn.setText(owned ? "Ya lo tenés" : "Comprar");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackgroundTintList(ColorStateList.valueOf(owned ? COL_BTN_DISABLED : COL_BTN_LILAC));
        btn.setEnabled(!owned);
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        lpBtn.setMargins(0, dp(8), 0, 0);
        btn.setLayoutParams(lpBtn);

        String cosId   = d.getId();
        String cosName = d.getString("cos_nombre") == null ? "" : d.getString("cos_nombre");
        Long   cosPrice= d.getLong("cos_precio");
        String asset   = d.getString("cos_asset");
        String aType   = d.getString("cos_assetType");
        String tipo    = d.getString("cos_tipo");

        name.setText(cosName);
        price.setText(cosPrice == null ? "-" : String.format(Locale.getDefault(), "%,d", cosPrice));

        if ("cloudinary".equalsIgnoreCase(aType)) Glide.with(this).load(asset).error(android.R.drawable.ic_menu_report_image).into(iv);
        else {
            int resId = (!TextUtils.isEmpty(asset)) ? getResources().getIdentifier(asset, "drawable", getPackageName()) : 0;
            iv.setImageResource(resId != 0 ? resId : android.R.drawable.ic_menu_report_image);
        }

        if (!owned) btn.setOnClickListener(v -> tryBuy(cosId, tipo, cosPrice == null ? 0L : cosPrice));

        root.addView(imgCard);
        root.addView(name);
        root.addView(price);
        root.addView(btn);
        return root;
    }

    private void tryBuy(String cosId, String tipo, long price) {
        // Gratis permitido
        if (price < 0) { Toast.makeText(this, "Precio inválido.", Toast.LENGTH_SHORT).show(); return; }

        db.collection("users").document(uid)
                .collection("my_cosmetics").document(cosId)
                .get().addOnSuccessListener(s -> {
                    if (s.exists()) { Toast.makeText(this, "Ya lo tenés.", Toast.LENGTH_SHORT).show(); return; }

                    if (price == 0L) {
                        repo.addToInventory(uid, cosId, false,
                                vv -> Toast.makeText(this, "Reclamado ✔", Toast.LENGTH_SHORT).show(),
                                e -> Toast.makeText(this, "Error al reclamar.", Toast.LENGTH_LONG).show());
                        return;
                    }

                    repo.getUser(uid, u -> {
                        long saldo = u.getLong("usu_saldo") == null ? 0L : u.getLong("usu_saldo");
                        if (saldo < price) { Toast.makeText(this, "Saldo insuficiente.", Toast.LENGTH_SHORT).show(); return; }

                        repo.addSaldo(uid, -price, v -> {
                            repo.addToInventory(uid, cosId, false,
                                    vv -> Toast.makeText(this, "Comprado ✔", Toast.LENGTH_SHORT).show(),
                                    e -> {
                                        repo.addSaldo(uid, price, vvv -> {}, ee -> {});
                                        Toast.makeText(this, "Error compra.", Toast.LENGTH_LONG).show();
                                    });
                        }, e -> Toast.makeText(this, "Error saldo.", Toast.LENGTH_SHORT).show());
                    }, e -> Toast.makeText(this, "Error usuario.", Toast.LENGTH_SHORT).show());
                });
    }

    // ==== BottomSheet de resultado de cofre usando fragment_newitem.xml ====
    private void showNewItemDialog(boolean isNew, DocumentSnapshot prizeDoc) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.fragment_newitem, null, false);

        ImageView iv = view.findViewById(getIdOrFallback(view, "ivNewItem", android.R.id.icon));
        TextView  tvTitle = view.findViewById(getIdOrFallback(view, "tvTitle", android.R.id.title));
        TextView  tvSubtitle = view.findViewById(getIdOrFallback(view, "tvSubtitle", 0));
        Button btn = view.findViewById(getIdOrFallback(view, "btnPrimary", android.R.id.button1));

        if (tvTitle != null) tvTitle.setText(isNew ? "¡Nuevo cosmético!" : "Cosmético repetido");
        if (tvSubtitle != null) tvSubtitle.setText(prizeDoc.getString("cos_nombre"));
        if (btn != null) {
            btn.setText(isNew ? "Reclamar" : "Qué mal");
            btn.setOnClickListener(v -> dialog.dismiss());
        }

        String asset = prizeDoc.getString("cos_asset");
        String aType = prizeDoc.getString("cos_assetType");
        if (iv != null) {
            if ("cloudinary".equalsIgnoreCase(aType)) {
                Glide.with(this).load(asset).error(android.R.drawable.ic_menu_report_image).into(iv);
            } else {
                int resId = (!TextUtils.isEmpty(asset)) ? getResources().getIdentifier(asset, "drawable", getPackageName()) : 0;
                iv.setImageResource(resId != 0 ? resId : android.R.drawable.ic_menu_report_image);
            }
        }

        dialog.setContentView(view);
        dialog.show();
    }

    private int getIdOrFallback(View parent, String name, int fallback) {
        int id = getResources().getIdentifier(name, "id", getPackageName());
        if (id == 0) return fallback;
        return id;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // ------------------ Fondo animado: "lluvia" ------------------
    private void attachRain(ViewGroup container, int iconRes, int pastelBackground) {
        if (container == null) return;
        container.setBackgroundColor(pastelBackground);

        RainView rain = new RainView(this);
        rain.setIcon(iconRes);
        rain.setConfig(18, dp(18), dp(32), 4000);

        // Insertar como PRIMER hijo (detrás del contenido)
        container.addView(rain, 0,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public static class RainView extends View {

        private static class Drop {
            float x, y, size, speed;
        }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bmp;
        private Drop[] drops = new Drop[0];
        private long lastTime = -1L;
        private float minSizePx = 24, maxSizePx = 40;
        private long travelMs = 4000;

        public RainView(android.content.Context ctx) { super(ctx); init(); }
        public RainView(android.content.Context ctx, @Nullable AttributeSet a) { super(ctx, a); init(); }
        public RainView(android.content.Context ctx, @Nullable AttributeSet a, int s) { super(ctx, a, s); init(); }

        private void init() {
            setWillNotDraw(false);
            setClickable(false);
            setFocusable(false);
            p.setAlpha(70);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(1000);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.addUpdateListener(animation -> invalidate());
            va.start();
        }

        public void setIcon(int res) {
            Bitmap raw = BitmapFactory.decodeResource(getResources(), res);
            if (raw != null) bmp = raw;
        }

        public void setConfig(int count, float minSize, float maxSize, long travelMillis) {
            this.minSizePx = minSize;
            this.maxSizePx = maxSize;
            this.travelMs = Math.max(1500, travelMillis);
            drops = new Drop[Math.max(1, count)];
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            for (int i = 0; i < drops.length; i++) drops[i] = randomDrop(w, h, true);
        }

        private Drop randomDrop(int w, int h, boolean anywhereY) {
            Drop d = new Drop();
            d.size = (float) (minSizePx + Math.random() * (maxSizePx - minSizePx));
            d.x = (float) (Math.random() * Math.max(1, (w - d.size)));
            d.y = anywhereY ? (float) (Math.random() * h) : -d.size;
            d.speed = (float) (h + d.size) / travelMs;
            return d;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (bmp == null || drops.length == 0) return;

            long now = System.currentTimeMillis();
            if (lastTime < 0) lastTime = now;
            long dt = now - lastTime;
            lastTime = now;

            int w = getWidth();
            int h = getHeight();

            for (int i = 0; i < drops.length; i++) {
                Drop d = drops[i];
                d.y += d.speed * dt;
                if (d.y - d.size > h) drops[i] = d = randomDrop(w, h, false);
                RectF dst = new RectF(d.x, d.y, d.x + d.size, d.y + d.size);
                c.drawBitmap(bmp, null, dst, p);
            }
        }
    }
    // ------------------ FIN lluvia ------------------
}
