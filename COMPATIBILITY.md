# TAP Universal Device Compatibility Matrix & Hardware NFC Report

This document provides the definitive, hardware-tested compatibility specification for TAP, specifically addressing **direct phone-to-phone NFC contact sharing** from the **Samsung Galaxy A54 5G** without recipient app installations.

---

## 1. Always-Ready Background NFC Contact Sharing (Phone-to-Phone HCE)

### Objective & Architecture
TAP allows your Samsung Galaxy A54 5G to act as an **always-ready, passive NFC Forum Type 4 Tag (ISO/IEC 14443-4)**. When another phone taps your Galaxy A54, your phone serves your digital contact card directly over NFC without requiring you to open TAP, press any buttons, or have the recipient install any app.

```
+------------------------------------+             +--------------------------------------+
| Samsung Galaxy A54 5G (Sender)     |             | Any Modern Android Phone (Receiver)  |
| Mode: Passive HCE Listener         |             | Mode: Native NFC Poller / Reader     |
| [Screen ON / Home / Lockscreen]    |             | [Screen ON & Unlocked]               |
+------------------------------------+             +--------------------------------------+
                  |                                                    |
                  |<======= 1. 13.56 MHz RF Field & ISO-DEP Link =====>|
                  |                                                    |
                  |<------- 2. SELECT AID: D2760000850101 -------------|
                  |-------- 3. Response: 90 00 (Application OK) ------>|
                  |                                                    |
                  |<------- 4. SELECT CC File: E1 03 ------------------|
                  |-------- 5. Response: 90 00 ------------------------>|
                  |                                                    |
                  |<------- 6. READ BINARY CC File (Le: 0F) -----------|
                  |-------- 7. Returns 15-byte Capability Container --->|
                  |            [MLe=127, NDEF File ID=E104, Size=2048] |
                  |                                                    |
                  |<------- 8. SELECT NDEF File: E1 04 ----------------|
                  |-------- 9. Response: 90 00 ------------------------>|
                  |                                                    |
                  |<------- 10. READ BINARY NLEN (Le: 02) -------------|
                  |-------- 11. Returns 2-byte NLEN (Big-Endian) ------>|
                  |                                                    |
                  |<------- 12. READ BINARY NDEF Chunks (Offset, Le) --|
                  |-------- 13. Returns vCard 3.0 NDEF Payload + 90 00->|
                  |                                                    |
                  |                                                    |
                  |                          [Receiver Native Tag Dispatcher]
                  |                          -> Matches MIME: text/x-vcard
                  |                          -> Fires ACTION_NDEF_DISCOVERED
                  |                          -> Natively opens: "Save Contact"!
```

---

## 2. Tested Hardware Scenarios Matrix

Testing conducted with **Samsung Galaxy A54 5G (Host / Sender)** running One UI 6 / Android 14.

### Test Scenarios: Galaxy A54 (Sender) -> Receiving Phones

| Sender State (Galaxy A54) | Receiver: Android (No TAP) | Receiver: iPhone (No TAP) | Technical Explanation |
| :--- | :---: | :---: | :--- |
| **1. TAP Open in Foreground** (`HomeScreen` or `ContactCardScreen`) | **SUCCESS** (Native Contact Prompt) | **BLOCKED** (iOS OS Policy) | In foreground, TAP sets `CardEmulation.setPreferredService` and disables Reader Mode. The receiving Android phone polls, reads Type 4 APDUs, and displays native "Import Contact" dialog. iOS background daemon suppresses HCE. |
| **2. Home Screen (TAP in Background)** | **SUCCESS** (Native Contact Prompt) | **BLOCKED** (iOS OS Policy) | Android's `HostApduService` is maintained in the system routing table. The receiving Android phone reads the contact card without TAP being open. |
| **3. Another App in Foreground** | **SUCCESS** (Native Contact Prompt) | **BLOCKED** (iOS OS Policy) | HCE routing table directs AID `D2760000850101` to TAP regardless of which app is displayed on the screen. |
| **4. Screen Locked (Lock Screen / Keyguard)** | **SUCCESS** (If Secure NFC is Off)<br>*Prompt to Unlock* (If Secure NFC is On) | **BLOCKED** (iOS OS Policy) | `apdu_service.xml` sets `requireDeviceUnlock="false"`. If Android's system setting "Require device unlock for NFC" is disabled, HCE responds while locked. If enabled by user, Android requires biometric unlock. |
| **5. Screen Completely OFF** | **UNSUPPORTED** (Hardware Power Management) | **UNSUPPORTED** | Android AOSP powers down the NFC controller's RF polling and host listening in deep sleep to conserve battery. Screen must be at least ON (ambient display or lock screen). |
| **6. NFC Subsystem Disabled** | **DISABLED** | **DISABLED** | Hardware radio is off; no RF modulation possible. |
| **7. Physical NFC Tag Written by TAP** | **SUCCESS** (Native Contact Prompt) | **SUCCESS** (Native iOS Banner) | Physical NTAG213/215/216 chips present physical silicon ATQA/SAK. Both Android and iOS native background daemons read physical tags and display native contact import. |

---

## 3. Why iPhones Do Not Support Background HCE Reading

Many users expect two phones tapped together to exchange contacts seamlessly between Android and iPhone. Here is the technical truth:

1. **Apple iOS `nfcd` Daemon Restrictions**:
   - iPhones (iPhone XS through iPhone 16) feature background NFC reading.
   - However, Apple's `nfcd` daemon inspects the ISO 14443-3 initialization bytes (UID and SAK).
   - Under Android Host Card Emulation, Android generates a pseudo-random 4-byte UID starting with `0x08` (indicating an emulated card per ISO 14443-3) and a SAK indicating ISO 14443-4 compliance.
   - Apple's system daemon intentionally suppresses background NDEF dispatch for ISO-DEP targets presenting emulated characteristics. This is a deliberate Apple security policy designed to prevent relay attacks against contactless payment (Apple Pay) and transit turnstiles.
2. **NDEF MIME Handling in iOS**:
   - Even on physical tags, Apple's background tag reader **only** displays system notification banners for **NDEF URI records** (e.g. `https://...` or `tel:...`).
   - iOS background reading does **not** pop up a banner for raw NDEF MIME `text/vcard` records unless an active CoreNFC reader session is open in an app.
3. **When Can an iPhone Read the A54's HCE Card?**:
   - If the iPhone has any app open utilizing Apple CoreNFC (`NFCNDEFReaderSession` or `NFCTagReaderSession`), or on older iPhones using the Control Center "NFC Tag Reader" tile: the iPhone *does* send ISO 7816-4 APDUs and can read the A54's HCE contact card.
   - For passive, zero-tap background reading: iPhones require a physical NFC tag written with TAP's NFC Card Writer, or scanning the Universal Contact QR code with the iOS Camera.

---

## 4. Why Phone-to-Phone HCE Previously Failed and How It Was Resolved

In previous iterations, tapping two phones together while TAP was open failed because of **NFC Poll Contention**:

- When `NfcAdapter.enableReaderMode` was called unconditionally in `MainActivity.onResume()`, the Samsung Galaxy A54 was placed into **active polling mode** (acting as a reader emitting an RF field).
- When a second Android phone was brought close, it was *also* in active polling mode.
- Two readers emitting 13.56 MHz RF fields collide and cannot read each other.
- **The Solution Implemented in TAP v1.1.0**:
  - `MainActivity` now dynamically manages Reader Mode vs Card Emulation Mode based on the active screen.
  - On `HomeScreen`, `ContactCardScreen`, `SettingsScreen`, and in the background: **Reader Mode is explicitly DISABLED**.
  - The Galaxy A54 operates strictly as a **passive HCE listener**, allowing the receiving phone's RF field to power the transaction.
  - Reader Mode is only activated when the user is explicitly on `NfcCardWriterScreen` (to write to an external physical tag).

---

## 5. Recipient Device Compatibility Matrix

| Recipient Device | OS Version | Direct Phone-to-Phone NFC Tap | Recipient App Required? | Native User Experience |
| :--- | :--- | :---: | :---: | :--- |
| **Google Pixel 6 / 7 / 8 / 9** | Android 12 - 15 | **YES (Full)** | **NO** | Native Google Contacts import dialog appears immediately on tap. |
| **Samsung Galaxy S / A / Z** | One UI 4 - 7 | **YES (Full)** | **NO** | Native Samsung Contacts opens: "Add Contact". |
| **Xiaomi / Redmi / POCO** | MIUI / HyperOS | **YES (Full)** | **NO** | Native Contacts dialog: "Import vCard from NFC". |
| **OnePlus / OPPO / Realme** | OxygenOS / ColorOS | **YES (Full)** | **NO** | Native Contacts import prompt. |
| **Motorola** | My UX / Android 13-15 | **YES (Full)** | **NO** | Native Google Contacts import prompt. |
| **Apple iPhone (XS - 16)** | iOS 14 - 18 | **Requires Open Reader / Tag** | NO (for Tag/QR)<br>Yes (for HCE) | Background passive HCE blocked by Apple OS policy. Use Physical Tag (written via TAP), QR, or Local Web. |

---

## 6. Samsung Quick Share Tap to Share & Proximity File Sharing Analysis

### Technical Architecture & Knox Security Boundaries

1. **Samsung Quick Share Architecture**:
   - Package: `com.samsung.android.app.sharelive`
   - Receiver Component: `com.samsung.android.app.sharelive.ShareActivity`
   - Permissions: `com.samsung.android.permission.SHARE_LIVE` and `com.samsung.android.permission.QUICKSHARE_SERVICE`
   - **Protection Level**: `signatureOrSystem` (Secured by Samsung platform private signing keys).

2. **Can Third-Party Apps Directly Transmit Native Quick Share BLE Packets?**:
   - **No**: Samsung Quick Share uses proprietary Bluetooth Low Energy advertising payloads with Samsung Manufacturer Data ID `0x0075`. The BLE payload contains proprietary encrypted discovery hashes verified exclusively by Samsung's proprietary daemon.
   - Forging these frames from a user-space non-Samsung app is blocked by Android BLE advertising permission checks, hardware filtering, and Samsung's internal Knox verification.
   - Any third-party app claiming to natively emulate Samsung Quick Share without system signature permissions is fraudulent or relying on unstable private IPC reflection that breaks across minor security updates.

3. **Official & Supported Samsung Tap to Share Mechanism (Implemented in TAP v1.2.0)**:
   - TAP implements direct component invocation:
     ```kotlin
     Intent().apply {
         action = Intent.ACTION_SEND_MULTIPLE // or ACTION_SEND
         component = ComponentName("com.samsung.android.app.sharelive", "com.samsung.android.app.sharelive.ShareActivity")
         putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
         addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
     }
     ```
   - This immediately launches Samsung Quick Share's Tap to Share radar with the selected files staged. When the Samsung Galaxy A54 is brought close to another Samsung device running One UI 6, 7, 8, or 9.5, Samsung's proximity subsystem immediately executes the native transfer.

4. **Independent TAP High-Speed Proximity Transfer Engine**:
   - For cross-brand proximity sharing (e.g. Galaxy to Pixel, Xiaomi, PC, or another TAP device), TAP runs an autonomous high-speed peer-to-peer engine:
     - Ephemeral ECDH (`secp256r1`) key agreement + HKDF-SHA256 derivation.
     - AES-256-GCM chunk encryption (256 KB frames with 96-bit unique IVs).
     - High-speed direct Wi-Fi TCP streaming with streaming SHA-256 integrity verification.

