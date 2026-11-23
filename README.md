# TAP UNIVERSAL (v1.2.0) — Cross-Device Proximity-Sharing & Samsung Quick Share

TAP is a minimal, fast, privacy-focused proximity-sharing platform built for Android (specifically optimized and tested for the **Samsung Galaxy A54 5G** running One UI 6 / 7, Android 14/15, and backward-compatible down to Android 8.0 / API 26).

**Zero accounts. Zero cloud servers. Zero tracking. Universal compatibility.**

The receiver does **not** need TAP installed to receive contacts, photos, or files.

---

## 1. Application Details & Deliverables

- **Application Name**: TAP
- **Application ID**: `dev.tap.app`
- **Version**: `1.2.0` (VersionCode: `3`)
- **Min SDK**: API 26 (Android 8.0 Oreo)
- **Compile SDK**: API 36 (Android 15+)
- **Target SDK**: API 36
- **Debug APK Location**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK Location**: `app/build/outputs/apk/release/app-release-unsigned.apk`
- **GitHub Repository**: [https://github.com/shuaib-mk/Tap](https://github.com/shuaib-mk/Tap)

---

## 2. NEW in v1.2.0: Proximity File Sharing & Samsung Quick Share Tap to Share

TAP v1.2.0 introduces **Proximity File Sharing** with dedicated interoperability for Samsung Galaxy devices (One UI 6.x, 7.x, 8.x, 9.5) and Galaxy A54 5G:

1. **Samsung Quick Share Tap to Share Integration**:
   - Detects `com.samsung.android.app.sharelive` (Samsung Quick Share) and One UI version at runtime.
   - Stages multi-category files (Photos, Videos, Audio, PDFs, Documents, Archives) with auto-resolved content URIs.
   - Hands off directly to `com.samsung.android.app.sharelive/.ShareActivity` with `FLAG_GRANT_READ_URI_PERMISSION`.
   - On compatible Galaxy devices (e.g. Galaxy A54 brought close to another Galaxy running One UI), this triggers Samsung's native proximity radar and Tap to Share prompt without friction.
2. **TAP Native High-Speed Proximity Engine**:
   - For non-Samsung devices or independent transfers, TAP runs its own zero-cloud proximity engine.
   - **Ephemeral Security**: ECDH (`secp256r1`) key agreement + HKDF-SHA256 key derivation + AES-256-GCM chunk encryption.
   - **Streaming Transport**: High-speed TCP socket streaming 256 KB encrypted chunks with real-time throughput (MB/s) and streaming SHA-256 checksums.
3. **Dedicated Screen & Home Screen Entry**:
   - Access via the **"Proximity File Sharing"** hero card on the Home Screen.
   - Categorized picker for Media & Documents, staged file preview with category badges, and dual transfer modes ("Share via Samsung Quick Share" & "TAP Proximity Transfer").

---

## 3. Universal Contact Sharing (Preserved & Always-Ready)

When sharing a contact card from the **Samsung Galaxy A54 5G**, the receiving phone does **not** need TAP installed:

1. **Digital Contact Profile & Granular Privacy**:
   - Customize name, title, company, phone, email, website, social profiles (LinkedIn, GitHub, X), and notes.
   - Field privacy checkboxes: choose exactly which details are shared and keep sensitive information private.
2. **Standards-Compliant vCard 3.0 & 4.0**:
   - Formatted to RFC 2426 (vCard 3.0) and RFC 6350 (vCard 4.0), natively understood by Apple iOS, Android, macOS, and Windows.
3. **Four Universal Sharing Methods (No Recipient App Needed)**:
   - **Method 1: Physical NFC Tag (NFC Card Writer)**: Hold the Galaxy A54 to an NFC tag (NTAG213, NTAG215, NTAG216, Mifare). TAP encodes the vCard NDEF record. Any iPhone (XR to iPhone 16) or Android device taps the tag and immediately prompts "Add to Contacts".
   - **Method 2: Universal QR Contact Card**: TAP generates a high-contrast vCard QR code. Scannable by 100% of native camera apps on iPhone and Android with instant 1-tap contact import.
   - **Method 3: Local Web Browser Sharing (Embedded HTTP Server)**: TAP spins up a lightweight embedded HTTP server on port 8989 (`http://<A54-IP>:8989/`). Recipient joins same Wi-Fi or Galaxy A54 Hotspot, scans the link, and downloads `.vcf` directly in Safari or Chrome.
   - **Method 4: System Sharesheet Export**: Export the `.vcf` file directly to Samsung Quick Share, Google Nearby Share, WhatsApp, Email, or Messages.

---

## 4. The 15 Product Screens

TAP features a unified Dark Charcoal (`#090B10`) and Soft Lime (`#B7F36B`) design across 15 interactive screens:

1. **Home (`HomeScreen`)**: Central command center with Universal Mode status pills, pulsing NFC ring, Send/Receive toggle, Digital Contact Card hero banner, and the new Proximity File Sharing card.
2. **Send (`FilePickerScreen`)**: Multi-content selector for Photos, Videos, Documents, Arbitrary Files, Links, and Contacts, with total payload size and Quick Share handoff.
3. **Receive (`QrConnectScreen`)**: Dual-mode receiver interface supporting TAP-to-TAP encrypted sessions and Local Web Browser Sharing with live URL and QR.
4. **Nearby Devices (`DiscoveryScreen`)**: High-contrast radar animation discovering local BLE beacons and Wi-Fi peers.
5. **NFC Readiness (`NfcReadinessScreen`)**: Real-time mode selector and hardware checklist across Mode A, Mode B, and Mode C.
6. **Contact Card (`ContactCardScreen`)**: Personal digital identity card preview, field privacy toggles, and instant sharing action buttons.
7. **NFC Card Writer (`NfcCardWriterScreen`)**: Live NFC tag encoder with tag capacity checking, writing progress, and success feedback.
8. **QR Scanner (`QrScannerScreen`)**: Viewfinder to scan peer TAP sessions or vCard contact QR codes, with manual JSON input fallback.
9. **Receiver Approval (`ReceiverApprovalScreen`)**: Explicit security gate displaying sender name, manifest, total size, and Accept/Reject actions.
10. **Transfer Progress (`TransferProgressScreen`)**: Real-time progress bar, transfer throughput in MB/s, ETA counter, and cancel button.
11. **Transfer Complete (`TransferCompleteScreen`)**: Success checkmark, SHA-256 integrity verification badge, and direct file launcher.
12. **Transfer History (`HistoryScreen`)**: Reactive SQLite transfer log with Sent/Received filters, file sizes, and deletion controls.
13. **Settings (`SettingsScreen`)**: Device name, default ports, storage directories, and privacy overview.
14. **Compatibility Diagnostics (`CompatibilityDiagnosticsScreen`)**: Runtime hardware audit of NFC, HCE, BLE, Wi-Fi, Camera, One UI, and native sharing.
15. **Proximity File Sharing (`ProximityShareScreen`)**: Dedicated Samsung Quick Share Tap to Share and TAP high-speed encrypted proximity file transfer hub with live multi-category file staging.

---

## 4. NFC Universal Subsystem: 3 Distinct Operating Modes

- **Mode A: TAP-to-TAP Handshake**:
  - Exclusively for devices with TAP installed.
  - Tapping devices exchanges a short-lived session token (ephemeral ECDH public key, IP, and port) over NFC HCE/Reader Mode.
  - Heavy file payload is negotiated separately over direct Wi-Fi TCP sockets.
- **Mode B: Universal NFC Contact Card**:
  - Standards-based vCard NDEF generation for physical NFC tags.
  - Granular privacy filtering: unselected fields are excluded from the output.
  - Automatic fallback to compact NDEF URI records if physical tag storage is constrained (e.g. NTAG213).
- **Mode C: Background HCE Tag Emulation**:
  - Emulates an NFC Forum Type 4 Tag with AID `D2760000850101` and proprietary AID `F04E464354415000`.
  - Implements ISO/IEC 7816-4 APDUs: SELECT NDEF, SELECT CC, READ CC, SELECT NDEF FILE, READ NDEF FILE.
  - Operates under the `other` category to ensure zero interference with Google Wallet / Samsung Pay.

---

## 5. Universal Device Compatibility Matrix

See the comprehensive [COMPATIBILITY.md](file:///c:/Users/q04ti/Downloads/TAP-Android-starter/TAP-Android/COMPATIBILITY.md) for full hardware analysis.

| Device Ecosystem | Protocol / Mode | Status | Recipient App Required? |
| :--- | :--- | :--- | :---: |
| **Samsung Galaxy A54 5G** (Host) | All Modes (A, B, C, Web, Sharesheet) | **Verified on Hardware** | Host device |
| **Samsung Galaxy S/A Series** | Mode A (TAP-to-TAP) | **Fully Implemented** | Yes (TAP) |
| **Samsung Galaxy S/A Series** | Mode B (Universal Contact) | **Fully Implemented** | **NO** |
| **Google Pixel (6 - 9)** | Mode A (TAP-to-TAP) | **Fully Implemented** | Yes (TAP) |
| **Google Pixel (6 - 9)** | Mode B (Universal Contact) | **Fully Implemented** | **NO** |
| **Xiaomi / Redmi / POCO** | Mode A & B | **Fully Implemented** | Only for Mode A |
| **OnePlus / OPPO / Vivo** | Mode A & B | **Fully Implemented** | Only for Mode A |
| **Apple iPhone (iOS 14 - 18)** | Universal Contact Card | **Fully Implemented** | **NO (Physical Tag, Camera QR, Safari Web)** |
| **Apple iPhone (iOS 14 - 18)** | Local Web File Download | **Fully Implemented** | **NO (Safari on Wi-Fi / Hotspot)** |
| **Desktop (Win / Mac / Linux)** | Local Web Sharing | **Fully Implemented** | **NO (Any standard browser)** |

---

## 6. Security Architecture

1. **Authenticated Key Exchange**: Ephemeral ECDH over NIST curve `secp256r1`.
2. **Key Derivation**: HKDF-SHA256 (`HMAC-SHA256` extract-and-expand) deriving a 256-bit symmetric session key.
3. **Chunked Encryption**: AES-256-GCM with 96-bit unique IV per 256 KB chunk and 128-bit authentication tag.
4. **Data Integrity**: Streaming SHA-256 hash computed incrementally on unencrypted plaintext and verified by receiver post-transfer.
5. **No Cloud Relay**: Zero bytes touch external third-party servers.

---

## 7. Build and Installation Instructions

### Prerequisites
- JDK 17 (Temurin OpenJDK 17.0.20.1)
- Android SDK (API 34/35/36, Build-Tools 35.0.0 or 36.0.0)

### Running Automated Tests
```powershell
.\gradlew.bat testDebugUnitTest
```

### Compiling APKs
```powershell
# Build Debug APK
.\gradlew.bat assembleDebug

# Build Unsigned Release APK
.\gradlew.bat assembleRelease
```

### Installing on Samsung Galaxy A54 5G
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
