# ATAK CIV 5.x Emulator Setup — Agent Context

## What Took Forever and Why

ATAK 5.x map renderer requires **GLES 3.0 or later** to create its GL surface.
Android Studio AVD on Windows defaults to a renderer that caps at GLES 2.0, causing an immediate fatal crash.
VirtualBox Android-x86 has the same problem — its GLES translation layer is hardcoded at 2.0 regardless of graphics controller.
The fix is to run the AVD with `-gpu swangle` which gives GLES 3.0 via SwiftShader + ANGLE.

---

## Working Configuration

### Environment
- OS: Windows 11 Pro
- Host GPU: NVIDIA RTX 4070 Ti SUPER
- ADB: `C:\Users\rober\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- Emulator: `C:\Users\rober\AppData\Local\Android\Sdk\emulator\emulator.exe`
- ATAK SDK: `D:\Development\ATAK\ATAK-CIV-5.6.0-SDK\`
- ATAK APK: `D:\Development\ATAK\ATAK-CIV-5.6.0-SDK\atak.apk`

### AVD
- Name: `Pixel_4_fr`
- API: 29 (Android 10), x86
- System image: `google_apis_playstore\x86`
- RAM: 4096 MB

### AVD config files
`~/.android/avd/Pixel_4_fr.avd/AVD.conf` — set by extended controls:
```ini
[set]
glesApiLevelPreference=1
glesBackendPreference=6
guestGlesDriverPreference=0
```

`~/.android/avd/Pixel_4_fr.avd/config.ini`:
```ini
hw.gpu.enabled=yes
hw.gpu.mode=host
```

### Launch command (the only one that works)
```powershell
& "C:\Users\rober\AppData\Local\Android\Sdk\emulator\emulator.exe" `
    -avd Pixel_4_fr -gpu swangle -no-snapshot-load
```

`-gpu swangle` = ANGLE on SwiftShader (software renderer). Slow but exposes correct EGL configs.
The emulator window takes **2-4 minutes** to appear. Do not close it thinking it crashed.

### Verify GLES before launching ATAK
```powershell
adb -s emulator-5554 shell getprop ro.opengles.version
# Must be 196608 (GLES 3.0) or higher. 131072 = GLES 2.0 = ATAK will crash.

adb -s emulator-5554 shell dumpsys SurfaceFlinger | findstr "GLES"
# Should say: OpenGL ES 3.0 SwiftShader
```

---

## Installing and Launching ATAK

### First install
```powershell
adb -s emulator-5554 install "D:\Development\ATAK\ATAK-CIV-5.6.0-SDK\atak.apk"
```

### First clean launch (required after any crash to clear ACRA state)
```powershell
adb -s emulator-5554 shell "pm clear com.atakmap.app.civ"
adb -s emulator-5554 shell monkey -p com.atakmap.app.civ -c android.intent.category.LAUNCHER 1
```

### Subsequent launches
```powershell
adb -s emulator-5554 shell monkey -p com.atakmap.app.civ -c android.intent.category.LAUNCHER 1
```

---

## Log Filtering

### Watch for these — actual failures
```powershell
adb -s emulator-5554 logcat -v time | Select-String -Pattern `
    "eglChooseConfig|No config chosen|FATAL EXCEPTION|SIGSEGV|tombstone|AndroidRuntime" `
    -CaseSensitive:$false
```

| Message | Meaning |
|---|---|
| `eglChooseConfig failed` | GLES 2.0 — wrong renderer, swangle not used |
| `No config chosen` | GLES 3.x but wrong EGL configs — wrong backend preference |
| `FATAL EXCEPTION` + `GLThread` | Map surface crash |
| `ANDROID_EMU_gles_max_version_2` | Renderer is capped at GLES 2.0 |
| `ANDROID_EMU_gles_max_version_3_1` | GLES 3.1 available (but may still fail on EGL config) |

### Ignore completely
- `failed migration of /storage/emulated/0/com.atakmap.map`
- `could not copy support/docs/ATAK_User_Guide.pdf`
- `GpsStatus APIs not supported`
- `PluginLayoutInflator`
- `greylist`
- `TextToSpeech`
- Google Play / Maps / Messages crashes
- `WebViewLoader: System.exit called, status: 0`

### ACRA behavior
ATAK uses ACRA for crash reporting. After a crash, ACRA saves a report and **kills ATAK on the next launch** to send it. This makes it look like ATAK crashed twice in a row. Always run `pm clear com.atakmap.app.civ` after any crash before testing again.

---

## What Was Tried and Failed

### Android Studio AVD with `-gpu host` (default)
- Exposes only `ANDROID_EMU_gles_max_version_2`
- ATAK crash: `eglChooseConfig failed` at `GLSurfaceView.java:883`

### AVD extended controls renderer change
- `glesBackendPreference=6` → GLES 3.1 available, but map GLThread crashes with `No config chosen` (`GLSurfaceView.java:900`)
- The widget surface gets GLES 3.1 fine; the map surface needs specific EGL configs (depth/stencil/MSAA combo) not exposed by this backend
- `glesBackendPreference=2` → back to GLES 2.0, worse

### `-gpu angle_vulkan` command line
- Not a valid option in emulator version 36.6.11.0
- Falls back to auto/host

### ANGLE D3D11 (`glesBackendPreference=4`, no `-gpu swangle`)
- Set via `AVD.conf glesBackendPreference=4`, launched without `-gpu swangle`
- Same crash: `No config chosen` at `GLSurfaceView.java:900`
- GLES 3.x is available but the required depth/stencil/MSAA EGL config combination is not exposed
- `-gpu swangle` remains the only confirmed working GPU flag

### VirtualBox Android-x86 9.0-r2
- Installed and working (VMSVGA + nomodeset for display, USB Tablet for mouse)
- ADB connects via NAT port-forward: `adb connect 127.0.0.1:5555`
- GLES is always `ANDROID_EMU_gles_max_version_2` regardless of VBoxSVGA vs VMSVGA
- VirtualBox's GLES translation layer is hardcoded to GLES 2.0 — not fixable without VMware or a physical device
- VM exists at `AndroidX86-ATAK` in VirtualBox but is not useful for ATAK

---

## Next Steps (Plugin Development)

ATAK base APK is running. Only now should plugin work begin.

### Install a plugin
```powershell
adb -s emulator-5554 install "D:\Development\ATAK\ATAK-CIV-5.6.0-SDK\samples\helloworld\app\build\outputs\apk\civ\debug\ATAK-Plugin-helloworld-debug.apk"
```

### Reinstall plugin without reinstalling ATAK
```powershell
adb -s emulator-5554 install -r "path\to\plugin.apk"
# Then restart ATAK
adb -s emulator-5554 shell am force-stop com.atakmap.app.civ
adb -s emulator-5554 shell monkey -p com.atakmap.app.civ -c android.intent.category.LAUNCHER 1
```

### If emulator is too slow for plugin work
Consider VMware Workstation Player (free) with the same Android-x86 9.0-r2 ISO.
VMware's SVGA3D exposes GLES 3.x natively and is faster than SwiftShader.
Add `mks.gl.allowBlacklistedDrivers = "TRUE"` to the `.vmx` file before first boot.

### AMD CPU — check SVM in BIOS
Host CPU is AMD. SVM (AMD's hardware virtualization) must be enabled in BIOS for the
emulator to use WHPX acceleration. Without it the emulator runs in slow software emulation.
Check: BIOS → CPU configuration → SVM Mode → Enabled.
After enabling, Android Studio should detect WHPX and use it automatically.

---

## Quick Start Checklist

1. `& "C:\Users\rober\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Pixel_4_fr -gpu swangle -no-snapshot-load`
2. Wait 2-4 minutes for emulator to boot
3. `adb -s emulator-5554 shell getprop ro.opengles.version` → must be `196608`
4. `adb -s emulator-5554 shell monkey -p com.atakmap.app.civ -c android.intent.category.LAUNCHER 1`
5. ATAK loads, map renders, stays open — done

---

## Plugin Architecture

### Class Hierarchy

Every ATAK plugin has exactly three classes that wire it together:

```
AbstractPlugin  (Lifecycle — your entry point, loaded by ATAK)
├── AbstractPluginTool  (Tool — the toolbar button + action intent)
└── DropDownMapComponent  (MapComponent — owns everything else)
    └── DropDownReceiver  (the UI panel that slides in)
```

**AbstractPlugin (Lifecycle)**
```java
public class MyPluginLifecycle extends AbstractPlugin {
    public MyPluginLifecycle(IServiceController svc) {
        super(svc,
              new MyTool(svc.getService(PluginContextProvider.class).getPluginContext()),
              new MyMapComponent());
    }
}
```
Declared in `AndroidManifest.xml` as the plugin service. ATAK discovers it by scanning for `<meta-data android:name="com.atakmap.app.civ">` in installed APKs.

**AbstractPluginTool**
```java
public class MyTool extends AbstractPluginTool {
    public MyTool(Context ctx) {
        super(ctx,
              "Tool Title",          // shown in nav
              "Tool Description",
              drawable,
              "com.example.myplugin.SHOW_MY_PLUGIN"); // the action string
    }
}
```
Adds a button to ATAK's nav bar. When tapped, fires the action intent which your `DropDownReceiver` catches.

**DropDownMapComponent**
The workhorse. `onCreate(context, intent, mapView)` is called when ATAK loads your plugin:
- Register `DropDownReceiver` instances with `registerDropDownReceiver(receiver, intentFilter)`
- Add map overlays: `view.getMapOverlayManager().addOverlay(...)`
- Register CoT detail handlers: `CotDetailManager.getInstance().registerHandler(...)`
- Register preferences: `ToolsPreferenceFragment.register(...)`
- Clean up everything in `onDestroyImpl(context, view)` — always mirror every register with an unregister

**DropDownReceiver**
The slide-in UI panel. Show it from `onReceive`:
```java
// Full-width panel:
showDropDown(myView, FULL_WIDTH, WRAP_CONTENT, false);
// Half-width:
showDropDown(myView, HALF_WIDTH, FULL_HEIGHT, false);
```
Close it with `closeDropDown()`. Listen for close events via `addOnStateChangedListener`.

### Communication Model

ATAK uses its own internal broadcast bus — **never** use `context.sendBroadcast()` inside a plugin:
```java
// Send
Intent intent = new Intent("com.example.myplugin.DO_THING");
intent.putExtra("uid", someUid);
AtakBroadcast.getInstance().sendBroadcast(intent);

// Register
AtakBroadcast.getInstance().registerReceiver(myReceiver,
    new AtakBroadcast.DocumentedIntentFilter("com.example.myplugin.DO_THING"));
```
System intents (e.g. `com.atakmap.app.QUITAPP`) also go through `AtakBroadcast`.

### Key APIs

| Class | Where to get it | What it does |
|---|---|---|
| `MapView` | `MapView.getMapView()` | Central map singleton; access everything from here |
| `MapGroup` | `mapView.getRootGroup()` | Tree of map items; use `.findItem()`, `.deepForEachItem()` |
| `MapItem` / `Marker` | `new Marker(uid, type)` | Things on the map |
| `CotEvent` | `new CotEvent()` | Cursor on Target event (XML wrapper) |
| `CotDetail` | `new CotDetail("tagname")` | Child element in a CoT event |
| `CotDetailManager` | `.getInstance()` | Register handlers for custom CoT detail tags |
| `CotMapComponent` | `.getInstance()` | Send CoT; add custom details to your own SA report |
| `AtakPreferences` | `AtakPreferences.getInstance(context)` | Shared prefs with listener support |
| `AtakAuthenticationDatabase` | static methods | Encrypted credential storage |
| `MapEventDispatcher` | `mapView.getMapEventDispatcher()` | Listen for `ITEM_ADDED`, `ITEM_REMOVED`, touch events |
| `NavButtonManager` | `.getInstance()` | Update badge counts on your toolbar button |
| `DropDownManager` | `.getInstance()` | Programmatically manage drop down state |
| `LocationManager` | `.getInstance()` | Register a custom location provider |

### CoT (Cursor on Target) Basics

CoT is the XML messaging format ATAK uses for situational awareness. Type strings follow the pattern:
`a-f-G-U-C` = `affiliation - battle_dimension - function - ...`
- `a-f-G` = friendly ground
- `a-h-A` = hostile air
- `b-r-f-h-c` = broadcast/reply/... (non-SA types)

Send a CoT event to all connected TAK clients:
```java
CotEvent event = new CotEvent();
event.setUID("my-unique-id");
event.setType("a-f-G-U-C");
event.setTime(new CoordinatedTime());
event.setStart(new CoordinatedTime());
event.setStale(new CoordinatedTime().addSeconds(30));
event.setHow("h-e");  // how-entry-manually
CotPoint point = new CotPoint(lat, lon, alt, ce, le);
event.setPoint(point);
CotDetail detail = new CotDetail();
detail.addChild(new CotDetail("contact")
    .setAttribute("callsign", "MYCALLSIGN"));
event.setDetail(detail);
CotMapComponent.getInstance().sendCoT(event, CoTTransport.TRANSPORT_BROADCAST);
```

Inject a custom XML child into your own SA (PPLI) broadcast:
```java
CotDetail cd = new CotDetail("mydata");
cd.setAttribute("key", "value");
CotMapComponent.getInstance().addAdditionalDetail("mydata", cd);
```

---

## Build System

### SDK dependency

Plugins compile against `atak-javadoc.jar` (stubs only — classes live in ATAK at runtime):
```groovy
implementation files('D:\\Development\\ATAK\\ATAK-CIV-5.6.0-SDK\\atak-javadoc.jar')
```
The `atak-gradle-takdev.jar` Gradle plugin handles packaging, signing, and flavor wiring.

### Flavors

Three flavors: `civ`, `mil`, `gov`. All fall back to `civ` if flavor-specific deps are missing.
```groovy
flavorDimensions "application"
productFlavors {
    civ { dimension "application" }
    mil { dimension "application" }
    gov { dimension "application"; applicationIdSuffix = ".gov" }
}
```
`manifestPlaceholders = [atakApiVersion: "com.atakmap.app@5.6.0.CIV"]` — must match the running ATAK version or the plugin won't load.

### Build variants

| Variant | ProGuard | Debuggable | matchingFallbacks |
|---|---|---|---|
| `debug` | no | yes | `sdk` |
| `release` | yes (`proguard-gradle.txt`) | no | `odk` |

### Signing

Both debug and release use the keystore at `${buildDir}/android_keystore` with:
- alias: `wintec_mapping`  
- store/key password: `tnttnt`

The keystore is generated by the build system. Do not commit it.

### Min/Target SDK

- `minSdkVersion 21`
- `targetSdk 30`
- `compileSdk 33`
- Java 17

### Build APK

```powershell
cd D:\Development\ATAK\ATAK-CIV-5.6.0-SDK\samples\helloworld
.\gradlew assembleCivDebug
# Output: app\build\outputs\apk\civ\debug\ATAK-Plugin-helloworld-1.0-...-5.6.0.apk
```

---

## Sample Plugins (in `samples/`)

| Folder | What it shows |
|---|---|
| `helloworld` | Kitchen sink — DropDownReceiver, CoT injection, AIDL service, overlays, preferences, credentials |
| `plugintemplate` | Minimal starting point (use this as the base) |
| `PluginTemplateLegacy` | Legacy API; avoid unless targeting older ATAK |
| `cotinjector` | Constructing and sending CoT events |
| `hello3d` | 3D map items / GL rendering |
| `customtiles` | Custom map tile layers |
| `commout-simplesocket` | Raw socket comms from a plugin |
| `sensortester` | Registering a sensor data provider |
| `windprovider` / `windconsumer` | Inter-plugin data sharing pattern |
| `platformsim` | Simulating a moving platform (SA tracks) |
| `videooverlay` | Embedding video in the map |
| `plugintemplate-compose` | Jetpack Compose UI in a plugin |

---

## Plugin Development Workflow

### First plugin build and install
```powershell
# Build
cd D:\Development\ATAK\ATAK-CIV-5.6.0-SDK\samples\plugintemplate
.\gradlew assembleCivDebug

# Install (ATAK must already be running)
adb -s emulator-5554 install app\build\outputs\apk\civ\debug\*.apk

# Restart ATAK to load the new plugin
adb -s emulator-5554 shell am force-stop com.atakmap.app.civ
adb -s emulator-5554 shell monkey -p com.atakmap.app.civ -c android.intent.category.LAUNCHER 1
```

### Iterate fast (reinstall without reinstalling ATAK)
```powershell
.\gradlew assembleCivDebug
adb -s emulator-5554 install -r app\build\outputs\apk\civ\debug\*.apk
adb -s emulator-5554 shell am force-stop com.atakmap.app.civ
adb -s emulator-5554 shell monkey -p com.atakmap.app.civ -c android.intent.category.LAUNCHER 1
```

### Watch plugin logs
```powershell
adb -s emulator-5554 logcat -v time -s MyPluginTag:D AtakPluginRegistry:D
```
Replace `MyPluginTag` with the `TAG` constant from your `MapComponent`.

### Plugin not appearing?
1. Check `manifestPlaceholders atakApiVersion` matches `com.atakmap.app@5.6.0.CIV` exactly
2. Check the APK flavor is `civ` (not `mil`/`gov`)
3. Logcat filter: `AtakPluginRegistry` — ATAK logs why it rejected a plugin
4. Try `adb shell pm clear com.atakmap.app.civ` then reinstall both ATAK and plugin
