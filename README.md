# 🏔️ Hiking Watch App — Wear OS

A full-featured Garmin-style hiking app for **Samsung Galaxy Watch 4** (Wear OS 3+), built with Kotlin and Jetpack Compose.

---

## ✅ Features

| Feature | Status |
|---|---|
| Offline OSMDroid map with GPS trail | ✅ |
| Real-time location tracking + polyline | ✅ |
| Compass bearing overlay | ✅ |
| Start / Pause / Resume / Stop | ✅ |
| HUD: time, altitude, speed | ✅ |
| Heart rate (Wear Health Services) | ✅ |
| Heart rate zones (green/yellow/red) | ✅ |
| Total distance (Haversine formula) | ✅ |
| Elevation gain/loss (Kalman filter) | ✅ |
| Current + average speed | ✅ |
| Battery indicator | ✅ |
| GPS accuracy indicator | ✅ |
| Calorie estimate (MET formula) | ✅ |
| Session summary screen | ✅ |
| GPX file export | ✅ |
| Foreground service (survives screen off) | ✅ |
| Room database persistence | ✅ |
| DataStore user settings | ✅ |
| Ambient mode (low-power screen) | ✅ |
| Permission rationale dialogs | ✅ |
| Metric / Imperial toggle | ✅ |
| WorkManager phone sync (graceful) | ✅ |
| MVVM + Clean Architecture | ✅ |
| Hilt dependency injection | ✅ |

---

## 🗂️ Project Structure

```
app/src/main/java/com/arcowebdesign/hikingwatch/
├── data/
│   ├── db/
│   │   └── HikingDatabase.kt     ← Room DB, DAOs, Entities
│   ├── repository/
│   │   ├── HikingRepository.kt   ← Sessions, waypoints, GPX export
│   │   └── SettingsRepository.kt ← DataStore preferences
│   └── service/
│       ├── HikingTrackingService.kt ← Foreground service (GPS + HR)
│       └── SessionSyncWorker.kt     ← WorkManager sync worker
├── domain/
│   └── model/
│       └── Models.kt             ← Domain models + enums
├── di/
│   └── AppModule.kt              ← Hilt DI module
└── presentation/
    ├── MainActivity.kt           ← Entry point, HorizontalPager, Ambient
    ├── HikingViewModel.kt        ← Main ViewModel
    ├── PermissionsScreen.kt      ← Permission rationale UI
    ├── map/
    │   └── MapScreen.kt          ← Screen 1: OSM map + HUD + controls
    ├── stats/
    │   └── StatsScreen.kt        ← Screen 2: Stats dashboard
    ├── summary/
    │   └── SummaryScreen.kt      ← Screen 3: Session summary + GPX save
    └── theme/
        └── Theme.kt              ← Wear OS color theme
```

---

## ☁️ Build APK via GitHub (No Computer Needed)

You can build the APK entirely online using GitHub Actions — **no Android Studio required**.

### Step 1 — Create a free GitHub account
Go to **github.com** and sign up (free).

### Step 2 — Create a new repository
- Click the **+** icon → **New repository**
- Name it `HikingWatchApp`
- Set it to **Public** or **Private** (both work)
- Click **Create repository**

### Step 3 — Upload the project files
- Click **uploading an existing file** on the repo page
- Extract the ZIP I gave you, then drag and drop **all files and folders** into the GitHub upload area
- Scroll down and click **Commit changes**

### Step 4 — Watch it build automatically
- Click the **Actions** tab at the top of your repo
- You'll see a workflow called **"Build Hiking Watch APK"** running automatically
- Wait about 5–10 minutes for it to finish (green checkmark = success)

### Step 5 — Download your APK
- Click on the completed workflow run
- Scroll down to the **Artifacts** section
- Click **HikingWatch-Debug-APK** to download it
- You'll get a ZIP containing the `.apk` file

### Step 6 — Install on your Galaxy Watch 4
Use **Wear Installer 2** (free on Google Play) on your phone to sideload the APK onto your watch.

---

## 🚀 Setup & Build Instructions (Alternative: Android Studio)

### 1. Prerequisites
- **Android Studio Hedgehog** or newer (download from developer.android.com)
- **JDK 17** (bundled with Android Studio)
- **Samsung Galaxy Watch 4** with Developer Mode enabled OR Wear OS emulator

### 2. Open Project
```
File → Open → Select the HikingWatchApp folder
```

### 3. Sync Gradle
Click **"Sync Now"** when prompted, or:
```
File → Sync Project with Gradle Files
```

### 4. Pre-download Offline Map Tiles (Optional but recommended)
OSMDroid will cache tiles automatically when the watch has Wi-Fi. For full offline use:
- Connect the watch to Wi-Fi
- Run the app and navigate to your hiking region
- Tiles are cached in `/data/data/com.arcowebdesign.hikingwatch/files/osm_tiles/`

### 5. Connect Your Galaxy Watch 4
**Enable ADB over Wi-Fi:**
1. On watch: Settings → Developer Options → ADB Debugging → ON
2. Settings → Developer Options → Debug over Wi-Fi → ON
3. Note the IP shown (e.g. `192.168.1.x:5555`)

**Connect via ADB:**
```bash
adb connect 192.168.1.x:5555
```

**Or use Wear Installer 2** (Play Store app on your phone) to sideload the APK.

### 6. Build & Install
```
Build → Generate Signed Bundle/APK → APK → Debug
```
Or run directly from Android Studio with the watch connected:
```
Run → Run 'app'  (select your watch as the target device)
```

### 7. Permissions
On first launch, grant all requested permissions on the watch:
- **Location** (required for GPS tracking)
- **Body Sensors** (required for heart rate)
- **Activity Recognition** (required for health metrics)
- **Notifications** (for the persistent tracking notification)

---

## ⚙️ User Settings
Swipe up on the Stats screen to access settings, or modify defaults in `SettingsRepository.kt`:
- **Distance Unit**: Metric (km) or Imperial (miles)
- **GPS Sampling**: High Accuracy (2s) or Battery Saver (10s)
- **User Age / Weight**: For accurate calorie calculation

---

## 📁 GPX Export
After stopping a hike, tap **"Save GPX"** on the summary screen. Files are saved to:
```
/data/data/com.arcowebdesign.hikingwatch/files/gpx_exports/
```
Retrieve them via ADB:
```bash
adb pull /data/data/com.arcowebdesign.hikingwatch/files/gpx_exports/ ./hikes/
```

---

## 🔧 Customization

### Change App Package Name
In `app/build.gradle.kts`:
```kotlin
applicationId = "your.package.name"
```

### Change Map Style
OSMDroid supports multiple tile sources. In `MapScreen.kt`:
```kotlin
setTileSource(TileSourceFactory.MAPNIK)        // Standard
setTileSource(TileSourceFactory.USGS_TOPO)     // Topographic
setTileSource(TileSourceFactory.OpenTopo)      // OpenTopoMap
```

### Adjust Heart Rate Zones
In `HikingTrackingService.kt`, modify `computeHrZone()` thresholds.

---

## 📦 Key Dependencies
| Library | Purpose |
|---|---|
| OSMDroid 6.1.18 | Offline vector maps |
| Wear Health Services 1.1.0 | Heart rate sensor |
| Room 2.6.1 | Local SQLite persistence |
| Hilt 2.51.1 | Dependency injection |
| DataStore 1.1.1 | User preferences |
| Play Services Location 21.3 | Fused GPS location |
| Wear Compose 1.3.1 | Wear OS UI components |
| WorkManager 2.9 | Background sync |
| Accompanist Permissions 0.34 | Runtime permissions |
