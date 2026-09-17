package com.acheter.pos

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import java.io.OutputStream

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
