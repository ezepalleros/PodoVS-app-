package com.example.podovs;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.database.Cursor;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "podovs.db";
    private static final int DB_VERSION = 2;
    // ------- Usuarios -------
    public static final String TABLE_USUARIOS = "usuarios";
    public static final String COL_ID = "id";
    public static final String COL_NOMBRE = "nombre";
    public static final String COL_EMAIL = "email";
    public static final String COL_PASSWORD = "password";
    public static final String COL_KM_HOY = "km_hoy";
    public static final String COL_KM_SEMANA = "km_semana";
    public static final String COL_SALDO = "saldo"; // monedas del usuario (INTEGER)

    // ------- Cosméticos -------
    public static final String TABLE_COSMETICOS = "cosmeticos";
    public static final String COL_COSM_ID = "id";
    public static final String COL_COSM_NOMBRE = "nombre";
    public static final String COL_COSM_TIPO = "tipo"; // cabeza/remera/pantalon/zapatillas/piel
    public static final String COL_COSM_PRECIO = "precio"; // INTEGER
    public static final String COL_COSM_ASSET = "asset"; // opcional (clave/archivo)

    // Tipos permitidos
    public static final String TIPO_CABEZA = "cabeza";
    public static final String TIPO_REMERA = "remera";
    public static final String TIPO_PANTALON = "pantalon";
    public static final String TIPO_ZAPATILLAS = "zapatillas";
    public static final String TIPO_PIEL = "piel";

    // ------- Inventario del usuario -------
    public static final String TABLE_INV = "usuario_inventario";
    public static final String COL_INV_USER_ID = "user_id";
    public static final String COL_INV_COSM_ID = "cosmetico_id";
    public static final String COL_INV_ADQUIRIDO_AT = "adquirido_at";

    // ------- Equipado (uno por tipo) -------
    public static final String TABLE_EQUIP = "usuario_equipado";
    public static final String COL_EQ_USER_ID = "user_id";
    public static final String COL_EQ_TIPO = "tipo";
    public static final String COL_EQ_COSM_ID = "cosmetico_id";

    // Códigos de compra
    public static final int BUY_OK = 0;
    public static final int BUY_ALREADY_OWNED = 1;
    public static final int BUY_NO_FUNDS = 2;
    public static final int BUY_INVALID_ITEM = 3;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Usuarios (con saldo)
        String createUsuarios = "CREATE TABLE " + TABLE_USUARIOS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NOMBRE + " TEXT NOT NULL, " +
                COL_EMAIL + " TEXT UNIQUE NOT NULL, " +
                COL_PASSWORD + " TEXT NOT NULL, " +
                COL_KM_HOY + " DECIMAL(6,2) DEFAULT 0, " +
                COL_KM_SEMANA + " DECIMAL(7,2) DEFAULT 0, " +
                COL_SALDO + " INTEGER NOT NULL DEFAULT 0" +
                ");";
        db.execSQL(createUsuarios);

        // Cosméticos
        String createCosmeticos = "CREATE TABLE " + TABLE_COSMETICOS + " (" +
                COL_COSM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_COSM_NOMBRE + " TEXT NOT NULL, " +
                COL_COSM_TIPO + " TEXT NOT NULL CHECK(" + COL_COSM_TIPO +
                " IN ('" + TIPO_CABEZA + "','" + TIPO_REMERA + "','" + TIPO_PANTALON + "','" + TIPO_ZAPATILLAS + "','" + TIPO_PIEL + "')), " +
                COL_COSM_PRECIO + " INTEGER NOT NULL DEFAULT 0, " +
                COL_COSM_ASSET + " TEXT" +
                ");";
        db.execSQL(createCosmeticos);

        // Inventario (posesiones)
        String createInventario = "CREATE TABLE " + TABLE_INV + " (" +
                COL_INV_USER_ID + " INTEGER NOT NULL, " +
                COL_INV_COSM_ID + " INTEGER NOT NULL, " +
                COL_INV_ADQUIRIDO_AT + " TEXT DEFAULT (datetime('now')), " +
                "PRIMARY KEY (" + COL_INV_USER_ID + ", " + COL_INV_COSM_ID + "), " +
                "FOREIGN KEY (" + COL_INV_USER_ID + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY (" + COL_INV_COSM_ID + ") REFERENCES " + TABLE_COSMETICOS + "(" + COL_COSM_ID + ") ON DELETE CASCADE" +
                ");";
        db.execSQL(createInventario);

        // Equipado actual
        String createEquipado = "CREATE TABLE " + TABLE_EQUIP + " (" +
                COL_EQ_USER_ID + " INTEGER NOT NULL, " +
                COL_EQ_TIPO + " TEXT NOT NULL, " +
                COL_EQ_COSM_ID + " INTEGER NOT NULL, " +
                "PRIMARY KEY (" + COL_EQ_USER_ID + ", " + COL_EQ_TIPO + "), " +
                "FOREIGN KEY (" + COL_EQ_USER_ID + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_ID + ") ON DELETE CASCADE, " +
                "FOREIGN KEY (" + COL_EQ_COSM_ID + ") REFERENCES " + TABLE_COSMETICOS + "(" + COL_COSM_ID + ") ON DELETE CASCADE" +
                ");";
        db.execSQL(createEquipado);

        // Seed inicial
        seedUsuarios(db);
        seedCosmeticos(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v2: añadimos tablas de cosméticos/inventario/equipado
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_COSMETICOS + " (" +
                    COL_COSM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_COSM_NOMBRE + " TEXT NOT NULL, " +
                    COL_COSM_TIPO + " TEXT NOT NULL CHECK(" + COL_COSM_TIPO +
                    " IN ('" + TIPO_CABEZA + "','" + TIPO_REMERA + "','" + TIPO_PANTALON + "','" + TIPO_ZAPATILLAS + "','" + TIPO_PIEL + "')), " +
                    COL_COSM_PRECIO + " INTEGER NOT NULL DEFAULT 0, " +
                    COL_COSM_ASSET + " TEXT" +
                    ");");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_INV + " (" +
                    COL_INV_USER_ID + " INTEGER NOT NULL, " +
                    COL_INV_COSM_ID + " INTEGER NOT NULL, " +
                    COL_INV_ADQUIRIDO_AT + " TEXT DEFAULT (datetime('now')), " +
                    "PRIMARY KEY (" + COL_INV_USER_ID + ", " + COL_INV_COSM_ID + "), " +
                    "FOREIGN KEY (" + COL_INV_USER_ID + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY (" + COL_INV_COSM_ID + ") REFERENCES " + TABLE_COSMETICOS + "(" + COL_COSM_ID + ") ON DELETE CASCADE" +
                    ");");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_EQUIP + " (" +
                    COL_EQ_USER_ID + " INTEGER NOT NULL, " +
                    COL_EQ_TIPO + " TEXT NOT NULL, " +
                    COL_EQ_COSM_ID + " INTEGER NOT NULL, " +
                    "PRIMARY KEY (" + COL_EQ_USER_ID + ", " + COL_EQ_TIPO + "), " +
                    "FOREIGN KEY (" + COL_EQ_USER_ID + ") REFERENCES " + TABLE_USUARIOS + "(" + COL_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY (" + COL_EQ_COSM_ID + ") REFERENCES " + TABLE_COSMETICOS + "(" + COL_COSM_ID + ") ON DELETE CASCADE" +
                    ");");

            seedCosmeticos(db);
        }
        // v3: añadimos columna saldo si no existe
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_USUARIOS + " ADD COLUMN " + COL_SALDO + " INTEGER NOT NULL DEFAULT 0");
        }
    }

    // ========= Seed =========
    private void seedUsuarios(SQLiteDatabase db) {
        insertarUsuario(db,"Martín Blanco", "martin.blanco@example.com", "1234", 2.50, 12.70, 500);
        insertarUsuario(db,"Julieta Torres", "julieta.torres@example.com", "abcd", 4.10, 20.30, 450);
        insertarUsuario(db,"Rodolfo Cervantes", "rodolfo.cervantes@example.com", "1111", 0.80, 5.00, 300);
        insertarUsuario(db,"Lucía Fernández", "lucia.fernandez@example.com", "pass123", 6.00, 25.90, 700);
        insertarUsuario(db,"Ezequiel Palleros", "ezequiel.palleros@example.com", "test", 3.30, 15.20, 1000);
    }

    private void seedCosmeticos(SQLiteDatabase db) {
        if (countRows(db, TABLE_COSMETICOS) > 0) return;
        insertarCosmetico(db, "Gorra azul", TIPO_CABEZA, 100, "head_cap_blue");
        insertarCosmetico(db, "Vincha roja", TIPO_CABEZA, 80, "head_band_red");
        insertarCosmetico(db, "Remera básica", TIPO_REMERA, 60, "shirt_basic");
        insertarCosmetico(db, "Pantalón negro", TIPO_PANTALON, 120, "pants_black");
        insertarCosmetico(db, "Zapas blancas", TIPO_ZAPATILLAS, 150, "shoes_white");
        insertarCosmetico(db, "Piel morena", TIPO_PIEL, 0, "skin_brown");
    }

    private int countRows(SQLiteDatabase db, String table) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try { return (c.moveToFirst() ? c.getInt(0) : 0); }
        finally { c.close(); }
    }

    // ========= CRUD Usuarios =========
    // Compatibilidad con tu firma original
    public long insertarUsuario(SQLiteDatabase db, String nombre, String email, String password,
                                double kmHoy, double kmSemana) {
        return insertarUsuario(db, nombre, email, password, kmHoy, kmSemana, 0);
    }

    // Overload con saldo inicial
    public long insertarUsuario(SQLiteDatabase db, String nombre, String email, String password,
                                double kmHoy, double kmSemana, long saldoInicial) {
        ContentValues cv = new ContentValues();
        cv.put(COL_NOMBRE, nombre);
        cv.put(COL_EMAIL, email);
        cv.put(COL_PASSWORD, password);
        cv.put(COL_KM_HOY, kmHoy);
        cv.put(COL_KM_SEMANA, kmSemana);
        cv.put(COL_SALDO, saldoInicial);
        return db.insert(TABLE_USUARIOS, null, cv);
    }

    public Cursor login(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USUARIOS +
                        " WHERE " + COL_EMAIL + "=? AND " + COL_PASSWORD + "=?",
                new String[]{email, password});
    }

    public Cursor getRankingSemana() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USUARIOS +
                " ORDER BY " + COL_KM_SEMANA + " DESC", null);
    }

    public Cursor getRankingHoy() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USUARIOS +
                " ORDER BY " + COL_KM_HOY + " DESC", null);
    }

    // ========= SALDO =========
    public long getSaldo(long userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_SALDO + " FROM " + TABLE_USUARIOS +
                        " WHERE " + COL_ID + "=? LIMIT 1",
                new String[]{String.valueOf(userId)});
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

    // ========= CRUD Cosméticos / Inventario =========
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
    /**
     * Compra un cosmético: descuenta saldo, agrega al inventario y opcionalmente equipa.
     * @return BUY_OK / BUY_ALREADY_OWNED / BUY_NO_FUNDS / BUY_INVALID_ITEM
     */
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
            // Descontar saldo
            ContentValues cv = new ContentValues();
            cv.put(COL_SALDO, saldo - precio);
            int rows = db.update(TABLE_USUARIOS, cv, COL_ID + "=?", new String[]{String.valueOf(userId)});
            if (rows <= 0) { db.endTransaction(); return BUY_INVALID_ITEM; }

            // Agregar al inventario
            db.execSQL("INSERT OR IGNORE INTO " + TABLE_INV +
                            " (" + COL_INV_USER_ID + ", " + COL_INV_COSM_ID + ") VALUES (?,?)",
                    new Object[]{userId, cosmeticoId});

            // Equipar si corresponde
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