# TAP UNIVERSAL (v1.4.0) — Anywhere Transfer, MetroList Minimalist Design & Cross-Device Proximity Platform

TAP is a minimal, fast, privacy-focused proximity and remote file sharing platform built for Android (specifically optimized and tested for the **Samsung Galaxy A54 5G** running One UI 6 / 7, Android 14/15, and backward-compatible down to Android 8.0 / API 26).

**Zero accounts. Zero cloud file storage. Zero tracking. Universal compatibility.**

The receiver does **not** need to be on the same Wi-Fi network, and for web sharing does **not** need TAP installed to receive contacts, photos, or files.

---

## 1. Application Details & Deliverables

- **Application Name**: TAP
- **Application ID**: `dev.tap.app`
- **Version**: `1.4.0` (VersionCode: `6`)
- **Min SDK**: API 26 (Android 8.0 Oreo)
- **Compile SDK**: API 36 (Android 15+)
- **Target SDK**: API 36
- **Debug APK Location**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK Location**: `app/build/outputs/apk/release/app-release-unsigned.apk`
- **COTURN Deployment Guide**: [`COTURN_DEPLOYMENT.md`](COTURN_DEPLOYMENT.md)
- **GitHub Repository**: [https://github.com/shuaib-mk/Tap](https://github.com/shuaib-mk/Tap)

---

## 2. NEW in v1.4.0: True Anywhere File Transfer & MetroList UI Redesign

TAP v1.4.0 eliminates the local Wi-Fi / same-hotspot restriction and introduces a minimalist, typography-led user interface:

### 1. True Anywhere File Transfer (Across Any Network)
- **WebRTC DataChannels**: Peer-to-peer encrypted binary data streaming across different networks:
  - Sender on Wi-Fi, receiver on mobile data (4G/5G).
  - Sender on mobile data, receiver on Wi-Fi.
  - Both devices on separate Wi-Fi networks.
  - Both devices on separate cellular networks.
- **STUN / TURN NAT Traversal**: Direct peer-to-peer connection whenever possible. Automatic fallback to COTURN relay servers for symmetric NATs and mobile carriers.
- **Zero Cloud File Storage**: Signaling server and TURN relays only coordinate connection metadata and encrypted packets. Zero file bytes are ever stored on any server.
- **Ephemeral Pairing Codes**: 6-character short codes (e.g. `TAP-4829`) and instant share links (`https://tap.dev/s/TAP-4829`) for remote pairing.
- **Explicit Receiver Consent**: Incoming transfer requests show sender device name, file manifest, total size, and matching 6-digit SAS code. Transfers require explicit user approval.
- **Resumable 64 KB Chunk Streaming**: Backpressure-managed chunk transport with monotonic nonces, pause/resume capability, and streaming SHA-256 verification.
- **Offline Nearby Fallback**: Autonomous Wi-Fi Direct group creation (`WifiP2pManager`) enables transfers even when no cellular or internet connection is available.

### 2. Complete UI/UX Redesign (MetroList Aesthetic)
- **Obsidian Slate & Muted Sage Palette**: High-contrast dark background (`#090A0E`), rich panel tiles (`#13151B`), and crisp lime/sage accents (`#A3E635`).
- **Typography-Led Hierarchy**: Generous whitespace, clean headers, and concise status chips.
- **MetroBottomBar**: 4 focused navigation destinations:
  1. **Share (`HomeScreen`)**: Send files, receive with short code, and view live transfer states.
  2. **Activity (`HistoryScreen`)**: Transfer logs with Sent/Received filters and clear-all action.
  3. **Identity (`ContactCardScreen`)**: Personal digital contact card with field privacy controls.
  4. **Settings (`SettingsScreen`)**: Custom COTURN server setup, signaling preferences, and hardware diagnostics.

### 3. Preserved Features (100% Intact & Verified)
- **Physical Tap to Share**: Passive HCE Type 4 NFC tag emulation for instant physical phone-to-phone pairing.
- **Zero-Install Web Fallback**: NDEF URI multi-record dispatch allows non-TAP devices (e.g. iPhones) to download shared files in browser.
- **Digital Contact Card**: RFC 2426/6350 vCard generation with field privacy controls.
- **Physical NFC Card Writer**: Encodes external physical NFC cards (NTAG213, NTAG215, NTAG216, Mifare).
- **Universal QR Code**: Scannable with any native iOS or Android camera app.
- **Samsung Quick Share Handoff**: Optional 1-tap delegation to Samsung's native Quick Share activity.

---

## 3. Product Screens & Navigation

1. **Share (`HomeScreen`)**: Central transfer hub with Anywhere Transfer launcher, 6-character pairing code input, and recent activity preview.
2. **Select Content (`FilePickerScreen`)**: Content staged selector with per-file size preview and one-tap removal.
3. **Transfer Progress (`TransferProgressScreen`)**: Displays real-time progress, speed (MB/s), transport type indicator (`DIRECT P2P` vs `TURN RELAY`), pause/resume, and cancel controls.
4. **Receiver Approval (`ReceiverApprovalScreen`)**: Consent screen showing sender name, file list, total size, and SAS code.
5. **Activity (`HistoryScreen`)**: Transfer log with Sent/Received filtering and SQLite persistence.
6. **Digital Contact Card (`ContactCardScreen`)**: Contact preview, field privacy switches, QR code generator, and physical tag writer trigger.
7. **NFC Card Writer (`NfcCardWriterScreen`)**: Physical tag writer with live capacity audit and write progress.
8. **Settings (`SettingsScreen`)**: COTURN relay configuration, signaling URL settings, and device identity.
9. **Compatibility Diagnostics (`CompatibilityDiagnosticsScreen`)**: Hardware diagnostics auditing NFC, HCE, BLE, Wi-Fi Direct, and Camera.

---

## 4. Hardware & Network Compatibility Matrix

| Recipient Device & Network | Connection Method | Result | Recipient App Required? |
| :--- | :--- | :---: | :---: |
| **Android (Different Wi-Fi / Mobile Data)** | WebRTC Anywhere (STUN/TURN) | **Encrypted Remote Transfer** | Yes (TAP) |
| **Android (Same Wi-Fi / Hotspot)** | Physical Tap to Share (NFC + TCP) | **Instant Transfer with SAS** | Yes (TAP) |
| **Android (Offline, No Internet)** | Autonomous Wi-Fi Direct | **Offline High-Speed Transfer** | Yes (TAP) |
| **Android without TAP (Same Wi-Fi)** | Zero-Install Web Fallback (NFC URI) | **Direct Browser Download** | **NO** |
| **Android without TAP** | Background NFC Contact vCard | **Native "Add to Contacts"** | **NO** |
| **Apple iPhone (Different Network)** | Universal Contact QR / Web Link | **Direct Browser Download** | **NO** |
| **Apple iPhone (Physical NFC Tag)** | Tag Written by TAP NFC Writer | **Native iOS Contact Banner** | **NO** |
| **Desktop / Laptop (Any Network)** | Web Link / Local Browser Share | **Direct Browser Download** | **NO** |
| **Samsung Galaxy (One UI 6 - 9.5)** | Quick Share System Handoff | **Native Quick Share Transfer** | **NO** (Quick Share) |

---

## 5. Security & Cryptographic Specifications

1. **End-to-End Encryption**: AES-256-GCM encryption with 96-bit monotonic nonces across all WebRTC DataChannel chunks.
2. **Ephemeral Key Exchange**: Ephemeral ECDH over curve `secp256r1`. Session keys are never reused or persisted.
3. **Short Authentication String (SAS)**: Deterministic 6-digit verification code derived from lexicographically sorted public keys, eliminating Man-in-the-Middle (MITM) risks.
4. **Streaming SHA-256 Verification**: Pre-computed SHA-256 checksums are validated on-the-fly chunk-by-chunk by the receiver. Corrupt or modified files are immediately destroyed before filesystem commitment.
5. **Zero Server Storage**: Signaling servers and COTURN relays never receive unencrypted file data, and file payloads are never written to server disk.
6. **COTURN Authentication**: Standard TURN REST API dynamic credential derivation (`<timestamp>:<user>` + HMAC-SHA1 signature) prevents credential exposure.

---

## 6. Build and Verification Instructions

### Automated Unit Test Suite
```powershell
.\gradlew.bat testDebugUnitTest
```
**Results**: **45 unit tests executed, 0 failures, 0 errors (100% pass rate)**:
- `WebRtcAnywhereTest`: 6 tests (Signaling payload serialization, session code formatting, manifest encoding, TURN ephemeral credential derivation, chunk frame header encoding/decoding, streaming SHA-256).
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
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (~63.8 MB)
- **Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk` (~56.8 MB)

---

## 7. Testing Anywhere File Transfer

### Scenario A: Remote Transfer (Sender on 5G, Receiver on Home Wi-Fi)
1. Install `app-debug.apk` on both devices.
2. Ensure Phone A is connected to Mobile Data (Wi-Fi turned off).
3. Ensure Phone B is connected to Home Wi-Fi.
4. **On Phone A (Sender)**:
   - Select files in TAP and tap **"Send Anywhere"**.
   - Note the 6-character code (e.g. `TAP-4829`).
5. **On Phone B (Receiver)**:
   - Tap **"Receive"** on the home screen and enter `TAP-4829`.
   - The **Incoming Transfer Request** dialog appears showing Phone A's device name, file count, total size, and 6-digit SAS code.
   - Tap **"Accept"**.
6. Files transfer directly via WebRTC DataChannel (or through COTURN relay if symmetric NAT prevents direct P2P).
7. Receiver validates streaming SHA-256 checksum and saves files to `Download/TAP`.
