# Raketen-Booster – Propwash: Mob Defense

Grundlage: `docs/design.md` §2.1.3 (Raketen-Booster), §2.4 (Aktion 3), §2.9 (Server, Zuschauer), §4.1 (Pakete), §4.2
(Drohnen-Daten `mobdefense:booster`), E10 (2 g, Brenndauer nach Flugdauer, Sterne explodieren), E11 (genau eine Rakete).
Dieses Dokument beschreibt den Strang `md-booster`: Verhalten, Rechnung, Daten, Darstellung und Tests.

---

## 1. Was Spieler sehen

- Der Raketen-Booster sitzt **oben** (`top`), 28 g leer, und hält **genau eine Feuerwerksrakete** (Tag
  `mobdefense:booster_accepts`, Stapel 1). Eine zweite Rakete, Pfeile oder andere Items nimmt der Slot nicht. Beladen und
  Nachladen nur in der Wartung (Schleichen und Rechtsklick auf die abgestellte Drohne, Knopf „Zubehör“).
- Die Rakete liegt sichtbar in der Wiege (B2), **Spitze nach vorn** am Bugstopp, Zündschnur hinten am Zünder. Alle Zuschauer
  sehen sie, in der eigenen FPV-Ansicht nicht.
- **Zubehör-Aktion 3** („Booster zünden“, Standard Ziffernblock Enter) zündet, solange die Drohne fliegt (Pilot oder
  autonomer Flug). Der Tab „Modi“ und der Hinweis beim ersten Flug nennen die Aktion über `actionNameKey(3)`.
- Schub **entlang der Nase**, für jede Drohnengröße dieselbe Beschleunigung **2 g** (`F = m × 19,6 N`), höchstens **60 N**
  (ab etwa 3,06 kg wird es weniger als 2 g). Brenndauer nach der Flugdauer der Rakete: **1 / 2 / 3 → 1,25 / 1,75 / 2,25 s**
  (andere Werte werden auf 1–3 begrenzt). Eine 5″ mit 0,65 kg wird in 1,25 s rechnerisch 24,5 m/s schneller, im Spiel durch
  den Luftwiderstand etwas weniger.
- Beim Brennen Funken und Rauch am Heck der Rakete, Ton `entity.firework_rocket.launch` beim Zünden. Das OSD-Element
  `mobdefense:booster` zeigt `RKT RDY`, die Restbrenndauer (`RKT 1.2`) und `RKT ---` (Modul `ui`).
- Am Ende ist der Slot leer. Eine Rakete **ohne Sterne** verpufft. Eine Rakete **mit Sternen** explodiert wie an der Elytra:
  Vanilla-Feuerwerk mit Farben und Knall, Schaden `5 + 2 × Sterne` an der Drohne und an Lebewesen im Umkreis von 5 m mit
  Sichtlinie (`Schaden × √((5 − Abstand) / 5)`). Propwash zerlegt eine Drohne ab 6 Schaden – schon eine Rakete mit einem
  Stern zerbricht die eigene Drohne (Elytra-Regel: zum Fliegen Raketen ohne Sterne nehmen).
- Spieler trifft die Explosion nur mit der Welt-Regel `payloads_hit_players` und Vanilla-PvP; der Pilot selbst nie.
- **Pilotenwechsel** (Pilot steigt aus, ein anderer übernimmt, ein Pilot übernimmt den autonomen Flug) beendet den Booster
  sofort: kein Schub mehr, keine Explosion, die Rakete ist verbraucht, ein kurzes Zischen (`block.fire.extinguish`).
- Nimmt jemand die Rakete während des Brennens aus der Wiege oder tauscht sie, endet der Booster ebenso sofort, ohne Schub
  und ohne Explosion; verbraucht wird nur die gezündete Rakete.
- Beim Zünden vergibt Mob Defense den Fortschritt `mobdefense:afterburner` („Nachbrenner“), falls er geladen ist.

---

## 2. Technik

### 2.1 Dateien

| Datei | Inhalt |
|---|---|
| `booster/core/BoostProfile` | portabel: Brenndauer in Ticks (`15 + 10 × Flugdauer`), Schub, Beschleunigung, Δv, Impuls, Restbrenndauer, Explosionsschaden mit Abfall, 60 g je Rakete |
| `booster/core/BoosterPose` | portabel: Lage der Rakete in der Wiege (Achse 15,9 mm über dem Anbaupunkt, Bugstopp bei z −29 mm, Zünder bei z +27,2 mm, Maßstab 0,43), Heck und Mitte für Schub und Explosion |
| `booster/BoosterBehavior` | `AccessoryBehavior`: 1 Slot, Stapel 1, Beladung 60 g je Rakete, `actionNameKey(3)`, `display(0)`, Aktion 3 zündet, `tick` |
| `booster/Boosters` | Anmeldung (Verhalten, Drohnen-Daten, `PILOT_CHANGED`, Aufräumen), Zünden, Schub je Tick, Ende, Abbruch |
| `booster/BoostState` | Drohnen-Daten `mobdefense:booster` mit Codec und Schlüssel |
| `booster/BoosterBlast` | Explosion der Sterne (Vanilla-Feuerwerk, Schaden an Drohne und Lebewesen) |
| `booster/BoosterEffects` | Funken, Rauch, Verpuffen, Töne |

Anmeldung in `MobDefense.init` (eine Zeile `booster.Boosters.init()`); Texte im Sprachmodul `common/src/lang/booster/`
(`mobdefense.action.booster.ignite`, `mobdefense.help.rocket_booster.what.1–4`; die Bedienzeilen `use.1–3` liegen im Modul
`ui`).

### 2.2 Ablauf

1. **Zünden** (`onAction`, Aktion 3 gedrückt): nur wenn die Drohne fliegt (`AccessoryContext#flying`), kein Booster brennt und
   im Slot eine Rakete liegt. Flugdauer und Sterne aus der Komponente `minecraft:fireworks`, Masse aus `DroneView#massKg`
   (mit Beladung, also samt Rakete), Schub `BoostProfile.thrustNewtons(m)`. Die Rakete bleibt bis zum Ende in der Wiege.
2. **Schub je Server-Tick** über `DronePhysics#push(F, frameRelative = true, Arm, 0,05 s)`: Kraft `(0, 0, −F)` in Rahmen-
   Richtung, also immer entlang der Nase, auch wenn die Drohne sich dreht. Zusammen genau `Brenndauer × F` Impuls (25/35/45
   Schübe), der erste im Tick der Zündung. Propwash schickt den Schub an das Flugmodell des Piloten (im autonomen Flug an das
   Server-Flugmodell). Weil der Server jeden Tick neu schiebt, endet der Schub bei einem Abbruch sofort.
3. **Angriffspunkt** ist das Heck der Rakete auf der Längsachse durch den Schwerpunkt (`x = 0`, `y = 0`, `z` = Abstand des
   Raketenhecks hinter dem Schwerpunkt aus `MountPose` und `DroneView`). Die Höhe der Halterung (am 5-Zöller etwa 5 cm über
   dem Schwerpunkt) bleibt bewusst außen vor: Sie gäbe 0,6 N·m Nickmoment nach unten, die Drohne würde beim Zünden abtauchen.
   Gewollt ist das Elytra-Gefühl (gleiche Beschleunigung, kein Kippen).
4. **Ende** nach der Brenndauer: Rakete aus dem Slot, Daten zurück, Explosion bzw. Verpuffen. Gründe im Speicher
   (`Boosters.lastEnding`): `BURNED_OUT`, `PILOT_CHANGED`, `REMOVED`, `LOST` (Server neu gestartet, während der Booster
   brannte: Rakete verbraucht, keine Explosion).

### 2.3 Drohnen-Daten `mobdefense:booster` (B6, `Sync.TRACKING`)

| Feld | Typ | Inhalt |
|---|---|---|
| `ends_at` | long | Spielzeit, zu der der Booster ausbrennt; 0 = kein Booster |
| `flight` | byte | Flugdauer 1–3 |
| `stars` | byte | Zahl der Sterne |
| `thrust_n` | float | Schub in N |

Die Werte sind 14 Byte groß, als NBT mit Feldnamen etwa 54 Byte (Propwash erlaubt 2048 je Schlüssel). Nach dem Ende wird der
Schlüssel entfernt. Das OSD (Modul `ui`, `client.OsdReader`) liest nur `ends_at`; Zuschauer bekommen die Daten ebenfalls, ein
OSD zeichnet Propwash aber nur, wer das Videobild hat.

### 2.4 Darstellung

- `display(0)` = `ItemDisplay((0; 13,75; −3,2) mm, Gieren 90°, Nicken 0°, Rollen −90°, Maßstab 0,43)`: Das Feuerwerks-Sprite
  (13 Pixel lang, Spitze oben) liegt waagerecht in der Wiege, Spitze am Bugstopp, Zündschnur am Zünder. Die Lage gilt in den
  Achsen der Halterung; am 5-Zöller ist die obere Halterung samt Wiege 22° nach oben geneigt (`rocket_booster.json` ohne
  `hangs_straight`), die Rakete liegt in der geneigten Wiege.
- **Funken und Rauch** schickt der Server je Tick am Heck der Rakete (`ServerLevel#sendParticles`, je Tick ein Feuerwerksfunke
  und eine Rauchwolke nach hinten, wie bei einer Vanilla-Rakete). Abweichung vom Design (dort „Client, aus dem Zustand in B6“): Der Client kennt die Lage der oberen Halterung
  nicht (`MountPose` gibt es nur im `AccessoryContext` am Server), das Heck ließe sich am Client nur schätzen. Kosten: zwei
  Partikel-Pakete je Tick für Spieler im Umkreis von 32 Blöcken, höchstens 45 Ticks je Zündung.
- **Explosion:** Der Server erzeugt am Ende eine Vanilla-Feuerwerksrakete mit dem Stapel aus der Wiege, schickt ihr das
  Ereignis 17 (Feuerwerk mit Farben und Knall an allen Clients) und entfernt sie im selben Tick wieder. Sie dient auch als
  direkte Ursache des Schadens (`minecraft:fireworks`, Angreifer = Pilot; an der eigenen Drohne ohne Angreifer, sonst würde
  Propwash sie wie einen Schlag des Piloten sofort zerlegen).

---

## 3. Tests

### 3.1 JUnit

| Test | Prüft |
|---|---|
| `booster.core.BoostProfileTest` | Brenndauer 1,25/1,75/2,25 s, Klemmen der Flugdauer, `F = m × 19,6`, gleiche Beschleunigung für 25 g bis 3 kg, Grenze 60 N, 24,5 m/s für 0,65 kg, ungültige Massen, Restbrenndauer, Explosionsschaden mit Abfall, 60 g |
| `booster.core.BoosterPoseTest` | Rakete zwischen Bugstopp und Zünder, Achse = Achse der Wiege, Heck hinter der Mitte, Grenzen von `ItemDisplay` |
| `booster.BoostStateTest` | Felder `ends_at`, `flight`, `stars`, `thrust_n` wie im Design und im OSD-Vertrag, Größe, Grenzen; Verhalten mit einem Slot, Name nur für Aktion 3, Anzeige nur für Slot 0 |

### 3.2 Selbsttest (Modul `booster`, Filter `-PmobdefenseSelftestOnly=booster/`)

| Schritt | Erwartung |
|---|---|
| S9 Booster – zweite Rakete abgelehnt, Zündung … | Wartung → Zubehör: zwei Raketen umschichten → 1 im Booster, die zweite bleibt im Inventar, Pfeil und Dreizack passen nicht; autonomer Schwebeflug 4 m, Zündung über Aktion 3: Daten `flight` 1, 25 Ticks, `thrust_n` = m × 19,6; zweite Zündung während des Brennens wirkungslos; im Flugmodell gemessen Beschleunigung der ersten 0,5 s 85–110 % von 2 g, Tempo-Zuwachs 70–105 % der Rechnung (Luftwiderstand); 25 Schübe, Slot leer, Daten zurück |
| S9 Booster – Funken am Heck (Foto), Rakete mit Sternen … | Schwebeflug 3 m mit einer Rakete ohne Sterne (Flugdauer 3), feste Kamera schräg hinter der Drohne (1,6 m hinter, 1,7 m seitlich, 0,45 m über ihr); Foto `booster_burn` (EN/DE) der davonfliegenden Drohne mit Funken und Rauch am Heck; nach 45 Schüben verpufft sie. Dann im Flug eine Rakete mit einem Stern nachladen (Server), ein Zombie folgt der Drohne in etwa 2,4 m Abstand, Zündung: nach 45 Schüben Explosion, Drohne 7 Feuerwerks-Schaden (zerbricht), Zombie verliert den Abfall-Wert ±1,5 |
| S9 Booster – Pilotenwechsel beendet den Booster … | Spieler fliegt die Drohne (FPV), zündet eine Sternen-Rakete, steigt nach 6 Ticks aus: Booster sofort aus, Daten zurück, Ende `PILOT_CHANGED`, Rakete verbraucht; nach der vollen Brenndauer keine Explosion, Drohne heil, Zombie unverletzt |

Server-Prüfung für D1 (`runServerSmoke`): Verhalten angemeldet, Aktion 3 mit Namen, Drohnen-Daten `TRACKING`, Tag nimmt nur
`minecraft:firework_rocket`, Listener auf `PILOT_CHANGED`.

### 3.3 D4 am dedizierten Server (Zuschauer sieht Speer und Rakete, kein fremdes OSD)

Mit `MOBDEFENSE_D4=1` baut der Server-Rauchtest die Szene 20 Blöcke nordwestlich des Weltspawns (abseits der Aufbauten
anderer Module): Chunks dauerhaft laden (Tick 10), Drohne mit Raketen-Booster und Speer-Halterung auf einem Sockel, Rakete
oben, Eisenspeer unten (Tick 40). Jeder Spieler, der beitritt, wird 1,4 m seitlich neben die Drohne gestellt und sieht zu
ihr. Der Beitritts-Client mit `MOBDEFENSE_D4=1` meldet ein Probe-Element im OSD an (an, Spalte 0, Zeile 0) und prüft
24 Ticks nach dem Betreten: fremde Drohne (nicht vom Zuschauer geflogen), Zuschauer fliegt nicht, Rakete und Speer am
Client im Slot mit B2-Anzeige, Booster-Daten lesbar, das Probe-Element wurde nie gezeichnet. Ergebnis
`runs/smoke-client/jointest/d4-spectator.txt` (PASS/FAIL), das Bildschirmfoto des Beitrittstests (`jointest/<fall>.png`)
zeigt die Drohne aus Sicht des Zuschauers.

Grenze: Die Drohne hat keinen Piloten. Propwash gibt eine Drohne sofort frei, deren Pilot nicht in der Spielerliste steht;
ein Testspieler ohne Client bleibt deshalb kein Pilot, und autonomer Flug verlangt ebenso einen Besitzer in der
Spielerliste. Ein fremder Pilot braucht einen zweiten echten Client (geschützter Fall mit zwei Clients).

```sh
# Server (hält bis jointest.stop)
MOBDEFENSE_D4=1 MOBDEFENSE_JOINTEST_HOLD=1800 <slot-skript> ./gradlew :fabric:runServerSmoke
# Zuschauer
MOBDEFENSE_D4=1 MOBDEFENSE_JOINTEST=127.0.0.1:<port> MOBDEFENSE_JOINTEST_LABEL=d4-zuschauer MOBDEFENSE_JOINTEST_EXPECT=joined \
  <slot-skript> ./gradlew :fabric:runClientSmoke
cat fabric/runs/smoke-client/jointest/d4-spectator.txt
touch fabric/runs/smoke-server/jointest.stop
```

Ohne gemergten Strang `md-spear` hat die Speer-Halterung kein Verhalten; dann prüft D4 nur die Rakete und sagt das im Ergebnis.

---

## 4. Stand der Prüfung

Strang `md-booster` auf `main` mit `md-osd-ux`, `md-mobs-1`, `md-spear` und `md-dispenser` (Propwash 0.6.0-dev.4):

- `:common:test` 158 von 158 grün (davon 18 aus `booster`), `checkPortable` grün, `build` beider Loader grün,
  `tools/fastcheck.sh test` und `all` OK, `strip_comments` ohne Änderung.
- Selbsttest `booster/` Fabric 4 von 4 PASS mit Fotos `booster_burn` EN/DE, NeoForge 4 von 4 PASS. Gemessen im
  Flugmodell der 5″ (0,613 kg, 12,02 N): Beschleunigung der ersten 0,5 s 18,6 m/s² (95 % von 2 g), Tempo-Zuwachs nach
  1,25 s 20,6 m/s (84 % der Rechnung 24,5 m/s, der Rest ist Luftwiderstand), 25 Schübe. Sterne: Drohne 7 Feuerwerks-Schaden
  (zerbricht), Zombie in 2,41 m −4,94 (Rechnung 5,04). Pilotenwechsel nach 11 von 45 Schüben: sofort aus, keine Explosion.
- Server-Rauchtest Fabric PASS (26 Prüfungen, keine Client-Klasse, kein ERROR) mit D4: Zuschauer sieht Rakete und Speer mit
  B2-Anzeige in 1,7 m, kein OSD gezeichnet.
