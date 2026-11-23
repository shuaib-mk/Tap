# TAP Universal Protocol Specification (v1.1.0)

## 1. Overview
The **TAP Universal Protocol** provides secure, serverless proximity communication between computing devices. It is transport-agnostic, supporting:
1. NFC Proximity Bootstrap (HCE Type 4 Tag Emulation & Reader Mode)
2. Bluetooth Low Energy (BLE) Advertising & Scanning
3. Dynamic Optical QR Codes
4. Direct Wi-Fi TCP Sockets with Authenticated TLS / AES-256-GCM
5. Universal Embedded HTTP Web Service for non-TAP recipients

---

## 2. Wire Format & Encryption Architecture

### Cryptographic Primitives
- **Key Agreement**: Ephemeral Elliptic Curve Diffie-Hellman (ECDH) over NIST curve `secp256r1`.
- **Key Derivation**: HKDF-SHA256 (`HMAC-SHA256` extract-and-expand) deriving a 256-bit symmetric session key.
- **Symmetric Cipher**: AES-256-GCM (Galois/Counter Mode) with 96-bit unique IV per chunk and 128-bit authentication tag.
- **Data Integrity**: Streaming SHA-256 hash computed incrementally on unencrypted plaintext and verified by receiver post-transfer.

---

## 3. Handshake & Transfer State Machine

```
   Sender                                             Receiver
     |                                                    |
     | ----- 1. Discover Session Offer (NFC / BLE / QR) ->|
     |                                                    |
     | <--- 2. Establish TCP Connection (Port 8765) ------|
     |                                                    |
     | ===== 3. Ephemeral ECDH Key Exchange =============|
     |                                                    |
     | ----- 4. Send Transfer Request (FileList, Meta) -->|
     |                                                    |
     | <--- 5. Receiver Explicit Approval / Rejection ---|
     |                                                    |
     | ===== 6. Stream Encrypted Chunks (AES-256-GCM) ===>|
     |                                                    |
     | ----- 7. Send EOF with SHA-256 Checksum ---------->|
     |                                                    |
     | <--- 8. Transfer Verified Acknowledgment ---------|
```

---

## 4. Universal Contact vCard Specification

Contact cards transmitted over NFC NDEF, QR codes, or Local Web use standardized **vCard 3.0 (RFC 2426)** and **vCard 4.0 (RFC 6350)** formats:

```vcard
BEGIN:VCARD
VERSION:3.0
FN:Alex Morgan
N:Morgan;Alex;;;
ORG:TAP Technologies
TITLE:Product Architect
TEL;TYPE=CELL,VOICE:+1 (555) 234-5678
EMAIL;TYPE=INTERNET,PREF:alex.morgan@tap-protocol.dev
URL:https://tap-protocol.dev
X-SOCIALPROFILE;TYPE=github:https://github.com/shuaib-mk/Tap
NOTE:Shared instantly via TAP Universal Contact Card
END:VCARD
```

---

## 5. Type 4 Tag HCE APDU Mapping

When operating in **Mode C (Background HCE Tag Emulation)**, TAP answers standard ISO/IEC 7816-4 APDU commands:

1. **Select NDEF Application**:
   `00 A4 04 00 07 D2 76 00 00 85 01 01 00` -> `90 00`
2. **Select Capability Container (CC)**:
   `00 A4 00 0C 02 E1 03` -> `90 00`
3. **Read Binary CC**:
   `00 B0 00 00 0F` -> `00 0F 20 00 7F 00 7F 04 06 E1 04 08 00 00 FF 90 00`
4. **Select NDEF File**:
   `00 A4 00 0C 02 E1 04` -> `90 00`
5. **Read Binary NDEF File**:
   `00 B0 [P1] [P2] [Le]` -> `[NLEN (2 bytes)] [NDEF Message] 90 00`
