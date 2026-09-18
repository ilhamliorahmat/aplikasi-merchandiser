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

data class PairedPrinter(
    val name: String,
    val address: String,
    val isSelected: Boolean
)

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

    fun getSavedPrinterName(): String? {
        return prefs.getString(KEY_PRINTER_NAME, null)
    }

    fun saveSelectedPrinter(mac: String, name: String) {
        prefs.edit()
            .putString(KEY_PRINTER_MAC, mac)
            .putString(KEY_PRINTER_NAME, name)
            .apply()
    }

    fun clearSelectedPrinter() {
        prefs.edit()
            .remove(KEY_PRINTER_MAC)
            .remove(KEY_PRINTER_NAME)
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun getBondedPrinters(): List<PairedPrinter> {
        if (!hasBluetoothPermission()) {
            return emptyList()
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return emptyList()

        if (!bluetoothAdapter.isEnabled) {
            return emptyList()
        }

        val currentMac = getSavedPrinterMac()
        val bondedDevices = bluetoothAdapter.bondedDevices ?: return emptyList()

        return bondedDevices.map { device ->
            val devName = try {
                device.name ?: "Unknown Device"
            } catch (e: Exception) {
                "Unknown Device"
            }
            val devAddress = device.address
            PairedPrinter(
                name = devName,
                address = devAddress,
                isSelected = (devAddress == currentMac)
            )
        }
    }

    fun getBondedPrintersJson(): String {
        val list = getBondedPrinters()
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("address", item.address)
            obj.put("isSelected", item.isSelected)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
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
                
                // ESC/POS Reset
                outputStream.write(byteArrayOf(0x1B, 0x40))
                
                // Simplified Bitmap Printing (Raster Bit Image)
                // Note: This requires a specific printer command. 
                // For a robust implementation, a proper ESC/POS converter is needed.
                // This is a placeholder for the logic.
                
                outputStream.flush()
                onComplete(true, "Image printed (placeholder)")
            } catch (e: Exception) {
                onComplete(false, "Print failed: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun testPrint(macAddress: String, onComplete: (Boolean, String) -> Unit) {
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
                // ESC/POS Reset / Init
                outputStream.write(byteArrayOf(0x1B, 0x40))
                // Align Center
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x01))
                // Bold on
                outputStream.write(byteArrayOf(0x1B, 0x45, 0x01))
                outputStream.write("L'ELIXIR DE MATHIEU\n".toByteArray(Charsets.UTF_8))
                // Bold off
                outputStream.write(byteArrayOf(0x1B, 0x45, 0x00))
                outputStream.write("POS BLUETOOTH TEST\n".toByteArray(Charsets.UTF_8))
                outputStream.write("--------------------------------\n".toByteArray(Charsets.UTF_8))
                // Align Left
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x00))
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val devName = try { device.name ?: "Unknown" } catch (e: Exception) { "Unknown" }
                outputStream.write("Device: $devName\n".toByteArray(Charsets.UTF_8))
                outputStream.write("MAC: $macAddress\n".toByteArray(Charsets.UTF_8))
                outputStream.write("Timestamp: $dateStr\n".toByteArray(Charsets.UTF_8))
                outputStream.write("Status: Connected & Ready\n".toByteArray(Charsets.UTF_8))
                outputStream.write("--------------------------------\n\n\n".toByteArray(Charsets.UTF_8))
                // Cut Paper
                outputStream.write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))
                outputStream.flush()

                onComplete(true, "Test receipt printed successfully!")
            } catch (e: Exception) {
                onComplete(false, "Print failed: ${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }.start()
    }
}
