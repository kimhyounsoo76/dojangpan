package kr.dojangpan.bokseup

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

object Notifier {

    private const val CHANNEL = "daily"
    private const val REQ = 4242
    private const val REQ_MIDNIGHT = 4243
    private const val NOTI_ID = 7

    private fun alarmPI(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun midnightPI(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ_MIDNIGHT,
            Intent(ctx, AlarmReceiver::class.java).setAction("MIDNIGHT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 자정 직후 위젯이 새 날짜로 바뀌도록 예약 */
    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 2)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, midnightPI(ctx))
        } catch (e: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, c.timeInMillis, midnightPI(ctx))
        }
    }

    /** 설정에 맞춰 다음 알림을 다시 예약한다 */
    fun reschedule(ctx: Context) {
        scheduleMidnight(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmPI(ctx)
        val s = Store.read(ctx)

        if (!s.optBoolean("notifyOn", true)) {
            am.cancel(pi)
            return
        }

        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, s.optInt("notifyHour", 21))
        c.set(Calendar.MINUTE, s.optInt("notifyMin", 0))
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)

        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
        } catch (e: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
        }
    }

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "복습 알림", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    /** 단계형: 오늘 몫 중 안 한 것만 */
    private fun notifyPhase(ctx: Context, s: org.json.JSONObject) {
        if (Plan.ensure(s)) Store.write(ctx, s.toString())

        val t = Schedule.todayISO()
        val start = s.optString("start", "")
        if (start.isNotEmpty() && t < start) return

        val parts = ArrayList<String>()
        for (ph in Plan.phases(s)) {
            val open = Plan.todayOpen(s, ph.key)
            if (open.isEmpty()) continue
            val tt = Plan.blockTitle(s, open)
            parts.add(ph.name + "  " + Plan.brief(open, 6) + (if (tt.isNotEmpty()) "  " + tt else ""))
        }
        if (parts.isEmpty()) return

        val dday = Schedule.examDDay(s)
        val title = "오늘 남은 공부" + (if (dday != null && dday >= 0) "  ·  시험 D-" + dday else "")

        val pi = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(parts.joinToString("  ·  "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString("\n")))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTI_ID, n)
        } catch (e: SecurityException) {
        }
    }

    /** 알림이 실제로 뜨는지 지금 바로 확인 */
    fun testNotify(ctx: Context) {
        channel(ctx)
        val pi = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("알림 확인")
            .setContentText("이 알림이 보이면 정상입니다")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTI_ID + 1, n)
        } catch (e: SecurityException) {
        }
    }

    /** 오늘 아직 안 한 것만 모아 알린다. 다 했으면 조용히 넘어간다. */
    fun notifyNow(ctx: Context) {
        channel(ctx)
        val s = Store.read(ctx)
        if (Plan.isOn(s)) { notifyPhase(ctx, s); return }
        val total = s.optInt("total", 60)
        val per = s.optInt("per", 3)
        val day = Schedule.dayIndex(s.optString("start", Schedule.todayISO()))
        if (day < 1) return

        fun line(prefix: String, b: Int, suffix: String): String {
            val t = Schedule.blockTitle(s, b)
            return prefix + "  " + Schedule.label(b, total, per) + suffix +
                (if (t.isNotEmpty()) "  " + t else "")
        }

        val parts = ArrayList<String>()
        var late = 0
        for (st in Schedule.stages(s)) {
            val od = Schedule.overdue(s, day, st)
            late += od.size
            for (b in od) parts.add(line(st.short, b, "  (밀림)"))
            val b = Schedule.blockFor(day, st.offset, total, per) ?: continue
            if (Schedule.blockDone(s, b, st.key)) continue
            parts.add(line(st.short, b, ""))
        }
        for (r in Schedule.redoDue(s, day)) {
            val b = Schedule.blockOf(r.first)
            val st = Schedule.stageOf(s, r.first)
            parts.add(line("다시", b, if (st != null) "  (" + st.label + ")" else ""))
        }
        if (parts.isEmpty()) return

        val dday = Schedule.examDDay(s)
        val ddayTxt = if (dday != null && dday >= 0) "  ·  시험 D-" + dday else ""
        val title = (if (late > 0) "D+" + day + " · 밀린 복습 " + late + "개"
                     else "D+" + day + " · 오늘 남은 복습") + ddayTxt

        val pi = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(parts.joinToString("  ·  "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString("\n")))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTI_ID, n)
        } catch (e: SecurityException) {
            // 알림 권한이 없으면 조용히 넘어간다
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "MIDNIGHT") {
            Notifier.scheduleMidnight(ctx)
            Widget.refresh(ctx)
            return
        }
        Notifier.notifyNow(ctx)
        Notifier.reschedule(ctx)
        Widget.refresh(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.reschedule(ctx)
        Widget.refresh(ctx)
    }
}
