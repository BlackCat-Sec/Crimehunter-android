package com.gowda.crimehunter

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = findViewById(R.id.gameWebView)
        configureWebView()
        installBackHandler()

        if (savedInstanceState == null) {
            webView.loadUrl(GAME_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        webView.onPause()
        webView.pauseTimers()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.apply {
            removeJavascriptInterface(JS_BRIDGE_NAME)
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        super.onDestroy()
    }

    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.setBackgroundColor(Color.BLACK)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.keepScreenOn = true
        webView.addJavascriptInterface(GameJavascriptBridge(this), JS_BRIDGE_NAME)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString CrimehunterAndroid/1.0.0"
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    TAG,
                    "${consoleMessage.messageLevel()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}",
                )
                return true
            }
        }

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest,
            ) = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest,
            ): Boolean {
                return handleExternalUrl(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleExternalUrl(Uri.parse(url))
            }
        }
    }

    private fun handleExternalUrl(url: Uri): Boolean {
        return if (isAppAssetUrl(url)) {
            false
        } else {
            GameJavascriptBridge(this).openExternalUrl(url.toString())
            true
        }
    }

    private fun isAppAssetUrl(url: Uri): Boolean {
        return url.scheme == "https" &&
            url.host == ASSET_HOST &&
            url.path?.startsWith("/assets/") == true
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            },
        )
    }

    private fun hideSystemUi() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        private const val TAG = "Crimehunter"
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val GAME_URL = "https://$ASSET_HOST/assets/web/index.html"
        private const val JS_BRIDGE_NAME = "AndroidBridge"
    }
}
