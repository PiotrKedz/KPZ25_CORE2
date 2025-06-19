package com.example.innertemp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

class NotificationsManager(private val context: Context) {

    companion object {
        private const val HIGH_TEMP_CHANNEL_ID = "high_temp_channel"
        private const val LOW_TEMP_CHANNEL_ID = "low_temp_channel"
        private const val BATTERY_CHANNEL_ID = "battery_channel"

        private const val HIGH_TEMP_NOTIFICATION_ID = 1001
        private const val LOW_TEMP_NOTIFICATION_ID = 1002
        private const val BATTERY_LOW_NOTIFICATION_ID = 1003
        private const val BATTERY_CRITICAL_NOTIFICATION_ID = 1004

        private const val TEMP_NOTIFICATION_INTERVAL_MINUTES = 5L
        private var lastHighTempNotificationTime: Long = 0
        private var lastLowTempNotificationTime: Long = 0
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun areNotificationsGloballyEnabled(): Boolean {
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("push_notifications", true)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val highTempChannel = NotificationChannel(
                HIGH_TEMP_CHANNEL_ID,
                "High Temperature Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for when temperature exceeds the high threshold."
            }

            val lowTempChannel = NotificationChannel(
                LOW_TEMP_CHANNEL_ID,
                "Low Temperature Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for when temperature goes below the low threshold."
            }

            val batteryChannel = NotificationChannel(
                BATTERY_CHANNEL_ID,
                "Battery Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for low battery levels."
            }

            notificationManager.createNotificationChannel(highTempChannel)
            notificationManager.createNotificationChannel(lowTempChannel)
            notificationManager.createNotificationChannel(batteryChannel)
        }
    }

    private fun canSendRepeatingNotification(lastNotificationTime: Long): Boolean {
        return (System.currentTimeMillis() - lastNotificationTime) >= TimeUnit.MINUTES.toMillis(TEMP_NOTIFICATION_INTERVAL_MINUTES)
    }

    fun showHighTemperatureNotification(currentTemp: Double, threshold: Double) {
        if (!areNotificationsGloballyEnabled()) return

        if (canSendRepeatingNotification(lastHighTempNotificationTime)) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val notification = NotificationCompat.Builder(context, HIGH_TEMP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_thermostat)
                .setContentTitle("High Temperature Alert")
                .setContentText("Core temperature is ${String.format("%.1f", currentTemp)}°C, exceeding the threshold of ${String.format("%.1f", threshold)}°C.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(HIGH_TEMP_NOTIFICATION_ID, notification)
            lastHighTempNotificationTime = System.currentTimeMillis()
        }
    }

    fun showLowTemperatureNotification(currentTemp: Double, threshold: Double) {
        if (!areNotificationsGloballyEnabled()) return

        if (canSendRepeatingNotification(lastLowTempNotificationTime)) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val notification = NotificationCompat.Builder(context, LOW_TEMP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_thermostat)
                .setContentTitle("Low Temperature Alert")
                .setContentText("Core temperature is ${String.format("%.1f", currentTemp)}°C, below the threshold of ${String.format("%.1f", threshold)}°C.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(LOW_TEMP_NOTIFICATION_ID, notification)
            lastLowTempNotificationTime = System.currentTimeMillis()
        }
    }

    fun showBatteryLowNotification(batteryLevel: Int) {
        if (!areNotificationsGloballyEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, BATTERY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_alert)
            .setContentTitle("Low Battery Warning")
            .setContentText("Battery level is at $batteryLevel%. Please charge soon.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BATTERY_LOW_NOTIFICATION_ID, notification)
    }

    fun showBatteryCriticalNotification(batteryLevel: Int) {
        if (!areNotificationsGloballyEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, BATTERY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_alert)
            .setContentTitle("Critical Battery Warning")
            .setContentText("Battery level is critically low at $batteryLevel%.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BATTERY_CRITICAL_NOTIFICATION_ID, notification)
    }

    fun resetTemperatureNotificationTimers() {
        lastHighTempNotificationTime = 0
        lastLowTempNotificationTime = 0
    }
}