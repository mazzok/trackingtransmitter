# Schatzpeiler – Design

Stand: 2026-11-12 · Repo: `trackingtransmitter` (github.com/mazzok/trackingtransmitter)

## Wozu

Schatzsuche auf einem Kindergeburtstag. Ein Elternteil versteckt vorher einen Schatz
und speichert die Stelle als Wegpunkt („Cache“) in der App. Bei der Party bekommen die
Kinder das Handy, wählen den Schatz aus und starten die Suche. Das Handy verhält sich
dann wie ein **Peilsender für Wildtiere mit Halsbandsender**: Zeigt es in Richtung
Schatz, piept es schnell, dreht man sich weg, piept es langsam. Die Kinder sehen auf
einer Karte, wo sie stehen und wohin sie schauen. **Wo der Schatz liegt, sehen sie nicht.**

Die Richtung zum Schatz wird aus GPS-Position und Zielkoordinate berechnet (Peilung).
Mit dem Lage-/Kompasssensor des Handys reagiert der Ton sofort auf jede Drehung. Das
macht die Suche schneller und genauer, als nur auf GPS-Bewegung zu warten.

## Nutzer

- **Elternteil (Spielleiter):** legt Schätze an, verwaltet sie, gibt das Handy weiter.
- **Kinder (ca. 6–12 Jahre):** eine Gruppe teilt sich **ein** Handy und sucht genau einen Schatz.

## Ziele

1. Schatz als Wegpunkt speichern, auf zwei Arten:
   - durch **Tippen auf die Karte**, ohne vor Ort zu sein (Hauptweg),
   - an der **eigenen aktuellen Position** (Zusatz, falls man beim Verstecken vor Ort ist).
2. Gespeicherte Schätze als Liste. Einen auswählen und den Suchmodus starten.
3. Im Suchmodus: Karte mit eigener Position und **Blickrichtung** (Kegel/Pfeil), kein
   Schatzmarker, keine Entfernung, kein Zielpfeil.
4. Peilton nach dem Vorbild eines Tierpeilgeräts (Details unten).
5. Bei Ankunft (ca. 5 m): Vibration und Jubelton, Suche beendet.
6. Bei jedem Push auf `main` baut eine GitHub Action automatisch eine **signierte APK**
   und veröffentlicht sie als GitHub Release.

## Nicht-Ziele

- Kein Play-Store-Eintrag.
- Keine Mehrspieler-/Mehrgerätefunktion, kein Server, kein Konto.
- Keine Schatzketten oder mehrere Ziele in einer Suche.
- Keine Genauigkeit unter GPS-Niveau: Die letzten Meter suchen die Kinder mit den Augen.

## Anti-Ziele (wäre ein Fehlschlag, auch wenn es technisch läuft)

- Die Kinder können den Schatz auf dem Bildschirm ablesen (Marker, Entfernungszahl,
  Zielpfeil). Dann ist das Piepen überflüssig.
- Der Ton reagiert träge oder springt beim Drehen. Das Gefühl „drehen → sofort hören“
  ist der Kern.
- Die Karte ist überladen mit Wanderwegen, Haltestellen, Symbolen und Beschriftungen.

## Ablauf

1. **Start / Schatzliste:** Liste gespeicherter Schätze (Name, Erstellzeit). Aktionen:
   „Neuer Schatz“, Schatz löschen, Schatz wählen → „Suche starten“.
2. **Neuer Schatz:** Karte, Tippen setzt/verschiebt den Marker. Knopf „Meine Position
   verwenden“. Name eingeben (Vorschlag: „Schatz 1“, „Schatz 2“ …). Speichern.
   In diesem Modus (Elternansicht) ist der Marker natürlich sichtbar.
3. **Suchmodus:** zuerst „Suche Signal…“, bis die GPS-Genauigkeit brauchbar ist; dann
   startet das Piepen. Karte folgt der Position; Blickrichtung wird angezeigt.
   Bildschirm bleibt an. Zurück-Knopf beendet die Suche.
4. **Gefunden:** Vibration + Jubelton, Anzeige „Gefunden! Der Schatz ist hier irgendwo.“,
   Piepen stoppt. Knöpfe „Fertig“ (zurück zur Schatzliste) und „Weitersuchen“ (Suche
   läuft weiter, falls der Jubel zu früh kam). *(Claudes Wahl)*

**Fehlauslösung vermeiden** *(Claudes Wahl)*: „Gefunden“ löst erst aus, wenn mehrere
aufeinanderfolgende Positionen innerhalb der Zielschwelle liegen und deren gemeldete
Genauigkeit brauchbar ist. Ein einzelner verrauschter Fix reicht nicht.

**Schutz vor versehentlichem Bedienen durch die Kinder** *(Claudes Wahl)*: Löschen
eines Schatzes nur mit Bestätigung. Die Suche wird über langes Drücken auf „Beenden“
verlassen, nicht durch einen einzelnen Tipp; die System-Zurück-Taste fragt nach.
Keine PIN/Elternsperre.

## Peilton (Verhalten)

Zwei Größen, getrennt hörbar:

- **Richtungsabweichung** Δ = |Peilung zum Schatz − Blickrichtung des Handys| (0–180°)
  steuert das **Tempo**: Δ = 0° → schnell, Δ = 180° → langsam. Der Verlauf ist stetig
  (kein Umschalten in Stufen), damit man beim Drehen das Maximum „ertasten“ kann.
- **Entfernung** steuert die **Tonhöhe**: je näher, desto heller. So hört man auch beim
  Wegdrehen, ob man insgesamt näherkommt.
- **Nahbereich (< ~15 m):** Das Tempo steigt zusätzlich unabhängig von der Richtung,
  bis zu sehr schnellem Piepen kurz vor dem Ziel. So nah ist die GPS-Peilung unzuverlässig.
- **Ziel (≤ ~5 m):** Vibration + Jubelton, Ende.

Ton laut über den Lautsprecher (Medienlautstärke), kurze, klare Pieptöne, wie bei
einem Peilempfänger.

## Wie es gebaut wird

Vom Nutzer entschieden:
- Native **Android-App**.
- Karte auf Basis von **OpenStreetMap**, mit Straßen, Wegen, Grünland, Wasser und
  Gebäuden. **Ohne** Wanderwege-Overlay, Haltestellen, Symbole und Beschriftungen.
- Aufbau orientiert sich an `D:\GIT\walkwalk`.
- **CI:** Push auf `main` → GitHub Action baut eine signierte APK. Claude darf das
  benötigte Secret anlegen.

Claudes Wahl (vom Nutzer bisher nicht widersprochen):
- **Kotlin + Jetpack Compose + MapLibre Android** wie in walkwalk.
- **Kartenquelle: OpenFreeMap-Vektorkacheln** (OSM-basiert, kostenlos, kein Schlüssel)
  mit einem eigenen, reduzierten Stil: nur Flächen (Grünland, Wald, Wasser), Straßen/Wege,
  Gebäude. Keine Symbol- und Textebenen. Die OpenTopoMap-Rasterkacheln aus walkwalk
  scheiden aus, weil Symbole und Namen dort fest eingezeichnet sind.
  Quellenangabe (© OpenStreetMap contributors, OpenFreeMap) bleibt sichtbar.
- Von walkwalk übernommen: Karten-Setup (`MapSetup`: OkHttp-Client mit eigenem
  User-Agent, Kachel-Cache), Projektstruktur, Gradle-Setup.
- **Blickrichtung:** Android-Sensor `TYPE_ROTATION_VECTOR` (fusioniert Gyro, Kompass,
  Beschleunigung), Azimut korrigiert um die Deklination (`GeomagneticField`).
  Funktionieren muss es mit dem Handy flach in der Hand und aufrecht vor sich.
  Fehlt der Sensor (Handy ohne Gyroskop), Rückfall auf `TYPE_GEOMAGNETIC_ROTATION_VECTOR`
  bzw. Beschleunigung + Magnetometer mit stärkerer Glättung. Ohne Magnetometer zeigt die
  App einen klaren Hinweis, dass die Peilung auf diesem Gerät nicht geht. *(Claudes Wahl)*
- **Position:** Fused Location Provider in hoher Genauigkeit; ohne Google Play Services
  Rückfall auf `LocationManager` (GPS). *(Claudes Wahl)*
- **Speicherung:** lokal auf dem Gerät (z. B. DataStore/JSON oder Room – Builder entscheidet).
- **Ton:** in der App erzeugt (Tongenerator/`AudioTrack`) oder kurze mitgelieferte
  Sounddateien; Jubelton als mitgelieferte Datei.
- **Release-Pipeline wie walkwalk** (`.github/workflows/android-release.yml`): Unit-Tests,
  semantic-release, signierte APK als GitHub Release. Secrets gleich benannt
  (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`,
  Variable `ANDROID_SIGNING_ALIAS`, `ANDROID_CERT_SHA256`). **Eigener neuer Keystore**
  für diese App, nicht der von walkwalk.
- **UI-Sprache:** Deutsch.

## Randbedingungen

- **Zielgerät:** ein etwa ein Jahr altes Android-Handy, laut Nutzer vermutlich Android 17.
  Gyroskop, Magnetometer und Google Play Services werden als vorhanden angenommen;
  die Rückfälle oben sind Absicherung, nicht Optimierungsziel. `minSdk 34` wie in walkwalk
  reicht.
- **Signaturschlüssel:** neuer Keystore samt Passwörtern liegt außerhalb des Repos unter
  `D:\GIT\keys\trackingtransmitter\`; der Nutzer sichert ihn selbst. Er darf nie ins Repo.
- Draußen, oft unter Bäumen: GPS-Genauigkeit typisch 3–10 m. Schwellen (5 m Ziel,
  15 m Nahbereich) müssen damit leben.
- Ohne Netz keine neuen Kacheln. Bereits angesehene Kacheln kommen aus dem Cache.
- Berechtigungen: Standort (genau), Vibration, Internet.
- Git: Commits und Pushes nur nach ausdrücklicher Freigabe durch den Nutzer.

## Dem Builder überlassen

- Genaue Kurven für Tempo und Tonhöhe sowie die Glättung des Sensorsignals,
  solange das Verhalten oben eingehalten wird.
- Exakte Schwellenwerte (um 5 m / 15 m herum) und die GPS-Genauigkeit, ab der die
  Suche startet.
- Kartenfarben und Stil im Detail, Darstellung des Blickrichtungskegels.
- Speichertechnik, Paketname, App-Icon.
