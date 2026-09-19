package com.acheter.pos

import android.graphics.Bitmap
import android.graphics.Color
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class PrinterManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "AcheterPrinterPrefs"
        private const val KEY_PRINTER_MAC = "selected_printer_mac"
        private const val KEY_PRINTER_NAME = "selected_printer_name"
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getSavedPrinterMac(): String? {
        return prefs.getString(KEY_PRINTER_MAC, null)
    }

    fun saveSelectedPrinter(mac: String, name: String) {
        prefs.edit()
            .putString(KEY_PRINTER_MAC, mac)
            .putString(KEY_PRINTER_NAME, name)
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): JSONArray {
        val jsonArray = JSONArray()
        if (!hasBluetoothPermission()) return jsonArray

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            val devices = bluetoothAdapter.bondedDevices
            for (device in devices) {
                val jsonDevice = JSONObject()
                jsonDevice.put("name", device.name ?: "Unknown")
                jsonDevice.put("mac", device.address)
                jsonArray.put(jsonDevice)
            }
        }
        return jsonArray
    }

    @SuppressLint("MissingPermission")
    fun getPrinterDiagnostics(): JSONObject {
        val diagnostics = JSONObject()
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        diagnostics.put("bluetoothEnabled", bluetoothAdapter?.isEnabled ?: false)
        
        val mac = getSavedPrinterMac()
        diagnostics.put("assigned", !mac.isNullOrBlank())
        diagnostics.put("mac", mac ?: "None")
        
        if (!mac.isNullOrBlank() && bluetoothAdapter != null) {
            // Check if device is still in paired list
            val device = bluetoothAdapter.bondedDevices.find { it.address == mac }
            diagnostics.put("paired", device != null)
            diagnostics.put("name", device?.name ?: "Unknown")
        } else {
            diagnostics.put("paired", false)
            diagnostics.put("name", "None")
        }
        return diagnostics
    }

    @SuppressLint("MissingPermission")
    fun printBitmap(macAddress: String, bitmap: Bitmap, onComplete: (Boolean, String) -> Unit) {
        if (!hasBluetoothPermission()) {
            onComplete(false, "Missing Bluetooth permission")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onComplete(false, "Bluetooth is disabled")
            return
        }

        Thread {
            var socket: android.bluetooth.BluetoothSocket? = null
            try {
                val sppUuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val device = bluetoothAdapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                bluetoothAdapter.cancelDiscovery()
                socket.connect()

                val outputStream = socket.outputStream
                
                // 1. ESC/POS Reset
                outputStream.write(byteArrayOf(0x1B, 0x40))

                // 2. Convert Bitmap to 1-bit raster data
                val width = bitmap.width
                val height = bitmap.height
                val bytesPerRow = (width + 7) / 8
                val rasterData = ByteArray(height * bytesPerRow)

                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pixel = bitmap.getPixel(x, y)
                        // Simple thresholding: darker pixels = printed
                        if (Color.red(pixel) < 128) {
                            val byteIndex = y * bytesPerRow + (x / 8)
                            rasterData[byteIndex] = (rasterData[byteIndex].toInt() or (0x80 shr (x % 8))).toByte()
                        }
                    }
                }

                // 3. Send GS v 0 raster print command
                // GS v 0 m xL xH yL yH
                val xL = (bytesPerRow and 0xFF).toByte()
                val xH = (bytesPerRow shr 8 and 0xFF).toByte()
                val yL = (height and 0xFF).toByte()
                val yH = (height shr 8 and 0xFF).toByte()
                
                outputStream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))
                outputStream.write(rasterData)
                
                // 4. Cut Paper
                outputStream.write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))
                
                outputStream.flush()
                onComplete(true, "Image printed successfully")
            } catch (e: Exception) {
                onComplete(false, "Print failed: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }.start()
    }
}
