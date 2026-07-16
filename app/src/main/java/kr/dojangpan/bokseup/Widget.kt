package kr.dojangpan.bokseup

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

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
                val s0 = Store.read(ctx)
                val sts = Schedule.stages(s0)
                if (i in sts.indices && b >= 1) {
                    val s = s0
                    val key = sts[i].key
                    val now = Schedule.blockDone(s, b, key)
                    Schedule.setBlock(s, b, key, !now)
                    Store.write(ctx, s.toString())
                }
                refresh(ctx)
            }
            ACTION_PHASE -> {
                val k = intent.getStringExtra("key")
                if (k != null) {
                    val s = Store.read(ctx)
                    Plan.ensure(s)
                    val all = Plan.todayAll(s, k)
                    val open = Plan.todayOpen(s, k)
                    // 다 했으면 되돌리기, 아니면 오늘 몫 전부 완료 — 잘못 눌러도 한 번 더 누르면 취소된다
                    Plan.setAll(s, all, k, open.isNotEmpty())
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
        const val ACTION_PHASE = "kr.dojangpan.bokseup.PHASE"
        const val ACTION_REDO_DONE = "kr.dojangpan.bokseup.REDO_DONE"

        private val ROWS = intArrayOf(R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3)
        private val DOTS = intArrayOf(R.id.w_dot0, R.id.w_dot1, R.id.w_dot2, R.id.w_dot3)
        private val LABS = intArrayOf(R.id.w_lab0, R.id.w_lab1, R.id.w_lab2, R.id.w_lab3)
        private val NUMS = intArrayOf(R.id.w_num0, R.id.w_num1, R.id.w_num2, R.id.w_num3)
        private val TTS  = intArrayOf(R.id.w_tt0,  R.id.w_tt1,  R.id.w_tt2,  R.id.w_tt3)
        private val CHKS = intArrayOf(R.id.w_chk0, R.id.w_chk1, R.id.w_chk2, R.id.w_chk3)

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, Widget::class.java))
            for (id in ids) render(ctx, mgr, id)
        }

        /** "34–36  조선 후기 경제" 처럼 제목이 있으면 붙여 준다 */
        fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val s = Store.read(ctx)
            if (Plan.isOn(s)) { renderPhase(ctx, mgr, id, s); return }
            renderInterval(ctx, mgr, id, s)
        }

        /** 단계형: 수강 · 1차 · 2차 · 3차 */
        private fun renderPhase(ctx: Context, mgr: AppWidgetManager, id: Int, s: JSONObject) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            if (Plan.ensure(s)) Store.write(ctx, s.toString())

            val dday = Schedule.examDDay(s)
            val head = StringBuilder()
            val t = Schedule.todayISO()
            val start = s.optString("start", "")
            head.append(if (start.isNotEmpty() && t < start) "시작 전" else "오늘 할 공부")
            if (dday != null) {
                head.append("   ·   시험 ")
                head.append(if (dday > 0) "D-" + dday else if (dday == 0) "D-DAY" else "지남")
            }
            rv.setTextViewText(R.id.w_head, head.toString())
            rv.setTextColor(R.id.w_head, 0xFF99A0B2.toInt())
            rv.setViewVisibility(R.id.w_row4, View.GONE)

            var shown = 0
            val phs = Plan.phases(s)
            for (i in phs.indices) {
                if (i >= ROWS.size) break
                val ph = phs[i]
                val all = Plan.todayAll(s, ph.key)
                if (all.isEmpty()) {
                    rv.setViewVisibility(ROWS[i], View.GONE)
                    continue
                }
                shown++
                val open = Plan.todayOpen(s, ph.key)
                rv.setViewVisibility(ROWS[i], View.VISIBLE)
                rv.setInt(DOTS[i], "setBackgroundColor", ph.color)
                rv.setTextViewText(LABS[i], ph.name)
                rv.setTextColor(LABS[i], ph.color)

                val show = if (open.isEmpty()) all else open
                rv.setTextViewText(NUMS[i], Plan.brief(show))
                rv.setTextColor(NUMS[i], 0xFF14172A.toInt())

                val tt = Plan.blockTitle(s, show)
                rv.setTextViewText(TTS[i], if (open.isEmpty()) "끝" else tt)
                rv.setTextColor(TTS[i], 0xFF99A0B2.toInt())

                rv.setTextViewText(CHKS[i], if (open.isEmpty()) "✓" else "○")
                rv.setTextColor(CHKS[i], if (open.isEmpty()) ph.color else 0xFFB0B7C6.toInt())

                val it = Intent(ctx, Widget::class.java).apply {
                    action = ACTION_PHASE
                    putExtra("key", ph.key)
                }
                rv.setOnClickPendingIntent(
                    CHKS[i],
                    PendingIntent.getBroadcast(
                        ctx, id * 20 + i, it,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            rv.setViewVisibility(R.id.w_empty, if (shown == 0) View.VISIBLE else View.GONE)
            rv.setTextViewText(
                R.id.w_empty,
                if (start.isNotEmpty() && t < start) "아직 시작 전입니다" else "오늘 할 공부가 없습니다"
            )
            rv.setOnClickPendingIntent(
                R.id.w_head,
                PendingIntent.getActivity(
                    ctx, id * 20 + 19, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            mgr.updateAppWidget(id, rv)
        }

        /** 간격형: 1일 · 1주 · 1달 (기존) */
        private fun renderInterval(ctx: Context, mgr: AppWidgetManager, id: Int, s: JSONObject) {
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
            rv.setTextColor(R.id.w_head, 0xFF99A0B2.toInt())

            var shown = 0

            val sts = Schedule.stages(s)
            for (i in sts.indices) {
                if (i >= ROWS.size) break
                val st = sts[i]
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

                rv.setTextViewText(NUMS[i], Schedule.label(b, total, per))
                rv.setTextColor(NUMS[i], if (isLate) 0xFFC0362C.toInt() else 0xFF14172A.toInt())
                rv.setTextViewText(TTS[i], if (isLate) "밀림" else Schedule.blockTitle(s, b))
                rv.setTextColor(TTS[i], if (isLate) 0xFFC0362C.toInt() else 0xFF99A0B2.toInt())

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
                val st = Schedule.stageOf(s, k)
                val t = Schedule.blockTitle(s, b)
                val more = if (redo.size > 1) " +" + (redo.size - 1) else ""
                rv.setTextViewText(R.id.w_num4, Schedule.label(b, total, per) + more)
                rv.setTextViewText(
                    R.id.w_tt4,
                    (if (st != null) st.label + (if (t.isNotEmpty()) " · " else "") else "") + t
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
