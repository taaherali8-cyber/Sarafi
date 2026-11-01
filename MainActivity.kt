package com.example.sarrafi

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.print.PrintAttributes
import android.print.PrintManager
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var printView: WebView? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
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
        fun saveBackup(text: String) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "sarrafi-backup-${System.currentTimeMillis()}.json")
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }

        @JavascriptInterface
        fun saveFile(name: String, mime: String, b64: String) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) contentResolver.openOutputStream(uri)?.use {
                it.write(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
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
