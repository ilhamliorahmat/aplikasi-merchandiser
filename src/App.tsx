import React, { useState, useEffect, useRef } from 'react';
import html2canvas from 'html2canvas';
import { 
  Printer, 
  Upload, 
  Smartphone, 
  CheckCircle2, 
  AlertCircle, 
  RefreshCw, 
  FileText, 
  Image as ImageIcon, 
  Camera, 
  Bluetooth, 
  Settings, 
  ShoppingCart, 
  Receipt, 
  X, 
  HardDriveUpload,
  Check
} from 'lucide-react';

declare global {
  interface Window {
    AndroidBridge?: {
      printReceiptImage: (base64Data: string) => void;
      isPrinterAssigned: () => boolean;
      getBondedDevices: () => string;
      getPrinterDiagnostics: () => string;
      assignPrinter: (mac: string, name: string) => void;
      supportsFileUpload?: () => boolean;
    };
    onHardwareStatusChanged?: (type: string, status: string, message: string) => void;
  }
}

interface Device {
  name: string;
  mac: string;
}

interface Diagnostics {
  bluetoothEnabled: boolean;
  assigned: boolean;
  mac: string;
  paired: boolean;
  name: string;
}

interface UploadedFile {
  id: string;
  name: string;
  size: string;
  type: string;
  url: string;
  uploadedAt: string;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'receipt' | 'upload' | 'printer'>('receipt');
  const [isNative, setIsNative] = useState<boolean>(false);
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' | 'info' } | null>(null);
  
  // Receipt State
  const [isPrinting, setIsPrinting] = useState<boolean>(false);
  const receiptRef = useRef<HTMLDivElement>(null);
  
  // File Upload State
  const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>([
    {
      id: '1',
      name: 'store_logo_header.png',
      size: '124 KB',
      type: 'image/png',
      url: 'https://images.unsplash.com/photo-1556742049-0a670f4a4591?auto=format&fit=crop&q=80&w=200',
      uploadedAt: '2026-09-19 14:00'
    }
  ]);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  
  // Printer Manager State
  const [bondedDevices, setBondedDevices] = useState<Device[]>([]);
  const [diagnostics, setDiagnostics] = useState<Diagnostics | null>(null);
  const [selectedMac, setSelectedMac] = useState<string>('');

  useEffect(() => {
    // Detect if running inside Android Native Wrapper
    const nativeAvailable = typeof window.AndroidBridge !== 'undefined';
    setIsNative(nativeAvailable);

    // Register global callback for hardware print feedback
    window.onHardwareStatusChanged = (type: string, status: string, message: string) => {
      setIsPrinting(false);
      if (status === 'SUCCESS') {
        setStatusMessage({ text: `Print Successful: ${message}`, type: 'success' });
      } else {
        setStatusMessage({ text: `Print Failure: ${message}`, type: 'error' });
      }
    };

    if (nativeAvailable) {
      loadPrinterInfo();
    }
  }, []);

  const loadPrinterInfo = () => {
    if (!window.AndroidBridge) return;
    try {
      const devicesJson = window.AndroidBridge.getBondedDevices();
      const devices = JSON.parse(devicesJson || '[]');
      setBondedDevices(devices);

      const diagJson = window.AndroidBridge.getPrinterDiagnostics();
      const diag = JSON.parse(diagJson || '{}');
      setDiagnostics(diag);
      if (diag.mac && diag.mac !== 'None') {
        setSelectedMac(diag.mac);
      }
    } catch (e) {
      console.error('Error loading printer bridge info:', e);
    }
  };

  const handlePrintReceipt = async () => {
    if (!receiptRef.current) return;
    setIsPrinting(true);
    setStatusMessage({ text: 'Generating receipt image bitmap...', type: 'info' });

    try {
      const canvas = await html2canvas(receiptRef.current, {
        width: 384,
        scale: 1,
        useCORS: true,
        backgroundColor: '#ffffff'
      });

      const base64Image = canvas.toDataURL('image/png');

      if (window.AndroidBridge) {
        setStatusMessage({ text: 'Sending raster bitmap to Bluetooth printer via Native Wrapper...', type: 'info' });
        window.AndroidBridge.printReceiptImage(base64Image);
      } else {
        // Simulation mode for browser preview
        setTimeout(() => {
          setIsPrinting(false);
          setStatusMessage({ 
            text: 'Web Browser Preview Mode: Native AndroidBridge not detected. Receipt generated as Base64 image successfully (384px width).', 
            type: 'info' 
          });
        }, 1200);
      }
    } catch (err: any) {
      setIsPrinting(false);
      setStatusMessage({ text: `Failed to capture receipt: ${err.message}`, type: 'error' });
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const file = files[0];
    setUploadProgress(0);

    // Simulate file upload process
    let current = 0;
    const interval = setInterval(() => {
      current += 20;
      setUploadProgress(current);
      if (current >= 100) {
        clearInterval(interval);
        setTimeout(() => {
          const newFile: UploadedFile = {
            id: Date.now().toString(),
            name: file.name,
            size: `${(file.size / 1024).toFixed(1)} KB`,
            type: file.type || 'application/octet-stream',
            url: file.type.startsWith('image/') ? URL.createObjectURL(file) : '',
            uploadedAt: new Date().toISOString().replace('T', ' ').substring(0, 16)
          };
          setUploadedFiles(prev => [newFile, ...prev]);
          setUploadProgress(null);
          setStatusMessage({ text: `File "${file.name}" uploaded successfully to web server!`, type: 'success' });
        }, 400);
      }
    }, 150);
  };

  const handleAssignPrinter = (mac: string, name: string) => {
    if (window.AndroidBridge) {
      window.AndroidBridge.assignPrinter(mac, name);
      setSelectedMac(mac);
      setStatusMessage({ text: `Assigned printer: ${name} (${mac})`, type: 'success' });
      loadPrinterInfo();
    } else {
      setSelectedMac(mac);
      setStatusMessage({ text: `Simulated assignment for ${name}`, type: 'info' });
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-slate-900 text-slate-100 font-sans">
      {/* Top Navigation Bar */}
      <header className="bg-slate-800 border-b border-slate-700 px-4 py-3 sticky top-0 z-50">
        <div className="max-w-5xl mx-auto flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="bg-sky-500 p-2 rounded-xl text-white font-bold shadow-lg shadow-sky-500/20">
              <ShoppingCart className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-tight text-white flex items-center gap-2">
                Acheter POS
                <span className="text-xs px-2 py-0.5 rounded-full bg-sky-500/10 text-sky-400 font-medium border border-sky-500/20">
                  Mobile Wrapper v1.0
                </span>
              </h1>
              <p className="text-xs text-slate-400">Bluetooth Thermal Printing & Native File Uploads</p>
            </div>
          </div>

          <div className="flex items-center space-x-3">
            {isNative ? (
              <span className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                Native Android Wrapper
              </span>
            ) : (
              <span className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                <GlobeIcon className="w-3.5 h-3.5" />
                Web Browser Mode
              </span>
            )}
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-5xl w-full mx-auto p-4 md:p-6 space-y-6">
        
        {/* Toast / Status Alert Banner */}
        {statusMessage && (
          <div className={`p-4 rounded-xl border flex items-start justify-between transition-all ${
            statusMessage.type === 'success' 
              ? 'bg-emerald-950/40 border-emerald-800 text-emerald-200' 
              : statusMessage.type === 'error'
              ? 'bg-rose-950/40 border-rose-800 text-rose-200'
              : 'bg-sky-950/40 border-sky-800 text-sky-200'
          }`}>
            <div className="flex items-center space-x-3">
              {statusMessage.type === 'success' && <CheckCircle2 className="w-5 h-5 text-emerald-400 flex-shrink-0" />}
              {statusMessage.type === 'error' && <AlertCircle className="w-5 h-5 text-rose-400 flex-shrink-0" />}
              {statusMessage.type === 'info' && <RefreshCw className="w-5 h-5 text-sky-400 animate-spin flex-shrink-0" />}
              <span className="text-sm font-medium">{statusMessage.text}</span>
            </div>
            <button 
              onClick={() => setStatusMessage(null)} 
              className="text-slate-400 hover:text-slate-200 p-1 rounded-lg"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        )}

        {/* Tab Navigation Controls */}
        <div className="flex border-b border-slate-800 space-x-2">
          <button
            onClick={() => setActiveTab('receipt')}
            className={`flex items-center space-x-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-all ${
              activeTab === 'receipt'
                ? 'border-sky-500 text-sky-400 bg-sky-500/5'
                : 'border-transparent text-slate-400 hover:text-slate-200 hover:border-slate-700'
            }`}
          >
            <Receipt className="w-4 h-4" />
            <span>Thermal Receipt & Print</span>
          </button>

          <button
            onClick={() => setActiveTab('upload')}
            className={`flex items-center space-x-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-all ${
              activeTab === 'upload'
                ? 'border-sky-500 text-sky-400 bg-sky-500/5'
                : 'border-transparent text-slate-400 hover:text-slate-200 hover:border-slate-700'
            }`}
          >
            <HardDriveUpload className="w-4 h-4" />
            <span>File & Attachment Uploads</span>
          </button>

          <button
            onClick={() => {
              setActiveTab('printer');
              if (isNative) loadPrinterInfo();
            }}
            className={`flex items-center space-x-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-all ${
              activeTab === 'printer'
                ? 'border-sky-500 text-sky-400 bg-sky-500/5'
                : 'border-transparent text-slate-400 hover:text-slate-200 hover:border-slate-700'
            }`}
          >
            <Bluetooth className="w-4 h-4" />
            <span>Printer Manager & Diagnostics</span>
          </button>
        </div>

        {/* TAB 1: THERMAL RECEIPT PRINTING */}
        {activeTab === 'receipt' && (
          <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-start">
            {/* Left: Interactive Controls */}
            <div className="md:col-span-6 space-y-4">
              <div className="bg-slate-800 rounded-2xl p-5 border border-slate-700 space-y-4">
                <h2 className="text-base font-semibold text-white flex items-center gap-2">
                  <Printer className="w-5 h-5 text-sky-400" />
                  Thermal Printer Controls
                </h2>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Renders the DOM element to a 384px raster PNG bitmap using <code className="text-sky-300">html2canvas</code> and transmits it via native bridge to the ESC/POS printer.
                </p>

                <div className="p-3 bg-slate-900/60 rounded-xl border border-slate-700/60 text-xs space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="text-slate-400">Assigned Printer:</span>
                    <span className="font-mono text-sky-300 font-semibold">
                      {diagnostics?.name && diagnostics.name !== 'None' ? diagnostics.name : (selectedMac || 'None Assigned')}
                    </span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-slate-400">Bridge Method:</span>
                    <span className="font-mono text-emerald-400">printReceiptImage(base64)</span>
                  </div>
                </div>

                <button
                  onClick={handlePrintReceipt}
                  disabled={isPrinting}
                  className="w-full bg-sky-500 hover:bg-sky-400 active:bg-sky-600 text-white font-semibold py-3 px-4 rounded-xl shadow-lg shadow-sky-500/20 flex items-center justify-center space-x-2 transition-all disabled:opacity-50"
                >
                  {isPrinting ? (
                    <>
                      <RefreshCw className="w-5 h-5 animate-spin" />
                      <span>Printing in Progress...</span>
                    </>
                  ) : (
                    <>
                      <Printer className="w-5 h-5" />
                      <span>Print Thermal Receipt</span>
                    </>
                  )}
                </button>
              </div>

              {/* Instructions Box */}
              <div className="bg-slate-800/60 rounded-2xl p-5 border border-slate-700/60 space-y-2 text-xs text-slate-300">
                <div className="font-semibold text-white flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  Image-Based Protocol Specs:
                </div>
                <ul className="list-disc list-inside space-y-1 text-slate-400">
                  <li>Width constrained strictly to 384px (58mm thermal paper standard).</li>
                  <li>Automatic 1-bit thresholding for clear monochrome text & QR graphics.</li>
                  <li>ESC/POS GS v 0 raster print command with automatic paper cut.</li>
                </ul>
              </div>
            </div>

            {/* Right: Receipt Preview Canvas Container */}
            <div className="md:col-span-6 flex flex-col items-center">
              <span className="text-xs text-slate-400 mb-2 font-medium">Receipt DOM Target (.receipt-wrapper)</span>
              
              {/* Actual Printable Wrapper */}
              <div 
                ref={receiptRef} 
                className="receipt-wrapper shadow-2xl rounded border border-slate-200 text-black text-xs leading-tight"
              >
                <div className="text-center border-b border-dashed border-black pb-3 mb-3">
                  <div className="font-bold text-base tracking-wider uppercase">L'ÉLIXIR DE MATHIEU</div>
                  <div>Grand Cafe & Gourmet Bistro</div>
                  <div>Jl. Boulevard No. 88, Jakarta</div>
                  <div>Tel: +62 812-3456-7890</div>
                </div>

                <div className="space-y-1 mb-3">
                  <div className="flex justify-between">
                    <span>Receipt #:</span>
                    <span className="font-bold">#INV-2026-0919</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Date:</span>
                    <span>2026-09-19 14:10</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Cashier:</span>
                    <span>Gabriel / Dev Team</span>
                  </div>
                </div>

                <table className="w-full text-left border-t border-b border-dashed border-black py-2 my-2">
                  <thead>
                    <tr className="font-bold">
                      <th className="py-1">ITEM</th>
                      <th className="text-center py-1">QTY</th>
                      <th className="text-right py-1">PRICE</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-dotted divide-gray-300">
                    <tr>
                      <td className="py-1">Espresso Elixir Gold</td>
                      <td className="text-center py-1">2</td>
                      <td className="text-right py-1">$12.00</td>
                    </tr>
                    <tr>
                      <td className="py-1">Artisan Croissant</td>
                      <td className="text-center py-1">1</td>
                      <td className="text-right py-1">$5.50</td>
                    </tr>
                    <tr>
                      <td className="py-1">Matcha Latte Special</td>
                      <td className="text-center py-1">1</td>
                      <td className="text-right py-1">$6.50</td>
                    </tr>
                  </tbody>
                </table>

                <div className="space-y-1 border-b border-dashed border-black pb-3 mb-3">
                  <div className="flex justify-between">
                    <span>Subtotal:</span>
                    <span>$24.00</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Tax (10%):</span>
                    <span>$2.40</span>
                  </div>
                  <div className="flex justify-between font-bold text-sm pt-1 border-t border-black">
                    <span>TOTAL:</span>
                    <span>$26.40</span>
                  </div>
                </div>

                <div className="text-center space-y-1">
                  <div className="font-semibold">Thank you for dining with us!</div>
                  <div className="text-[10px] text-gray-600">Please keep this receipt for records.</div>
                  <div className="pt-2 flex justify-center">
                    {/* Simulated barcode / QR */}
                    <div className="bg-black text-white px-3 py-1 font-mono text-[10px] tracking-widest">
                      *INV20260919*
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: FILE & ATTACHMENT UPLOADS */}
        {activeTab === 'upload' && (
          <div className="space-y-6">
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-semibold text-white flex items-center gap-2">
                    <HardDriveUpload className="w-5 h-5 text-sky-400" />
                    Web Server File Upload Center
                  </h2>
                  <p className="text-xs text-slate-400">
                    Upload store logos, product images, and invoice PDF documents directly from mobile device storage or camera.
                  </p>
                </div>

                <span className="text-xs px-3 py-1 rounded-full bg-sky-500/10 text-sky-400 font-medium border border-sky-500/20">
                  Native WebChromeClient File Chooser Active
                </span>
              </div>

              {/* Upload Input Cards */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
                
                {/* Standard File Picker */}
                <label className="cursor-pointer bg-slate-900/80 hover:bg-slate-900 border-2 border-dashed border-slate-700 hover:border-sky-500 rounded-xl p-5 flex flex-col items-center justify-center text-center transition-all group">
                  <input 
                    type="file" 
                    onChange={handleFileUpload} 
                    className="hidden" 
                  />
                  <Upload className="w-8 h-8 text-slate-400 group-hover:text-sky-400 transition-colors mb-2" />
                  <span className="text-sm font-semibold text-slate-200">Select File</span>
                  <span className="text-[11px] text-slate-500 mt-1">Any document or file</span>
                </label>

                {/* Image Only Picker */}
                <label className="cursor-pointer bg-slate-900/80 hover:bg-slate-900 border-2 border-dashed border-slate-700 hover:border-sky-500 rounded-xl p-5 flex flex-col items-center justify-center text-center transition-all group">
                  <input 
                    type="file" 
                    accept="image/*" 
                    onChange={handleFileUpload} 
                    className="hidden" 
                  />
                  <ImageIcon className="w-8 h-8 text-slate-400 group-hover:text-sky-400 transition-colors mb-2" />
                  <span className="text-sm font-semibold text-slate-200">Select Image</span>
                  <span className="text-[11px] text-slate-500 mt-1">PNG, JPG, WEBP, SVG</span>
                </label>

                {/* Direct Camera Capture */}
                <label className="cursor-pointer bg-slate-900/80 hover:bg-slate-900 border-2 border-dashed border-slate-700 hover:border-sky-500 rounded-xl p-5 flex flex-col items-center justify-center text-center transition-all group">
                  <input 
                    type="file" 
                    accept="image/*" 
                    capture="environment" 
                    onChange={handleFileUpload} 
                    className="hidden" 
                  />
                  <Camera className="w-8 h-8 text-slate-400 group-hover:text-sky-400 transition-colors mb-2" />
                  <span className="text-sm font-semibold text-slate-200">Take Photo</span>
                  <span className="text-[11px] text-slate-500 mt-1">Camera Capture Intent</span>
                </label>
              </div>

              {/* Progress Bar */}
              {uploadProgress !== null && (
                <div className="space-y-1 pt-2">
                  <div className="flex justify-between text-xs text-slate-300 font-medium">
                    <span>Uploading file to web app server...</span>
                    <span>{uploadProgress}%</span>
                  </div>
                  <div className="w-full bg-slate-700 h-2 rounded-full overflow-hidden">
                    <div 
                      className="bg-sky-500 h-full transition-all duration-150"
                      style={{ width: `${uploadProgress}%` }}
                    />
                  </div>
                </div>
              )}
            </div>

            {/* List of Uploaded Attachments */}
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 space-y-4">
              <h3 className="text-sm font-semibold text-white flex items-center justify-between">
                <span>Uploaded Files & Receipts ({uploadedFiles.length})</span>
                <span className="text-xs text-slate-400">Stored on Web App Server</span>
              </h3>

              <div className="divide-y divide-slate-700/60 border border-slate-700/60 rounded-xl overflow-hidden bg-slate-900/50">
                {uploadedFiles.map(file => (
                  <div key={file.id} className="p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors">
                    <div className="flex items-center space-x-3">
                      {file.type.startsWith('image/') ? (
                        <div className="w-10 h-10 rounded-lg bg-slate-800 border border-slate-700 overflow-hidden flex items-center justify-center">
                          {file.url ? (
                            <img src={file.url} alt={file.name} className="w-full h-full object-cover" />
                          ) : (
                            <ImageIcon className="w-5 h-5 text-sky-400" />
                          )}
                        </div>
                      ) : (
                        <div className="w-10 h-10 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center text-sky-400">
                          <FileText className="w-5 h-5" />
                        </div>
                      )}
                      <div>
                        <div className="text-sm font-medium text-slate-200">{file.name}</div>
                        <div className="text-xs text-slate-500 flex items-center space-x-2 mt-0.5">
                          <span>{file.size}</span>
                          <span>•</span>
                          <span>{file.type}</span>
                          <span>•</span>
                          <span>{file.uploadedAt}</span>
                        </div>
                      </div>
                    </div>

                    <span className="text-xs text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/20 flex items-center gap-1 font-medium">
                      <Check className="w-3.5 h-3.5" />
                      Uploaded
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: PRINTER MANAGER & DIAGNOSTICS */}
        {activeTab === 'printer' && (
          <div className="space-y-6">
            
            {/* Diagnostics Panel */}
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-white flex items-center gap-2">
                  <Bluetooth className="w-5 h-5 text-sky-400" />
                  Native Bluetooth Diagnostics
                </h2>
                <button
                  onClick={loadPrinterInfo}
                  className="p-2 bg-slate-700 hover:bg-slate-600 rounded-xl text-slate-200 transition-colors flex items-center gap-1.5 text-xs font-medium"
                >
                  <RefreshCw className="w-4 h-4" />
                  Refresh Diagnostics
                </button>
              </div>

              {diagnostics ? (
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div className="bg-slate-900/60 p-4 rounded-xl border border-slate-700/60">
                    <span className="text-xs text-slate-400">Bluetooth Radio:</span>
                    <div className="mt-1 flex items-center space-x-2 font-semibold">
                      {diagnostics.bluetoothEnabled ? (
                        <span className="text-emerald-400 flex items-center gap-1">
                          <CheckCircle2 className="w-4 h-4" /> Enabled
                        </span>
                      ) : (
                        <span className="text-rose-400 flex items-center gap-1">
                          <AlertCircle className="w-4 h-4" /> Disabled
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="bg-slate-900/60 p-4 rounded-xl border border-slate-700/60">
                    <span className="text-xs text-slate-400">Assignment:</span>
                    <div className="mt-1 font-semibold text-slate-200">
                      {diagnostics.assigned ? 'Printer Assigned' : 'Unassigned'}
                    </div>
                  </div>

                  <div className="bg-slate-900/60 p-4 rounded-xl border border-slate-700/60">
                    <span className="text-xs text-slate-400">Target Printer:</span>
                    <div className="mt-1 font-semibold text-sky-300">
                      {diagnostics.name !== 'None' ? diagnostics.name : 'None'}
                    </div>
                  </div>

                  <div className="bg-slate-900/60 p-4 rounded-xl border border-slate-700/60">
                    <span className="text-xs text-slate-400">MAC Address:</span>
                    <div className="mt-1 font-mono text-xs text-slate-300 truncate">
                      {diagnostics.mac}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="p-4 bg-slate-900/60 rounded-xl border border-slate-700/60 text-xs text-slate-400 text-center">
                  Native AndroidBridge diagnostics not connected. Open inside Android APK wrapper to query hardware radio.
                </div>
              )}
            </div>

            {/* Paired Bluetooth Printers List */}
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 space-y-4">
              <h3 className="text-sm font-semibold text-white flex items-center gap-2">
                <Settings className="w-4 h-4 text-sky-400" />
                Paired Bluetooth Thermal Printers ({bondedDevices.length})
              </h3>

              {bondedDevices.length > 0 ? (
                <div className="divide-y divide-slate-700/60 border border-slate-700/60 rounded-xl overflow-hidden bg-slate-900/50">
                  {bondedDevices.map((device, idx) => {
                    const isSelected = selectedMac === device.mac;
                    return (
                      <div key={idx} className="p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors">
                        <div className="flex items-center space-x-3">
                          <div className={`p-2.5 rounded-xl ${isSelected ? 'bg-sky-500/20 text-sky-400' : 'bg-slate-800 text-slate-400'}`}>
                            <Printer className="w-5 h-5" />
                          </div>
                          <div>
                            <div className="text-sm font-semibold text-slate-200">{device.name}</div>
                            <div className="text-xs font-mono text-slate-400">{device.mac}</div>
                          </div>
                        </div>

                        {isSelected ? (
                          <span className="px-3 py-1 rounded-full text-xs font-semibold bg-sky-500/10 text-sky-400 border border-sky-500/20 flex items-center gap-1">
                            <Check className="w-3.5 h-3.5" /> Assigned
                          </span>
                        ) : (
                          <button
                            onClick={() => handleAssignPrinter(device.mac, device.name)}
                            className="px-3 py-1.5 rounded-lg text-xs font-medium bg-slate-700 hover:bg-sky-500 hover:text-white text-slate-200 transition-all"
                          >
                            Assign Printer
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="p-6 bg-slate-900/60 rounded-xl border border-slate-700/60 text-center space-y-2">
                  <Printer className="w-8 h-8 text-slate-600 mx-auto" />
                  <div className="text-sm font-medium text-slate-300">No Paired Printers Found</div>
                  <p className="text-xs text-slate-500 max-w-md mx-auto">
                    Ensure your Bluetooth thermal printer is powered on and paired in your Android device's Bluetooth settings menu.
                  </p>
                </div>
              )}
            </div>
          </div>
        )}

      </main>
    </div>
  );
}

function GlobeIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg 
      {...props} 
      xmlns="http://www.w3.org/2000/svg" 
      width="24" 
      height="24" 
      viewBox="0 0 24 24" 
      fill="none" 
      stroke="currentColor" 
      strokeWidth="2" 
      strokeLinecap="round" 
      strokeLinejoin="round"
    >
      <circle cx="12" cy="12" r="10" />
      <line x1="2" x2="22" y1="12" y2="12" />
      <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
    </svg>
  );
}
