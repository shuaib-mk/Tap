# Samsung Quick Share & One UI Tap to Share: Interoperability Research Report

## 1. Executive Summary

This report documents the technical findings regarding third-party interoperability with **Samsung Quick Share** and **One UI Tap to Share** on Samsung Galaxy devices (including the Samsung Galaxy A54 5G and One UI devices).

---

## 2. Background: Quick Share Evolution

1. **Origin & Rebranding**:
   - Quick Share was introduced with One UI 2.1 on the Galaxy S20.
   - In early 2024, Google and Samsung officially merged Google Nearby Share and Samsung Quick Share under the single brand name **Quick Share**.
   - On Samsung devices running One UI 6, One UI 7, and future One UI releases, the primary Quick Share subsystem is provided by package `com.samsung.android.app.sharelive`, integrated closely with Samsung Cloud, Samsung Account, and Google Play Services (`com.google.android.gms`).

2. **One UI Tap to Share**:
   - Samsung's "Tap to Share" proximity workflow uses:
     - **Bluetooth Low Energy (BLE)** background advertising and scanning for proximity detection.
     - **NFC Handshake**: If two Galaxy devices are brought into physical contact, the NFC controller can trigger a session wake-up.
     - **Ultra-Wideband (UWB)**: Available on Galaxy S Plus/Ultra and Z Fold models (note: the Galaxy A54 5G does **not** have a UWB transceiver; it relies on BLE and Wi-Fi Direct).
     - **High-Speed Transfer**: Conducted over Wi-Fi Direct (`p2p-wlan0`) or local Wi-Fi SoftAP.

---

## 3. Third-Party Developer Access & API Analysis

### Does Samsung provide an official public SDK for Quick Share?
- **Finding**: **No.** Samsung does not expose an open, third-party Android client SDK that allows non-system apps to directly act as Quick Share transfer endpoints, nor does Samsung publish an open socket specification for direct file injection into the Quick Share daemon.
- **Security Boundaries**:
  - The Quick Share package (`com.samsung.android.app.sharelive`) requires system-level signature permissions:
    - `com.samsung.android.permission.SHARE_LIVE` (Protection level: `signatureOrSystem`)
    - `com.samsung.android.permission.QUICKSHARE_SERVICE` (Protection level: `signature`)
  - Any third-party application attempting to bind directly to Quick Share's internal AIDL services or impersonate Quick Share BLE manufacturer frames (`0x0075`) will fail signature verification and SELinux policy checks.

### What is the officially supported integration mechanism?
- **Official Supported Path**: **Android System Handoff (Sharesheet & Explicit Component Invocation)**.
  - Third-party applications provide content through Android's `FileProvider` and launch Samsung Quick Share via:
    - `Intent.ACTION_SEND` (single file/photo/video)
    - `Intent.ACTION_SEND_MULTIPLE` (batch files)
    - Explicit Component: `ComponentName("com.samsung.android.app.sharelive", "com.samsung.android.app.sharelive.ShareActivity")`
  - When invoked, the Samsung operating system immediately opens the native Quick Share proximity radar, searches for nearby One UI devices (and Tap to Share targets), and handles the end-to-end file transfer using Samsung's verified cryptographic keys.

---

## 4. Architectural Decision for TAP v2

To satisfy both native Samsung Galaxy interoperability and independent proximity file sharing without compromising security:

1. **Samsung Quick Share Compatibility Adapter (`QuickShareCompatibilityAdapter`)**:
   - Detects whether Samsung Quick Share or Google Quick Share is installed.
   - Provides clean, seamless handoff to Samsung's native Quick Share activity, staging selected photos, videos, documents, or files so the user can send directly to any Samsung Galaxy device with 1 tap.
2. **TAP Proximity Sharing Engine (`ProximitySharingEngine`)**:
   - An independent, high-performance, privacy-focused proximity file-sharing engine.
   - Uses dedicated BLE beaconing and direct Wi-Fi TCP sockets with AES-256-GCM chunk streaming and SHA-256 integrity verification.
   - Allows seamless file transfers between any compatible TAP devices.
3. **Strict Separation of Concerns**:
   - Existing NFC Contact Sharing (Mode A, B, C) remains 100% intact and unaffected.
   - The new Proximity File Sharing feature operates in its own dedicated package (`dev.tap.app.proximity`).
