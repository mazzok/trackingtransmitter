# Schatzpeiler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android-App, mit der ein Elternteil einen Schatz auf einer OSM-Karte setzt und Kinder ihn danach per Peilton (Tempo = Richtung, Tonhöhe = Entfernung) wiederfinden; signierte APK per GitHub Action.

**Architecture:** Eine Activity mit Compose-Navigation (Liste → Neuer Schatz / Suche). Reine Logik (Geo-Mathematik, Peilsignal, Ankunftserkennung, Kursglättung, Suchzustand) liegt in `domain/` ohne Android-Abhängigkeit und ist per JUnit getestet. Sensoren, Ton und Karte sind dünne Adapter darum. Karte: MapLibre mit eigenem reduziertem Vektorstil auf OpenFreeMap-Kacheln.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, Jetpack Compose (BOM 2024.12.01), Navigation Compose, MapLibre Android 11.8.0, OkHttp 4.12.0, JUnit 4 + Robolectric 4.14.1, semantic-release.

**Spec:** `docs/superpowers/specs/2026-11-12-schatzpeiler-design.md`

**Referenzprojekt:** `D:\GIT\walkwalk`. Wo der Plan „wie walkwalk“ sagt, die genannte Datei von dort kopieren und Namen anpassen (`walkwalk` → `trackingtransmitter`, Paket `com.walkwalk` → `com.trackingtransmitter`).

## Global Constraints

- **Keine Git-Commits, keine Pushes.** Der Nutzer macht die Versionsverwaltung selbst. Kein Task enthält einen Commit-Schritt.
- `applicationId`/`namespace` = `com.trackingtransmitter`; App-Name (Launcher) = `Schatzpeiler`.
- `minSdk 34`, `targetSdk 35`, `compileSdk 35`, Java/JVM-Target 17.
- Bibliotheksversionen exakt wie in `D:\GIT\walkwalk\gradle\libs.versions.toml` (nur die benötigten Einträge übernehmen; kein Room, kein KSP).
- UI-Texte ausschließlich Deutsch.
- Suchbildschirm zeigt **niemals** Schatzmarker, Entfernung oder Zielpfeil.
- Kartenstil enthält **keine** Ebenen vom Typ `symbol` und keine Quell-Ebenen `poi`, `place`, `transportation_name`, `water_name`, `aerodrome_label`, `mountain_peak`, `housenumber`. Quellenangabe `© OpenStreetMap contributors · OpenFreeMap` bleibt sichtbar.
- Berechtigungen im Manifest: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`, `VIBRATE`. Keine Hintergrund-Ortung.
- Signatur-Keystore liegt nur in `D:\GIT\keys\trackingtransmitter\` und in GitHub-Secrets, nie im Repo (`*.jks`, `*.keystore` in `.gitignore`).
- Zahlenwerte (überall genau diese Konstanten verwenden):
  - Zielradius „gefunden“: `FOUND_RADIUS_M = 5.0`
  - Nahbereich: `NEAR_RADIUS_M = 15.0`
  - Fixes in Folge für „gefunden“: `FOUND_CONSECUTIVE_FIXES = 3`
  - Max. Genauigkeit für „gefunden“: `FOUND_MAX_ACCURACY_M = 10f`
  - Max. Genauigkeit, ab der die Suche startet: `START_MAX_ACCURACY_M = 20f`
  - Pieptakt: `MIN_INTERVAL_MS = 150`, `MAX_INTERVAL_MS = 1500`, `NEAR_MIN_INTERVAL_MS = 80`
  - Tonhöhe: `FAR_HZ = 600.0` bei ≥ `FAR_DISTANCE_M = 300.0`, `NEAR_HZ = 1600.0` bei ≤ 5 m

## Review Focus

1. **Kurs über Norden (359° ↔ 1°):** Abweichung muss 2° sein, Glättung darf nicht über Süden schwenken. → Tests in Task 2 (`angleDiffDeg`) und Task 4 (`HeadingFilter`).
2. **Einzelner verrauschter GPS-Fix innerhalb 5 m oder Fix mit schlechter Genauigkeit:** darf kein „Gefunden“ auslösen. → Tests in Task 3 (`ArrivalDetector`).
3. **Schatzdatei fehlt oder ist kaputt:** App startet mit leerer Liste statt abzustürzen. → Test in Task 5 (`TreasureStore`).
4. **Handy hat keinen Kompass bzw. kein Gyroskop:** Rückfallsensor wird gewählt bzw. klarer Hinweis statt Absturz. → Test in Task 7 (`chooseHeadingSensor`).
5. **Suche läuft, App geht in den Hintergrund oder Bildschirm aus:** Piepen hört auf und geht beim Zurückkehren weiter; nach „Gefunden“ piept nichts mehr. → Tests in Task 6 (`SearchController`) + Lifecycle-Verdrahtung in Task 10.

---

### Task 1: Projektgerüst, das baut

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.gitignore` (alle wie walkwalk, angepasst)
- Create: `app/build.gradle.kts` (wie walkwalk inkl. Versions-/Signaturlogik und Robolectric-Offline-Setup; ohne Room/KSP/lifecycle-service)
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/trackingtransmitter/SchatzApp.kt`, `MainActivity.kt`
- Create: `app/src/test/resources/robolectric.properties` (`application=android.app.Application`, `sdk=34`)
- Test: `app/src/test/java/com/trackingtransmitter/SmokeTest.kt`

**Interfaces:**
- Produces: `class SchatzApp : Application` mit `val httpClient: OkHttpClient` (User-Agent-Interceptor wie walkwalk, `USER_AGENT = "trackingtransmitter/<versionName> (github.com/mazzok/trackingtransmitter)"`). `MainActivity : ComponentActivity` mit `setContent { … }` (vorerst Platzhaltertext „Schatzpeiler“).

- [ ] **Step 1:** Dateien wie walkwalk anlegen; Manifest mit den vier Berechtigungen aus den Global Constraints, `android:label="Schatzpeiler"`, Theme `@android:style/Theme.Material.Light.NoActionBar`, Activity `exported`, `screenOrientation="portrait"`.
- [ ] **Step 2:** `SmokeTest`: `assertEquals("com.trackingtransmitter", BuildConfig.APPLICATION_ID)`.
- [ ] **Step 3:** Run: `./gradlew testDebugUnitTest assembleDebug` — Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` existiert.

---

### Task 2: Geo-Mathematik

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/domain/Geo.kt`
- Test: `app/src/test/java/com/trackingtransmitter/domain/GeoTest.kt`

**Interfaces:**
- Produces:
  - `data class LatLon(val lat: Double, val lon: Double)`
  - `fun distanceM(a: LatLon, b: LatLon): Double` (Haversine, Erdradius 6_371_000 m)
  - `fun bearingDeg(from: LatLon, to: LatLon): Double` (Großkreis-Anfangskurs, 0..<360, 0 = Nord, 90 = Ost)
  - `fun angleDiffDeg(a: Double, b: Double): Double` (kleinster Winkelabstand, 0..180)

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun distance_oneHundredthDegreeLatitude() =
    assertEquals(1111.95, distanceM(LatLon(48.0, 11.0), LatLon(48.01, 11.0)), 0.5)
@Test fun bearing_north() = assertEquals(0.0, bearingDeg(LatLon(48.0, 11.0), LatLon(48.001, 11.0)), 0.01)
@Test fun bearing_east() = assertEquals(90.0, bearingDeg(LatLon(48.0, 11.0), LatLon(48.0, 11.001)), 0.01)
@Test fun bearing_west_isPositive() = assertEquals(270.0, bearingDeg(LatLon(48.0, 11.0), LatLon(48.0, 10.999)), 0.01)
@Test fun angleDiff_acrossNorth() = assertEquals(2.0, angleDiffDeg(359.0, 1.0), 1e-9)
@Test fun angleDiff_opposite() = assertEquals(180.0, angleDiffDeg(90.0, 270.0), 1e-9)
@Test fun angleDiff_symmetric() = assertEquals(angleDiffDeg(10.0, 350.0), angleDiffDeg(350.0, 10.0), 1e-9)
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*GeoTest'` — Expected: FAIL (unresolved reference).
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Gleicher Befehl — Expected: PASS.

---

### Task 3: Peilsignal und Ankunftserkennung

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/domain/Beacon.kt`, `domain/ArrivalDetector.kt`, `domain/SearchConstants.kt` (alle Konstanten aus den Global Constraints als `const val`)
- Test: `domain/BeaconTest.kt`, `domain/ArrivalDetectorTest.kt`

**Interfaces:**
- Produces:
  - `data class BeaconSignal(val intervalMs: Int, val frequencyHz: Double)`
  - `fun beaconSignal(distanceM: Double, deviationDeg: Double): BeaconSignal`
  - `class ArrivalDetector { fun onFix(distanceM: Double, accuracyM: Float?): Boolean; fun reset() }` — `true` genau ab dem Fix, mit dem `FOUND_CONSECUTIVE_FIXES` Fixes in Folge `distanceM <= FOUND_RADIUS_M` **und** `accuracyM != null && accuracyM <= FOUND_MAX_ACCURACY_M` erfüllen; jeder andere Fix setzt den Zähler auf 0.

Algorithmus `beaconSignal` (Kurven sind festgelegt, damit Tests sie prüfen):

```
f = (1 + cos(deviationDeg in rad)) / 2                       // 1 bei 0°, 0 bei 180°
dirInterval = MAX_INTERVAL_MS - f * (MAX_INTERVAL_MS - MIN_INTERVAL_MS)
if distanceM < NEAR_RADIUS_M:
    t = clamp((distanceM - FOUND_RADIUS_M) / (NEAR_RADIUS_M - FOUND_RADIUS_M), 0, 1)
    nearInterval = NEAR_MIN_INTERVAL_MS + t * (dirInterval - NEAR_MIN_INTERVAL_MS)
    interval = min(dirInterval, nearInterval)
else interval = dirInterval
d = clamp(distanceM, FOUND_RADIUS_M, FAR_DISTANCE_M)
frequencyHz = NEAR_HZ * (FAR_HZ / NEAR_HZ) ^ (ln(d / 5) / ln(300 / 5))   // log. Verlauf
intervalMs = round(interval)
```

- [ ] **Step 1: Failing tests**

```kotlin
// BeaconTest
@Test fun facingTarget_far_isFastest() = assertEquals(150, beaconSignal(100.0, 0.0).intervalMs)
@Test fun facingAway_far_isSlowest() = assertEquals(1500, beaconSignal(100.0, 180.0).intervalMs)
@Test fun sideways_isMiddle() = assertEquals(825, beaconSignal(100.0, 90.0).intervalMs)
@Test fun turningTowards_isMonotonic() {
    val intervals = (0..180 step 10).map { beaconSignal(100.0, it.toDouble()).intervalMs }
    assertEquals(intervals.sorted(), intervals)
}
@Test fun atFoundRadius_isVeryFast_evenFacingAway() = assertEquals(80, beaconSignal(5.0, 180.0).intervalMs)
@Test fun nearZone_speedsUpWithoutDirection() =
    assertTrue(beaconSignal(8.0, 180.0).intervalMs < beaconSignal(14.0, 180.0).intervalMs)
@Test fun pitch_farAndNear() {
    assertEquals(600.0, beaconSignal(1000.0, 0.0).frequencyHz, 0.01)
    assertEquals(1600.0, beaconSignal(2.0, 0.0).frequencyHz, 0.01)
    assertTrue(beaconSignal(50.0, 0.0).frequencyHz > beaconSignal(100.0, 0.0).frequencyHz)
}

// ArrivalDetectorTest
@Test fun threeGoodFixesInside_found() { val d = ArrivalDetector()
    assertFalse(d.onFix(4.0, 5f)); assertFalse(d.onFix(3.0, 5f)); assertTrue(d.onFix(4.5, 5f)) }
@Test fun singleNoisyFix_notFound() { val d = ArrivalDetector()
    assertFalse(d.onFix(4.0, 5f)); assertFalse(d.onFix(12.0, 5f)); assertFalse(d.onFix(4.0, 5f)); assertFalse(d.onFix(4.0, 5f)) }
@Test fun poorAccuracy_notFound() { val d = ArrivalDetector()
    repeat(5) { assertFalse(d.onFix(2.0, 15f)) } }
@Test fun missingAccuracy_notFound() { val d = ArrivalDetector()
    repeat(5) { assertFalse(d.onFix(2.0, null)) } }
@Test fun reset_requiresFreshRun() { val d = ArrivalDetector()
    d.onFix(1.0, 5f); d.onFix(1.0, 5f); d.reset(); assertFalse(d.onFix(1.0, 5f)) }
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*BeaconTest' --tests '*ArrivalDetectorTest'` — Expected: FAIL.
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Gleicher Befehl — Expected: PASS.

---

### Task 4: Kursglättung

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/domain/HeadingFilter.kt`
- Test: `domain/HeadingFilterTest.kt`

**Interfaces:**
- Produces: `class HeadingFilter(private val alpha: Double = 0.25) { fun update(rawDeg: Double): Double; fun reset() }` — zirkulare Exponentialglättung über (sin, cos)-Mittelung; erster Wert wird unverändert übernommen; Ergebnis 0..<360.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun firstValue_passesThrough() = assertEquals(42.0, HeadingFilter().update(42.0), 1e-9)
@Test fun wrapAroundNorth_staysNearNorth() {
    val f = HeadingFilter(); f.update(350.0)
    val out = f.update(10.0)
    assertTrue("got $out", angleDiffDeg(out, 0.0) < 10.0)   // nicht ~180
}
@Test fun converges() { val f = HeadingFilter(); f.update(0.0)
    var out = 0.0; repeat(40) { out = f.update(90.0) }; assertEquals(90.0, out, 0.5) }
@Test fun outputInRange() { val f = HeadingFilter(); f.update(359.0)
    val out = f.update(358.0); assertTrue(out >= 0.0 && out < 360.0) }
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*HeadingFilterTest'` — Expected: FAIL.
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Gleicher Befehl — Expected: PASS.

---

### Task 5: Schatzspeicher

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/data/Treasure.kt`, `data/TreasureStore.kt`
- Modify: `SchatzApp.kt` — `val treasures: TreasureStore by lazy { TreasureStore(File(filesDir, "treasures.json")) }`
- Test: `app/src/test/java/com/trackingtransmitter/data/TreasureStoreTest.kt` (JUnit `TemporaryFolder`; Robolectric-Runner, weil `org.json` aus android.jar kommt)

**Interfaces:**
- Consumes: `LatLon` (Task 2)
- Produces:
  - `data class Treasure(val id: String, val name: String, val position: LatLon, val createdAt: Long)`
  - `class TreasureStore(file: File, clock: () -> Long = System::currentTimeMillis)` mit
    - `val treasures: StateFlow<List<Treasure>>` (neueste zuerst)
    - `fun add(name: String, position: LatLon): Treasure` (id = `UUID.randomUUID().toString()`)
    - `fun delete(id: String)`
    - `fun get(id: String): Treasure?`
    - `fun nextDefaultName(): String` → `"Schatz N"`, N = kleinste Zahl ≥ 1, für die der Name noch nicht existiert
  - Format: JSON-Array von Objekten `{id,name,lat,lon,createdAt}`. Schreiben atomar (temporäre Datei + `renameTo`).

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun addAndReload() { val f = tmp.newFile(); f.delete()
    TreasureStore(f).add("Schatz 1", LatLon(48.1, 11.5))
    val reloaded = TreasureStore(f).treasures.value
    assertEquals(listOf("Schatz 1"), reloaded.map { it.name })
    assertEquals(LatLon(48.1, 11.5), reloaded[0].position) }
@Test fun delete_removes() { val s = TreasureStore(tmp.newFile().apply { delete() })
    val t = s.add("A", LatLon(0.0, 0.0)); s.delete(t.id); assertTrue(s.treasures.value.isEmpty()) }
@Test fun missingFile_isEmpty() = assertTrue(TreasureStore(File(tmp.root, "nope.json")).treasures.value.isEmpty())
@Test fun corruptFile_isEmpty() { val f = tmp.newFile().apply { writeText("{kaputt") }
    assertTrue(TreasureStore(f).treasures.value.isEmpty()) }
@Test fun defaultNames_fillGaps() { val s = TreasureStore(tmp.newFile().apply { delete() })
    assertEquals("Schatz 1", s.nextDefaultName()); s.add("Schatz 1", LatLon(0.0, 0.0))
    val two = s.add(s.nextDefaultName(), LatLon(0.0, 0.0)); assertEquals("Schatz 2", two.name)
    s.delete(s.treasures.value.first { it.name == "Schatz 1" }.id); assertEquals("Schatz 1", s.nextDefaultName()) }
@Test fun newestFirst() { var now = 1L; val s = TreasureStore(tmp.newFile().apply { delete() }) { now++ }
    s.add("alt", LatLon(0.0, 0.0)); s.add("neu", LatLon(0.0, 0.0))
    assertEquals(listOf("neu", "alt"), s.treasures.value.map { it.name }) }
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*TreasureStoreTest'` — Expected: FAIL.
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Gleicher Befehl — Expected: PASS.

---

### Task 6: Suchlogik

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/domain/SearchController.kt`
- Test: `domain/SearchControllerTest.kt`

**Interfaces:**
- Consumes: `LatLon`, `distanceM`, `bearingDeg`, `angleDiffDeg` (Task 2); `beaconSignal`, `BeaconSignal`, `ArrivalDetector`, Konstanten (Task 3)
- Produces:
  - `data class Fix(val position: LatLon, val accuracyM: Float?)`
  - `sealed interface SearchState { data object WaitingForSignal; data class Searching(val position: LatLon); data object Found }`
  - `class SearchController(private val target: LatLon, private val detector: ArrivalDetector = ArrivalDetector())`
    - `val state: StateFlow<SearchState>` (Start: `WaitingForSignal`)
    - `fun onFix(fix: Fix)` — solange `WaitingForSignal`: Wechsel zu `Searching` erst bei `accuracyM != null && accuracyM <= START_MAX_ACCURACY_M`. In `Searching`: Position aktualisieren, `detector.onFix(distance, accuracy)` → bei `true` `Found`. In `Found`: ignorieren.
    - `fun signal(headingDeg: Double): BeaconSignal?` — `null` außer in `Searching`; sonst `beaconSignal(distance zur letzten Position, angleDiffDeg(bearing zur Ziel, headingDeg))`.
    - `fun continueSearching()` — `Found` → `Searching(letzte Position)`, `detector.reset()`.
  - `SearchState.Searching` trägt bewusst **keine** Entfernung (Anti-Ziel: UI soll sie nicht anzeigen können).

- [ ] **Step 1: Failing tests** (Ziel `T = LatLon(48.0, 11.0)`, Punkte 100 m nördlich/2 m daneben per Hilfsfunktion `north(m)` = `LatLon(48.0 + m / 111_195.0, 11.0)`)

```kotlin
@Test fun startsWaiting() = assertEquals(SearchState.WaitingForSignal, SearchController(T).state.value)
@Test fun poorAccuracy_keepsWaiting_andSilent() { val c = SearchController(T)
    c.onFix(Fix(north(100.0), 35f)); assertEquals(SearchState.WaitingForSignal, c.state.value); assertNull(c.signal(0.0)) }
@Test fun goodFix_startsSearching() { val c = SearchController(T)
    c.onFix(Fix(north(100.0), 8f)); assertTrue(c.state.value is SearchState.Searching) }
@Test fun facingTarget_fromNorth_isFast() { val c = SearchController(T)
    c.onFix(Fix(north(100.0), 8f)); assertEquals(150, c.signal(180.0)!!.intervalMs); assertEquals(1500, c.signal(0.0)!!.intervalMs) }
@Test fun arrival_found_andSilent() { val c = SearchController(T)
    c.onFix(Fix(north(100.0), 8f)); repeat(3) { c.onFix(Fix(north(2.0), 5f)) }
    assertEquals(SearchState.Found, c.state.value); assertNull(c.signal(180.0))
    c.onFix(Fix(north(100.0), 5f)); assertEquals(SearchState.Found, c.state.value) }
@Test fun continueSearching_reArms() { val c = SearchController(T)
    c.onFix(Fix(north(100.0), 8f)); repeat(3) { c.onFix(Fix(north(2.0), 5f)) }
    c.continueSearching(); assertTrue(c.state.value is SearchState.Searching)
    c.onFix(Fix(north(2.0), 5f)); assertTrue(c.state.value is SearchState.Searching) }
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*SearchControllerTest'` — Expected: FAIL.
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Gleicher Befehl — Expected: PASS.

---

### Task 7: Sensoren – Position und Blickrichtung

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/sensors/LocationSource.kt`, `sensors/HeadingSource.kt`
- Test: `app/src/test/java/com/trackingtransmitter/sensors/HeadingSensorChoiceTest.kt`

**Interfaces:**
- Consumes: `Fix`, `LatLon` (Task 6/2), `HeadingFilter` (Task 4)
- Produces:
  - `class LocationSource(context: Context) { fun fixes(): Flow<Fix>; fun lastKnown(): LatLon? }` — wie walkwalk `GpsLocationSource` (`LocationManager`, Provider `FUSED_PROVIDER` falls vorhanden, sonst `GPS_PROVIDER`, `QUALITY_HIGH_ACCURACY`), aber Intervall `1_000L` und ohne Höhenkorrektur. Das ist der Rückfall „ohne Play Services“ aus der Spec: der Plattform-Fused-Provider braucht keine Play Services.
  - `enum class HeadingSensor { ROTATION_VECTOR, GEOMAGNETIC_ROTATION_VECTOR, ACCEL_MAGNETOMETER, NONE }`
  - `fun chooseHeadingSensor(available: Set<Int>): HeadingSensor` (Eingabe: `Sensor.TYPE_*`-Konstanten) — Reihenfolge: `TYPE_ROTATION_VECTOR` → `TYPE_GEOMAGNETIC_ROTATION_VECTOR` → (`TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD`) → `NONE`.
  - `class HeadingSource(context: Context) { val sensor: HeadingSensor; fun headings(): Flow<Double> }` — liefert geglätteten wahren Kurs 0..<360 (Azimut + `GeomagneticField(...).declination` für `lastKnown()`, sonst 0). Haltung: aus der Rotationsmatrix die Neigung bestimmen; ist das Handy aufrecht (|pitch| > 45°), vorher `SensorManager.remapCoordinateSystem(R, AXIS_X, AXIS_Z, out)` anwenden, sodass der Kurs die Richtung der Rückseite ist; flach = Richtung der Oberkante. Glättung: `HeadingFilter(0.25)` für Rotationsvektor, `HeadingFilter(0.1)` für die beiden Rückfälle. `SENSOR_DELAY_GAME`.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun prefersRotationVector() = assertEquals(HeadingSensor.ROTATION_VECTOR,
    chooseHeadingSensor(setOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)))
@Test fun noGyro_usesGeomagnetic() = assertEquals(HeadingSensor.GEOMAGNETIC_ROTATION_VECTOR,
    chooseHeadingSensor(setOf(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)))
@Test fun rawFallback() = assertEquals(HeadingSensor.ACCEL_MAGNETOMETER,
    chooseHeadingSensor(setOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)))
@Test fun noMagnetometer_none() = assertEquals(HeadingSensor.NONE, chooseHeadingSensor(setOf(Sensor.TYPE_ACCELEROMETER)))
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*HeadingSensorChoiceTest'` — Expected: FAIL.
- [ ] **Step 3:** `chooseHeadingSensor`, `LocationSource`, `HeadingSource` implementieren.
- [ ] **Step 4:** Gleicher Befehl — Expected: PASS. Zusätzlich `./gradlew assembleDebug` — BUILD SUCCESSFUL.

---

### Task 8: Ton und Vibration

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/audio/BeepPlayer.kt`, `audio/Celebration.kt`

**Interfaces:**
- Consumes: `BeaconSignal` (Task 3)
- Produces:
  - `class BeepPlayer { fun start(signal: () -> BeaconSignal?); fun stop() }` — eigener Thread/Coroutine auf `Dispatchers.Default`; Schleife: Signal holen; bei `null` 100 ms warten; sonst Piep von **60 ms** Sinus mit `frequencyHz` (5 ms Ein-/Ausblendung gegen Knacken) über `AudioTrack` (MODE_STREAM, 44_100 Hz, PCM 16 bit mono, `AudioAttributes.USAGE_GAME`), dann bis `intervalMs` ab Pieptakt-Beginn warten. Das Signal wird **vor jedem Piep neu** gelesen, damit Drehen sofort wirkt. `stop()` ist idempotent.
  - `fun celebrate(context: Context)` — Vibration `VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 600), -1)` über `VibratorManager.defaultVibrator`, gleichzeitig synthetisierte Fanfare (aufsteigendes Arpeggio C6–E6–G6–C7 je 150 ms, letzter Ton 500 ms) über denselben Sinus-Generator. Kein mitgeliefertes Audiofile (vermeidet Lizenzfragen; Abweichung von der Spec-Formulierung „mitgelieferte Datei“, die Spec überlässt den Ton dem Builder).

- [ ] **Step 1:** Implementieren.
- [ ] **Step 2:** Run `./gradlew assembleDebug` — Expected: BUILD SUCCESSFUL. (Hörprüfung erfolgt in Task 10.)

---

### Task 9: Karte

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/map/MapStyle.kt`, `map/MapSetup.kt`, `map/TreasureMap.kt`
- Modify: `SchatzApp.kt` — `onCreate` ruft `MapSetup.init(this, httpClient)`
- Test: `app/src/test/java/com/trackingtransmitter/map/MapStyleTest.kt` (Robolectric-Runner wegen `org.json`)

**Interfaces:**
- Consumes: `LatLon` (Task 2)
- Produces:
  - `object MapStyle { const val ATTRIBUTION = "© OpenStreetMap contributors · OpenFreeMap"; fun json(): String }` — Quelle `"omt"`: `{"type":"vector","url":"https://tiles.openfreemap.org/planet"}`. Ebenen (Reihenfolge, alle `fill`/`line`, OpenMapTiles-Schema): `background` (#F2EFE9), `landcover` (class `grass`/`wood`/`farmland`, Grüntöne), `park` (Grün), `landuse` (blass), `water` (fill #AAD3DF), `waterway` (line #AAD3DF), `building` (fill #D9D0C9), `transportation` (line; Breite/Farbe nach `class`: motorway/trunk/primary breit + orange, secondary/tertiary gelb, minor/service weiß, path/track gestrichelt braun). Kein `glyphs`, kein `sprite`.
  - `object MapSetup { fun init(context: Context, client: OkHttpClient) }` — wie walkwalk (`HttpRequestUtil.setOkHttpClient`, Ambient-Cache `200L * 1024 * 1024`).
  - `@Composable fun TreasureMap(modifier: Modifier, user: LatLon?, headingDeg: Double?, treasure: LatLon?, followUser: Boolean, initialCenter: LatLon?, onTap: ((LatLon) -> Unit)?)` — `MapView` in `AndroidView` mit Lifecycle-Weiterleitung (wie walkwalk `TopoMap.kt`). Nordausgerichtet, Rotation per Geste aus. Nutzer: `CircleLayer` (blauer Punkt, weißer Rand) + Blickkegel als `FillLayer`-Polygon (60° Öffnung, 30 m Radius, halbtransparent blau) um `headingDeg`. `treasure != null` → roter `CircleLayer`-Punkt. `followUser` → Kamera folgt `user` (Zoom 17). Start: `initialCenter` (Zoom 17) oder Deutschland-Mitte `LatLon(51.16, 10.45)` (Zoom 5). Quellenangabe sichtbar (MapLibre-Attribution an).

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun noSymbolLayers() { val layers = JSONObject(MapStyle.json()).getJSONArray("layers")
    for (i in 0 until layers.length()) assertNotEquals("symbol", layers.getJSONObject(i).getString("type")) }
@Test fun noLabelOrPoiSourceLayers() { val forbidden = setOf("poi", "place", "transportation_name", "water_name", "aerodrome_label", "mountain_peak", "housenumber")
    val layers = JSONObject(MapStyle.json()).getJSONArray("layers")
    for (i in 0 until layers.length()) assertFalse(layers.getJSONObject(i).optString("source-layer") in forbidden) }
@Test fun hasRoadsGreenWaterBuildings() { val src = (0 until JSONObject(MapStyle.json()).getJSONArray("layers").length())
        .map { JSONObject(MapStyle.json()).getJSONArray("layers").getJSONObject(it).optString("source-layer") }.toSet()
    assertTrue(src.containsAll(setOf("transportation", "landcover", "park", "water", "building"))) }
@Test fun usesOpenFreeMap() = assertEquals("https://tiles.openfreemap.org/planet",
    JSONObject(MapStyle.json()).getJSONObject("sources").getJSONObject("omt").getString("url"))
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*MapStyleTest'` — Expected: FAIL.
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Gleicher Befehl — Expected: PASS; `./gradlew assembleDebug` — BUILD SUCCESSFUL.

---

### Task 10: Bildschirme und Navigation

**Files:**
- Create: `app/src/main/java/com/trackingtransmitter/ui/TreasureListScreen.kt`, `ui/NewTreasureScreen.kt`, `ui/SearchScreen.kt`, `ui/SearchViewModel.kt`, `ui/Permissions.kt`
- Modify: `MainActivity.kt` — `NavHost` mit Routen `list`, `new`, `search/{treasureId}`; `volumeControlStream = AudioManager.STREAM_MUSIC`

**Interfaces:**
- Consumes: `TreasureStore`, `Treasure` (Task 5); `SearchController`, `SearchState`, `Fix` (Task 6); `LocationSource`, `HeadingSource`, `HeadingSensor` (Task 7); `BeepPlayer`, `celebrate` (Task 8); `TreasureMap` (Task 9)
- Produces: `class SearchViewModel(app: Application, treasureId: String) : AndroidViewModel(app)` mit `val state: StateFlow<SearchState>`, `val user: StateFlow<LatLon?>`, `val heading: StateFlow<Double?>`, `val headingSensor: HeadingSensor`, `fun resume()`, `fun pause()`, `fun continueSearching()`. `resume()` sammelt `fixes()` → `controller.onFix`, `headings()` → `heading`, startet `BeepPlayer.start { heading.value?.let(controller::signal) }`; `pause()` bricht beides ab und `BeepPlayer.stop()`. Beim Übergang nach `Found`: `BeepPlayer.stop()` + `celebrate()`. `onCleared()` → `pause()`.

Texte (exakt):
- Liste: Titel „Schätze“; leer: „Noch kein Schatz versteckt.“; Knopf „Neuer Schatz“; pro Eintrag Name + Datum (`dd.MM.yyyy HH:mm`), Antippen startet „Suche starten“-Bestätigung über Knopf „Suche starten“ unter der Liste (aktiv bei Auswahl); Löschsymbol → Dialog „„%s“ löschen?“ mit „Löschen“ / „Abbrechen“.
- Neuer Schatz: Titel „Neuer Schatz“; Hinweis „Tippe auf die Karte, um den Schatz zu setzen.“; Knopf „Meine Position verwenden“ (setzt Marker auf letzten Fix bzw. wartet auf ersten Fix); Textfeld „Name“, vorbelegt mit `nextDefaultName()`; Knopf „Speichern“ (aktiv, wenn Marker gesetzt und Name nicht leer) → zurück zur Liste.
- Suche: Karte mit `treasure = null`, `followUser = true`. `WaitingForSignal` → Overlay „Suche Signal…“. `Found` → Overlay „Gefunden! Der Schatz ist hier irgendwo.“ mit „Fertig“ (→ Liste) und „Weitersuchen“ (`continueSearching()`, `BeepPlayer` neu starten). Unten Knopf „Zum Beenden gedrückt halten“: beendet nach 1 s Druck (`detectTapGestures(onLongPress)`). System-Zurück (`BackHandler`) → Dialog „Suche beenden?“ mit „Beenden“ / „Weitersuchen“. `headingSensor == NONE` → statt Suche Hinweis „Dieses Handy hat keinen Kompass – die Peilung funktioniert hier nicht.“ Bildschirm bleibt an (`LocalView.current.keepScreenOn = true` solange sichtbar). Lifecycle: `ON_START` → `resume()`, `ON_STOP` → `pause()`.
- Berechtigung (`Permissions.kt`, `rememberLauncherForActivityResult(RequestMultiplePermissions)` für FINE+COARSE, abgefragt beim Öffnen von „Neuer Schatz“ und „Suche“): verweigert → „Ohne Standortfreigabe kann die Suche nicht starten.“ + Knopf „Freigeben“. „Neuer Schatz“ funktioniert ohne Freigabe weiter (nur „Meine Position verwenden“ ist dann deaktiviert).

- [ ] **Step 1:** Implementieren.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest assembleDebug` — Expected: alle Tests PASS, BUILD SUCCESSFUL.
- [ ] **Step 3: Geräteprüfung** (`adb install -r app/build/outputs/apk/debug/app-debug.apk`, falls Gerät/Emulator verfügbar; sonst als offen melden): Schatz per Tippen setzen, App neu starten → Schatz noch da; Suche zeigt keinen Schatzmarker; Drehen ändert Pieptempo sofort; Bildschirm aus → Stille, wieder an → Piepen; Zurück-Taste fragt nach; Kartenausschnitt ohne Schrift/Symbole. Ergebnis ehrlich berichten.

---

### Task 11: Release-Pipeline und Signaturschlüssel

**Files:**
- Create (wie walkwalk, Namen angepasst): `.github/workflows/android-release.yml`, `.releaserc.json`, `package.json` (`"name": "trackingtransmitter-release"`), `scripts/build-release-apk.sh`, `docs/android-release.md` (Download-Link `https://github.com/mazzok/trackingtransmitter/releases/latest/download/app-release.apk`, Alias `trackingtransmitter`, Keystore-Ablage `D:\GIT\keys\trackingtransmitter\`)
- Run `npm install` einmal, um `package-lock.json` zu erzeugen (CI nutzt `npm ci`).
- Außerhalb des Repos: `D:\GIT\keys\trackingtransmitter\trackingtransmitter-release.jks`, `D:\GIT\keys\trackingtransmitter\README.txt` (Passwort, Alias, SHA-256)

- [ ] **Step 1:** Zufallspasswort erzeugen (≥ 32 Zeichen, `openssl rand -base64 32`), Keystore erzeugen:
  `keytool -genkeypair -v -keystore D:/GIT/keys/trackingtransmitter/trackingtransmitter-release.jks -storetype PKCS12 -alias trackingtransmitter -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=trackingtransmitter" -storepass <pw> -keypass <pw>`; Passwort, Alias und SHA-256 (`keytool -list -v … | grep SHA256`) in `README.txt` daneben schreiben. Passwort nicht ins Terminal-Protokoll/Antwort ausgeben.
- [ ] **Step 2:** Lokale Release-Probe (Git Bash, `ANDROID_HOME` gesetzt, die vier Signatur-Env-Variablen + `EXPECTED_CERT_SHA256` gesetzt): `bash scripts/build-release-apk.sh 0.0.1` — Expected: „Built dist/app-release.apk (0.0.1)“; `dist/` danach löschen.
- [ ] **Step 3:** Secrets/Variablen setzen (Nutzer hat das freigegeben):
  `base64 -w0 <jks> | gh secret set ANDROID_KEYSTORE_BASE64 -R mazzok/trackingtransmitter`, `gh secret set ANDROID_KEYSTORE_PASSWORD`, `gh secret set ANDROID_KEY_PASSWORD` (Werte über stdin), `gh variable set ANDROID_SIGNING_ALIAS --body trackingtransmitter`, `gh variable set ANDROID_CERT_SHA256 --body <sha256>`.
  Verify: `gh secret list -R mazzok/trackingtransmitter` zeigt die drei Secrets; `gh variable list` die zwei Variablen.
- [ ] **Step 4:** `git status --porcelain` prüfen: keine `.jks`, kein `dist/`, kein `node_modules/` auftaucht. Nichts committen. Dem Nutzer melden: Der erste CI-Lauf startet erst, wenn er pusht, und erzeugt nur dann ein Release, wenn ein Commit `feat:`/`fix:` ist.

---

## Spec-Abdeckung (Selbstprüfung)

| Spec-Anforderung | Task |
|---|---|
| Schatz per Tippen / eigener Position, Name, Liste, Löschen mit Bestätigung | 5, 10 |
| Suchmodus: Position + Blickrichtung, kein Schatz/Entfernung/Pfeil | 6, 9, 10 |
| Tempo = Richtung, Tonhöhe = Entfernung, Nahbereich < 15 m, Ziel ≤ 5 m | 3, 6, 8 |
| Fehlauslösung vermeiden, „Fertig“/„Weitersuchen“ | 3, 6, 10 |
| „Suche Signal…“, Bildschirm an, laut | 6, 8, 10 |
| Langes Drücken zum Beenden, Zurück fragt nach | 10 |
| Rotationsvektor + Deklination + flach/aufrecht + Rückfälle, kein Kompass → Hinweis | 4, 7, 10 |
| Ortung ohne Play Services | 7 (Plattform-`FUSED_PROVIDER`) |
| Reduzierte OSM-Karte ohne Symbole/Schrift, Quellenangabe | 9 |
| APK bei Push auf `main`, eigener Keystore, Ablage außerhalb Repo | 11 |
| Lokale Speicherung, Deutsch | 5, 10 |
