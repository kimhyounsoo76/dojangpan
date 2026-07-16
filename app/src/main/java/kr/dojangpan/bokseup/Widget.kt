package kr.dojangpan.bokseup

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class Widget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(ctx, mgr, id)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val i = intent.getIntExtra("stage", -1)
                val b = intent.getIntExtra("block", -1)
                if (i in Schedule.STAGES.indices && b >= 1) {
                    val s = Store.read(ctx)
                    val key = Schedule.STAGES[i].key
                    val now = Schedule.blockDone(s, b, key)
                    Schedule.setBlock(s, b, key, !now)
                    Store.write(ctx, s.toString())
                }
                refresh(ctx)
            }
            ACTION_REDO_DONE -> {
                val k = intent.getStringExtra("key")
                if (k != null) {
                    val s = Store.read(ctx)
                    s.optJSONObject("redo")?.remove(k)
                    Store.write(ctx, s.toString())
                }
                refresh(ctx)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "kr.dojangpan.bokseup.TOGGLE"
        const val ACTION_REDO_DONE = "kr.dojangpan.bokseup.REDO_DONE"

        private val ROWS = intArrayOf(R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3)
        private val DOTS = intArrayOf(R.id.w_dot0, R.id.w_dot1, R.id.w_dot2, R.id.w_dot3)
        private val LABS = intArrayOf(R.id.w_lab0, R.id.w_lab1, R.id.w_lab2, R.id.w_lab3)
        private val VALS = intArrayOf(R.id.w_val0, R.id.w_val1, R.id.w_val2, R.id.w_val3)
        private val CHKS = intArrayOf(R.id.w_chk0, R.id.w_chk1, R.id.w_chk2, R.id.w_chk3)

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, Widget::class.java))
            for (id in ids) render(ctx, mgr, id)
        }

        /** "34–36  조선 후기 경제" 처럼 제목이 있으면 붙여 준다 */
        fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val s = Store.read(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget)

            val total = s.optInt("total", 60)
            val per = s.optInt("per", 3)
            val day = Schedule.dayIndex(s.optString("start", Schedule.todayISO()))

            // 머리글: D+일차 · 시험 D-day
            val dday = Schedule.examDDay(s)
            val head = StringBuilder()
            head.append(if (day < 1) "아직 시작 전" else "D+" + day)
            if (dday != null) {
                head.append("   ·   시험 ")
                head.append(if (dday > 0) "D-" + dday else if (dday == 0) "D-DAY" else "지남")
            }
            rv.setTextViewText(R.id.w_head, head.toString())

            var shown = 0

            for (i in Schedule.STAGES.indices) {
                val st = Schedule.STAGES[i]
                val picked = if (day >= 1) Schedule.pick(s, day, st) else null

                if (picked == null) {
                    rv.setViewVisibility(ROWS[i], View.GONE)
                    continue
                }
                val b = picked.first
                val isLate = picked.second

                shown++
                rv.setViewVisibility(ROWS[i], View.VISIBLE)
                rv.setInt(DOTS[i], "setBackgroundColor", st.color)
                rv.setTextViewText(LABS[i], st.label)
                rv.setTextColor(LABS[i], st.color)

                val t = Schedule.blockTitle(s, b)
                val text = Schedule.label(b, total, per) +
                    (if (isLate) "  밀림" else "") +
                    (if (t.isNotEmpty()) "   " + t else "")
                rv.setTextViewText(VALS[i], text)
                rv.setTextColor(VALS[i], if (isLate) 0xFFB3261E.toInt() else 0xFF171B2E.toInt())

                val done = Schedule.blockDone(s, b, st.key)
                rv.setTextViewText(CHKS[i], if (done) "✓" else "○")
                rv.setTextColor(CHKS[i], if (done) st.color else 0xFFB0B7C6.toInt())

                val toggle = Intent(ctx, Widget::class.java).apply {
                    action = ACTION_TOGGLE
                    putExtra("stage", i)
                    putExtra("block", b)
                }
                rv.setOnClickPendingIntent(
                    CHKS[i],
                    PendingIntent.getBroadcast(
                        ctx, id * 20 + i, toggle,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            // 다시 볼 것 (막힘 표시)
            val redo = if (day >= 1) Schedule.redoDue(s, day) else emptyList()
            if (redo.isEmpty()) {
                rv.setViewVisibility(R.id.w_row4, View.GONE)
            } else {
                shown++
                rv.setViewVisibility(R.id.w_row4, View.VISIBLE)
                val k = redo[0].first
                val b = Schedule.blockOf(k)
                val st = Schedule.stageOf(k)
                val t = Schedule.blockTitle(s, b)
                val more = if (redo.size > 1) "  +" + (redo.size - 1) else ""
                rv.setTextViewText(
                    R.id.w_val4,
                    Schedule.label(b, total, per) +
                        (if (st != null) " (" + st.label + ")" else "") +
                        more + (if (t.isNotEmpty()) "   " + t else "")
                )
                rv.setTextViewText(R.id.w_chk4, "○")
                rv.setTextColor(R.id.w_chk4, 0xFFB0B7C6.toInt())

                val doneIntent = Intent(ctx, Widget::class.java).apply {
                    action = ACTION_REDO_DONE
                    putExtra("key", k)
                }
                rv.setOnClickPendingIntent(
                    R.id.w_chk4,
                    PendingIntent.getBroadcast(
                        ctx, id * 20 + 10, doneIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            rv.setViewVisibility(R.id.w_empty, if (shown == 0) View.VISIBLE else View.GONE)

            rv.setOnClickPendingIntent(
                R.id.w_head,
                PendingIntent.getActivity(
                    ctx, id * 20 + 19, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            mgr.updateAppWidget(id, rv)
        }
    }
}
