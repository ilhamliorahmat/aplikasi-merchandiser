package com.acheter.pos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val MENU_PRINTER_SETTINGS = 1001

    private lateinit var webView: WebView
    lateinit var bridge: WebAppInterface
    lateinit var printerManager: PrinterManager
    
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
            // Hardened: Spoof standard Chrome Mobile User-Agent to bypass strict WAF/Cloudflare blocks
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
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

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    // Route to our graceful native offline screen
                    view?.loadUrl("file:///android_asset/error.html")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    // Also route HTTP 5xx or connection drops to the offline screen
                    view?.loadUrl("file:///android_asset/error.html")
                }
            }
        }
        webView.webChromeClient = WebChromeClient()

        // Inject the Native Hardware Bridge into the Web App
        bridge = WebAppInterface(this, webView)
        printerManager = bridge.printerManager
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val item = menu?.add(0, MENU_PRINTER_SETTINGS, 0, "Printer")
        item?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_PRINTER_SETTINGS) {
            showPrinterManagerDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun showPrinterManagerDialog() {
        val printers = printerManager.getBondedPrinters()
        if (printers.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth Printer Manager")
                .setMessage("No paired Bluetooth devices detected.\n\nPlease open Android Bluetooth Settings, turn on Bluetooth, and pair your thermal receipt printer first.")
                .setPositiveButton("Open BT Settings") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
            return
        }

        val savedMac = printerManager.getSavedPrinterMac()
        var selectedIndex = printers.indexOfFirst { it.address == savedMac }
        if (selectedIndex == -1) selectedIndex = 0

        val itemLabels = printers.map { p ->
            val statusTag = if (p.address == savedMac) "  ★ [ACTIVE]" else ""
            "${p.name}\n${p.address}$statusTag"
        }.toTypedArray()

        var currentPick = selectedIndex

        AlertDialog.Builder(this)
            .setTitle("Assign Thermal Printer")
            .setSingleChoiceItems(itemLabels, selectedIndex) { _, which ->
                currentPick = which
            }
            .setPositiveButton("Assign & Save") { _, _ ->
                if (currentPick in printers.indices) {
                    val chosen = printers[currentPick]
                    printerManager.saveSelectedPrinter(chosen.address, chosen.name)
                    Toast.makeText(this, "Assigned printer: ${chosen.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Test Print") { _, _ ->
                if (currentPick in printers.indices) {
                    val chosen = printers[currentPick]
                    Toast.makeText(this, "Sending test print to ${chosen.name}...", Toast.LENGTH_SHORT).show()
                    printerManager.testPrint(chosen.address) { success, message ->
                        runOnUiThread {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Clear") { _, _ ->
                printerManager.clearSelectedPrinter()
                Toast.makeText(this, "Printer assignment cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
