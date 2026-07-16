package kr.dojangpan.bokseup

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

data class Stage(
    val key: String,
    val label: String,
    val short: String,
    val offset: Int,
    val color: Int
)

object Schedule {

    val STAGES = listOf(
        Stage("new", "신규", "수강", 0, 0xFF3B4664.toInt()),
        Stage("d1", "1일", "1일 복습", 1, 0xFF0E7C86.toInt()),
        Stage("d7", "1주", "1주 복습", 7, 0xFFA96400.toInt()),
        Stage("d30", "1달", "1달 복습", 30, 0xFF7A3E9D.toInt())
    )

    private fun iso(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayISO(): String = iso().format(Calendar.getInstance().time)

    private fun cal(s: String): Calendar {
        val c = Calendar.getInstance()
        try {
            val p = s.split("-")
            c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        } catch (e: Exception) {
            // 날짜가 이상하면 오늘로 취급
        }
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    /** 시작일 기준 오늘이 몇 일차인가 (1일차부터) */
    fun dayIndex(startIso: String): Int {
        val a = cal(startIso).timeInMillis
        val b = cal(todayISO()).timeInMillis
        return ((b - a) / 86400000.0).roundToInt() + 1
    }

    fun blockCount(total: Int, per: Int): Int =
        if (per <= 0) 0 else ceil(total.toDouble() / per).toInt()

    /** 해당 일차에 이 단계로 해야 할 덩어리 번호. 없으면 null */
    fun blockFor(day: Int, offset: Int, total: Int, per: Int): Int? {
        val b = day - offset
        return if (b >= 1 && b <= blockCount(total, per)) b else null
    }

    fun lectures(b: Int, total: Int, per: Int): IntRange {
        val from = (b - 1) * per + 1
        val to = min(b * per, total)
        return from..to
    }

    fun label(b: Int, total: Int, per: Int): String {
        val r = lectures(b, total, per)
        return if (r.first == r.last) "${r.first}" else "${r.first}–${r.last}"
    }

    fun blockDone(s: JSONObject, b: Int, key: String): Boolean {
        val total = s.optInt("total", 60)
        val per = s.optInt("per", 3)
        val done = s.optJSONObject("done") ?: return false
        for (n in lectures(b, total, per)) {
            if (!done.optBoolean("$n:$key", false)) return false
        }
        return true
    }

    /** 예정일이 지났는데 아직 안 한 덩어리들 (오래된 것부터) */
    fun overdue(s: JSONObject, day: Int, stage: Stage): List<Int> {
        val total = s.optInt("total", 60)
        val per = s.optInt("per", 3)
        val out = ArrayList<Int>()
        for (b in 1..blockCount(total, per)) {
            val due = b + stage.offset
            if (due < day && !blockDone(s, b, stage.key)) out.add(b)
        }
        return out
    }

    /**
     * 위젯·알림이 이 단계에서 지금 보여줄 덩어리.
     * 밀린 게 있으면 가장 오래 밀린 것부터, 없으면 오늘 것.
     * @return 덩어리 번호 to 밀린 것인지 여부
     */
    fun pick(s: JSONObject, day: Int, stage: Stage): Pair<Int, Boolean>? {
        val total = s.optInt("total", 60)
        val per = s.optInt("per", 3)
        val late = overdue(s, day, stage)
        if (late.isNotEmpty()) return Pair(late[0], true)
        val today = blockFor(day, stage.offset, total, per) ?: return null
        return Pair(today, false)
    }

    /** 강의 제목. 없으면 빈 문자열 */
    fun title(s: JSONObject, n: Int): String {
        val t = s.optJSONObject("titles") ?: return ""
        return t.optString(n.toString(), "")
    }

    /** 덩어리의 첫 강의 제목 (위젯·알림에서 한 줄로 보여줄 용도) */
    fun blockTitle(s: JSONObject, b: Int): String {
        val total = s.optInt("total", 60)
        val per = s.optInt("per", 3)
        for (n in lectures(b, total, per)) {
            val t = title(s, n)
            if (t.isNotEmpty()) return t
        }
        return ""
    }

    /** "막힘" 표시로 다시 잡힌 것들 중 오늘까지 차례가 된 것. key는 "덩어리:단계" */
    fun redoDue(s: JSONObject, day: Int): List<Pair<String, Int>> {
        val redo = s.optJSONObject("redo") ?: return emptyList()
        val out = ArrayList<Pair<String, Int>>()
        val it = redo.keys()
        while (it.hasNext()) {
            val k = it.next()
            val due = redo.optInt(k, 0)
            if (due in 1..day) out.add(Pair(k, due))
        }
        out.sortBy { it.second }
        return out
    }

    fun stageOf(key: String): Stage? {
        val p = key.split(":")
        if (p.size < 2) return null
        return STAGES.firstOrNull { it.key == p[1] }
    }

    fun blockOf(key: String): Int = key.split(":")[0].toIntOrNull() ?: 0

    /** 시험일까지 남은 날. 시험일이 없으면 null */
    fun examDDay(s: JSONObject): Int? {
        val e = s.optString("exam", "")
        if (e.isEmpty()) return null
        val a = cal(todayISO()).timeInMillis
        val b = cal(e).timeInMillis
        return ((b - a) / 86400000.0).roundToInt()
    }

    fun setBlock(s: JSONObject, b: Int, key: String, value: Boolean) {
        val total = s.optInt("total", 60)
        val per = s.optInt("per", 3)
        var done = s.optJSONObject("done")
        if (done == null) {
            done = JSONObject()
            s.put("done", done)
        }
        for (n in lectures(b, total, per)) {
            if (value) done.put("$n:$key", true) else done.remove("$n:$key")
        }
    }
}
