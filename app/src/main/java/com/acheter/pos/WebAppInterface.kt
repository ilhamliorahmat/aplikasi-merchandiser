package com.acheter.pos

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast

class WebAppInterface(private val mContext: Context, private val webView: WebView) {

    /**
     * Called from Web App: window.POSHardware.printReceipt("...")
     */
    @JavascriptInterface
    fun printReceipt(text: String) {
        // Here you implement the actual ESC/POS transmission via Bluetooth or USB
        Toast.makeText(mContext, "Printing Receipt: ${text.take(20)}...", Toast.LENGTH_SHORT).show()
        
        // Example: Notify Web App that printing finished
        val js = "if(typeof window.onHardwareStatusChanged === 'function') { window.onHardwareStatusChanged('PRINTER', 'SUCCESS'); }"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Called from Web App: window.POSHardware.scanBluetoothDevices()
     */
    @JavascriptInterface
    fun scanBluetoothDevices() {
        Toast.makeText(mContext, "Opening Bluetooth Settings...", Toast.LENGTH_SHORT).show()
        // Here you would launch a BT discovery dialog or intent
    }
    
    /**
     * Called from Web App: window.POSHardware.openCashDrawer()
     */
    @JavascriptInterface
    fun openCashDrawer() {
        Toast.makeText(mContext, "Opening Cash Drawer...", Toast.LENGTH_SHORT).show()
        // Here you send the ESC/POS kick code to the connected printer
    }
}
