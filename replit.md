# Dr.Clicker — Android Auto-Accept App

**Package:** `com.dr.clicker.atikapp`  
**Version:** 1.0.0  
**100% Free · No Firebase · No Login · No Ads · Fully Offline**

---

## What it does
Dr.Clicker automatically taps the Accept button on **Rapido Captain** and **Ola Driver** the instant a ride request appears. It uses Android's Accessibility Service to read the screen and dispatch a tap gesture — no screenshots, no image recognition, no internet.

---

## How to build

1. Install [Flutter SDK](https://docs.flutter.dev/get-started/install) (stable channel)
2. Open Android Studio → **File → Open** → select this folder
3. Run in terminal:
   ```bash
   flutter pub get
   ```
4. Connect a real Android device (API 23 / Android 6.0 or newer)
5. Press **Run ▶** or `flutter run`

---

## First-time setup on your phone

| Step | Action |
|------|--------|
| 1 | Open Dr.Clicker — tap the **yellow warning banner** |
| 2 | Find **Dr.Clicker** in Accessibility Settings → Enable it |
| 3 | Go back to the app → set your price/distance filters |
| 4 | Tap **Save Settings** |
| 5 | Flip **Engine Activation** ON |
| 6 | Switch to Rapido or Ola — done ✓ |

---

## Project structure

```
lib/
  main.dart           ← Flutter entry point & theme
  home_screen.dart    ← Full UI: engine switch, filters, stats

android/app/src/main/
  kotlin/com/dr/clicker/atikapp/
    MainActivity.kt       ← Flutter + MethodChannel bridge
    AutoClickService.kt   ← Accessibility engine: scan → tap → beep
  res/
    xml/accessibility_service_config.xml
    drawable/ic_launcher_foreground.xml  ← Lightning bolt logo
    mipmap-anydpi-v26/ic_launcher.xml
  AndroidManifest.xml
```

---

## Engine logic

1. Every Accessibility event from Rapido/Ola is received (50 ms throttle)
2. All text nodes in visible windows are scanned for accept keywords in **7 languages** (English, Tamil, Telugu, Kannada, Hindi, Marathi, Bengali)
3. Visible price/distance numbers are extracted and checked against saved filters
4. Matching ride → tap dispatched after **10–100 ms** random human-like delay
5. Dual beep plays, counter increments, latency is recorded

---

## User Preferences
- App name: Dr.Clicker
- Package: com.dr.clicker.atikapp
- No Firebase, no login, no network, 100% free
