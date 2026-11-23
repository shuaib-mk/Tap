# TAP UNIVERSAL (v1.3.0) — Physical Tap to Share, Zero-Install Web Fallback & Cross-Device Proximity Platform

TAP is a minimal, fast, privacy-focused proximity-sharing platform built for Android (specifically optimized and tested for the **Samsung Galaxy A54 5G** running One UI 6 / 7, Android 14/15, and backward-compatible down to Android 8.0 / API 26).

**Zero accounts. Zero cloud servers. Zero tracking. Universal compatibility.**

The receiver does **not** need TAP installed to receive contacts, photos, or files.

---

## 1. Application Details & Deliverables

- **Application Name**: TAP
- **Application ID**: `dev.tap.app`
- **Version**: `1.3.0` (VersionCode: `5`)
- **Min SDK**: API 26 (Android 8.0 Oreo)
- **Compile SDK**: API 36 (Android 15+)
- **Target SDK**: API 36
- **Debug APK Location**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK Location**: `app/build/outputs/apk/release/app-release-unsigned.apk`
- **GitHub Repository**: [https://github.com/shuaib-mk/Tap](https://github.com/shuaib-mk/Tap)

---

## 2. NEW in v1.3.0: Physical Tap to Share & Zero-Install Web Fallback

TAP v1.3.0 delivers the full **Physical Tap to Share** system following the architectural feasibility report:

### 1. TAP-to-TAP Physical Proximity Sharing (Primary Feature)
- **Physical Tap Initiation**: Sender stages files and activates "Physical Tap to Share". Phone enters discoverable NFC Host Card Emulation (HCE) state emulating an NFC Forum Type 4 Tag.
- **RF Poll Contention Prevention**: The Sender operates as a **Passive HCE Listener** (Reader Mode disabled). The Receiver operates as the **Poller/Reader**. This completely prevents RF carrier collision between two NFC phones.
- **Automated P2P Connection**: Bringing the devices together physically triggers the NFC tap, exchanging ephemeral cryptographic session info via Type 4 HCE. No manual IP address entry is required.
- **Short Authentication String (SAS)**: Ephemeral ECDH (`secp256r1`) agreement derives a deterministic 6-digit verification code (`calculateSasCode`) displayed on both screens (e.g. `482 193`). Public keys are lexicographically sorted to guarantee identical key derivation on both sides.
- **Explicit Recipient Consent**: The receiver displays a prominent incoming transfer consent card with the sender's device name, file count, total size, and the matching 6-digit SAS code. Zero bytes are transferred until the recipient taps "Accept".
- **Streaming AES-256-GCM Transfer**: Payloads are streamed in 256 KB encrypted chunks with strictly monotonic nonces (`chunkIndex` + direction byte + session entropy) over high-speed bidirectional TCP sockets (port 8990).
- **Streaming SHA-256 Integrity Verification**: Payloads are verified on-the-fly against pre-computed SHA-256 digests and atomically committed to MediaStore / `Download/TAP`. Hash mismatches trigger immediate file destruction.

### 2. Zero-Install NFC Web Sharing (Fallback for Devices Without TAP)
- **NDEF Multi-Record Dispatch**: The emulated Type 4 Tag serves a multi-record NDEF payload:
  1. Record 1: Custom MIME `application/vnd.dev.tap.proximity` (for TAP-to-TAP bootstrap).
  2. Record 2: NFC Forum URI `http://<IP>:8989/?token=<sessionToken>` (for zero-install web fallback).
  3. Record 3: Android Application Record (AAR) `dev.tap.app`.
- **Zero-Install Web Download**: If the recipient phone does not have TAP installed (or is an iPhone), the native NFC dispatcher detects Record 2 and prompts to open Safari / Chrome.
- **Short-Lived Single-Session Security**: Embedded `LocalHttpServer` enforces the session token. Any unauthorized requests without the token are rejected with `HTTP/1.1 403 Forbidden`. The token is cleanly revoked when the session ends.

### 3. Preserved Features (100% Intact & Verified)
- **Digital Contact Card**: RFC 2426 / 6350 vCard generation with granular field privacy toggles.
- **Always-Ready Background NFC HCE**: Tapping the Galaxy A54 while locked or on the home screen shares the contact card directly.
- **Physical NFC Tag Writer**: Encodes external physical NFC cards (NTAG213, NTAG215, NTAG216, Mifare).
- **QR Scanner & Viewfinder**: Scans peer TAP sessions or contact QR codes with manual JSON fallback.
- **Samsung Quick Share Handoff**: Preserved optional 1-tap delegation to `com.samsung.android.app.sharelive/.ShareActivity`.
- **Payment Coexistence**: Uses ISO 7816-4 AID category `other`, ensuring zero conflict with Google Wallet / Samsung Pay.

---

## 3. Product Screens & Architecture

TAP features a unified Dark Charcoal (`#090B10`) and Soft Lime (`#B7F36B`) design across 15 interactive screens:

1. **Home (`HomeScreen`)**: Central hub with Universal Mode status pills, pulsing NFC ring, Send/Receive toggle, Digital Contact Card banner, and Proximity File Sharing card.
2. **Send (`FilePickerScreen`)**: Multi-content selector for Photos, Videos, Documents, Arbitrary Files, Links, and Contacts.
3. **Receive (`QrConnectScreen`)**: Dual-mode receiver interface supporting TAP encrypted sessions and Local Web Browser Sharing with live URL and QR code.
4. **Nearby Devices (`DiscoveryScreen`)**: High-contrast radar animation discovering local BLE beacons and Wi-Fi Direct peers.
5. **NFC Readiness (`NfcReadinessScreen`)**: Real-time mode selector and hardware checklist across Mode A, Mode B, and Mode C.
6. **Contact Card (`ContactCardScreen`)**: Personal digital identity card preview, field privacy toggles, and instant sharing action buttons.
7. **NFC Card Writer (`NfcCardWriterScreen`)**: Live NFC tag encoder with tag capacity checking, writing progress, and success feedback.
8. **QR Scanner (`QrScannerScreen`)**: Viewfinder to scan peer TAP sessions or vCard contact QR codes.
9. **Receiver Approval (`ReceiverApprovalScreen`)**: Explicit security gate displaying sender name, manifest, total size, and Accept/Reject actions.
10. **Transfer Progress (`TransferProgressScreen`)**: Real-time progress bar, throughput in MB/s, ETA counter, and cancel button.
11. **Transfer Complete (`TransferCompleteScreen`)**: Success checkmark, SHA-256 integrity verification badge, and direct file launcher.
12. **Transfer History (`HistoryScreen`)**: Reactive SQLite transfer log with Sent/Received filters and file sizes.
13. **Settings (`SettingsScreen`)**: Device name, default ports, storage directories, and privacy overview.
14. **Compatibility Diagnostics (`CompatibilityDiagnosticsScreen`)**: Runtime hardware audit of NFC, HCE, BLE, Wi-Fi, Camera, One UI, and native sharing.
15. **Proximity File Sharing (`ProximityShareScreen`)**: Dedicated Physical Tap to Share hub with pulsing NFC radar, 6-digit SAS verification code dialog, live progress indicator, and Samsung Quick Share handoff.

---

## 4. Hardware Compatibility Matrix

| Recipient Device | Protocol / Mode | Result | Recipient App Required? |
| :--- | :--- | :---: | :---: |
| **Android Phone with TAP** | Physical Tap to Share (NFC + TCP) | **Instant Transfer with SAS** | Yes (TAP) |
| **Android Phone without TAP** | Zero-Install Web Fallback (NFC URI) | **Direct Browser Download** | **NO** |
| **Android Phone without TAP** | Background NFC Contact vCard | **Native "Add to Contacts"** | **NO** |
| **Apple iPhone (XS - 16)** | Zero-Install Web Fallback (NFC URI) | **Direct Safari Download** | **NO** |
| **Apple iPhone (XS - 16)** | Physical NFC Tag Written by TAP | **Native iOS Contact Banner** | **NO** |
| **Apple iPhone (XS - 16)** | Universal QR Contact Card | **Native iOS Camera Scan** | **NO** |
| **Desktop / Laptop (Win/Mac/Linux)** | Local Web Browser Sharing | **Direct Browser Download** | **NO** |
| **Samsung Galaxy (One UI 6.x - 9.5)** | Samsung Quick Share Handoff | **Native Quick Share Transfer** | **NO** (Quick Share) |

---

## 5. Security & Cryptographic Specifications

1. **Key Agreement**: Ephemeral Elliptic Curve Diffie-Hellman (ECDH) over curve `secp256r1`.
2. **Symmetric Key Derivation**: HKDF-SHA256 (`HMAC-SHA256` extract-and-expand) binding session key to `sessionId`, `senderDeviceId`, `receiverDeviceId`, and lexicographically ordered public keys (`$lowerPub:$higherPub`).
3. **Short Authentication String (SAS)**: 6-digit human-verifiable authentication code computed from HKDF info string with lexicographically sorted public keys to eliminate Man-in-the-Middle (MITM) risks.
4. **Transport Encryption**: AES-256-GCM with 96-bit monotonically incrementing IV per 256 KB chunk and 128-bit authentication tag.
5. **Data Integrity**: Streaming SHA-256 digest calculated over unencrypted input streams and verified byte-for-byte by the receiver before atomic filesystem commit.
6. **Zero-Install Web Security**: Embedded HTTP server enforces short-lived single-session random tokens. Requests lacking the token return `HTTP/1.1 403 Forbidden`.

---

## 6. Build and Verification Instructions

### Automated Unit Test Suite
```powershell
.\gradlew.bat testDebugUnitTest
```
**Results**: 38 tests executed, **0 failures, 0 errors** across:
- `ProximitySharingTest`: 12 tests (ECDH, SAS matching, MITM resistance, AES-GCM chunking, handshake state machine, SHA-256 integrity, NDEF repository, offer/response round-trip).
- `TapHostApduServiceTest`: 8 tests (ISO 7816-4 APDUs, Select AID, CC file, NDEF file read, proximity payload injection).
- `CapabilitiesTest`: 3 tests (Hardware capability detection).
- `ContactCardTest`: 5 tests (vCard 3.0/4.0 formatting, field privacy filters).
- `TapSecurityTest`: 5 tests (Filename sanitization, MIME validation).
- `TapProtocolTest`: 3 tests (JSON serialization).
- `SelectedFileTest`: 2 tests (File sizing and formatting).

### Compiling APKs
```powershell
# Build Debug APK
.\gradlew.bat assembleDebug

# Build Unsigned Release APK
.\gradlew.bat assembleRelease
```
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 7. Two-Device Physical Testing Guide

### Test 1: TAP-to-TAP Physical Tap to Share (Phone A -> Phone B)
1. Install `app-debug.apk` on Phone A (Samsung Galaxy A54) and Phone B.
2. Connect both phones to the same Wi-Fi or turn on Portable Hotspot on Phone A and connect Phone B.
3. **On Phone A (Sender)**:
   - Open TAP → Tap **Proximity File Sharing**.
   - Tap **"Media"** or **"Files"** and select files.
   - Tap **"Physical Tap to Share"**.
   - The card pulses with *"Ready to Tap! Hold backs of phones together to transfer"*.
4. **On Phone B (Receiver)**:
   - Unlock Phone B and touch its back against Phone A.
5. **Approval & SAS Verification**:
   - Phone B's screen automatically displays the **Incoming Proximity Transfer** card with Phone A's name, file count, and a prominent 6-digit code (e.g. `482 193`).
   - Confirm the exact same 6-digit code appears on Phone A.
   - Tap **"Accept"** on Phone B.
6. **High-Speed Transfer & Integrity Check**:
   - Both devices display the live progress bar and transfer speed (MB/s).
   - Once all chunks are received and decrypted, Phone B verifies the SHA-256 checksum.
   - Green badge **"SHA-256 Verified ✓"** appears and the files are saved to `Download/TAP`.

### Test 2: Zero-Install NFC Web Sharing (Phone A -> iPhone / Phone without TAP)
1. On Phone A: Stage files and tap **"Physical Tap to Share"**.
2. Touch the back of an iPhone (screen unlocked) to the back of Phone A.
3. The iPhone native NFC banner appears: *"Website Tag: http://192.168...:8989/?token=..."*.
4. Tap the notification on iPhone: Safari opens and displays the download buttons for each shared file.
5. Tap **"Download"** to save the files directly in Safari without installing TAP.
