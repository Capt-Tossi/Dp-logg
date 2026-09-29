package no.sordal.dpcompanion

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object RenewalReminder {
    private const val CHANNEL = "certificate_renewal"
    private const val REQUEST = 6101

    private fun pending(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, RenewalReceiver::class.java).setAction("no.sordal.dpcompanion.RENEWAL"),
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    fun sync(context: Context, data: AppData) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        pending(context, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        if (!data.renewalReminderEnabled) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val opening = CertificateRenewal.openingDate(data.certificateExpiry) ?: return
        // The status on the certificate screen covers a window which is already open.
        if (opening.isBefore(LocalDate.now())) return
        val whenMillis = opening.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (whenMillis <= System.currentTimeMillis()) return
        pending(context, PendingIntent.FLAG_UPDATE_CURRENT)?.let { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, it) }
    }

    fun notifyIfDue(context: Context) {
        val data = runCatching { Store(context).load() }.getOrNull() ?: return
        if (!data.renewalReminderEnabled) return
        val opening = CertificateRenewal.openingDate(data.certificateExpiry) ?: return
        val expiry = runCatching { LocalDate.parse(data.certificateExpiry) }.getOrNull() ?: return
        val today = LocalDate.now()
        if (today.isBefore(opening) || today.isAfter(expiry)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "DP certificate renewal", NotificationManager.IMPORTANCE_DEFAULT))
        val launch = PendingIntent.getActivity(context, REQUEST, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(REQUEST, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.nav_me).setContentTitle("DP certificate renewal window open")
            .setContentText("You can start your NI online revalidation application.")
            .setContentIntent(launch).setAutoCancel(true).build())
    }
}

class RenewalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) {
            runCatching { RenewalReminder.sync(context, Store(context).load()) }
        } else RenewalReminder.notifyIfDue(context)
    }
}
