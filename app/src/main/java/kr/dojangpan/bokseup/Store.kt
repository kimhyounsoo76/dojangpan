package kr.dojangpan.bokseup

import android.content.Context
import org.json.JSONObject

/** 앱·위젯·알림이 함께 보는 하나의 저장소 */
object Store {

    private const val PREF = "dojangpan"
    private const val KEY = "state"

    fun defaultJson(): String =
        """{"start":"${Schedule.todayISO()}","total":60,"per":3,"done":{},"notifyOn":true,"notifyHour":21,"notifyMin":0}"""

    fun raw(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: defaultJson()

    fun read(ctx: Context): JSONObject = try {
        JSONObject(raw(ctx))
    } catch (e: Exception) {
        JSONObject(defaultJson())
    }

    fun write(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, json).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
