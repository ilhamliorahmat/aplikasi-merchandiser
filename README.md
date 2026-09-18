# Acheter POS - Native Android Hardware Wrapper
**Version:** 1.3.0 (Build 6)  
**Target:** `https://acheter.xo.je`  
**Brand:** L'élixir de Mathieu

---

## What's New in v1.3.0

- **Internal Bluetooth Printer Manager:** No more hardcoded MAC addresses. The app now features a native device picker that enumerates all paired thermal receipt printers, saves the assigned hardware to Android `SharedPreferences`, and routes all receipt printing dynamically.
- **Dedicated Diagnostic Engine:** Instant test print support generating branded ESC/POS test receipts (*L'élixir de Mathieu POS Bluetooth Test*) with hardware address, timestamp, and automatic paper cutting.
- **Bi-directional Web Bridge:** Global JavaScript APIs under `window.POSNativeBridge` and `window.AndroidBridge` allow web POS buttons to launch the native printer dialog, probe printer assignment state, and stream ESC/POS print jobs.
- **Zero-Config Fallback:** If a cashier triggers a print job without having assigned a thermal printer, the app automatically alerts the cashier and pops up the printer assignment dialog.

---

## Cashier / Store Setup Guide

1. **Pair Printer via Android Settings:**
   - Turn on your thermal Bluetooth printer (58mm or 80mm).
   - On the Android tablet, open **Android Settings > Bluetooth** and pair the printer (common PIN: `0000` or `1234`).
2. **Assign Printer in Acheter POS:**
   - Launch the Acheter POS app.
   - Tap the **Printer** button in the top action bar (or click "Select / Assign Printer" from the POS web interface).
   - Select your printer from the list of paired devices and tap **Assign & Save**.
   - Tap **Test Print** to verify direct ESC/POS communication.
3. **Start Selling:**
   - All receipts from `https://acheter.xo.je` (e.g., checkout completion or `window.AndroidBridge.printReceipt(url)`) are seamlessly intercepted and printed via the assigned printer.

---

## Web App Integration APIs (`window.POSNativeBridge` & `window.AndroidBridge`)

```javascript
// 1. Launch Native Bluetooth Printer Dialog
window.POSNativeBridge.openPrinterSettings();

// 2. Query Current Assigned Printer
const printerInfo = JSON.parse(window.POSNativeBridge.getAssignedPrinter());
console.log(printerInfo.assigned); // true / false
console.log(printerInfo.name);     // e.g. "RPP02N" or "MPT-II"
console.log(printerInfo.mac);      // e.g. "66:22:AA:BB:CC:DD"

// 3. Fast Check
if (window.POSNativeBridge.isPrinterAssigned()) {
    console.log("Printer ready for checkout");
}

// 4. Trigger Branded Test Print
window.POSNativeBridge.testPrintAssignedPrinter();

// 5. Silent Receipt Printing (Auto-routed to Assigned Printer)
window.AndroidBridge.printReceipt("https://acheter.xo.je/receipt.php?id=8921");

// 6. Direct Raw Text Print to Bluetooth
window.POSNativeBridge.printReceiptBluetooth("", "STORE RECEIPT TEXT...\n\n");
```

---

## Hardware Barcode Scanner Bridge

HID USB and Bluetooth Barcode Scanners automatically buffer key events and inject the result into your web app:
```javascript
window.onBarcodeScanned = function(barcode) {
    console.log("Scanned Barcode:", barcode);
    // Automatically query item or append to cart
};

window.onHardwareStatusChanged = function(device, status) {
    console.log("Hardware Event:", device, status); // e.g. ("PRINTER", "SUCCESS")
};
```

---

## Building & Signing the Release APK

The project includes an automated GitHub Action workflow (`.github/workflows/build.yml`) and is signed with `acheter.keystore`.

To build locally:
```bash
gradle assembleRelease
```
The output APK is generated at:
`app/build/outputs/apk/release/acheter.apk`
