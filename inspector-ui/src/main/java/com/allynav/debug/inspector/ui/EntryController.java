package com.allynav.debug.inspector.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import com.allynav.debug.inspector.api.EntryConfig;

import java.util.Collections;

final class EntryController {
    private static final String CHANNEL_ID = "debug_inspector_capture";
    private static final int NOTIFICATION_ID = 0xD061;
    private static boolean installed;
    private static ShakeListener shakeListener;

    private EntryController() {
    }

    static synchronized void install(Context context, EntryConfig config) {
        // Application 初始化通常早于 Android 13 的运行时授权；授权返回后允许补发通知。
        if (installed) {
            if (config.isNotificationEnabled()) installNotification(context);
            return;
        }
        installed = true;
        if (config.isNotificationEnabled()) installNotification(context);
        if (config.isShortcutEnabled()) installShortcut(context);
        if (config.isShakeEnabled()) installShake(context);
        cleanupExports(context);
    }

    static boolean notificationPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static void installNotification(Context context) {
        if (!notificationPermissionGranted(context)) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.inspector_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(context.getString(R.string.inspector_notification_text));
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, InspectorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        Notification notification = builder.setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(context.getString(R.string.inspector_name))
                .setContentText(context.getString(R.string.inspector_notification_text))
                .setContentIntent(pending).setOngoing(true).setShowWhen(false).build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    private static void installShortcut(Context context) {
        if (Build.VERSION.SDK_INT < 25) return;
        ShortcutManager manager = (ShortcutManager) context.getSystemService(ShortcutManager.class);
        if (manager == null) return;
        for (ShortcutInfo existing : manager.getPinnedShortcuts()) {
            if ("debug_inspector".equals(existing.getId())) return;
        }
        for (ShortcutInfo existing : manager.getDynamicShortcuts()) {
            if ("debug_inspector".equals(existing.getId())) return;
        }
        Intent intent = new Intent(context, InspectorActivity.class).setAction(Intent.ACTION_VIEW);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, "debug_inspector")
                .setShortLabel(context.getString(R.string.inspector_name))
                .setLongLabel(context.getString(R.string.inspector_notification_text))
                .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_info_details))
                .setIntent(intent).build();
        if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinShortcutSupported()) {
            manager.requestPinShortcut(shortcut, null);
        } else {
            manager.setDynamicShortcuts(Collections.singletonList(shortcut));
        }
    }

    private static void installShake(Context context) {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) return;
        Sensor accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) return;
        shakeListener = new ShakeListener(context);
        manager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    private static void cleanupExports(Context context) {
        java.io.File directory = ShareFiles.exportDirectory(context);
        java.io.File[] files = directory.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        for (java.io.File file : files) if (file.lastModified() < cutoff) file.delete();
    }

    private static final class ShakeListener implements SensorEventListener {
        private final Context context;
        private long lastOpen;
        ShakeListener(Context context) { this.context = context.getApplicationContext(); }
        @Override public void onSensorChanged(SensorEvent event) {
            float x = event.values[0] / SensorManager.GRAVITY_EARTH;
            float y = event.values[1] / SensorManager.GRAVITY_EARTH;
            float z = event.values[2] / SensorManager.GRAVITY_EARTH;
            double force = Math.sqrt(x * x + y * y + z * z);
            long now = System.currentTimeMillis();
            if (force > 2.7 && now - lastOpen > 1500) {
                lastOpen = now;
                InspectorUi.open(context);
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    }
}
