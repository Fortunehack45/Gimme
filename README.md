# 🚀 AirShare — Privacy-First Peer-to-Peer File Transfer

<p align="center">
  <img src="app/src/main/res/drawable/ic_airshare_logo.xml" width="100" height="100" alt="AirShare Logo" />
</p>

<p align="center">
  <strong>Zero Ads. Zero Trackers. Direct Peer Transfer with Multi-Device Broadcast &amp; End-to-End Encryption.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Architecture-MVVM-6366F1" alt="MVVM" />
  <img src="https://img.shields.io/badge/Security-AES--256--GCM-FF3B30" alt="AES-256" />
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="License" />
</p>

---

## 🌟 Key Features

- **🔴 Apple Minimalist UI/UX with Floating Navigation Bar**:
  - Elevated floating pill bottom navigation bar with fluid screen transitions.
  - Curated Apple-style dark mode (OLED Pitch Black & Apple Red) and light mode (Crisp White & Apple Red).
  - Customizable theme color engine with 7 Apple presets (Crimson Red, Electric Blue, Purple, Green, Orange, Gold, Monochrome).
- **📡 One-to-Many Multi-Device Broadcast**:
  - Broadcast files to unlimited simultaneous receivers over local Wi-Fi in a single session without sequential looping.
  - Per-device chunk completion tracking so one slow receiver does not block other participants.
- **🔒 End-to-End Local Encryption (AES-256-GCM)**:
  - Ephemeral Elliptic Curve Diffie-Hellman (ECDH) key exchange per transfer session.
  - All chunks transferred over local Wi-Fi are encrypted with AES-256-GCM. Traffic cannot be sniffed even on shared open Wi-Fi.
- **💻 Zero-Install Web Connect (PC / Mac / Linux / iOS)**:
  - Phone runs a lightweight embedded HTTP server serving a modern responsive Single Page Application.
  - Receivers and computers simply scan a QR code or open `http://192.168.x.x:8080` in any browser to download and drag-and-drop upload files.
- **📱 Phone Clone Migration**:
  - One-tap migration between Old Phone and New Phone with categories for Photos, Videos, Music, Apps (APKs), and Documents.
- **⚡ Resumable Chunked Transfers**:
  - 512KB chunk-based transport with `RandomAccessFile` direct writes and automatic resumption from last completed byte offset.
- **🗄️ Local Room Database History**:
  - Date-grouped transfer history, FileProvider opening, and one-tap re-sending with zero cloud tracking.

---

## 🛠️ Architecture & Tech Stack

```
+------------------------------------------------------------------------------------+
|                                    AirShare UI                                     |
|  [Onboarding]   [Home / Nav]   [Send / Picker]   [Receive]   [Group]   [Web]   [Clone]  |
+------------------------------------------------------------------------------------+
                                      | (StateFlow / ViewModel)
+------------------------------------------------------------------------------------+
|                                 ViewModel Layer                                    |
|  MainViewModel, SendViewModel, ReceiveViewModel, GroupViewModel, CloneViewModel   |
+------------------------------------------------------------------------------------+
                                      |
+------------------------------------------------------------------------------------+
|                                Repository Layer                                    |
|  TransferRepository, FileRepository, SettingsRepository, DiscoveryRepository       |
+------------------------------------------------------------------------------------+
           |                                  |                          |
+--------------------+              +-------------------+      +---------------------+
|    Local Room DB   |              |  Transfer Engine  |      |   Security Layer    |
| - TransferRecord   |              | - Embedded Server |      | - ECDH Key Exchange |
| - TransferDao      |              | - Chunk Client    |      | - AES-256-GCM Enc   |
| - AppDatabase      |              | - Resume Manager  |      | - SHA-256 Checksums |
+--------------------+              +-------------------+      +---------------------+
                                              |
                   +----------------------------------------------------+
                   |                Transport Layer                     |
                   |  - WiFi Direct (WifiP2pManager)                   |
                   |  - Local Hotspot / AP Fallback                     |
                   |  - NSD / UDP Beacon Discovery                      |
                   +----------------------------------------------------+
```

- **Language**: Kotlin 1.9+
- **Architecture**: MVVM with Coroutines & StateFlow
- **Database**: AndroidX Room SQLite
- **Security**: Ephemeral ECDH (secp256r1) + HKDF-SHA256 + AES-256-GCM + SHA-256
- **QR Code Engine**: Google ZXing & AndroidX CameraX
- **Local Server**: Embedded NanoHTTPD + Custom Streaming Chunk Pipeline

---

## 🚀 Building & Testing Locally

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 34)

### Build Commands
```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤖 CI / CD Workflows

- **`.github/workflows/ci.yml`**: Automatically runs unit tests and builds the debug APK artifact on every push and pull request.
- **`.github/workflows/release.yml`**: Automatically builds and publishes a GitHub Release with attached APK binaries when a version tag (`v*.*.*`) is pushed.

---

## 📄 License
MIT License. Built for privacy, speed, and peer-to-peer sharing.
