# FieldSync AI — Plugin Architecture Guide

> Agent context for implementing the FieldSync AI ATAK plugin.
> For emulator setup, SDK config, and ATAK plugin wiring fundamentals see `D:\Development\ATAK\AGENTS.md`.

---

## What This Plugin Does

FieldSync AI is an ATAK CIV 5.6.0 plugin. It gives field operators a shared observation store that synchronizes peer-to-peer via Ditto (no server, no internet), and lets them query a local AI node for summaries and answers.

Three things happen:

1. Operators create text observations with a category and optional location.
2. Ditto synchronizes those observations across all nearby ATAK devices automatically.
3. Operators ask questions. Queries are published to Ditto. A separate AI node reads them, generates answers, and publishes responses back. The plugin displays those responses.

The plugin never makes decisions. It only captures, syncs, queries, and displays.

---

## Technology Stack

| Layer | Technology |
|---|---|
| ATAK plugin host | ATAK CIV 5.6.0 |
| Language | Java 17 (with Kotlin bridge for Ditto SDK) |
| Build | Gradle + atak-gradle-takdev.jar |
| Peer sync | Ditto Android SDK v5 (`com.ditto:ditto-kotlin-android:5.0.0`) |
| AI transport | Ditto (queries and responses as documents) |
| AI node | External — not part of this repo |
| Min SDK | 21 |
| Target SDK | 30 |
| Compile SDK | 33 |

The AI node is out of scope for this repository. It is a separate process (e.g. Ollama, llama.cpp) that participates in the same Ditto mesh. The plugin treats it as a black box: write a query document, wait for a response document.

The Ditto v5 Android SDK is Kotlin-first. Its store and sync methods are Kotlin suspend functions. Because the rest of the plugin is Java, a small `DittoHelper.kt` bridge class (using `runBlocking`) is required to call them from Java — exactly as done in `D:\Development\quickstart\android-java`.

---

## Repository Structure

```
fieldsync-ai/
├── AGENTS.md                               ← this file
├── app/
│   ├── build.gradle
│   ├── proguard-gradle.txt
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       │   ├── layout/
│       │   │   ├── dropdown_main.xml
│       │   │   ├── dropdown_observation_list.xml
│       │   │   ├── dropdown_observation_form.xml
│       │   │   └── dropdown_ai_query.xml
│       │   ├── drawable/
│       │   │   └── ic_fieldsync.png
│       │   └── values/
│       │       └── strings.xml
│       └── java/com/fieldsync/plugin/
│           ├── plugin/
│           │   ├── FieldSyncLifecycle.java
│           │   ├── FieldSyncTool.java
│           │   └── FieldSyncMapComponent.java
│           ├── model/
│           │   ├── Observation.java
│           │   ├── ObservationCategory.java
│           │   ├── AIQuery.java
│           │   └── AIResponse.java
│           ├── sync/
│           │   ├── DittoHelper.kt          ← Kotlin bridge for Ditto v5 suspend API
│           │   ├── DittoManager.java
│           │   ├── ObservationRepository.java
│           │   └── AIQueryRepository.java
│           └── ui/
│               ├── MainDropDown.java
│               ├── ObservationListDropDown.java
│               ├── ObservationFormDropDown.java
│               └── AIQueryDropDown.java
├── build.gradle
└── settings.gradle
```

---

## AndroidManifest.xml

The manifest declares the plugin to ATAK via a `<meta-data>` tag on a `<service>` element. ATAK scans installed APKs for this pattern.

```xml
<service
    android:name="com.fieldsync.plugin.plugin.FieldSyncLifecycle"
    android:enabled="true"
    android:exported="true"
    android:label="FieldSync AI"
    android:permission="com.atakmap.app.civ.ACCESS">

    <meta-data
        android:name="com.atakmap.app.civ"
        android:value="@string/app_desc" />
</service>
```

Required permissions beyond standard ATAK plugin needs (for Ditto peer discovery). These are version-gated — request them at runtime using the same conditional pattern as `D:\Development\quickstart\android-java\app\src\main\java\com\example\dittotasks\MainActivity.java` `requestPermissions()`:

```xml
<!-- WiFi peer discovery -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- BLE peer discovery (API <= 30) -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />

<!-- BLE peer discovery (API >= 31) -->
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<!-- Location (API <= 32 for BLE) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Nearby WiFi (API >= 33) -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
```

---

## Build Configuration (app/build.gradle)

The project uses Groovy DSL to match the ATAK plugin template. Ditto credentials are loaded from a `.env` file at repo root (never committed) and injected into `BuildConfig`.

```groovy
apply plugin: 'com.android.application'
apply plugin: 'org.jetbrains.kotlin.android'
apply from: 'D:\\Development\\ATAK\\ATAK-CIV-5.6.0-SDK\\atak-gradle-takdev.jar'

android {
    compileSdkVersion 33
    namespace 'com.fieldsync.plugin'

    defaultConfig {
        applicationId 'com.fieldsync.plugin'
        minSdkVersion 21
        targetSdkVersion 30
        versionCode 1
        versionName "1.0"
        manifestPlaceholders = [atakApiVersion: "com.atakmap.app@5.6.0.CIV"]
    }

    flavorDimensions "application"
    productFlavors {
        civ { dimension "application" }
    }

    buildTypes {
        debug { debuggable true }
        release {
            minifyEnabled true
            proguardFiles 'proguard-gradle.txt'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig true
    }

    // Required by Ditto for meaningful stack traces
    packagingOptions {
        jniLibs {
            useLegacyPackaging true
            keepDebugSymbols.add("**/libdittoffi.so")
        }
    }
}

// Load Ditto credentials from .env file
def envProps = new Properties()
def envFile = rootProject.file('../.env')
if (envFile.exists()) {
    envFile.withInputStream { envProps.load(it) }
}

android.applicationVariants.all { variant ->
    variant.buildConfigField "String", "DITTO_APP_ID",
        "\"${envProps.getProperty('DITTO_APP_ID', '')}\""
    variant.buildConfigField "String", "DITTO_PLAYGROUND_TOKEN",
        "\"${envProps.getProperty('DITTO_PLAYGROUND_TOKEN', '')}\""
    variant.buildConfigField "String", "DITTO_AUTH_URL",
        "\"${envProps.getProperty('DITTO_AUTH_URL', '')}\""
}

dependencies {
    implementation fileTree(include: ['*.jar'], dir: 'libs')
    implementation files('D:\\Development\\ATAK\\ATAK-CIV-5.6.0-SDK\\atak-javadoc.jar')

    // Ditto v5 Android SDK (Kotlin artifact, callable from Java via bridge)
    implementation 'com.ditto:ditto-kotlin-android:5.0.0'

    // Required for Ditto Kotlin bridge
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

Create a `.env` file at the repo root (not committed):
```
DITTO_APP_ID=your-app-id
DITTO_PLAYGROUND_TOKEN=your-token
DITTO_AUTH_URL=https://your-app.cloud.ditto.live
```

Add `.env` to `.gitignore`.

---

## Data Models

These are plain Java objects. Ditto stores them as documents. Define them in `model/`.

### Observation.java

```java
public class Observation {
    public String id;           // UUID — Ditto document _id
    public String operatorUID;  // ATAK self UID from MapView.getMapView().getSelfMarker().getUID()
    public String callsign;     // from getSelfMarker().getMetaString("callsign", "")
    public String text;         // free-form observation text
    public String category;     // ObservationCategory name()
    public double latitude;     // 0.0 if no location attached
    public double longitude;    // 0.0 if no location attached
    public long timestamp;      // System.currentTimeMillis()
    public boolean archived;    // soft delete — never hard-delete from Ditto
}
```

### ObservationCategory.java

```java
public enum ObservationCategory {
    HAZARD,
    INFRASTRUCTURE,
    LOGISTICS,
    MEDICAL,
    SECURITY,
    GENERAL
}
```

### AIQuery.java

```java
public class AIQuery {
    public String id;               // UUID
    public String queryText;        // operator's question
    public String requestedBy;      // ATAK self UID
    public String requestedByCallsign;
    public long timestamp;
    public String status;           // "pending" | "processing" | "complete" | "error"
}
```

### AIResponse.java

```java
public class AIResponse {
    public String id;           // UUID
    public String queryId;      // matches AIQuery.id
    public String responseText; // AI-generated answer
    public String confidence;   // "low" | "moderate" | "high" — AI node sets this
    public long timestamp;
}
```

---

## Ditto Collections

Three collections in the Ditto store. Use these exact names throughout:

| Collection | Contents | Who writes | Who reads |
|---|---|---|---|
| `observations` | Observation documents | Plugin (all devices) | Plugin (all devices), AI node |
| `ai_queries` | AIQuery documents | Plugin | AI node |
| `ai_responses` | AIResponse documents | AI node | Plugin |

Documents are never deleted. Use `archived: true` on observations. Queries and responses are immutable once written.

---

## Class Responsibilities

### plugin/FieldSyncLifecycle.java

Extends `AbstractPlugin`. This is the class declared in `AndroidManifest.xml`.

Responsibilities:
- Constructor calls `super(svc, new FieldSyncTool(ctx), new FieldSyncMapComponent())`
- Nothing else. All logic lives in the component.

```java
public class FieldSyncLifecycle extends AbstractPlugin {
    public FieldSyncLifecycle(IServiceController svc) {
        super(svc,
            new FieldSyncTool(
                svc.getService(PluginContextProvider.class).getPluginContext()),
            new FieldSyncMapComponent());
    }
}
```

---

### plugin/FieldSyncTool.java

Extends `AbstractPluginTool`. Adds the FieldSync button to ATAK's nav bar.

Responsibilities:
- Define the action intent string: `com.fieldsync.plugin.SHOW_MAIN`
- Provide icon and display name

```java
public class FieldSyncTool extends AbstractPluginTool {
    static final String SHOW_MAIN = "com.fieldsync.plugin.SHOW_MAIN";

    public FieldSyncTool(Context ctx) {
        super(ctx,
            "FieldSync",
            "FieldSync AI",
            ctx.getResources().getDrawable(R.drawable.ic_fieldsync),
            SHOW_MAIN);
    }
}
```

---

### plugin/FieldSyncMapComponent.java

Extends `DropDownMapComponent`. Loaded by ATAK when the plugin activates.

Responsibilities:
- Initialize `DittoManager` in `onCreate`
- Register all `DropDownReceiver` instances with their intent filters
- Unregister and shut down `DittoManager` in `onDestroyImpl`

```java
@Override
public void onCreate(Context ctx, Intent intent, MapView mapView) {
    super.onCreate(ctx, intent, mapView);

    DittoManager.getInstance().init(ctx);

    mainDropDown = new MainDropDown(ctx, mapView);
    observationListDropDown = new ObservationListDropDown(ctx, mapView);
    observationFormDropDown = new ObservationFormDropDown(ctx, mapView);
    aiQueryDropDown = new AIQueryDropDown(ctx, mapView);

    registerDropDownReceiver(mainDropDown,
        new AtakBroadcast.DocumentedIntentFilter(FieldSyncTool.SHOW_MAIN));
    registerDropDownReceiver(observationListDropDown,
        new AtakBroadcast.DocumentedIntentFilter("com.fieldsync.plugin.SHOW_OBS_LIST"));
    registerDropDownReceiver(observationFormDropDown,
        new AtakBroadcast.DocumentedIntentFilter("com.fieldsync.plugin.SHOW_OBS_FORM"));
    registerDropDownReceiver(aiQueryDropDown,
        new AtakBroadcast.DocumentedIntentFilter("com.fieldsync.plugin.SHOW_AI_QUERY"));
}

@Override
protected void onDestroyImpl(Context ctx, MapView mapView) {
    DittoManager.getInstance().close();
    mainDropDown.disposeImpl();
    observationListDropDown.disposeImpl();
    observationFormDropDown.disposeImpl();
    aiQueryDropDown.disposeImpl();
}
```

---

### sync/DittoHelper.kt

A Kotlin object that bridges Ditto v5's Kotlin suspend API to Java callers. Put this file in the same package as the Java sources. The android-java quickstart at `D:\Development\quickstart\android-java` uses this exact pattern.

```kotlin
package com.fieldsync.plugin.sync

import com.ditto.kotlin.*
import kotlinx.coroutines.runBlocking
import java.util.function.Consumer

object DittoHelper {

    @JvmStatic
    fun createDitto(appId: String, serverUrl: String): Ditto {
        val config = DittoConfig(
            databaseId = appId,
            connect = DittoConfig.Connect.Server(serverUrl)
        )
        return DittoFactory.create(config)
    }

    @JvmStatic
    fun setupAuth(ditto: Ditto, token: String) {
        ditto.auth?.expirationHandler = { d, _ ->
            d.auth?.login(token, DittoAuthenticationProvider.development())
        }
    }

    @JvmStatic
    @Throws(DittoException::class)
    fun execute(ditto: Ditto, query: String, args: Map<String, Any?>) {
        runBlocking { ditto.store.execute(query, args) }
    }

    @JvmStatic
    fun registerSubscription(ditto: Ditto, query: String): DittoSyncSubscription =
        ditto.sync.registerSubscription(query)

    @JvmStatic
    fun registerObserver(
        ditto: Ditto,
        query: String,
        callback: Consumer<DittoQueryResult>
    ): DittoStoreObserver =
        ditto.store.registerObserver(query) { callback.accept(it) }

    @JvmStatic
    fun startSync(ditto: Ditto) = ditto.sync.start()

    @JvmStatic
    fun stopSync(ditto: Ditto) = ditto.sync.stop()
}
```

---

### sync/DittoManager.java

Singleton. Owns the `Ditto` instance for the lifetime of the plugin. Credentials come from `BuildConfig` (loaded from `.env` at build time).

Responsibilities:
- Initialize Ditto and configure auth on `init()`
- Register sync subscription for all three collections
- Provide the `Ditto` instance to repositories via `getInstance().getDitto()`
- Stop sync and close Ditto on `close()`

```java
public class DittoManager {
    private static DittoManager instance;
    private Ditto ditto;
    private DittoSyncSubscription observationsSubscription;
    private DittoSyncSubscription queriesSubscription;
    private DittoSyncSubscription responsesSubscription;

    public static synchronized DittoManager getInstance() {
        if (instance == null) instance = new DittoManager();
        return instance;
    }

    public void init() {
        ditto = DittoHelper.createDitto(
            BuildConfig.DITTO_APP_ID,
            BuildConfig.DITTO_AUTH_URL
        );
        DittoHelper.setupAuth(ditto, BuildConfig.DITTO_PLAYGROUND_TOKEN);

        observationsSubscription = DittoHelper.registerSubscription(
            ditto, "SELECT * FROM observations");
        queriesSubscription = DittoHelper.registerSubscription(
            ditto, "SELECT * FROM ai_queries");
        responsesSubscription = DittoHelper.registerSubscription(
            ditto, "SELECT * FROM ai_responses");

        DittoHelper.startSync(ditto);
    }

    public Ditto getDitto() { return ditto; }

    public void close() {
        if (observationsSubscription != null) observationsSubscription.close();
        if (queriesSubscription != null) queriesSubscription.close();
        if (responsesSubscription != null) responsesSubscription.close();
        if (ditto != null) {
            DittoHelper.stopSync(ditto);
            ditto.close();
        }
    }
}
```

---

### sync/ObservationRepository.java

Wraps the `observations` Ditto collection using DQL (Ditto Query Language). All observation persistence goes through here.

Responsibilities:
- `insert(Observation)` — INSERT INTO observations
- `archive(String id)` — soft delete via UPDATE SET archived=true
- `observeActive(ObservationListener)` — live observer on non-archived rows; returns `DittoStoreObserver`
- `fromQueryItem(DittoQueryResultItem)` — parse a result row into an `Observation`

```java
public interface ObservationListener {
    void onObservationsChanged(List<Observation> observations);
}

public void insert(Observation obs) throws DittoException {
    Map<String, Object> doc = new HashMap<>();
    doc.put("_id", obs.id);
    doc.put("operatorUID", obs.operatorUID);
    doc.put("callsign", obs.callsign);
    doc.put("text", obs.text);
    doc.put("category", obs.category);
    doc.put("latitude", obs.latitude);
    doc.put("longitude", obs.longitude);
    doc.put("timestamp", obs.timestamp);
    doc.put("archived", false);

    Map<String, Object> args = Collections.singletonMap("doc", doc);
    DittoHelper.execute(ditto, "INSERT INTO observations DOCUMENTS (:doc)", args);
}

public void archive(String id) throws DittoException {
    Map<String, Object> args = Collections.singletonMap("id", id);
    DittoHelper.execute(ditto, "UPDATE observations SET archived=true WHERE _id=:id", args);
}

public DittoStoreObserver observeActive(ObservationListener listener) {
    return DittoHelper.registerObserver(
        ditto,
        "SELECT * FROM observations WHERE archived=false ORDER BY timestamp DESC",
        result -> {
            List<Observation> obs = result.getItems().stream()
                .map(ObservationRepository::fromQueryItem)
                .collect(Collectors.toList());
            listener.onObservationsChanged(obs);
        }
    );
}

private static Observation fromQueryItem(DittoQueryResultItem item) {
    try {
        JSONObject json = new JSONObject(item.jsonString());
        Observation obs = new Observation();
        obs.id = json.optString("_id");
        obs.operatorUID = json.optString("operatorUID");
        obs.callsign = json.optString("callsign");
        obs.text = json.optString("text");
        obs.category = json.optString("category");
        obs.latitude = json.optDouble("latitude", 0.0);
        obs.longitude = json.optDouble("longitude", 0.0);
        obs.timestamp = json.optLong("timestamp");
        obs.archived = json.optBoolean("archived", false);
        return obs;
    } catch (JSONException e) {
        throw new RuntimeException("Failed to parse observation", e);
    }
}
```

The observer is the primary data source for the UI. Store the returned `DittoStoreObserver` as a field. Call `.close()` on it when the drop-down panel closes. If it is garbage collected without being closed, updates stop silently.

---

### sync/AIQueryRepository.java

Wraps `ai_queries` and `ai_responses` collections using DQL.

Responsibilities:
- `submitQuery(AIQuery)` — insert into `ai_queries` with status `"pending"`
- `observeResponse(String queryId, ResponseListener)` — live observer on `ai_responses` filtered by `queryId`; returns `DittoStoreObserver`
- `getRecentQueries(int limit)` — one-shot SELECT for history display

```java
public interface ResponseListener {
    void onResponseReceived(AIResponse response);
}

public void submitQuery(AIQuery query) throws DittoException {
    Map<String, Object> doc = new HashMap<>();
    doc.put("_id", query.id);
    doc.put("queryText", query.queryText);
    doc.put("requestedBy", query.requestedBy);
    doc.put("requestedByCallsign", query.requestedByCallsign);
    doc.put("timestamp", query.timestamp);
    doc.put("status", "pending");

    Map<String, Object> args = Collections.singletonMap("doc", doc);
    DittoHelper.execute(ditto, "INSERT INTO ai_queries DOCUMENTS (:doc)", args);
}

public DittoStoreObserver observeResponse(String queryId, ResponseListener listener) {
    Map<String, Object> args = Collections.singletonMap("queryId", queryId);
    // DittoHelper.registerObserver does not yet support args directly — use the overload:
    return DittoHelper.getDitto().getStore().registerObserver(
        "SELECT * FROM ai_responses WHERE queryId=:queryId",
        args,
        result -> {
            if (!result.getItems().isEmpty()) {
                listener.onResponseReceived(fromQueryItem(result.getItems().get(0)));
            }
        }
    );
}
```

> `registerObserver` with args requires calling the Ditto Kotlin API directly. Add a `registerObserverWithArgs` method to `DittoHelper.kt` following the same `runBlocking` bridge pattern.

---

## UI Drop-Down Receivers

All four extend `DropDownReceiver`. All accept `(Context ctx, MapView mapView)` in their constructor.

### ui/MainDropDown.java

Shown when the user taps the FieldSync nav button. Acts as the root navigation panel.

Layout (`dropdown_main.xml`): Two large buttons — "Observations" and "Ask AI". Each button fires an `AtakBroadcast` intent to open the corresponding sub-panel.

```java
@Override
public void onReceive(Context ctx, Intent intent) {
    if (intent.getAction().equals(FieldSyncTool.SHOW_MAIN)) {
        showDropDown(mainView, HALF_WIDTH, FULL_HEIGHT, false);
    }
}
```

Button handlers:
```java
btnObservations.setOnClickListener(v ->
    AtakBroadcast.getInstance().sendBroadcast(
        new Intent("com.fieldsync.plugin.SHOW_OBS_LIST")));

btnAskAI.setOnClickListener(v ->
    AtakBroadcast.getInstance().sendBroadcast(
        new Intent("com.fieldsync.plugin.SHOW_AI_QUERY")));
```

---

### ui/ObservationListDropDown.java

Displays all non-archived observations in a scrollable list. Each row shows: callsign, category badge, first 80 chars of text, relative timestamp.

Lifecycle:
- On `showDropDown`: register live subscription via `ObservationRepository.subscribeAll`
- On close (`onHide` / state listener): deregister subscription
- Tap on a row: open `ObservationFormDropDown` in edit mode with the observation id as an extra
- "New" button: open `ObservationFormDropDown` in create mode

State management: Store the `DittoLiveQuery` reference as a field. Null it out on close.

---

### ui/ObservationFormDropDown.java

Create or edit a single observation.

Fields:
- Multi-line text input (the observation body)
- Category spinner — populated from `ObservationCategory.values()`
- "Use current location" checkbox — reads from `MapView.getMapView().getSelfMarker().getPoint()`
- Save button
- Archive button (edit mode only) — calls `ObservationRepository.archive(id)` then closes

On save (create):
```java
Observation obs = new Observation();
obs.id = UUID.randomUUID().toString();
obs.operatorUID = mapView.getSelfMarker().getUID();
obs.callsign = mapView.getSelfMarker().getMetaString("callsign", "UNKNOWN");
obs.text = textInput.getText().toString().trim();
obs.category = selectedCategory.name();
obs.timestamp = System.currentTimeMillis();
obs.archived = false;
// populate lat/lon if checkbox checked
ObservationRepository.getInstance().insert(obs);
closeDropDown();
```

On save (edit): call `ObservationRepository.update(id, changes)` with only the changed fields.

---

### ui/AIQueryDropDown.java

Single-screen query interface.

Layout (`dropdown_ai_query.xml`):
- Text input at top: "Ask a question..."
- Send button
- Response area (scrollable TextView or RecyclerView of recent query+response pairs)
- Status indicator: "Waiting for AI node..." shown when query is pending

Lifecycle:
- On send: build an `AIQuery`, call `AIQueryRepository.submitQuery`, then start polling via `subscribeResponses`
- When response arrives: display it in the response area, clear status indicator
- Show the last 5 query/response pairs on open

Timeout: If no response arrives within 60 seconds, show "No response from AI node. Is it running on the network?"

---

## Data Flow

### Observation created by operator

```
User taps Save in ObservationFormDropDown
  → ObservationRepository.insert(obs)
    → Ditto writes document to local store
      → Ditto syncs to all peers on mesh
        → ObservationListDropDown live query fires on all devices
          → UI updates
```

### AI query submitted

```
User types question, taps Send in AIQueryDropDown
  → AIQueryRepository.submitQuery(query)
    → Ditto writes to ai_queries collection
      → AI node receives via its own Ditto subscription
        → AI node reads observations collection
          → AI node generates response
            → AI node writes to ai_responses collection
              → AIQueryDropDown subscribeResponses fires
                → Response displayed
```

The plugin never directly contacts the AI node. All communication is through Ditto documents.

---

## Intent Action Strings

Define all action strings as constants in a single class to avoid typos:

```java
public final class Actions {
    public static final String SHOW_MAIN     = "com.fieldsync.plugin.SHOW_MAIN";
    public static final String SHOW_OBS_LIST = "com.fieldsync.plugin.SHOW_OBS_LIST";
    public static final String SHOW_OBS_FORM = "com.fieldsync.plugin.SHOW_OBS_FORM";
    public static final String SHOW_AI_QUERY = "com.fieldsync.plugin.SHOW_AI_QUERY";
}
```

Always use `AtakBroadcast` — never `context.sendBroadcast()`.

---

## Development Phases

Implement in this order. Each phase produces a working, testable state.

### Phase 1 — Scaffold

Goal: Plugin appears in ATAK Plugin Manager and loads without crashing.

- Copy `plugintemplate` from ATAK SDK samples as the starting point
- Rename package to `com.fieldsync.plugin`
- Replace lifecycle, tool, and component with FieldSync versions (empty stubs)
- Verify: plugin appears in Plugin Manager, activates, nav button visible

### Phase 2 — Observation UI (local only)

Goal: Operator can create, view, and archive observations stored only on-device.

- Implement `Observation`, `ObservationCategory`, `AIQuery`, `AIResponse` models
- Implement `DittoManager` with offline playground identity (no sync yet — just local store)
- Implement `ObservationRepository` (insert, archive, subscribeAll)
- Implement `ObservationListDropDown` and `ObservationFormDropDown`
- Wire `MainDropDown` navigation buttons
- Verify: create an observation, see it in the list, archive it, it disappears from the list

### Phase 3 — Ditto Sync

Goal: Two emulator instances (or emulator + AI node laptop) see each other's observations.

- Enable Ditto `startSync()` in `DittoManager`
- Add Ditto permissions to manifest
- Verify on two devices: observation created on Device A appears on Device B within a few seconds

### Phase 4 — AI Query UI

Goal: Operator can submit a query and see it was sent (even if no AI node responds yet).

- Implement `AIQueryRepository` (submitQuery)
- Implement `AIQueryDropDown` with input, send, and pending state
- Verify: query appears in Ditto `ai_queries` collection (inspect via Ditto Portal or logs)

### Phase 5 — AI Response Display

Goal: When an AI node publishes a response, it appears in the plugin UI.

- Implement `AIQueryRepository.subscribeResponses`
- Wire response into `AIQueryDropDown` display
- Test with a mock AI node that manually inserts a response document into Ditto
- Verify: mock response appears in UI within seconds of being written

### Phase 6 — Polish

Goal: MVP complete.

- Relative timestamps ("2 minutes ago")
- Category filter on observation list
- Observation count badge on nav button via `NavButtonManager`
- 60-second AI response timeout with user-visible message
- Handle Ditto sync errors gracefully (log, show status, do not crash)

---

## Rules for the Agent

1. **Never use `context.sendBroadcast()`** inside the plugin. Always use `AtakBroadcast.getInstance().sendBroadcast()`.

2. **Never hard-delete Ditto documents.** Set `archived: true`. Ditto CRDTs do not handle deletion well across peers.

3. **Always deregister subscriptions when a DropDown closes.** Live queries hold memory and fire callbacks even when the UI is gone.

4. **The AI node is not your problem.** The plugin writes to `ai_queries` and reads from `ai_responses`. It does not know what the AI node is, where it runs, or how it works.

5. **atak-javadoc.jar is stubs only.** Classes in it do not exist at runtime — ATAK provides them. Never ship it in the APK. It must remain `implementation files(...)` not `api` or it will fail at runtime.

6. **Ditto credentials never go in source.** Store `DITTO_APP_ID`, `DITTO_PLAYGROUND_TOKEN`, and `DITTO_AUTH_URL` in a `.env` file at the repo root. The build script reads them into `BuildConfig`. Add `.env` to `.gitignore`. The working pattern is in `D:\Development\quickstart\android-java\app\build.gradle.kts`.

7. **No internet required.** Do not add any dependency that phones home. Ditto in OfflinePlayground mode is self-contained.

8. **No autonomous actions.** The plugin displays information. It does not create map markers, CoT events, routes, or missions based on AI output.

---

## Quick Build and Install

```powershell
# Build
cd D:\Development\ATAK\fieldsync-ai
.\gradlew assembleCivDebug

# Install (ATAK must be running on emulator)
adb -s emulator-5554 install -r app\build\outputs\apk\civ\debug\*.apk

# Restart ATAK to load plugin
adb -s emulator-5554 shell am force-stop com.atakmap.app.civ
adb -s emulator-5554 shell monkey -p com.atakmap.app.civ -c android.intent.category.LAUNCHER 1

# Watch plugin logs
adb -s emulator-5554 logcat -v time -s FieldSyncMapComponent:D FieldSyncDropDown:D DittoManager:D AtakPluginRegistry:D
```

---

## Reference

- ATAK plugin wiring, CoT, build system, emulator setup: `D:\Development\ATAK\AGENTS.md`
- ATAK SDK location: `D:\Development\ATAK\ATAK-CIV-5.6.0-SDK\`
- Plugin template to copy from: `D:\Development\ATAK\ATAK-CIV-5.6.0-SDK\samples\plugintemplate\`
- Working Ditto Android Java example: `D:\Development\quickstart\android-java\`
  - `MainActivity.java` — observer/subscription wiring, DQL INSERT/UPDATE
  - `DittoHelper.kt` — Kotlin bridge pattern for Java callers
  - `Task.java` — `fromQueryItem()` JSON parse pattern
  - `build.gradle.kts` — `.env` → `BuildConfig` credential injection, Ditto packaging flags
- Working Ditto Java server example: `D:\Development\quickstart\java-server\`
  - `DittoService.java` — Ditto lifecycle, presence observer, sync start/stop
  - `DittoTaskService.java` — DQL CRUD operations pattern
