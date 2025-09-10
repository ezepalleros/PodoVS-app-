package com.example.podovs;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.database.Cursor;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "podovs.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_USUARIOS = "usuarios";

    public static final String COL_ID = "id";
    public static final String COL_NOMBRE = "nombre";
    public static final String COL_EMAIL = "email";
    public static final String COL_PASSWORD = "password";
    public static final String COL_KM_HOY = "km_hoy";
    public static final String COL_KM_SEMANA = "km_semana";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_USUARIOS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NOMBRE + " TEXT NOT NULL, " +
                COL_EMAIL + " TEXT UNIQUE NOT NULL, " +
                COL_PASSWORD + " TEXT NOT NULL, " +
                COL_KM_HOY + " DECIMAL(6,2) DEFAULT 0, " +
                COL_KM_SEMANA + " DECIMAL(7,2) DEFAULT 0" +
                ");";
        db.execSQL(createTable);

        // Datos de ejemplo iniciales
        insertarUsuario(db,"Martín Blanco", "martin.blanco@example.com", "1234", 2.50, 12.70);
        insertarUsuario(db,"Julieta Torres", "julieta.torres@example.com", "abcd", 4.10, 20.30);
        insertarUsuario(db,"Rodolfo Cervantes", "rodolfo.cervantes@example.com", "1111", 0.80, 5.00);
        insertarUsuario(db,"Lucía Fernández", "lucia.fernandez@example.com", "pass123", 6.00, 25.90);
        insertarUsuario(db,"Ezequiel Palleros", "ezequiel.palleros@example.com", "test", 3.30, 15.20);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USUARIOS);
        onCreate(db);
    }

    // --- CRUD ---

    public long insertarUsuario(SQLiteDatabase db, String nombre, String email, String password,
                                double kmHoy, double kmSemana) {
        ContentValues cv = new ContentValues();
        cv.put(COL_NOMBRE, nombre);
        cv.put(COL_EMAIL, email);
        cv.put(COL_PASSWORD, password);
        cv.put(COL_KM_HOY, kmHoy);
        cv.put(COL_KM_SEMANA, kmSemana);
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
}
