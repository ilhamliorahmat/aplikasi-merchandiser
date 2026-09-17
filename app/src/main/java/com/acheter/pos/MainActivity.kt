package com.acheter.pos

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    
    // Buffer for HID Keyboard Scanners
    private var barcodeBuffer = java.lang.StringBuilder()
    private var lastKeyTime = 0L
    private val SCANNER_THRESHOLD_MS = 50L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request Bluetooth permissions
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }

        // Removed immersive full-screen mode for debugging purposes.
        // The status bar and navigation buttons will now be visible.
        supportActionBar?.show()

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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                
                // Let the WebView load standard web pages
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                
                // Handle custom schemes like whatsapp://, intent://, tel:, mailto:
                return try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to launch intent for: $url", e)
                    android.widget.Toast.makeText(this@MainActivity, "No app installed to handle this link.", android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
        webView.webChromeClient = WebChromeClient()

        // Inject the Native Hardware Bridge into the Web App
        val bridge = WebAppInterface(this, webView)
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.addJavascriptInterface(bridge, "POSNativeBridge")

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
    
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
