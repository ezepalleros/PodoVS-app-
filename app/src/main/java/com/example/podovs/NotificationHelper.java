package com.example.podovs;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class NotificationHelper {

    private static final String CHANNEL_ID = "podovs_channel";
    private static final String CHANNEL_NAME = "PodoVS Notificaciones";
    private static final String CHANNEL_DESC = "Notificaciones de metas y nivel";

    private static final String PREFS = "podovs_notif_store";
    private static final String KEY_LIST = "list";
    private static final int MAX_STORED = 50;

    private NotificationHelper() {
    }

    // ================== API pública ==================

    public static void showGoalClaimAvailable(Context context, String goalType) {
        String title = "¡Reclamo disponible!";
        String body = "Tenés la meta " + goalType + " lista para reclamar.";
        pushAndLog(context, title, body, android.R.drawable.ic_dialog_info);
    }

    public static void showLevelUp(Context context, int newLevel) {
        String title = "¡Subiste de nivel! ⭐";
        String body = "Ahora sos nivel " + newLevel + ". ¡Seguí así!";
        pushAndLog(context, title, body, android.R.drawable.star_big_on);
    }

    public static List<Item> getLast(Context context, int n) {
        ArrayList<Item> out = new ArrayList<>();
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY_LIST, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = arr.length() - 1; i >= 0 && out.size() < n; i--) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Item(
                        o.optLong("ts", 0L),
                        o.optString("title", ""),
                        o.optString("text", "")
                ));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void pushAndLog(Context context, String title, String body, int smallIcon) {
        log(context, title, body);
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            if (canPostNotifications(context)) {
                NotificationManagerCompat.from(context)
                        .notify((int) System.currentTimeMillis(), builder.build());
            }
        } catch (SecurityException ignored) {
        }
    }

    private static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESC);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private static void log(Context context, String title, String text) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY_LIST, "[]");
            JSONArray arr = new JSONArray(raw);

            JSONObject obj = new JSONObject();
            obj.put("ts", System.currentTimeMillis());
            obj.put("title", title == null ? "" : title);
            obj.put("text", text == null ? "" : text);

            arr.put(obj);

            if (arr.length() > MAX_STORED) {
                JSONArray trimmed = new JSONArray();
                for (int i = arr.length() - MAX_STORED; i < arr.length(); i++) {
                    trimmed.put(arr.getJSONObject(i));
                }
                arr = trimmed;
            }

            sp.edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static class Item {
        public final long ts;
        public final String title;
        public final String text;

        public Item(long ts, String title, String text) {
            this.ts = ts;
            this.title = title;
            this.text = text;
        }
    }
}
