package kr.dojangpan.bokseup

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

data class Phase(
    val key: String,
    val name: String,
    val color: Int
)

data class Stage(
    val key: String,
    val label: String,
    val short: String,
    val offset: Int,
    val color: Int
)

/**
 * 단계형 계획 — 「수강 + 1·2·3차 복습」.
 * 각 단계는 마감일이 있고, 하루 몫 = 남은 것 ÷ 남은 날.
 * 하루 몫은 그날 한 번만 정해서 state.plan 에 박아 둔다(안 그러면 지울 때마다 새로 딸려 나온다).
 */
object Plan {

    private val ROUND_COLORS = intArrayOf(
        0xFF0B6E78.toInt(),   // 1차
        0xFF9A5B00.toInt(),   // 2차
        0xFF6E3691.toInt(),   // 3차
        0xFF2F6B3C.toInt()    // 4차
    )

    /** 복습 횟수는 계획마다 다르다 (2~4회) */
    fun phases(s: JSONObject): List<Phase> {
        val out = ArrayList<Phase>()
        out.add(Phase("new", "수강", 0xFF2E3A5C.toInt()))
        val r = s.optJSONArray("rounds")?.length() ?: 0
        for (i in 1..Math.min(r, 4)) out.add(Phase("p$i", "${i}차", ROUND_COLORS[i - 1]))
        return out
    }

    fun isOn(s: JSONObject): Boolean = s.optString("mode", "interval") == "phase"

    fun to(s: JSONObject, k: String): String {
        if (k == "new") return s.optString("newTo", "")
        val i = k.substring(1).toIntOrNull() ?: return ""
        val arr = s.optJSONArray("rounds") ?: return ""
        if (i < 1 || i > arr.length()) return ""
        return arr.optJSONObject(i - 1)?.optString("to", "") ?: ""
    }

    fun from(s: JSONObject, k: String): String {
        if (k == "new" || k == "p1") return s.optString("start", Schedule.todayISO())
        val i = k.substring(1).toIntOrNull() ?: return ""
        val prev = to(s, "p" + (i - 1))
        return if (prev.isEmpty()) "" else Schedule.plusDays(prev, 1)
    }

    fun isDone(s: JSONObject, n: Int, k: String): Boolean =
        s.optJSONObject("done")?.optBoolean("$n:$k", false) ?: false

    fun isHard(s: JSONObject, n: Int): Boolean =
        s.optJSONObject("hard")?.optBoolean(n.toString(), false) ?: false

    /** 마지막 회차의 키 (복습 2회면 p2, 4회면 p4) */
    fun lastRound(s: JSONObject): String {
        val r = s.optJSONArray("rounds")?.length() ?: 0
        return if (r >= 1) "p" + Math.min(r, 4) else ""
    }

    /** 그 단계에서 아직 안 한 강의. 마지막 회차는 막힌 것부터. */
    fun remaining(s: JSONObject, k: String): List<Int> {
        val total = s.optInt("total", 60)
        val out = ArrayList<Int>()
        for (n in 1..total) if (!isDone(s, n, k)) out.add(n)
        if (k == lastRound(s)) return out.sortedWith(compareByDescending<Int> { isHard(s, it) }.thenBy { it })
        return out
    }

    /** 오늘 몫을 아직 안 정했으면 정한다. 정했으면 그대로 쓴다. 바뀌었으면 true */
    fun ensure(s: JSONObject): Boolean {
        val t = Schedule.todayISO()
        val cur = s.optJSONObject("plan")
        if (cur != null && cur.optString("date") == t) return false
        val p = JSONObject()
        p.put("date", t)
        for (ph in phases(s)) {
            val a = from(s, ph.key)
            val b = to(s, ph.key)
            val arr = JSONArray()
            if (a.isNotEmpty() && b.isNotEmpty() && t >= a && t <= b) {
                val rem = remaining(s, ph.key)
                if (rem.isNotEmpty()) {
                    val left = Schedule.between(t, b) + 1
                    val cnt = Math.max(1, Math.ceil(rem.size.toDouble() / left).toInt())
                    for (n in rem.take(cnt)) arr.put(n)
                }
            }
            p.put(ph.key, arr)
        }
        s.put("plan", p)
        return true
    }

    /** 오늘 몫 중 아직 안 한 것 */
    fun todayOpen(s: JSONObject, k: String): List<Int> {
        val p = s.optJSONObject("plan") ?: return emptyList()
        if (p.optString("date") != Schedule.todayISO()) return emptyList()
        val arr = p.optJSONArray(k) ?: return emptyList()
        val out = ArrayList<Int>()
        for (i in 0 until arr.length()) {
            val n = arr.optInt(i, 0)
            if (n >= 1 && !isDone(s, n, k)) out.add(n)
        }
        return out
    }

    /** 오늘 몫 전체 (완료 포함) */
    fun todayAll(s: JSONObject, k: String): List<Int> {
        val p = s.optJSONObject("plan") ?: return emptyList()
        if (p.optString("date") != Schedule.todayISO()) return emptyList()
        val arr = p.optJSONArray(k) ?: return emptyList()
        val out = ArrayList<Int>()
        for (i in 0 until arr.length()) out.add(arr.optInt(i, 0))
        return out
    }

    fun setAll(s: JSONObject, list: List<Int>, k: String, v: Boolean) {
        var done = s.optJSONObject("done")
        if (done == null) { done = JSONObject(); s.put("done", done) }
        for (n in list) if (v) done.put("$n:$k", true) else done.remove("$n:$k")
    }

    /** "47 48" 또는 "55 59 1 2 +8" */
    fun brief(list: List<Int>, max: Int = 4): String {
        if (list.isEmpty()) return ""
        val head = list.take(max).joinToString(" ")
        val more = list.size - Math.min(max, list.size)
        return if (more > 0) "$head +$more" else head
    }

    fun blockTitle(s: JSONObject, list: List<Int>): String {
        for (n in list) {
            val t = Schedule.title(s, n)
            if (t.isNotEmpty()) return t
        }
        return ""
    }
}

object Schedule {

    /** ISO 날짜에 n일 더하기 */
    fun plusDays(isoDate: String, n: Int): String {
        if (isoDate.isEmpty()) return ""
        val c = cal(isoDate)
        c.add(Calendar.DAY_OF_YEAR, n)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    /** a 에서 b 까지 며칠 */
    fun between(a: String, b: String): Int =
        ((cal(b).timeInMillis - cal(a).timeInMillis) / 86400000.0).roundToInt()


    private val STAGE_KEYS = arrayOf("d1", "d7", "d30")
    private val STAGE_COLS = intArrayOf(0xFF0E7C86.toInt(), 0xFFA96400.toInt(), 0xFF7A3E9D.toInt())
    val DEFAULT_OFFS = intArrayOf(1, 7, 30)

    /** 1 → "1일", 7 → "1주", 30 → "1달", 그 밖엔 "N일" */
    fun offLabel(n: Int): String = when (n) {
        1 -> "1일"; 7 -> "1주"; 14 -> "2주"; 21 -> "3주"
        30 -> "1달"; 60 -> "2달"; 90 -> "3달"
        else -> n.toString() + "일"
    }

    /** 간격은 계획마다 다를 수 있다 (기본 1·7·30) */
    fun stages(s: JSONObject): List<Stage> {
        val arr = s.optJSONArray("offs")
        val out = ArrayList<Stage>()
        out.add(Stage("new", "신규", "수강", 0, 0xFF3B4664.toInt()))
        for (i in 0 until 3) {
            val o = if (arr != null && i < arr.length()) arr.optInt(i, DEFAULT_OFFS[i]) else DEFAULT_OFFS[i]
            if (o <= 0) continue
            out.add(Stage(STAGE_KEYS[i], offLabel(o), offLabel(o) + " 복습", o, STAGE_COLS[i]))
        }
        return out
    }


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

    /** key 는 "1:d7" 처럼 덩어리:단계 꼴이다 */
    fun stageOf(s: JSONObject, key: String): Stage? {
        val p = key.split(":")
        if (p.size < 2) return null
        return stages(s).firstOrNull { it.key == p[1] }
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
