package com.acheter.pos

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.util.UUID

class WebAppInterface(private val mContext: Context, private val webView: WebView) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hiddenPrintWebView: WebView? = null
    
    // Standard SPP UUID for Bluetooth Serial Port Profile
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Called from Web App: AndroidBridge.printReceiptBluetooth(macAddress, textPayload)
     */
    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun printReceiptBluetooth(macAddress: String, textPayload: String) {
        if (!hasBluetoothPermissions()) {
            mainHandler.post {
                Toast.makeText(mContext, "Missing Bluetooth Permissions!", Toast.LENGTH_LONG).show()
            }
            return
        }

        Thread {
            var socket: BluetoothSocket? = null
            try {
                val bluetoothManager = mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter
                
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    mainHandler.post { Toast.makeText(mContext, "Bluetooth is disabled", Toast.LENGTH_LONG).show() }
                    return@Thread
                }

                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                
                bluetoothAdapter.cancelDiscovery()
                socket.connect()

                val outputStream = socket.outputStream
                
                // Initialize printer
                val initCmd = byteArrayOf(0x1B, 0x40)
                outputStream.write(initCmd)
                
                // Send text
                outputStream.write(textPayload.toByteArray(Charsets.UTF_8))
                
                // Feed and cut paper
                val cutCmd = byteArrayOf(0x1D, 0x56, 0x41, 0x10)
                outputStream.write(cutCmd)
                
                outputStream.flush()

                mainHandler.post {
                    Toast.makeText(mContext, "Receipt Printed Successfully!", Toast.LENGTH_SHORT).show()
                    val js = "if(typeof window.onHardwareStatusChanged === 'function') { window.onHardwareStatusChanged('PRINTER', 'SUCCESS'); }"
                    this.webView.evaluateJavascript(js, null)
                }

            } catch (e: Exception) {
                Log.e("WebAppInterface", "Bluetooth Print Error", e)
                mainHandler.post {
                    Toast.makeText(mContext, "Printer Error: ${e.message}", Toast.LENGTH_LONG).show()
                    val js = "if(typeof window.onHardwareStatusChanged === 'function') { window.onHardwareStatusChanged('PRINTER', 'ERROR'); }"
                    this.webView.evaluateJavascript(js, null)
                }
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    Log.e("WebAppInterface", "Error closing socket", e)
                }
            }
        }.start()
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Called from Web App: AndroidBridge.printReceipt(htmlContentOrUrl)
     */
    @JavascriptInterface
    fun printReceipt(htmlContentOrUrl: String) {
        mainHandler.post {
            // Create a hidden WebView to render the content
            val printWebView = WebView(mContext)
            hiddenPrintWebView = printWebView
            
            printWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    createWebPrintJob(view)
                }
            }
            
            // Check if the payload is a URL (e.g., "receipt-print.php?id=17" or "http...")
            if (htmlContentOrUrl.contains(".php") || htmlContentOrUrl.startsWith("http")) {
                var finalUrl = htmlContentOrUrl
                
                // If it's a relative URL, resolve it against the current main WebView URL
                if (!htmlContentOrUrl.startsWith("http")) {
                    val currentMainUrl = this.webView.url ?: ""
                    if (currentMainUrl.isNotEmpty()) {
                        try {
                            val baseUri = java.net.URI(currentMainUrl)
                            finalUrl = baseUri.resolve(htmlContentOrUrl).toString()
                        } catch (e: Exception) {
                            Log.e("WebAppInterface", "Failed to resolve relative URL", e)
                        }
                    }
                }
                
                Log.d("WebAppInterface", "Loading Receipt URL: $finalUrl")
                printWebView.loadUrl(finalUrl)
            } else {
                // Treat as raw HTML string
                val baseURL = "file:///android_asset/"
                printWebView.loadDataWithBaseURL(baseURL, htmlContentOrUrl, "text/HTML", "UTF-8", null)
            }
        }
    }

    private fun createWebPrintJob(webView: WebView) {
        val printManager = mContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(mContext, "Print service not available", Toast.LENGTH_LONG).show()
            return
        }

        val jobName = "${mContext.getString(R.string.app_name)} Receipt"
        
        // Get the print adapter from the WebView
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        
        // Let the Android OS handle the rest
        printManager.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder().build()
        )
        
        // Clean up memory
        hiddenPrintWebView = null
        
        // Notify Web App that print intent was fired
        val js = "if(typeof window.onHardwareStatusChanged === 'function') { window.onHardwareStatusChanged('PRINTER', 'SUCCESS'); }"
        this.webView.post {
            this.webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Called from Web App: AndroidBridge.saveBase64Image(base64, filename)
     */
    @JavascriptInterface
    fun saveBase64Image(base64: String, filename: String) {
        try {
            // 1. Strip the data URI prefix if present (e.g., "data:image/png;base64,")
            val cleanBase64 = if (base64.contains(",")) {
                base64.split(",")[1]
            } else {
                base64
            }

            // 2. Decode the Base64 string into a byte array
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            // 3. Prepare the MediaStore insertion
            val resolver = mContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Receipts")
                }
            }

            // 4. Insert the file into the Gallery/MediaStore
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            // 5. Open an output stream to the new URI and write the bytes
            if (imageUri != null) {
                val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
                outputStream?.use {
                    it.write(decodedBytes)
                }
                mainHandler.post {
                    Toast.makeText(mContext, "Receipt saved to Gallery!", Toast.LENGTH_SHORT).show()
                }
            } else {
                throw Exception("Failed to create new MediaStore record.")
            }

        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error saving image", e)
            mainHandler.post {
                Toast.makeText(mContext, "Failed to save image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Called from Web App: POSNativeBridge.shareImageAndText(base64Data, text, phone)
     */
    @JavascriptInterface
    fun shareImageAndText(base64Data: String, text: String, phone: String) {
        try {
            // 1. Strip the data URI prefix if present
            val cleanBase64 = if (base64Data.contains(",")) {
                base64Data.split(",")[1]
            } else {
                base64Data
            }

            // 2. Decode the Base64 string into a byte array
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            // 3. Save to a temporary file in the cache directory
            val cachePath = java.io.File(mContext.cacheDir, "shared_receipts")
            cachePath.mkdirs() // Create directory if it doesn't exist
            val imageFile = java.io.File(cachePath, "receipt_share_${System.currentTimeMillis()}.png")
            
            val fos = java.io.FileOutputStream(imageFile)
            fos.use { it.write(decodedBytes) }

            // 4. Generate the secure content:// URI using FileProvider
            val authority = "${mContext.packageName}.fileprovider"
            val contentUri = androidx.core.content.FileProvider.getUriForFile(mContext, authority, imageFile)

            // 5. Fire the Share Intent on the Main Thread
            mainHandler.post {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = android.content.Intent.createChooser(shareIntent, "Share Receipt via...")
                // In case the context isn't an activity context, though it usually is
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                mContext.startActivity(chooser)
            }

        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error sharing image", e)
            mainHandler.post {
                Toast.makeText(mContext, "Failed to share image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
