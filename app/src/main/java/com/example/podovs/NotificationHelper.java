package com.example.podovs;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID = "podovs_channel";

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PodoVS Notificaciones",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notificaciones de metas y nivel");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void showGoalCompleted(Context context, String goalType) {
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Meta completada 🎉")
                .setContentText("Completaste tu meta " + goalType + ". ¡Bien hecho!")
                .setAutoCancel(true);

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // El permiso no fue concedido, no mostramos la notificación
            return;
        }
        NotificationManagerCompat.from(context).notify(
                (int) System.currentTimeMillis(),
                builder.build()
        );
    }

    public static void showLevelUp(Context context, int newLevel) {
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_big_on)
                .setContentTitle("¡Subiste de nivel! ⭐")
                .setContentText("Ahora sos nivel " + newLevel + ". ¡Seguí así!")
                .setAutoCancel(true);

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // El permiso no fue concedido, no mostramos la notificación
            return;
        }
        NotificationManagerCompat.from(context).notify(
                (int) System.currentTimeMillis(),
                builder.build()
        );
    }
}
