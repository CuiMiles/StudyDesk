package io.github.cuimiles.studydesk

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/** Native LAN client. All learning records and provider keys remain on the server. */
class LanActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var connectionBar: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var tts: TextToSpeech
    private var voiceReady = false
    private var serverAddress = ""
    private var loadFailed = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: String? = null
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("lan_connection", MODE_PRIVATE) }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCallback = null
    }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val source = pendingDownload
        pendingDownload = null
        if (uri != null && source != null && trusted(source)) downloadBackup(source, uri)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 247, 242)
        window.navigationBarColor = Color.rgb(252, 253, 249)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 247, 242))
            fitsSystemWindows = true
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(4), dp(10), dp(4))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        connectionBar = bar
        status = TextView(this).apply {
            text = "连接失败 · 点击修改地址"; textSize = 12f
            setTextColor(Color.rgb(95, 116, 83))
            setOnClickListener { addressDialog() }
        }
        bar.addView(status, LinearLayout.LayoutParams(0, dp(40), 1f))
        bar.addView(Button(this).apply {
            text = "设置"; textSize = 11f; minWidth = 0; minimumWidth = 0
            setOnClickListener { addressDialog() }
        }, LinearLayout.LayoutParams(dp(92), dp(42)))
        root.addView(bar)
        bar.visibility = View.GONE
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(246, 247, 242))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true // Documents explicitly chosen through the system picker.
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setSupportMultipleWindows(false)
            settings.userAgentString += " StudyDeskAndroid/1.0"
            addJavascriptInterface(NativeBridge(), "StudyDeskNative")
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    loadFailed = false
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (trusted(url) && !request.url.path.orEmpty().endsWith(".pdf")) return false
                    if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) openExternal(request.url)
                    return true
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (trusted(request.url.toString())) return null
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (trusted(url) && !loadFailed) {
                        connectionBar.visibility = View.GONE
                        this@LanActivity.progress.visibility = View.GONE
                    }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        loadFailed = true
                        status.text = "连接中断 · 点击修改地址"
                        connectionBar.visibility = View.VISIBLE
                        this@LanActivity.progress.visibility = View.GONE
                        message("请确认手机与服务器在同一局域网，且后台服务正在运行。")
                    }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) {
                        loadFailed = true
                        status.text = "服务器暂不可用 · ${response.statusCode}"
                        connectionBar.visibility = View.VISIBLE
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, value: Int) {
                    this@LanActivity.progress.progress = value
                    this@LanActivity.progress.visibility = if (value < 100) View.VISIBLE else View.GONE
                }
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    if (!trusted(view.url.orEmpty())) return false
                    fileCallback?.onReceiveValue(null); fileCallback = callback
                    filePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    return true
                }
            }
            setDownloadListener { url, _, _, _, _ ->
                if (trusted(url) && Uri.parse(url).path == "/api/backup") {
                    pendingDownload = url; saveFile.launch("studydesk-backup.json")
                } else if (trusted(url)) openExternal(Uri.parse(url))
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        tts = TextToSpeech(this, this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        serverAddress = prefs.getString("server", "").orEmpty()
        if (serverAddress.isBlank()) addressDialog() else connect(serverAddress)
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private fun message(value: String) = runOnUiThread { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun trusted(url: String) = LanAddress.sameOrigin(serverAddress, url)
    private fun openExternal(uri: Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { message("没有可打开这个链接的浏览器") }
    }
    private fun connect(value: String) {
        serverAddress = LanAddress.normalize(value)
        prefs.edit().putString("server", serverAddress).apply()
        connectionBar.visibility = View.GONE
        this@LanActivity.progress.visibility = View.VISIBLE
        web.loadUrl(serverAddress)
    }
    private fun addressDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(8)) }
        layout.addView(TextView(this).apply {
            text = "输入电脑的局域网 IP，默认端口 8765。\n例如：10.184.17.163\n模拟器连接本机服务：10.0.2.2"
            textSize = 13f; setTextColor(Color.rgb(111, 126, 99))
        })
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            hint = "192.168.1.100"; setText(serverAddress)
        }
        layout.addView(input)
        val error = TextView(this).apply { setTextColor(Color.rgb(164, 83, 63)); textSize = 12f }
        layout.addView(error)
        val dialog = AlertDialog.Builder(this).setTitle("连接学习空间").setView(layout)
            .setPositiveButton("连接", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching { LanAddress.normalize(input.text.toString()) }.onSuccess { address ->
                    connect(address); dialog.dismiss()
                }.onFailure { error.text = it.message ?: "请输入有效的局域网地址" }
            }
        }
        dialog.show()
    }
    inner class NativeBridge {
        @JavascriptInterface fun speak(value: String) {
            runOnUiThread {
                if (!trusted(web.url.orEmpty()) || value.length > 300) return@runOnUiThread
                if (voiceReady) tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "studydesk-word")
                else message("请在系统设置中安装英语文字转语音引擎或英语语音包。")
            }
        }
        @JavascriptInterface fun openSettings() { runOnUiThread { if (trusted(web.url.orEmpty())) addressDialog() } }
    }
    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val available = tts.setLanguage(Locale.US)
            val local = tts.voices?.firstOrNull { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            if (local != null) tts.voice = local
            voiceReady = available != TextToSpeech.LANG_MISSING_DATA && available != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(.85f)
        }
    }
    private fun downloadBackup(source: String, destination: Uri) {
        io.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(source).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000; connection.readTimeout = 30_000
                check(connection.responseCode == 200 && connection.contentType.orEmpty().startsWith("application/json"))
                contentResolver.openOutputStream(destination, "w")!!.use { out ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192); var total = 0
                        while (true) { val read = input.read(buffer); if (read < 0) break
                            total += read; check(total <= 32_000_000); out.write(buffer, 0, read) }
                    }
                }
                message("完整学习备份已保存")
            } catch (_: Exception) { message("备份下载失败，请检查局域网连接后重新导出。") }
            finally { connection?.disconnect() }
        }
    }
    override fun onPause() { tts.stop(); super.onPause() }
    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        web.removeJavascriptInterface("StudyDeskNative"); web.destroy()
        tts.stop(); tts.shutdown(); io.shutdownNow(); super.onDestroy()
    }
}
