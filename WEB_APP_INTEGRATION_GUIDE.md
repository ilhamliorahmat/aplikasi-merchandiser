# WEB APP INTEGRATION GUIDE
## Acheter POS - Android Native Wrapper Integration Specs
**Version:** 1.0.0  
**Target Audience:** Web Application Frontend & Backend Developers  
**Maintainer:** L'élixir de Mathieu Development Team  

---

## 1. System Architecture Overview

The Acheter POS application runs as a hybrid system:
- **Web Application:** Standard React/TypeScript or HTML/JS web app hosted on Cloud Run or a local server.
- **Android Native Wrapper:** A custom Kotlin Android WebView container (`com.acheter.pos`) providing native hardware access (Bluetooth ESC/POS thermal printing, native camera/file picker, device persistence).

```
+-------------------------------------------------------------------+
|                        Web Application                            |
|     (React / TypeScript / HTML5 / html2canvas File Uploader)     |
+-------------------------------------------------------------------+
                                 |
                     Javascript Bridge Window Object
                        window.AndroidBridge
                                 |
+-------------------------------------------------------------------+
|                     Android Native Wrapper                        |
|                                                                   |
|   +-------------------+    +-----------------+    +-----------+   |
|   |   PrinterManager  |    | WebChromeClient |    | SharedPref|   |
|   |  (ESC/POS Raster) |    |  (File/Camera)  |    | (MAC Store)|  |
|   +-------------------+    +-----------------+    +-----------+   |
+-------------------------------------------------------------------+
          |                           |
          v                           v
  Bluetooth Thermal Printer     Native Storage / Camera
     (384px / 58mm Paper)        File Chooser Dialog
```

---

## 2. Detecting the Native Bridge

Always safely check whether the web app is running inside the **Android Native Wrapper** or a standard web browser.

### TypeScript / ES6 Detection Helper

```typescript
export function isNativeAndroidWrapper(): boolean {
  return typeof window !== 'undefined' && typeof window.AndroidBridge !== 'undefined';
}
```

---

## 3. JavaScript Bridge API Reference (`window.AndroidBridge`)

When running inside the native wrapper, the `window.AndroidBridge` object exposes the following methods:

| Method Signature | Return Type | Description |
| :--- | :--- | :--- |
| `printReceiptImage(base64Image: string)` | `void` | Transmits a 384px Base64 PNG data URL to the assigned Bluetooth thermal printer for ESC/POS raster printing. |
| `getBondedDevices()` | `string` (JSON) | Returns a JSON string array of all paired Bluetooth devices on the Android device. |
| `assignPrinter(mac: string, name: string)` | `void` | Saves the selected printer's MAC address and friendly name into Android `SharedPreferences`. |
| `getPrinterDiagnostics()` | `string` (JSON) | Returns a JSON string object containing Bluetooth radio state, assigned printer MAC, and pairing status. |
| `isPrinterAssigned()` | `boolean` | Returns `true` if a printer MAC address is currently saved. |
| `supportsFileUpload()` | `boolean` | Returns `true` indicating native file chooser and camera capture capabilities are available. |

---

## 4. Hardware Status Callback (`window.onHardwareStatusChanged`)

The native layer communicates asynchronous print results back to the web application by invoking a global JavaScript function on `window`.

### Interface Signature

```typescript
declare global {
  interface Window {
    onHardwareStatusChanged?: (type: string, status: 'SUCCESS' | 'ERROR', message: string) => void;
  }
}
```

### Web App Listener Registration Example

```javascript
window.onHardwareStatusChanged = (type, status, message) => {
  if (type === 'PRINT') {
    if (status === 'SUCCESS') {
      console.log('Print Job Completed:', message);
      // Update UI state: Show success toast
    } else {
      console.error('Print Job Failed:', message);
      // Update UI state: Prompt user to check printer connection
    }
  }
};
```

---

## 5. Thermal Receipt Printing Integration Guide

### 5.1 Receipt DOM Layout Rules
1. **Strict 384px Width:** 58mm thermal printers print at 203 DPI (384 pixels width). The receipt container DOM element **must** be styled to `width: 384px`.
2. **Monochrome Typography:** Use high-contrast black text on white background (`#000000` text on `#FFFFFF` bg).
3. **Monospaced Font:** Use monospaced fonts (`Courier New`, `monospace`) for aligned columns.

### 5.2 HTML & CSS Receipt Template

```html
<div id="thermal-receipt" style="width: 384px; background: #ffffff; color: #000000; font-family: 'Courier New', monospace; padding: 16px; box-sizing: border-box;">
  <div style="text-align: center; border-bottom: 1px dashed #000; padding-bottom: 8px; margin-bottom: 8px;">
    <h2 style="font-size: 16px; font-weight: bold; margin: 0;">L'ÉLIXIR DE MATHIEU</h2>
    <p style="font-size: 11px; margin: 2px 0;">Jl. Boulevard No. 88, Jakarta</p>
  </div>

  <div style="font-size: 11px; margin-bottom: 8px;">
    <div>Receipt #: #INV-2026-0919</div>
    <div>Date: 2026-09-19 14:10</div>
  </div>

  <table style="width: 100%; font-size: 11px; border-top: 1px dashed #000; border-bottom: 1px dashed #000; margin: 8px 0; border-collapse: collapse;">
    <thead>
      <tr style="text-align: left;">
        <th>ITEM</th>
        <th style="text-align: center;">QTY</th>
        <th style="text-align: right;">PRICE</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>Espresso Gold</td>
        <td style="text-align: center;">2</td>
        <td style="text-align: right;">$12.00</td>
      </tr>
      <tr>
        <td>Matcha Latte</td>
        <td style="text-align: center;">1</td>
        <td style="text-align: right;">$6.50</td>
      </tr>
    </tbody>
  </table>

  <div style="font-size: 12px; font-weight: bold; text-align: right; margin-top: 8px;">
    TOTAL: $18.50
  </div>
</div>
```

### 5.3 JavaScript Printing Execution Code

```typescript
import html2canvas from 'html2canvas';

export async function printReceiptElement(elementId: string): Promise<void> {
  const receiptEl = document.getElementById(elementId);
  if (!receiptEl) {
    throw new Error(`Receipt element #${elementId} not found`);
  }

  // 1. Capture DOM element to Canvas at exactly 384px width
  const canvas = await html2canvas(receiptEl, {
    width: 384,
    scale: 1,
    useCORS: true,
    backgroundColor: '#ffffff'
  });

  // 2. Convert Canvas to Base64 PNG Data URI
  const base64Image = canvas.toDataURL('image/png');

  // 3. Send to Android Native Bridge
  if (typeof window.AndroidBridge !== 'undefined') {
    window.AndroidBridge.printReceiptImage(base64Image);
  } else {
    console.warn('Native AndroidBridge not detected. Printing simulated.');
  }
}
```

---

## 6. File & Attachment Upload Integration Guide

The Android wrapper intercepts standard HTML file input elements via a native `WebChromeClient`. **No special JS bridge calls are needed for file uploads**—simply use standard HTML file inputs.

### 6.1 HTML File Input Types Supported

```html
<!-- 1. General File Picker (Documents, PDFs, Files) -->
<input type="file" name="attachment" />

<!-- 2. Image Gallery Picker -->
<input type="file" accept="image/*" />

<!-- 3. Direct Camera Capture Intent -->
<input type="file" accept="image/*" capture="environment" />
```

### 6.2 Native Behavior Triggered
When a user taps an `<input type="file">` inside the WebView:
1. The native `WebChromeClient.onShowFileChooser` intercepts the request.
2. An Android Chooser Dialog opens offering options:
   - **Take Photo** (Launches Android Camera Intent with temporary `FileProvider` URI).
   - **Choose File / Gallery** (Launches Android System Document / Media Picker).
3. The selected file URI is returned directly to the web app's form / `FormData` object.

---

## 7. Printer Management & Diagnostics Integration Guide

### 7.1 Fetching Paired Bluetooth Printers

```typescript
interface BluetoothDevice {
  name: string;
  mac: string;
}

export function getPairedPrinters(): BluetoothDevice[] {
  if (typeof window.AndroidBridge === 'undefined') return [];
  
  try {
    const jsonString = window.AndroidBridge.getBondedDevices();
    return JSON.parse(jsonString || '[]');
  } catch (error) {
    console.error('Failed to parse bonded devices:', error);
    return [];
  }
}
```

### 7.2 Assigning a Printer

```typescript
export function selectPrinter(mac: string, name: string): void {
  if (typeof window.AndroidBridge !== 'undefined') {
    window.AndroidBridge.assignPrinter(mac, name);
    console.log(`Assigned printer: ${name} (${mac})`);
  }
}
```

### 7.3 Fetching Diagnostics

```typescript
interface PrinterDiagnostics {
  bluetoothEnabled: boolean;
  assigned: boolean;
  mac: string;
  paired: boolean;
  name: string;
}

export function getDiagnostics(): PrinterDiagnostics | null {
  if (typeof window.AndroidBridge === 'undefined') return null;

  try {
    const jsonString = window.AndroidBridge.getPrinterDiagnostics();
    return JSON.parse(jsonString || '{}');
  } catch (error) {
    console.error('Failed to fetch printer diagnostics:', error);
    return null;
  }
}
```

---

## 8. Troubleshooting & Frequently Asked Questions

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| `window.AndroidBridge is undefined` | Running in standard browser or WebView bridge failed to attach. | Ensure code is running inside the compiled APK wrapper (`com.acheter.pos`). |
| Receipt prints blank paper | Image brightness thresholding issue or non-monochrome content. | Ensure receipt container has explicit `#ffffff` background and `#000000` text. |
| Print fails with "No printer assigned" | No Bluetooth printer selected in SharedPreferences. | Call `getBondedDevices()` and `assignPrinter(mac, name)` before printing. |
| File chooser doesn't open camera | Camera permission denied or device lacks camera hardware. | Check Android App Permissions in Android Settings -> Apps -> Acheter POS -> Permissions -> Camera. |
| File chooser freezes on second tap | Previous file callback wasn't cleared on cancel. | Fixed in wrapper v1.0.0 (`filePathCallback.onReceiveValue(null)` handles cancellations cleanly). |

---
*Documentation prepared by L'élixir de Mathieu Head of Development & 10 Dev Team.*
