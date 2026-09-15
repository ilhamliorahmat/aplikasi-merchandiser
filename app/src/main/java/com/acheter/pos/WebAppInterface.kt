package com.acheter.pos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast

class WebAppInterface(private val mContext: Context, private val webView: WebView) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Called from Web App: AndroidBridge.printReceipt(url)
     */
    @JavascriptInterface
    fun printReceipt(url: String) {
        mainHandler.post {
            Toast.makeText(mContext, "Printing Receipt from: ${url.takeLast(20)}...", Toast.LENGTH_LONG).show()
        }
        
        // Example: Notify Web App that printing finished
        val js = "if(typeof window.onHardwareStatusChanged === 'function') { window.onHardwareStatusChanged('PRINTER', 'SUCCESS'); }"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Called from Web App: AndroidBridge.saveBase64Image(base64, filename)
     */
    @JavascriptInterface
    fun saveBase64Image(base64: String, filename: String) {
        mainHandler.post {
            Toast.makeText(mContext, "Saving Image: $filename", Toast.LENGTH_LONG).show()
        }
        // Here you would decode the Base64 string and save it to the Android MediaStore/Gallery
    }
}
