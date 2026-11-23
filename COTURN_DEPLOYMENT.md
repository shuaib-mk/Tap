# TAP v1.4.0 — COTURN Server Deployment & NAT Traversal Guide

This guide provides instructions for deploying and configuring a production-ready **COTURN** (STUN/TURN) relay server for **TAP Anywhere File Transfer**.

---

## 1. Architecture: Direct P2P vs TURN Relay

TAP v1.4.0 uses **WebRTC DataChannels** to transfer files between devices across different networks (e.g. Wi-Fi to Mobile Data, different Wi-Fi networks, or different 4G/5G carriers).

```
   +-----------------------+                    +-----------------------+
   |   Sender Device       |                    |   Receiver Device     |
   | (e.g. 5G Mobile Data) |                    |  (e.g. Home Wi-Fi)    |
   +-----------------------+                    +-----------------------+
               \                                            /
                \--- 1. STUN NAT Discovery (Reflexive) ----/
                 \                                        /
                  \                                      /
            [Direct P2P Candidate Pairs Possible?]
                   /                             \
             YES  /                               \  NO (Symmetric NAT / CGNAT)
                 v                                 v
   +---------------------------+       +------------------------------------+
   | DIRECT P2P DATACHANNEL    |       | COTURN RELAY (TURNS)               |
   | Zero Intermediate Servers |       | Direct Encrypted UDP/TCP Relay     |
   | Maximum Wireless Speed    |       | Strict End-to-End Encryption       |
   +---------------------------+       +------------------------------------+
```

- **STUN (Session Traversal Utilities for NAT)**: Discovers the public IP and port binding of each device. In ~85% of connections (Full Cone, Restricted Cone, Port Restricted Cone), direct P2P connections are established.
- **TURN (Traversal Using Relays around NAT)**: When one or both devices are behind **Symmetric NATs** or **Carrier-Grade NAT (CGNAT)** (standard on mobile carriers like T-Mobile, Verizon, Jio, Airtel), direct packet exchange is blocked by router firewalls. COTURN acts as an encrypted relay server.
- **Zero Server File Storage**: COTURN relays encrypted RTP/SCTP packets in memory. File contents are encrypted end-to-end with AES-256-GCM. No data or files are ever stored on the TURN server.

---

## 2. Option A: Self-Hosted COTURN on Linux (Ubuntu / Debian)

### Prerequisites
- A cloud VPS (Ubuntu 22.04 LTS or Debian 12) with a public IPv4 address (e.g., DigitalOcean, Hetzner, AWS EC2, Linode).
- A domain or subdomain pointing to your VPS (e.g., `turn.tap.dev`).
- Let's Encrypt SSL certificate (for TURNS over TLS/port 5349).

### Step 1: Install COTURN
```bash
sudo apt update
sudo apt install coturn certbot -y
```

### Step 2: Enable COTURN Service
Edit `/etc/default/coturn`:
```bash
sudo nano /etc/default/coturn
```
Uncomment or set:
```ini
TURNSERVER_ENABLED=1
```

### Step 3: Obtain Let's Encrypt TLS Certificate
```bash
sudo certbot certonly --standalone -d turn.tap.dev
```

### Step 4: Configure `/etc/turnserver.conf`
Generate a strong 32-character random shared secret:
```bash
openssl rand -hex 16
# Example output: a7f8e3d21b4c90e5f6123456789abcde
```

Replace `/etc/turnserver.conf` with the following production configuration:
```ini
# /etc/turnserver.conf — TAP Production Configuration

# Listening Ports
listening-port=3478
tls-listening-port=5349
listening-ip=0.0.0.0

# External Public IP (Replace with your server's public IPv4)
external-ip=YOUR_SERVER_PUBLIC_IP

# Realm and Fingerprint
realm=turn.tap.dev
fingerprint
lt-cred-mech

# Ephemeral Shared Secret (TURN REST API)
use-auth-secret
static-auth-secret=YOUR_32_CHAR_SECRET_HERE

# Dynamic Relay Port Range
min-port=49152
max-port=65535

# TLS Certificates (Let's Encrypt)
cert=/etc/letsencrypt/live/turn.tap.dev/fullchain.pem
pkey=/etc/letsencrypt/live/turn.tap.dev/privkey.pem

# Security and Anti-Abuse
no-cli
no-tcp-relay
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=192.168.0.0-192.168.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=127.0.0.0-127.255.255.255

# Logging
log-file=/var/log/turnserver/turnserver.log
verbose
```

### Step 5: Configure Permissions and Start Service
Ensure `turnserver` user has read access to Let's Encrypt certificates:
```bash
sudo chown -R turnserver:turnserver /var/log/turnserver
sudo systemctl restart turnserver
sudo systemctl enable turnserver
```

---

## 3. Firewall Configuration

Configure your cloud firewall (UFW, AWS Security Groups, or Hetzner Firewall) to allow:

| Port | Protocol | Purpose |
| :--- | :--- | :--- |
| **3478** | UDP & TCP | Standard STUN / TURN |
| **5349** | UDP & TCP | Secure TURNS (TLS/DTLS) |
| **49152–65535** | UDP | Media / DataChannel Relay Allocations |

Using UFW on Ubuntu:
```bash
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 5349/tcp
sudo ufw allow 5349/udp
sudo ufw allow 49152:65535/udp
sudo ufw reload
```

---

## 4. Option B: Docker Compose Deployment

If you prefer deploying with Docker, create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  coturn:
    image: coturn/coturn:latest
    container_name: tap-coturn
    restart: always
    network_mode: "host"
    volumes:
      - ./turnserver.conf:/etc/turnserver.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
    command: ["-c", "/etc/turnserver.conf"]
```

Start the container:
```bash
docker compose up -d
```

---

## 5. Ephemeral Token Generation & App Configuration

TAP enforces strict **zero hardcoded secrets** in the APK.

### Ephemeral REST Credential Mechanism
When configured with a shared secret, COTURN credentials are dynamically generated using the standard **TURN REST API** format:

1. **Username**: `<expiry_timestamp>:<user_identity>`
   - `expiry_timestamp`: Unix timestamp (e.g., `System.currentTimeMillis() / 1000 + 86400` for 24-hour expiration).
   - `user_identity`: Arbitrary device identifier (e.g., `tap_user`).
2. **Password**: `Base64(HMAC-SHA1(secret, username))`
3. **Automatic Refresh**: Credentials expire automatically and cannot be reused beyond their TTL.

### Configuring COTURN in TAP Settings
1. Open TAP.
2. Tap the **Settings** tab (gear icon in the bottom navigation bar).
3. Scroll to **Custom TURN Server**.
4. Enable the toggle and input:
   - **TURN Host**: `turn.tap.dev` (or your IP/domain)
   - **TURN Port**: `3478` (or `5349` for TLS)
   - **Shared Secret / Password**: Your shared secret.
5. Tap **Save**. Settings are stored securely in Android `EncryptedSharedPreferences`.

---

## 6. Option C: Managed TURN Cloud Services

If you do not want to host your own COTURN server, you can use any standard TURN provider:

### 1. Metered.ca (Free Tier Available)
- **Free Quota**: 50 GB relay bandwidth per month.
- **Setup**: Sign up at [metered.ca](https://www.metered.ca), copy the TURN server host and credentials into TAP Settings.

### 2. Twilio Network Traversal
- High global reliability with data centers worldwide.
- Enter Twilio's STUN/TURN hostnames and auth credentials into TAP Settings.

### 3. Xirsys
- WebRTC specialized infrastructure.
- Provides standard TURN/TURNS endpoints compatible with TAP.

---

## 7. Verification & Health Check

### 1. WebRTC Trickle ICE Test
Open Chrome and visit the WebRTC sample:
`https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/`

1. Add your TURN server:
   - STUN or TURN URI: `turn:turn.tap.dev:3478?transport=udp`
   - TURN username: `<your_username>`
   - TURN password: `<your_password>`
2. Click **Gather candidates**.
3. Verify that candidate type `relay` is returned with component `rtp` and priority > 0.

### 2. Inspecting Active Transport in TAP
During any Anywhere transfer, TAP displays the active connection mode on the transfer screen:
- **`DIRECT P2P`**: Devices communicating directly over UDP without relay.
- **`TURN RELAY`**: Traffic actively flowing through COTURN relay (with full end-to-end AES-256-GCM encryption).
- **`OFFLINE DIRECT`**: Autonomous Wi-Fi Direct connection when offline.
