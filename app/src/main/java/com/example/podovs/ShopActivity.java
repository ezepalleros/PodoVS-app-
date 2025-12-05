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
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.BuildConfig;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirestoreRepo repo;
    private String uid;

    private TextView tvCoins;
    private LinearLayout rowDailyShop;
    private LinearLayout rowChests;
    private GridLayout gridEventos;

    private TextView tvRotationTitle;
    private TextView tvRotationTimer;

    private ViewGroup sectionRotation;
    private ViewGroup sectionChests;
    private ViewGroup sectionEvents;

    private static final int PRICE_BRONZE = 10000;
    private static final int PRICE_SILVER = 25000;
    private static final int PRICE_GOLD   = 50000;

    private static final int POOL_BRONZE  = 20000;
    private static final int POOL_SILVER  = 50000;
    private static final int POOL_GOLD    = 100000;

    // Paleta
    private static final int COL_BG_CARD      = Color.parseColor("#F3F4F6");
    private static final int COL_TEXT_DARK    = Color.parseColor("#111827");
    private static final int COL_TEXT_MEDIUM  = Color.parseColor("#4B5563");
    private static final int COL_BTN_LILAC    = Color.parseColor("#8B5CF6");
    private static final int COL_BTN_DISABLED = Color.parseColor("#9CA3AF");
    private static final int COL_BTN_GREEN    = Color.parseColor("#22C55E");

    // Rotación 3h
    private long lastSeed = -1L;
    private Handler handler;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateCountdown();
            long s = computedSeed();
            if (s != lastSeed) {
                lastSeed = s;
                loadDailyShop();
            }
            handler.postDelayed(this, 1000);
        }
    };

    private static final String PREF_SHOP  = "shop_rotation_local";
    private static final String KEY_SEED   = "rot_seed";
    private static final String KEY_IDS    = "rot_ids";

    // ======= Ads recompensados (cofre 10k) =======
    private static final String REWARDED_AD_UNIT_ID_BRONZE =
            BuildConfig.DEBUG
                    ? "ca-app-pub-3940256099942544/5224354917"
                    : "ca-app-pub-1198349658294342/3811853152";

    private RewardedAd rewardedBronzeAd;
    private boolean isLoadingBronzeAd = false;

    private static final String PREF_REWARDED_BRONZE = "shop_rewarded_bronze";
    private static final String KEY_DAY_INDEX = "day_index";
    private static final String KEY_COUNT     = "count";
    private static final int MAX_BRONZE_ADS_PER_DAY = 3;

    private Button btnBronzeChest;
    private TextView tvBronzePrice;

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

        sectionRotation = findViewById(R.id.sectionRotation);
        sectionChests   = findViewById(R.id.sectionChests);
        sectionEvents   = findViewById(R.id.sectionEvents);

        attachRain(sectionRotation, R.drawable.icon_bag,   0xFFE6F7EC);
        attachRain(sectionChests,   R.drawable.icon_chest, 0xFFFFF4CC);
        attachRain(sectionEvents,   R.drawable.icon_event, 0xFFE5F0FF);

        repo.listenUser(uid, (snap, err) -> {
            if (err != null || snap == null || !snap.exists()) return;
            Long saldo = snap.getLong("usu_saldo");
            if (saldo != null) tvCoins.setText(String.format(Locale.getDefault(), "%,d", saldo));
        });

        // Inicializar AdMob
        MobileAds.initialize(this, status -> {});
        loadRewardedBronzeAd();

        lastSeed = computedSeed();
        loadDailyShop();
        setupChests();
        loadEventos();

        ImageButton btnHome = findViewById(R.id.btnHome);
        ImageButton btnShop = findViewById(R.id.btnShop);
        ImageButton btnVs   = findViewById(R.id.btnVs);
        ImageButton btnEvt  = findViewById(R.id.btnEvents);
        ImageButton btnLb   = findViewById(R.id.btnLeaderboards);

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        btnShop.setOnClickListener(v ->
                Toast.makeText(this, "Estás en la tienda", Toast.LENGTH_SHORT).show());

        btnVs.setOnClickListener(v -> {
            startActivity(new Intent(this, VersusActivity.class));
            finish();
        });

        btnEvt.setOnClickListener(v -> {
            startActivity(new Intent(this, EventActivity.class));
            finish();
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (handler == null) handler = new Handler();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        refreshBronzeButtonState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) handler.removeCallbacks(ticker);
    }

    // ==================== Rotación ====================
    private static final long SLOT_MS = 3L * 60L * 60L * 1000L;

    private long rotationSeed() { return System.currentTimeMillis() / SLOT_MS; }

    private long computedSeed() {
        long slot = rotationSeed();
        long userHash = uid == null ? 0 : uid.hashCode();
        return slot ^ userHash;
    }

    private void updateCountdown() {
        long now = System.currentTimeMillis();
        long next = ((now / SLOT_MS) + 1) * SLOT_MS;
        long remain = Math.max(0, next - now);

        long h = remain / 3_600_000;
        long m = (remain % 3_600_000) / 60_000;
        long s = (remain % 60_000) / 1000;

        if (tvRotationTitle != null) tvRotationTitle.setText("Ofertas (rota cada 3 hs)");
        if (tvRotationTimer != null) tvRotationTimer.setText(
                String.format(Locale.getDefault(), "%01d:%02d:%02d", h, m, s));
    }

    private void safeNavigate(String className, String msgIfMissing) {
        try { startActivity(new Intent(this, Class.forName(className))); }
        catch (ClassNotFoundException e) { Toast.makeText(this, msgIfMissing, Toast.LENGTH_SHORT).show(); }
    }

    // ==================== Ofertas ====================
    private void loadDailyShop() {
        SharedPreferences p = getSharedPreferences(PREF_SHOP + "_" + uid, MODE_PRIVATE);
        long storedSeed = p.getLong(KEY_SEED, -1);
        String csv = p.getString(KEY_IDS, null);

        if (storedSeed == lastSeed && !TextUtils.isEmpty(csv)) {
            String[] ids = csv.split(",");
            fetchOffersByIds(ids);
            return;
        }

        db.collection("cosmetics")
                .whereEqualTo("cos_tienda", true)
                .whereEqualTo("cos_activo", true)
                .get()
                .addOnSuccessListener(qs -> {
                    List<DocumentSnapshot> all = new ArrayList<>(qs.getDocuments());
                    if (all.isEmpty()) return;

                    Collections.sort(all, Comparator.comparing(DocumentSnapshot::getId));

                    SecureRandom rnd = new SecureRandom(longToBytes(lastSeed));
                    Set<Integer> picked = new HashSet<>();
                    ArrayList<String> chosenIds = new ArrayList<>();

                    while (picked.size() < Math.min(3, all.size())) {
                        int idx = rnd.nextInt(all.size());
                        if (picked.add(idx)) chosenIds.add(all.get(idx).getId());
                    }

                    p.edit()
                            .putLong(KEY_SEED, lastSeed)
                            .putString(KEY_IDS, TextUtils.join(",", chosenIds))
                            .apply();

                    fetchOffersByIds(chosenIds.toArray(new String[0]));
                })
                .addOnFailureListener(e -> rowDailyShop.removeAllViews());
    }

    private void fetchOffersByIds(String[] ids) {
        rowDailyShop.removeAllViews();

        ArrayList<String> clean = new ArrayList<>();
        for (String s : ids == null ? new String[0] : ids) if (!TextUtils.isEmpty(s)) clean.add(s.trim());
        if (clean.isEmpty()) return;
        if (clean.size() > 3) clean = new ArrayList<>(clean.subList(0, 3));

        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (String id : clean) reads.add(db.collection("cosmetics").document(id).get());

        final ArrayList<String> finalClean = new ArrayList<>(clean);
        Tasks.whenAllSuccess(reads).addOnSuccessListener(list -> {
            for (int i = 0; i < finalClean.size(); i++) {
                DocumentSnapshot d = (DocumentSnapshot) list.get(i);
                if (d != null && d.exists()) {
                    rowDailyShop.addView(makeCosmeticCard(d, true));
                }
            }
            if (rowDailyShop.getChildCount() < 3) {
                final int faltanInicial = 3 - rowDailyShop.getChildCount();
                AtomicInteger faltan = new AtomicInteger(faltanInicial);
                db.collection("cosmetics")
                        .whereEqualTo("cos_tienda", true)
                        .whereEqualTo("cos_activo", true)
                        .get()
                        .addOnSuccessListener(qs -> {
                            for (DocumentSnapshot d : qs) {
                                if (finalClean.contains(d.getId())) continue;
                                rowDailyShop.addView(makeCosmeticCard(d, true));
                                if (faltan.decrementAndGet() <= 0) break;
                            }
                        });
            }
        }).addOnFailureListener(e -> {
            SharedPreferences p = getSharedPreferences(PREF_SHOP + "_" + uid, MODE_PRIVATE);
            p.edit().clear().apply();
        });
    }

    private byte[] longToBytes(long l) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) { b[i] = (byte) (l & 0xFF); l >>= 8; }
        return b;
    }

    // ==================== Cofres ====================
    private void setupChests() {
        rowChests.removeAllViews();
        rowChests.addView(makeChestCard(PRICE_BRONZE, POOL_BRONZE, FirestoreRepo.CHEST_T1));
        rowChests.addView(makeChestCard(PRICE_SILVER, POOL_SILVER, FirestoreRepo.CHEST_T2));
        rowChests.addView(makeChestCard(PRICE_GOLD,   POOL_GOLD,   FirestoreRepo.CHEST_T3));
    }

    private View makeChestCard(int price, int poolPrice, int tier) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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
        int imgRes = (tier == FirestoreRepo.CHEST_T1)
                ? R.drawable.pixel_ad
                : R.drawable.pixel_chest;
        iv.setImageResource(imgRes);
        card.addView(iv);

        TextView tvPrice = new TextView(this);
        tvPrice.setTypeface(Typeface.DEFAULT_BOLD);
        tvPrice.setTextColor(COL_TEXT_DARK);
        tvPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvPrice.setPadding(0, dp(6), 0, 0);

        TextView tvSub = new TextView(this);
        tvSub.setTextColor(COL_TEXT_MEDIUM);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackgroundTintList(ColorStateList.valueOf(COL_BTN_LILAC));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        lpBtn.setMargins(0, dp(8), 0, 0);
        btn.setLayoutParams(lpBtn);

        if (tier == FirestoreRepo.CHEST_T1) {
            tvBronzePrice = tvPrice;
            tvSub.setText("Premios de 20000");

            btnBronzeChest = btn;
            btn.setOnClickListener(v -> showBronzeRewardedAd());

            refreshBronzeButtonState();
        } else {
            tvPrice.setText(String.format(Locale.getDefault(), "%,d", price));
            tvSub.setText("Premios de " + String.format(Locale.getDefault(), "%,d", poolPrice));
            btn.setText("Abrir");
            btn.setOnClickListener(v -> openChest(price, poolPrice));
        }

        root.addView(card);
        root.addView(tvPrice);
        root.addView(tvSub);
        root.addView(btn);
        return root;
    }

    private void openChest(int chestPrice, int prizePrice) {
        repo.getUser(uid, user -> {
            long saldo = user.getLong("usu_saldo") == null ? 0L : user.getLong("usu_saldo");
            if (chestPrice > 0 && saldo < chestPrice) {
                Toast.makeText(this, "Saldo insuficiente.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (chestPrice > 0) {
                repo.addSaldo(uid, -chestPrice, v1 -> abrirCofreConPool(chestPrice, prizePrice),
                        e -> Toast.makeText(this, "Error al descontar saldo.", Toast.LENGTH_SHORT).show());
            } else {
                abrirCofreConPool(0, prizePrice);
            }

        }, e -> Toast.makeText(this, "Error usuario.", Toast.LENGTH_SHORT).show());
    }

    private void abrirCofreConPool(int chestPrice, int prizePrice) {
        db.collection("cosmetics")
                .whereEqualTo("cos_activo", true)
                .whereEqualTo("cos_precio", prizePrice)
                .get()
                .addOnSuccessListener(qs -> {
                    List<DocumentSnapshot> pool = qs.getDocuments();
                    if (pool.isEmpty()) {
                        if (chestPrice > 0) {
                            repo.addSaldo(uid, chestPrice, vv -> {}, ee -> {});
                        }
                        Toast.makeText(this, "Sin premios disponibles.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DocumentSnapshot prize = pool.get(new SecureRandom().nextInt(pool.size()));
                    String cosId = prize.getId();

                    db.collection("users").document(uid)
                            .collection("my_cosmetics").document(cosId)
                            .get().addOnSuccessListener(s -> {
                                boolean already = s.exists();
                                if (already) {
                                    int refund = chestPrice / 2;
                                    if (refund > 0) {
                                        repo.addSaldo(uid, refund,
                                                v2 -> {
                                                    showNewItemDialog(false, prize);
                                                    Toast.makeText(this, "Repetido. Devolución de " + refund, Toast.LENGTH_SHORT).show();
                                                },
                                                e2 -> Toast.makeText(this, "Error devolución.", Toast.LENGTH_SHORT).show());
                                    } else {
                                        showNewItemDialog(false, prize);
                                        Toast.makeText(this, "Cosmético repetido.", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    repo.addToInventory(uid, cosId, false,
                                            v2 -> {
                                                showNewItemDialog(true, prize);
                                                Toast.makeText(this, "¡Nuevo cosmético!", Toast.LENGTH_SHORT).show();
                                            },
                                            e2 -> {
                                                if (chestPrice > 0) {
                                                    repo.addSaldo(uid, chestPrice, v3 -> {}, e3 -> {});
                                                }
                                                Toast.makeText(this, "Error al entregar.", Toast.LENGTH_LONG).show();
                                            });
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (chestPrice > 0) {
                        repo.addSaldo(uid, chestPrice, vv -> {}, ee -> {});
                    }
                    Toast.makeText(this, "Error cargando premios.", Toast.LENGTH_SHORT).show();
                });
    }

    // ==================== RewardedAd cofre 10k ====================

    private long todayIndex() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    private int getBronzeAdsUsedToday() {
        SharedPreferences p = getSharedPreferences(PREF_REWARDED_BRONZE, MODE_PRIVATE);
        long storedDay = p.getLong(KEY_DAY_INDEX, -1L);
        long today = todayIndex();
        if (storedDay != today) {
            p.edit()
                    .putLong(KEY_DAY_INDEX, today)
                    .putInt(KEY_COUNT, 0)
                    .apply();
            return 0;
        }
        return p.getInt(KEY_COUNT, 0);
    }

    private void incrementBronzeAdsUsedToday() {
        SharedPreferences p = getSharedPreferences(PREF_REWARDED_BRONZE, MODE_PRIVATE);
        long today = todayIndex();
        long storedDay = p.getLong(KEY_DAY_INDEX, -1L);
        int count = p.getInt(KEY_COUNT, 0);
        if (storedDay != today) {
            storedDay = today;
            count = 0;
        }
        count++;
        p.edit()
                .putLong(KEY_DAY_INDEX, today)
                .putInt(KEY_COUNT, count)
                .apply();
    }

    private void loadRewardedBronzeAd() {
        if (isLoadingBronzeAd) return;
        isLoadingBronzeAd = true;

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(
                this,
                REWARDED_AD_UNIT_ID_BRONZE,
                adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedBronzeAd = ad;
                        isLoadingBronzeAd = false;
                        if (btnBronzeChest != null) refreshBronzeButtonState();
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        rewardedBronzeAd = null;
                        isLoadingBronzeAd = false;
                        if (btnBronzeChest != null) refreshBronzeButtonState();
                        // Solo para debug visual
                        Toast.makeText(ShopActivity.this,
                                "No se pudo cargar el anuncio aún.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void refreshBronzeButtonState() {
        int used = getBronzeAdsUsedToday();
        int remaining = MAX_BRONZE_ADS_PER_DAY - used;
        if (remaining < 0) remaining = 0;

        if (tvBronzePrice != null) {
            String base = String.format(Locale.getDefault(), "%,d", PRICE_BRONZE);
            tvBronzePrice.setText(base + " (" + remaining + ")");
        }

        if (btnBronzeChest == null) return;

        if (remaining <= 0) {
            btnBronzeChest.setEnabled(false);
            btnBronzeChest.setBackgroundTintList(ColorStateList.valueOf(COL_BTN_DISABLED));
            btnBronzeChest.setText("Sin usos hoy");
            return;
        }

        btnBronzeChest.setEnabled(true);
        if (rewardedBronzeAd != null) {
            btnBronzeChest.setBackgroundTintList(ColorStateList.valueOf(COL_BTN_GREEN));
        } else {
            btnBronzeChest.setBackgroundTintList(ColorStateList.valueOf(COL_BTN_LILAC));
        }
        btnBronzeChest.setText("Gratis (" + remaining + " restantes)");
    }

    private void showBronzeRewardedAd() {
        int used = getBronzeAdsUsedToday();
        if (used >= MAX_BRONZE_ADS_PER_DAY) {
            Toast.makeText(this, "Ya usaste los 3 anuncios de hoy.", Toast.LENGTH_SHORT).show();
            refreshBronzeButtonState();
            return;
        }

        if (rewardedBronzeAd == null) {
            Toast.makeText(this, "Anuncio todavía no está listo, probá de nuevo en unos segundos.", Toast.LENGTH_SHORT).show();
            loadRewardedBronzeAd();
            return;
        }

        RewardedAd ad = rewardedBronzeAd;
        rewardedBronzeAd = null;
        refreshBronzeButtonState();

        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                loadRewardedBronzeAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                loadRewardedBronzeAd();
            }
        });

        ad.show(this, new com.google.android.gms.ads.OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(RewardItem rewardItem) {
                incrementBronzeAdsUsedToday();
                refreshBronzeButtonState();
                openChest(0, POOL_BRONZE);
            }
        });
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

    // ==================== Cards comunes ====================
    private View makeCosmeticCard(DocumentSnapshot d, boolean big) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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

        if ("cloudinary".equalsIgnoreCase(aType)) {
            Glide.with(this).load(asset)
                    .error(android.R.drawable.ic_menu_report_image).into(iv);
        } else {
            int resId = (!TextUtils.isEmpty(asset))
                    ? getResources().getIdentifier(asset, "drawable", getPackageName())
                    : 0;
            iv.setImageResource(resId != 0
                    ? resId
                    : android.R.drawable.ic_menu_report_image);
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

        if ("cloudinary".equalsIgnoreCase(aType)) {
            Glide.with(this).load(asset)
                    .error(android.R.drawable.ic_menu_report_image).into(iv);
        } else {
            int resId = (!TextUtils.isEmpty(asset))
                    ? getResources().getIdentifier(asset, "drawable", getPackageName())
                    : 0;
            iv.setImageResource(resId != 0
                    ? resId
                    : android.R.drawable.ic_menu_report_image);
        }

        if (!owned) btn.setOnClickListener(v -> tryBuy(cosId, tipo, cosPrice == null ? 0L : cosPrice));

        root.addView(imgCard);
        root.addView(name);
        root.addView(price);
        root.addView(btn);
        return root;
    }

    private void tryBuy(String cosId, String tipo, long price) {
        if (price < 0) {
            Toast.makeText(this, "Precio inválido.", Toast.LENGTH_SHORT).show();
            return;
        }

        repo.buyCosmetic(uid, cosId, price,
                v -> db.collection("cosmetics").document(cosId).get()
                        .addOnSuccessListener(doc -> {
                            if (doc != null && doc.exists()) showNewItemDialog(true, doc);
                            Toast.makeText(this, "Comprado ✔", Toast.LENGTH_SHORT).show();
                        }),
                e -> Toast.makeText(this,
                        e.getMessage() == null ? "Error compra." : e.getMessage(),
                        Toast.LENGTH_LONG).show());
    }

    // ==== Dialog nuevo item ====
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
            btn.setText("OK");
            btn.setOnClickListener(v -> dialog.dismiss());
        }

        String asset = prizeDoc.getString("cos_asset");
        String aType = prizeDoc.getString("cos_assetType");
        if (iv != null) {
            if ("cloudinary".equalsIgnoreCase(aType)) {
                Glide.with(this).load(asset)
                        .error(android.R.drawable.ic_menu_report_image).into(iv);
            } else {
                int resId = (!TextUtils.isEmpty(asset))
                        ? getResources().getIdentifier(asset, "drawable", getPackageName())
                        : 0;
                iv.setImageResource(resId != 0
                        ? resId
                        : android.R.drawable.ic_menu_report_image);
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

    // ===== Fondo animado =====
    private void attachRain(ViewGroup container, int iconRes, int pastelBackground) {
        if (container == null) return;
        container.setBackgroundColor(pastelBackground);

        RainView rain = new RainView(this);
        rain.setIcon(iconRes);
        rain.setConfig(28, dp(14), dp(26), 4500);

        container.addView(rain, 0,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public static class RainView extends View {

        private static class Drop {
            float x, y, size, vy, vx, alpha;
        }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bmp;
        private Drop[] drops = new Drop[0];
        private long lastTime = -1L;
        private float minSizePx = 18, maxSizePx = 28;
        private long travelMs = 4500;
        private int bucketCount = 12;

        public RainView(android.content.Context ctx) { super(ctx); init(); }
        public RainView(android.content.Context ctx, @Nullable AttributeSet a) { super(ctx, a); init(); }
        public RainView(android.content.Context ctx, @Nullable AttributeSet a, int s) { super(ctx, a, s); init(); }

        private void init() {
            setWillNotDraw(false);
            setClickable(false);
            setFocusable(false);
            p.setAlpha(90);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(900);
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
            bucketCount = (int) Math.max(8, Math.min(20, w / dp(24)));
            int perBucket = Math.max(1, drops.length / bucketCount);
            int i = 0;
            for (int b = 0; b < bucketCount; b++) {
                float bx0 = (w * b) / (float) bucketCount;
                float bx1 = (w * (b + 1)) / (float) bucketCount;
                for (int k = 0; k < perBucket && i < drops.length; k++, i++) {
                    drops[i] = randomDrop(w, h, bx0, bx1, true);
                }
            }
            while (i < drops.length) {
                drops[i++] = randomDrop(w, h, 0, w, true);
            }
        }

        private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

        private Drop randomDrop(int w, int h, float x0, float x1, boolean anywhereY) {
            Drop d = new Drop();
            d.size = (float) (minSizePx + Math.random() * (maxSizePx - minSizePx));
            float jitter = (float) (Math.random() * (x1 - x0 - d.size));
            d.x = Math.max(0, x0 + jitter);
            d.y = anywhereY ? (float) (Math.random() * h) : -d.size;

            d.vy = (float) ((h + d.size) / (travelMs * (0.7 + Math.random() * 0.6)));
            d.vx = (float) (dp(10) / travelMs * (0.5 + Math.random()));
            d.alpha = (float) (0.35 + Math.random() * 0.55);
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
                d.y += d.vy * dt;
                d.x += d.vx * dt;

                if (d.y - d.size > h || d.x - d.size > w) {
                    int b = (int) (Math.random() * bucketCount);
                    float bx0 = (w * b) / (float) bucketCount;
                    float bx1 = (w * (b + 1)) / (float) bucketCount;
                    drops[i] = d = randomDrop(w, h, bx0, bx1, false);
                }

                p.setAlpha((int) (255 * d.alpha));
                RectF dst = new RectF(d.x, d.y, d.x + d.size, d.y + d.size);
                c.drawBitmap(bmp, null, dst, p);
            }
        }
    }
}
