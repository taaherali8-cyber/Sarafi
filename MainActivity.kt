package com.example.sarrafi

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var printView: WebView? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBytes: ByteArray? = null
    private var pendingName: String = ""

    // انتخاب فایل (برای بازیابی پشتیبان و پیوست سند)
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    // ذخیره‌ی فایل با پنجره‌ی استاندارد اندروید (روی همه‌ی نسخه‌ها و گوشی‌ها کار می‌کند)
    private val saver = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        val bytes = pendingBytes
        var ok = false
        if (res.resultCode == RESULT_OK && uri != null && bytes != null) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ok = true
            } catch (e: Exception) {
                ok = false
            }
        }
        val n = pendingName.replace("'", "")
        pendingBytes = null
        web.evaluateJavascript("if(typeof bkDone==='function')bkDone($ok,'$n')", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = true
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                picker.launch("*/*")
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                loader.shouldInterceptRequest(request.url)
        }
        web.addJavascriptInterface(Bridge(), "AndroidBio")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(function(){return typeof appBack==='function'&&appBack()})()") { r ->
                    if (r != "true") finish()
                }
            }
        })
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        web.evaluateJavascript("if(typeof applyTheme==='function')applyTheme()", null)
    }

    inner class Bridge {
        @JavascriptInterface
        fun available(): Boolean =
            BiometricManager.from(this@MainActivity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

        @JavascriptInterface
        fun authenticate() {
            runOnUiThread {
                val prompt = BiometricPrompt(
                    this@MainActivity,
                    ContextCompat.getMainExecutor(this@MainActivity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                            web.evaluateJavascript("unlock()", null)
                        }
                    })
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("ورود به دفتر صرافی")
                    .setNegativeButtonText("استفاده از رمز")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()
                prompt.authenticate(info)
            }
        }

        @JavascriptInterface
        fun saveFile(name: String, mime: String, b64: String) {
            pendingBytes = Base64.decode(b64, Base64.DEFAULT)
            pendingName = name
            runOnUiThread {
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    putExtra(Intent.EXTRA_TITLE, name)
                }
                try {
                    saver.launch(i)
                } catch (e: Exception) {
                    pendingBytes = null
                    web.evaluateJavascript("if(typeof bkDone==='function')bkDone(false,'')", null)
                }
            }
        }

        @JavascriptInterface
        fun shareFile(name: String, mime: String, b64: String) {
            try {
                val dir = File(cacheDir, "backups")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, name)
                f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "ارسال پشتیبان")) }
            } catch (e: Exception) {
                web.post { web.evaluateJavascript("if(typeof bkDone==='function')bkDone(false,'')", null) }
            }
        }

        @JavascriptInterface
        fun setSecure(on: Boolean) {
            runOnUiThread {
                if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        @JavascriptInterface
        fun printReport(html: String, title: String) {
            runOnUiThread {
                val wv = WebView(this@MainActivity)
                printView = wv
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val pm = this@MainActivity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        pm.print(
                            title,
                            view.createPrintDocumentAdapter(title),
                            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                        )
                    }
                }
                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    }
}
