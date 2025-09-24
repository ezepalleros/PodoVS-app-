package com.example.podovs;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "podovs.db";
    private static final int DB_VERSION = 6; // mantenemos 6

    private Context appContext;

    // ------- Usuarios -------
    public static final String TABLE_USUARIOS   = "usuarios";
    public static final String COL_ID           = "id";
    public static final String COL_NOMBRE       = "nombre";
    public static final String COL_EMAIL        = "email";
    public static final String COL_PASSWORD     = "password";
    public static final String COL_SALDO        = "saldo";
    public static final String COL_NIVEL        = "nivel";
    public static final String COL_DIFICULTAD   = "dificultad";
    public static final String COL_STATS_FK     = "stats_id";

    // ------- Estadísticas -------
    public static final String TABLE_STATS          = "estadisticas";
    public static final String COL_ST_ID            = "id";
    public static final String COL_ST_KM_HOY        = "km_hoy";
    public static final String COL_ST_KM_SEMANA     = "km_semana";
    public static final String COL_ST_KM_TOTAL      = "km_total";
    public static final String COL_ST_PASOS_TOTALES = "pasos_totales";
    public static final String COL_ST_META_DIARIA   = "meta_diaria_pasos";
    public static final String COL_ST_META_SEMANAL  = "meta_semanal_pasos";
    public static final String COL_ST_CARRERAS_GANADAS   = "carreras_ganadas";
    public static final String COL_ST_OBJ_COMPRADOS      = "objetos_comprados";
    public static final String COL_ST_EVENTOS_PART       = "eventos_participados";
    public static final String COL_ST_MEJOR_POS_MENSUAL  = "mejor_posicion_mensual";
    public static final String COL_ST_METAS_DIARIAS_OK   = "metas_diarias_ok";
    public static final String COL_ST_METAS_SEMANALES_OK = "metas_semanales_ok";
    public static final String COL_ST_XP            = "xp";

    // ------- Cosméticos -------
    public static final String TABLE_COSMETICOS  = "cosmeticos";
    public static final String COL_COSM_ID       = "id";
    public static final String COL_COSM_NOMBRE   = "nombre";
    public static final String COL_COSM_TIPO     = "tipo";
    public static final String COL_COSM_PRECIO   = "precio";
    public static final String COL_COSM_ASSET    = "asset";

    public static final String TIPO_CABEZA      = "cabeza";
    public static final String TIPO_REMERA      = "remera";
    public static final String TIPO_PANTALON    = "pantalon";
    public static final String TIPO_ZAPATILLAS  = "zapatillas";
    public static final String TIPO_PIEL        = "piel";

    // ------- Inventario / Equipado -------
    public static final String TABLE_INV              = "usuario_inventario";
    public static final String COL_INV_USER_ID        = "user_id";
    public static final String COL_INV_COSM_ID        = "cosmetico_id";
    public static final String COL_INV_ADQUIRIDO_AT   = "adquirido_at";

    public static final String TABLE_EQUIP            = "usuario_equipado";
    public static final String COL_EQ_USER_ID         = "user_id";
    public static final String COL_EQ_TIPO            = "tipo";
    public static final String COL_EQ_COSM_ID         = "cosmetico_id";

    // Códigos de compra
    public static final int BUY_OK              = 0;
    public static final int BUY_ALREADY_OWNED   = 1;
    public static final int BUY_NO_FUNDS        = 2;
    public static final int BUY_INVALID_ITEM    = 3;

    // Metas / XP
    private static final int BASE_DIARIA   = 1000;
    private static final int BASE_SEMANAL  = 10000;
    private static final int XP_PER_LEVEL  = 100;
    private static final int XP_DAILY      = 10;
    private static final int XP_WEEKLY     = 70;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1) Estadísticas
        db.execSQL("CREATE TABLE " + TABLE_STATS + " (" +
                COL_ST_ID                 + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_ST_KM_HOY             + " DECIMAL(6,2) DEFAULT 0, " +
                COL_ST_KM_SEMANA          + " DECIMAL(7,2) DEFAULT 0, " +
                COL_ST_KM_TOTAL           + " DECIMAL(9,2) DEFAULT 0, " +
                COL_ST_PASOS_TOTALES      + " INTEGER       DEFAULT 0, " +
                COL_ST_META_DIARIA        + " INTEGER       DEFAULT " + BASE_DIARIA + ", " +
                COL_ST_META_SEMANAL       + " INTEGER       DEFAULT " + BASE_SEMANAL + ", " +
                COL_ST_CARRERAS_GANADAS   + " INTEGER       DEFAULT 0, " +
                COL_ST_OBJ_COMPRADOS      + " INTEGER       DEFAULT 0, " +
                COL_ST_EVENTOS_PART       + " INTEGER       DEFAULT 0, " +
                COL_ST_MEJOR_POS_MENSUAL  + " INTEGER       DEFAULT NULL, " +
                COL_ST_METAS_DIARIAS_OK   + " INTEGER       DEFAULT 0, " +
                COL_ST_METAS_SEMANALES_OK + " INTEGER       DEFAULT 0, " +
                COL_ST_XP                 + " INTEGER       DEFAULT 0" +
                ");");

        // 2) Usuarios
        db.execSQL("CREATE TABLE " + TABLE_USUARIOS + " (" +
                COL_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NOMBRE     + " TEXT NOT NULL, " +
                COL_EMAIL      + " TEXT UNIQUE NOT NULL, " +
                COL_PASSWORD   + " TEXT NOT NULL, " +
                COL_SALDO      + " INTEGER NOT NULL DEFAULT 0, " +
                COL_NIVEL      + " INTEGER NOT NULL DEFAULT 1, " +
                COL_DIFICULTAD + " TEXT NOT NULL DEFAULT 'medio' CHECK(" + COL_DIFICULTAD + " IN ('bajo','medio','alto')), " +
                COL_STATS_FK   + " INTEGER, " +
                "FOREIGN KEY(" + COL_STATS_FK + ") REFERENCES " + TABLE_STATS + "(" + COL_ST_ID + ") ON DELETE SET NULL" +
                ");");

        // 3) Cosméticos / Inventario / Equipado
        db.execSQL("CREATE TABLE " + TABLE_COSMETICOS + " (" +
                COL_COSM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_COSM_NOMBRE + " TEXT NOT NULL, " +
                COL_COSM_TIPO + " TEXT NOT NULL CHECK(" + COL_COSM_TIPO +
                " IN ('" + TIPO_CABEZA + "','" + TIPO_REMERA + "','" + TIPO_PANTALON + "','" + TIPO_ZAPATILLAS + "','" + TIPO_PIEL + "')), " +
                COL_COSM_PRECIO + " INTEGER NOT NULL DEFAULT 0, " +
                COL_COSM_ASSET + " TEXT" +
                ");");

        db.execSQL("CREATE TABLE " + TABLE_INV + " (" +
                COL_INV_USER_ID + " INTEGER NOT NULL, " +
                COL_INV_COSM_ID + " INTEGER NOT NULL, " +
                COL_INV_ADQUIRIDO_AT + " TEXT DEFAULT (datetime('now')), " +
                "PRIMARY KEY (" + COL_INV_USER_ID + ", " + COL_INV_COSM_ID + "), " +
                "FOREIGN KEY (" + COL_INV_USER_ID + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY (" + COL_INV_COSM_ID + ") REFERENCES " + TABLE_COSMETICOS + "(" + COL_COSM_ID + ") ON DELETE CASCADE" +
                ");");

        db.execSQL("CREATE TABLE " + TABLE_EQUIP + " (" +
                COL_EQ_USER_ID + " INTEGER NOT NULL, " +
                COL_EQ_TIPO + " TEXT NOT NULL, " +
                COL_EQ_COSM_ID + " INTEGER NOT NULL, " +
                "PRIMARY KEY (" + COL_EQ_USER_ID + ", " + COL_EQ_TIPO + "), " +
                "FOREIGN KEY (" + COL_EQ_USER_ID + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY (" + COL_EQ_COSM_ID + ") REFERENCES " + TABLE_COSMETICOS + "(" + COL_COSM_ID + ") ON DELETE CASCADE" +
                ");");

        // Seeds usando SOLO el "db" recibido (sin abrir DB de nuevo)
        seedSoloMartinUnsafe(db);
        seedCosmeticos(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            try { db.execSQL("ALTER TABLE " + TABLE_USUARIOS + " ADD COLUMN " + COL_SALDO + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_STATS + " (" +
                    COL_ST_ID            + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_ST_KM_HOY        + " DECIMAL(6,2) DEFAULT 0, " +
                    COL_ST_KM_SEMANA     + " DECIMAL(7,2) DEFAULT 0, " +
                    COL_ST_PASOS_TOTALES + " INTEGER       DEFAULT 0, " +
                    COL_ST_META_DIARIA   + " INTEGER       DEFAULT " + BASE_DIARIA + ", " +
                    COL_ST_META_SEMANAL  + " INTEGER       DEFAULT " + BASE_SEMANAL + ", " +
                    COL_ST_XP            + " INTEGER       DEFAULT 0" +
                    ");");

            try { db.execSQL("ALTER TABLE " + TABLE_USUARIOS + " ADD COLUMN " + COL_NIVEL + " INTEGER NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_USUARIOS + " ADD COLUMN " + COL_DIFICULTAD + " TEXT NOT NULL DEFAULT 'medio'"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_USUARIOS + " ADD COLUMN " + COL_STATS_FK + " INTEGER"); } catch (Exception ignored) {}

            Cursor cur = db.rawQuery("SELECT " + COL_ID + " FROM " + TABLE_USUARIOS, null);
            try {
                if (cur.moveToFirst()) {
                    do {
                        long uid = cur.getLong(0);

                        String dif = "medio";
                        int nivel = 1;
                        Cursor c4 = db.rawQuery("SELECT " + COL_DIFICULTAD + ", " + COL_NIVEL +
                                        " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=?",
                                new String[]{String.valueOf(uid)});
                        if (c4.moveToFirst()) {
                            int idxD = c4.getColumnIndex(COL_DIFICULTAD);
                            int idxN = c4.getColumnIndex(COL_NIVEL);
                            if (idxD >= 0) dif = c4.getString(idxD);
                            if (idxN >= 0) nivel = c4.getInt(idxN);
                        }
                        c4.close();

                        int metaD = targetFor(nivel, dif, BASE_DIARIA);
                        int metaS = targetFor(nivel, dif, BASE_SEMANAL);

                        ContentValues st = new ContentValues();
                        st.put(COL_ST_KM_HOY, 0);
                        st.put(COL_ST_KM_SEMANA, 0);
                        st.put(COL_ST_PASOS_TOTALES, 0);
                        st.put(COL_ST_META_DIARIA, metaD);
                        st.put(COL_ST_META_SEMANAL, metaS);
                        st.put(COL_ST_XP, 0);
                        long statsId = db.insert(TABLE_STATS, null, st);

                        ContentValues up = new ContentValues();
                        up.put(COL_STATS_FK, statsId);
                        db.update(TABLE_USUARIOS, up, COL_ID + "=?", new String[]{String.valueOf(uid)});
                    } while (cur.moveToNext());
                }
            } finally {
                cur.close();
            }
        }
        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE " + TABLE_STATS + " ADD COLUMN " + COL_ST_KM_TOTAL           + " DECIMAL(9,2) DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_STATS + " ADD COLUMN " + COL_ST_CARRERAS_GANADAS   + " INTEGER      DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_STATS + " ADD COLUMN " + COL_ST_OBJ_COMPRADOS      + " INTEGER      DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_STATS + " ADD COLUMN " + COL_ST_EVENTOS_PART       + " INTEGER      DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_STATS + " ADD COLUMN " + COL_ST_MEJOR_POS_MENSUAL  + " INTEGER      DEFAULT NULL"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_STATS + " ADD COLUMN " + COL_ST_METAS_DIARIAS_OK   + " INTEGER      DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_STATS + " ADD COLUMN " + COL_ST_METAS_SEMANALES_OK + " INTEGER      DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 6) {
            // Limpiar y dejar solo a Martín (usar SIEMPRE el "db" recibido)
            db.delete(TABLE_EQUIP, null, null);
            db.delete(TABLE_INV, null, null);
            db.delete(TABLE_USUARIOS, null, null);
            db.delete(TABLE_STATS, null, null);
            seedSoloMartinUnsafe(db);
        }
    }

    public long registrarUsuario(String nombre, String email, String password, String dificultad, long saldoInicial) {
        String dif = "medio";
        if ("bajo".equalsIgnoreCase(dificultad)) dif = "bajo";
        else if ("alto".equalsIgnoreCase(dificultad)) dif = "alto";

        try {
            SQLiteDatabase db = getWritableDatabase();

            int nivel = 1;
            int metaD = targetFor(nivel, dif, BASE_DIARIA);
            int metaS = targetFor(nivel, dif, BASE_SEMANAL);

            ContentValues st = new ContentValues();
            st.put(COL_ST_KM_HOY, 0);
            st.put(COL_ST_KM_SEMANA, 0);
            st.put(COL_ST_PASOS_TOTALES, 0);
            st.put(COL_ST_META_DIARIA, metaD);
            st.put(COL_ST_META_SEMANAL, metaS);
            st.put(COL_ST_XP, 0);
            long statsId = db.insert(TABLE_STATS, null, st);
            if (statsId <= 0) return -1;

            ContentValues u = new ContentValues();
            u.put(COL_NOMBRE, nombre);
            u.put(COL_EMAIL, email);
            u.put(COL_PASSWORD, password);
            u.put(COL_SALDO, Math.max(0, saldoInicial));
            u.put(COL_NIVEL, nivel);
            u.put(COL_DIFICULTAD, dif);
            u.put(COL_STATS_FK, statsId);

            long userId = db.insert(TABLE_USUARIOS, null, u);
            if (userId <= 0) return -1;
            return userId;

        } catch (android.database.sqlite.SQLiteConstraintException ex) {
            return -2;
        } catch (Exception e) {
            return -1;
        }
    }

    // ====== Seeds (UNSAFE: usan el "db" provisto) ======
    private void seedSoloMartinUnsafe(SQLiteDatabase db) {
        long u1 = insertarUsuarioUnsafe(db,"Martín Blanco", "martin.blanco@example.com", "1234", 500, "medio");

        // 15 km totales y 90 XP (a 10 de subir). También seteo hoy/semana como antes.
        setKmTotalUnsafe(db, u1, 15.00);
        setXpRawUnsafe(db, u1, 90);
        setKmHoyUnsafe(db, u1, 2.50);
        setKmSemanaUnsafe(db, u1, 12.70);
    }

    private void seedCosmeticos(SQLiteDatabase db) {
        insertarCosmetico(db, "Gorra azul",     TIPO_CABEZA,     100, "head_cap_blue");
        insertarCosmetico(db, "Vincha roja",    TIPO_CABEZA,      80, "head_band_red");
        insertarCosmetico(db, "Remera básica",  TIPO_REMERA,      60, "shirt_basic");
        insertarCosmetico(db, "Pantalón negro", TIPO_PANTALON,   120, "pants_black");
        insertarCosmetico(db, "Zapas blancas",  TIPO_ZAPATILLAS, 150, "shoes_white");
        insertarCosmetico(db, "Piel morena",    TIPO_PIEL,         0, "skin_brown");
    }

    // ====== Helpers de creación usuario + stats ======
    private long insertarUsuarioUnsafe(SQLiteDatabase db, String nombre, String email, String password, long saldoInicial, String dificultad) {
        int nivel = 1;
        int metaD = targetFor(nivel, dificultad, BASE_DIARIA);
        int metaS = targetFor(nivel, dificultad, BASE_SEMANAL);

        ContentValues st = new ContentValues();
        st.put(COL_ST_KM_HOY, 0);
        st.put(COL_ST_KM_SEMANA, 0);
        st.put(COL_ST_PASOS_TOTALES, 0);
        st.put(COL_ST_META_DIARIA, metaD);
        st.put(COL_ST_META_SEMANAL, metaS);
        st.put(COL_ST_XP, 0);
        long statsId = db.insert(TABLE_STATS, null, st);

        ContentValues u = new ContentValues();
        u.put(COL_NOMBRE, nombre);
        u.put(COL_EMAIL, email);
        u.put(COL_PASSWORD, password);
        u.put(COL_SALDO, saldoInicial);
        u.put(COL_NIVEL, nivel);
        u.put(COL_DIFICULTAD, dificultad);
        u.put(COL_STATS_FK, statsId);
        return db.insert(TABLE_USUARIOS, null, u);
    }

    private static double factor(String dificultad) {
        if ("bajo".equalsIgnoreCase(dificultad)) return 1.1;
        if ("alto".equalsIgnoreCase(dificultad)) return 1.3;
        return 1.2; // medio
    }
    private static int targetFor(int nivel, String dificultad, int base) {
        double f = factor(dificultad);
        double t = base * Math.pow(f, Math.max(0, nivel - 1));
        return (int)Math.round(t);
    }

    // ========= Lecturas simples =========
    public Cursor login(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USUARIOS +
                        " WHERE " + COL_EMAIL + "=? AND " + COL_PASSWORD + "=?",
                new String[]{email, password});
    }

    public Cursor getRankingSemana() {
        SQLiteDatabase db = this.getReadableDatabase();
        String sql = "SELECT u." + COL_ID + ", u." + COL_NOMBRE + ", s." + COL_ST_KM_SEMANA +
                " FROM " + TABLE_USUARIOS + " u " +
                " JOIN " + TABLE_STATS + " s ON s." + COL_ST_ID + " = u." + COL_STATS_FK +
                " ORDER BY s." + COL_ST_KM_SEMANA + " DESC";
        return db.rawQuery(sql, null);
    }
    public Cursor getRankingHoy() {
        SQLiteDatabase db = this.getReadableDatabase();
        String sql = "SELECT u." + COL_ID + ", u." + COL_NOMBRE + ", s." + COL_ST_KM_HOY +
                " FROM " + TABLE_USUARIOS + " u " +
                " JOIN " + TABLE_STATS + " s ON s." + COL_ST_ID + " = u." + COL_STATS_FK +
                " ORDER BY s." + COL_ST_KM_HOY + " DESC";
        return db.rawQuery(sql, null);
    }

    // ========= SALDO =========
    public long getSaldo(long userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_SALDO + " FROM " + TABLE_USUARIOS +
                " WHERE " + COL_ID + "=? LIMIT 1", new String[]{String.valueOf(userId)});
        try { return c.moveToFirst() ? c.getLong(0) : 0L; }
        finally { c.close(); }
    }
    public boolean setSaldo(long userId, long saldo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SALDO, saldo);
        int rows = db.update(TABLE_USUARIOS, cv, COL_ID + "=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }
    public boolean addSaldo(long userId, long delta) {
        long actual = getSaldo(userId);
        long nuevo = actual + delta;
        if (nuevo < 0) nuevo = 0;
        return setSaldo(userId, nuevo);
    }

    // ========= KM / Pasos / Metas / XP =========
    private long statsIdFor(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS +
                " WHERE " + COL_ID + "=? LIMIT 1", new String[]{String.valueOf(userId)});
        try { return c.moveToFirst() ? c.getLong(0) : -1L; }
        finally { c.close(); }
    }

    public double getKmHoy(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT s." + COL_ST_KM_HOY +
                " FROM " + TABLE_STATS + " s JOIN " + TABLE_USUARIOS + " u ON u." + COL_STATS_FK + "=s." + COL_ST_ID +
                " WHERE u." + COL_ID + "=? LIMIT 1", new String[]{String.valueOf(userId)});
        try { return c.moveToFirst() ? c.getDouble(0) : 0.0; }
        finally { c.close(); }
    }
    public double getKmSemana(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT s." + COL_ST_KM_SEMANA +
                " FROM " + TABLE_STATS + " s JOIN " + TABLE_USUARIOS + " u ON u." + COL_STATS_FK + "=s." + COL_ST_ID +
                " WHERE u." + COL_ID + "=? LIMIT 1", new String[]{String.valueOf(userId)});
        try { return c.moveToFirst() ? c.getDouble(0) : 0.0; }
        finally { c.close(); }
    }
    public double getKmTotal(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT s." + COL_ST_KM_TOTAL +
                " FROM " + TABLE_STATS + " s JOIN " + TABLE_USUARIOS + " u ON u." + COL_STATS_FK + "=s." + COL_ST_ID +
                " WHERE u." + COL_ID + "=? LIMIT 1", new String[]{String.valueOf(userId)});
        try { return c.moveToFirst() ? c.getDouble(0) : 0.0; }
        finally { c.close(); }
    }

    // ===== Métodos públicos (abren DB) =====
    public void updateKmHoy(long userId, double kmHoy) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_KM_HOY + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{kmHoy, userId});
    }
    public void updateKmSemana(long userId, double kmSem) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_KM_SEMANA + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{kmSem, userId});
    }
    public void setKmHoy(long userId, double kmHoy) { updateKmHoy(userId, kmHoy); }
    public void setKmSemana(long userId, double kmSemana) { updateKmSemana(userId, kmSemana); }
    public void setKmTotal(long userId, double kmTotal) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_KM_TOTAL + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{kmTotal, userId});
    }
    public void setXpRaw(long userId, int xp) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_XP + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{xp, userId});
    }

    // ===== Versiones UNSAFE que NO abren la DB (para seeds/upgrade) =====
    private void setKmHoyUnsafe(SQLiteDatabase db, long userId, double kmHoy) {
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_KM_HOY + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{kmHoy, userId});
    }
    private void setKmSemanaUnsafe(SQLiteDatabase db, long userId, double kmSem) {
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_KM_SEMANA + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{kmSem, userId});
    }
    private void setKmTotalUnsafe(SQLiteDatabase db, long userId, double kmTotal) {
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_KM_TOTAL + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{kmTotal, userId});
    }
    private void setXpRawUnsafe(SQLiteDatabase db, long userId, int xp) {
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_XP + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{xp, userId});
    }

    public int[] getMetas(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT s." + COL_ST_META_DIARIA + ", s." + COL_ST_META_SEMANAL +
                " FROM " + TABLE_STATS + " s JOIN " + TABLE_USUARIOS + " u ON u." + COL_STATS_FK + "=s." + COL_ST_ID +
                " WHERE u." + COL_ID + "=? LIMIT 1", new String[]{String.valueOf(userId)});
        try {
            if (c.moveToFirst()) return new int[]{ c.getInt(0), c.getInt(1) };
            return new int[]{ BASE_DIARIA, BASE_SEMANAL };
        } finally { c.close(); }
    }

    public String getDificultad(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_DIFICULTAD + " FROM " + TABLE_USUARIOS +
                " WHERE " + COL_ID + "=? LIMIT 1", new String[]{String.valueOf(userId)});
        try { return c.moveToFirst() ? c.getString(0) : "medio"; }
        finally { c.close(); }
    }

    public boolean setDificultad(long userId, String dificultad) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_DIFICULTAD, dificultad);
        int rows = db.update(TABLE_USUARIOS, cv, COL_ID + "=?", new String[]{String.valueOf(userId)});
        if (rows > 0) { recalcularMetas(userId); }
        return rows > 0;
    }

    public int getNivel(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_NIVEL + " FROM " + TABLE_USUARIOS +
                " WHERE " + COL_ID + "=? LIMIT 1", new String[]{String.valueOf(userId)});
        try { return c.moveToFirst() ? c.getInt(0) : 1; }
        finally { c.close(); }
    }

    public void recalcularMetas(long userId) {
        int nivel = getNivel(userId);
        String dif = getDificultad(userId);
        int metaD = targetFor(nivel, dif, BASE_DIARIA);
        int metaS = targetFor(nivel, dif, BASE_SEMANAL);
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_META_DIARIA + "=?, " + COL_ST_META_SEMANAL + "=? WHERE " +
                        COL_ST_ID + "=(SELECT " + COL_STATS_FK + " FROM " + TABLE_USUARIOS + " WHERE " + COL_ID + "=? LIMIT 1)",
                new Object[]{metaD, metaS, userId});
    }

    public int addXpAndMaybeLevelUp(long userId, int deltaXp) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long statsId = statsIdFor(userId);
            if (statsId <= 0) {
                db.setTransactionSuccessful();
                return getNivel(userId);
            }

            int xp = 0;
            Cursor c = db.rawQuery("SELECT " + COL_ST_XP + " FROM " + TABLE_STATS + " WHERE " + COL_ST_ID + "=?",
                    new String[]{String.valueOf(statsId)});
            if (c.moveToFirst()) xp = c.getInt(0);
            c.close();

            xp += deltaXp;

            int nivel = getNivel(userId);
            while (xp >= XP_PER_LEVEL) {
                xp -= XP_PER_LEVEL;
                nivel += 1;
                // Notificación de level up
                NotificationHelper.showLevelUp(appContext, nivel);
            }

            ContentValues cvS = new ContentValues();
            cvS.put(COL_ST_XP, xp);
            db.update(TABLE_STATS, cvS, COL_ST_ID + "=?", new String[]{String.valueOf(statsId)});

            ContentValues cvU = new ContentValues();
            cvU.put(COL_NIVEL, nivel);
            db.update(TABLE_USUARIOS, cvU, COL_ID + "=?", new String[]{String.valueOf(userId)});

            int metaD = targetFor(nivel, getDificultad(userId), BASE_DIARIA);
            int metaS = targetFor(nivel, getDificultad(userId), BASE_SEMANAL);
            db.execSQL("UPDATE " + TABLE_STATS + " SET " + COL_ST_META_DIARIA + "=?, " + COL_ST_META_SEMANAL + "=? WHERE " +
                            COL_ST_ID + "=?",
                    new Object[]{metaD, metaS, statsId});

            db.setTransactionSuccessful();
            return nivel;
        } finally {
            db.endTransaction();
        }
    }

    public int onDailyGoalReached(long userId)  {
        // Notificación de meta diaria
        NotificationHelper.showGoalCompleted(appContext, "diaria");
        return addXpAndMaybeLevelUp(userId, XP_DAILY);
    }
    public int onWeeklyGoalReached(long userId) {
        // Notificación de meta semanal
        NotificationHelper.showGoalCompleted(appContext, "semanal");
        return addXpAndMaybeLevelUp(userId, XP_WEEKLY);
    }

    // ========= Cosméticos / Inventario =========
    public long insertarCosmetico(SQLiteDatabase db, String nombre, String tipo, int precio, String asset) {
        ContentValues cv = new ContentValues();
        cv.put(COL_COSM_NOMBRE, nombre);
        cv.put(COL_COSM_TIPO, tipo);
        cv.put(COL_COSM_PRECIO, precio);
        cv.put(COL_COSM_ASSET, asset);
        return db.insert(TABLE_COSMETICOS, null, cv);
    }

    public Cursor getCosmeticosPorTipo(String tipo) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_COSMETICOS +
                        " WHERE " + COL_COSM_TIPO + "=? ORDER BY " + COL_COSM_PRECIO + " ASC",
                new String[]{tipo});
    }

    public void agregarAlInventario(long userId, long cosmeticoId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO " + TABLE_INV +
                        " (" + COL_INV_USER_ID + ", " + COL_INV_COSM_ID + ") VALUES (?,?)",
                new Object[]{userId, cosmeticoId});
    }

    public boolean usuarioPosee(long userId, long cosmeticoId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM " + TABLE_INV +
                        " WHERE " + COL_INV_USER_ID + "=? AND " + COL_INV_COSM_ID + "=? LIMIT 1",
                new String[]{String.valueOf(userId), String.valueOf(cosmeticoId)});
        try { return c.moveToFirst(); }
        finally { c.close(); }
    }

    public String getTipoCosmetico(long cosmeticoId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_COSM_TIPO + " FROM " + TABLE_COSMETICOS +
                        " WHERE " + COL_COSM_ID + "=? LIMIT 1",
                new String[]{String.valueOf(cosmeticoId)});
        try { return c.moveToFirst() ? c.getString(0) : null; }
        finally { c.close(); }
    }

    public int getPrecioCosmetico(long cosmeticoId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_COSM_PRECIO + " FROM " + TABLE_COSMETICOS +
                        " WHERE " + COL_COSM_ID + "=? LIMIT 1",
                new String[]{String.valueOf(cosmeticoId)});
        try { return c.moveToFirst() ? c.getInt(0) : -1; }
        finally { c.close(); }
    }

    public boolean equipar(long userId, long cosmeticoId) {
        if (!usuarioPosee(userId, cosmeticoId)) return false;
        String tipo = getTipoCosmetico(cosmeticoId);
        if (tipo == null) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO " + TABLE_EQUIP +
                        " (" + COL_EQ_USER_ID + ", " + COL_EQ_TIPO + ", " + COL_EQ_COSM_ID + ") VALUES (?,?,?)",
                new Object[]{userId, tipo, cosmeticoId});
        return true;
    }

    public long getEquipado(long userId, String tipo) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_EQ_COSM_ID + " FROM " + TABLE_EQUIP +
                        " WHERE " + COL_EQ_USER_ID + "=? AND " + COL_EQ_TIPO + "=? LIMIT 1",
                new String[]{String.valueOf(userId), tipo});
        try { return c.moveToFirst() ? c.getLong(0) : -1L; }
        finally { c.close(); }
    }

    public Cursor getInventarioUsuario(long userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String sql = "SELECT c." + COL_COSM_ID + ", c." + COL_COSM_NOMBRE + ", c." + COL_COSM_TIPO + ", " +
                "c." + COL_COSM_PRECIO + ", c." + COL_COSM_ASSET + ", i." + COL_INV_ADQUIRIDO_AT +
                " FROM " + TABLE_INV + " i " +
                "JOIN " + TABLE_COSMETICOS + " c ON c." + COL_COSM_ID + "=i." + COL_INV_COSM_ID +
                " WHERE i." + COL_INV_USER_ID + "=? " +
                " ORDER BY c." + COL_COSM_TIPO + ", c." + COL_COSM_PRECIO;
        return db.rawQuery(sql, new String[]{String.valueOf(userId)});
    }

    // ========= COMPRA =========
    public int comprarCosmetico(long userId, long cosmeticoId, boolean autoEquip) {
        int precio = getPrecioCosmetico(cosmeticoId);
        String tipo = getTipoCosmetico(cosmeticoId);
        if (precio < 0 || tipo == null) return BUY_INVALID_ITEM;
        if (usuarioPosee(userId, cosmeticoId)) return BUY_ALREADY_OWNED;

        long saldo = getSaldo(userId);
        if (saldo < precio) return BUY_NO_FUNDS;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_SALDO, saldo - precio);
            int rows = db.update(TABLE_USUARIOS, cv, COL_ID + "=?", new String[]{String.valueOf(userId)});
            if (rows <= 0) { db.endTransaction(); return BUY_INVALID_ITEM; }

            db.execSQL("INSERT OR IGNORE INTO " + TABLE_INV +
                            " (" + COL_INV_USER_ID + ", " + COL_INV_COSM_ID + ") VALUES (?,?)",
                    new Object[]{userId, cosmeticoId});

            if (autoEquip) {
                db.execSQL("INSERT OR REPLACE INTO " + TABLE_EQUIP +
                                " (" + COL_EQ_USER_ID + ", " + COL_EQ_TIPO + ", " + COL_EQ_COSM_ID + ") VALUES (?,?,?)",
                        new Object[]{userId, tipo, cosmeticoId});
            }

            db.setTransactionSuccessful();
            return BUY_OK;
        } finally {
            db.endTransaction();
        }
    }
}