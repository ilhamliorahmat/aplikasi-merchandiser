# Acheter POS - Native Android Wrapper

This is a dedicated Native Android Wrapper for `https://acheter.xo.je`. 

## Features
- **Immersive POS Mode:** Hides the system navigation and status bars. Back button is disabled.
- **Auto Barcode Scanning:** HID scanners (USB & Bluetooth) are intercepted via keyboard emulation logic. Scanned codes are injected into the web app using `window.onBarcodeScanned(data)`.
- **Hardware Javascript Bridge:** The web app can execute commands on the Android tablet using `window.POSHardware`.
- **Automated Cloud Builds:** A GitHub Action is included. Every time you push to this repository, a new `.apk` is built and attached to the run for easy download.

## Web App Integration Guide

On `https://acheter.xo.je`, listen for hardware events by defining this globally:
```javascript
window.onBarcodeScanned = function(barcodeData) {
    console.log("Barcode scanned natively:", barcodeData);
    // Add to cart logic here
};

window.onHardwareStatusChanged = function(device, status) {
    console.log("Hardware Update:", device, status);
};
```

To command the hardware from your web app:
```javascript
if (window.POSHardware) {
    window.POSHardware.printReceipt("Raw Text or ESC/POS Commands");
    window.POSHardware.scanBluetoothDevices();
    window.POSHardware.openCashDrawer();
}
```
