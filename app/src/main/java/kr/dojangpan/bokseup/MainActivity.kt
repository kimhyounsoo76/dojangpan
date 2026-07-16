package kr.dojangpan.bokseup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var pendingExport: String? = null

    /** 화면에 팝업이 떠 있는지 — 뒤로가기가 앱을 닫지 않고 팝업만 닫도록 */
    @Volatile
    private var dialogOpen = false

    private val createDoc =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val data = pendingExport
            pendingExport = null
            if (uri == null || data == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                toast("저장했습니다")
            } catch (e: Exception) {
                toast("저장하지 못했습니다")
            }
        }

    private val openDoc =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)!!
                    .bufferedReader().use { it.readText() }
                val o = JSONObject(text)
                if (!o.has("start") || !o.has("total")) throw IllegalArgumentException()
                Store.write(this, text)
                reloadWeb()
                Notifier.reschedule(this)
                Widget.refresh(this)
                toast("불러왔습니다")
            } catch (e: Exception) {
                toast("이 파일은 복습 도장판 저장 파일이 아닙니다")
            }
        }

    private val askNotify =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { reloadWeb() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.textZoom = 100
        web.webViewClient = WebViewClient()

        // 이게 없으면 confirm()/alert() 창이 아예 뜨지 않고,
        // confirm() 은 무조건 false 를 돌려준다 → 「지우기」·「밀기」가 먹통이 된다.
        web.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(v: WebView?, url: String?, msg: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(msg ?: "")
                    .setPositiveButton("확인") { _, _ -> r.confirm() }
                    .setOnCancelListener { r.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(v: WebView?, url: String?, msg: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(msg ?: "")
                    .setPositiveButton("계속") { _, _ -> r.confirm() }
                    .setNegativeButton("취소") { _, _ -> r.cancel() }
                    .setOnCancelListener { r.cancel() }
                    .show()
                return true
            }
        }

        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (dialogOpen) {
                    web.evaluateJavascript("window.__closeTop && window.__closeTop();", null)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        Notifier.reschedule(this)
    }

    override fun onResume() {
        super.onResume()
        reloadWeb()
    }

    private fun reloadWeb() {
        if (::web.isInitialized) {
            web.post { web.evaluateJavascript("window.__reload && window.__reload();", null) }
        }
    }

    private fun toast(s: String) = runOnUiThread {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    inner class Bridge {

        @JavascriptInterface
        fun isNative(): Boolean = true

        @JavascriptInterface
        fun getState(): String = Store.raw(this@MainActivity)

        @JavascriptInterface
        fun setState(json: String) {
            Store.write(this@MainActivity, json)
            Widget.refresh(this@MainActivity)
        }

        @JavascriptInterface
        fun syncAlarm() {
            Notifier.reschedule(this@MainActivity)
        }

        @JavascriptInterface
        fun setDialogOpen(open: Boolean) {
            dialogOpen = open
        }

        /** 알림 권한이 켜져 있는가 */
        @JavascriptInterface
        fun notifyAllowed(): Boolean =
            NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()

        /** 배터리 절약에서 빠져 있는가 — 아니면 알림이 씹힐 수 있다 */
        @JavascriptInterface
        fun batteryFree(): Boolean {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }

        @JavascriptInterface
        fun openBattery() {
            runOnUiThread {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:" + packageName))
                    )
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e2: Exception) {
                        toast("설정 화면을 열지 못했습니다")
                    }
                }
            }
        }

        @JavascriptInterface
        fun openAppSettings() {
            runOnUiThread {
                try {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:" + packageName))
                    )
                } catch (e: Exception) {
                    toast("설정 화면을 열지 못했습니다")
                }
            }
        }

        /** 알림이 제대로 오는지 지금 확인해 보기 */
        @JavascriptInterface
        fun testNotify() {
            Notifier.testNotify(this@MainActivity)
        }

        @JavascriptInterface
        fun exportState(json: String) {
            pendingExport = json
            runOnUiThread { createDoc.launch("복습도장판_" + Schedule.todayISO() + ".json") }
        }

        @JavascriptInterface
        fun importState() {
            runOnUiThread { openDoc.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }

        @JavascriptInterface
        fun resetAll() {
            Store.clear(this@MainActivity)
            Notifier.reschedule(this@MainActivity)
            Widget.refresh(this@MainActivity)
        }
    }
}
