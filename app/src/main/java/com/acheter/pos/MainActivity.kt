package com.acheter.pos

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    
    // Buffer for HID Keyboard Scanners
    private var barcodeBuffer = java.lang.StringBuilder()
    private var lastKeyTime = 0L
    private val SCANNER_THRESHOLD_MS = 50L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide Action Bar and set immersive fullscreen mode
        supportActionBar?.hide()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        webView = WebView(this)
        setContentView(webView)

        // Configure WebView for modern web apps
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        // Enable debugging via chrome://inspect/#devices
        WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // Inject the Native Hardware Bridge into the Web App
        webView.addJavascriptInterface(WebAppInterface(this, webView), "POSHardware")

        // Load the Dedicated POS Web App
        webView.loadUrl("https://acheter.xo.je")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - lastKeyTime
            lastKeyTime = currentTime

            // Check for HID Barcode Scanner Input
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (barcodeBuffer.isNotEmpty()) {
                    val scannedCode = barcodeBuffer.toString()
                    barcodeBuffer.clear()
                    
                    // Push the scanned barcode up to the Web App via Javascript
                    runOnUiThread {
                        val js = "if(typeof window.onBarcodeScanned === 'function') { window.onBarcodeScanned('$scannedCode'); }"
                        webView.evaluateJavascript(js, null)
                    }
                    return true // Consume the enter key event
                }
            } else if (timeDiff < SCANNER_THRESHOLD_MS || barcodeBuffer.isEmpty()) {
                val char = event.unicodeChar.toChar()
                if (char.isLetterOrDigit() || char.isWhitespace() || event.unicodeChar > 0) {
                    barcodeBuffer.append(char)
                }
            } else {
                // Keystrokes were too slow; it's a human typing. Clear the buffer.
                barcodeBuffer.clear()
            }
        }
        return super.dispatchKeyEvent(event)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            // Intentionally block closing the app via the back button for kiosk/POS mode
            // super.onBackPressed()
        }
    }
}
