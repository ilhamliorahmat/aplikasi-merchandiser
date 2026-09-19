package com.acheter.pos

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import android.content.Context

class WebAppInterface(private val mContext: Context, private val webView: WebView) {

    val printerManager = PrinterManager(mContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun printReceiptImage(base64Data: String) {
        try {
            val cleanBase64 = if (base64Data.contains(",")) {
                base64Data.split(",")[1]
            } else {
                base64Data
            }
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

            val assignedMac = printerManager.getSavedPrinterMac()
            if (assignedMac.isNullOrBlank()) {
                mainHandler.post {
                    Toast.makeText(mContext, "No printer assigned.", Toast.LENGTH_LONG).show()
                }
                return
            }

            printerManager.printBitmap(assignedMac, bitmap) { success, msg ->
                mainHandler.post {
                    Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show()
                    val js = "if(typeof window.onHardwareStatusChanged === 'function') { window.onHardwareStatusChanged('PRINTER', '${if(success) "SUCCESS" else "ERROR"}', '$msg'); }"
                    this.webView.evaluateJavascript(js, null)
                }
            }
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error printing image", e)
            mainHandler.post {
                Toast.makeText(mContext, "Print error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @JavascriptInterface
    fun isPrinterAssigned(): Boolean {
        return !printerManager.getSavedPrinterMac().isNullOrBlank()
    }

    @JavascriptInterface
    fun getBondedDevices(): String {
        return printerManager.getBondedDevices().toString()
    }

    @JavascriptInterface
    fun getPrinterDiagnostics(): String {
        return printerManager.getPrinterDiagnostics().toString()
    }

    @JavascriptInterface
    fun assignPrinter(mac: String, name: String) {
        printerManager.saveSelectedPrinter(mac, name)
        mainHandler.post {
            Toast.makeText(mContext, "Printer assigned: $name", Toast.LENGTH_SHORT).show()
        }
    }

}
