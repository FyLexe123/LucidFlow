package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import java.util.Calendar

enum class NotificationCategory(val channelId: String) {
    DIGEST("auraglass_digest"),
    SYNC_SECURITY("auraglass_sync_security"),
    AI_INSIGHT("auraglass_ai_insight")
}

object AuraNotificationManager {

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    NotificationCategory.DIGEST.channelId,
                    context.getString(R.string.notif_channel_digest_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_digest_desc)
                },
                NotificationChannel(
                    NotificationCategory.SYNC_SECURITY.channelId,
                    context.getString(R.string.notif_channel_sync_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_sync_desc)
                },
                NotificationChannel(
                    NotificationCategory.AI_INSIGHT.channelId,
                    context.getString(R.string.notif_channel_ai_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_ai_desc)
                }
            )
            manager.createNotificationChannels(channels)
        }
    }

    fun isInQuietHours(startHour: Int, endHour: Int): Boolean {
        if (startHour == endHour) return false
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (startHour < endHour) {
            currentHour in startHour until endHour
        } else {
            currentHour >= startHour || currentHour < endHour
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendNotification(
        context: Context,
        category: NotificationCategory,
        title: String,
        body: String,
        respectQuietHours: Boolean = true,
        quietStart: Int = 23,
        quietEnd: Int = 7
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        if (respectQuietHours && isInQuietHours(quietStart, quietEnd)) return false

        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
