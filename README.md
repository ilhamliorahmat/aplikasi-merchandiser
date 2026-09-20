# Acheter POS - Android Native Wrapper & Web App

**Project Name:** MOB-WRAPPER-FILEUPLOAD-V1  
**Maintained by:** L'élixir de Mathieu Development Team  

This repository contains the complete hybrid solution for **Acheter POS**:
1. **Web Application (Vite / React / TypeScript):** High-performance web POS interface with thermal receipt canvas rendering and file upload management.
2. **Android Native Wrapper (Kotlin / Gradle):** Native WebView container (`com.acheter.pos`) providing ESC/POS Bluetooth thermal printing, hardware diagnostics, and native camera/file choosers.

---

## 📚 Complete Technical Documentation

For complete API specifications, bridge integration details, and code examples for the Web App Development Team, refer to:
👉 **[`WEB_APP_INTEGRATION_GUIDE.md`](./WEB_APP_INTEGRATION_GUIDE.md)**

---

## 🚀 Key Capabilities

### 1. Bluetooth Thermal Printing Engine
- **Protocol:** Raster image bitmap ESC/POS printing (`GS v 0` command set).
- **Target Paper Size:** 58mm thermal paper (384px exact width constraint).
- **Bridge Method:** `window.AndroidBridge.printReceiptImage(base64Png)`
- **Asynchronous Callback:** `window.onHardwareStatusChanged(type, status, message)`

### 2. Native File & Camera Chooser
- **WebChromeClient:** Intercepts standard HTML `<input type="file">` elements.
- **Support:** Image gallery, document picker, and live camera capture (`capture="environment"`).
- **Bridge Method:** `window.AndroidBridge.supportsFileUpload()`

### 3. Printer Discovery & Diagnostics
- **Device Pairing:** Enumerates paired Bluetooth devices via `window.AndroidBridge.getBondedDevices()`.
- **Assignment:** Persists selected printer MAC in Android `SharedPreferences`.
- **Diagnostics:** Live monitoring of Bluetooth radio status, assigned printer MAC, and pairing status via `window.AndroidBridge.getPrinterDiagnostics()`.

---

## 💻 Local Development & Building

### Running the Web Dev Server
```bash
npm install
npm run dev
```
The web app will start on `http://localhost:3000`.

### Building the Android APK
```bash
./gradlew assembleRelease
```
The output APK will be generated at `app/build/outputs/apk/release/app-release.apk`.
