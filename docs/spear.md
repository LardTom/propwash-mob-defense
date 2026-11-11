# Speer-Halterung – nur Speere, Schaden wie Vanilla × Regel, Anzeige über B2

Grundlage: `docs/design.md` §2.1.2, §2.3, §2.9, §4.1, §6 und die Entscheidungen E8 und E9. Dieses Dokument beschreibt,
was das Modul `spear` tut, wo es liegt und wie es geprüft wird.

---

## 1. Dateien

| Datei | Inhalt |
|---|---|
| `spear/Spears.java` | Anmeldung (`Spears.init()` aus `MobDefense.init()`), Tag `mobdefense:spear_mount_accepts`, Prüfung „ist ein Speer“, Werte der Kinetik-Komponente als `SpearDamage.Stats` |
| `spear/SpearBehavior.java` | `AccessoryBehavior` der Speer-Halterung: Slots, Annahme, Stapel, Beladung, Anzeige, Tick |
| `spear/SpearStrikes.java` | Treffer am Server: Strecke der Speerspitze je Tick, Stich beim Rammen (Körpertreffer von Propwash), Ziele, Schaden, Rückstoß, Abwerfen, Haltbarkeit |
| `spear/SpearFeedback.java` | Rückmeldung eines Treffers: Ton für Umstehende und direkt für den Piloten, Partikel, OSD-Hinweis `HIT`/`TREFFER` |
| `spear/core/SpearDamage.java` | portabel: Vanilla-Formel, Anteil, Obergrenze, Schwellen |
| `spear/core/SpearMass.java` | portabel: Gewichte der sieben Vanilla-Speere |
| `spear/core/SpearPose.java` | portabel: Lage des Speers an der Halterung (Anzeige und Spitze) |
| `spear/core/ContactMemory.java` | portabel: Sperre je Ziel für `contact_cooldown_ticks` |
| `spear/core/SpearRam.java` | portabel: liegt ein Ziel beim Rammen vor der Nase (höchstens 60° neben der Nase) |
| `common/src/lang/spear/` | Hilfetexte der Halterung (`mobdefense.help.spear_mount.what.N`; die Bedienzeilen `.use.N` kommen aus dem Modul `ui`, siehe `docs/osd-ux.md` §3) |
| `dev/spear/` | Server-Prüfungen (D1, D3 Speer) und die Schritte S8 (§7) |

Die `*.core`-Klassen kennen weder Minecraft noch Propwash (`:common:checkPortable`).

---

## 2. Nur Speere (E9)

`SpearBehavior#accepts(0, stapel)` gilt genau dann, wenn der Stapel im Tag `mobdefense:spear_mount_accepts` steht
(enthält `#minecraft:spears`) **und** die Komponente `minecraft:kinetic_weapon` trägt. Ein Slot, Stapel 1.

| Item | Ergebnis |
|---|---|
| die sieben Vanilla-Speere | angenommen |
| Dreizack, Schwerter, Stock, Streitkolben, Pfeil | abgelehnt (kein Tag) |
| Speer, dem die Kinetik-Komponente fehlt | abgelehnt |
| Stock mit Kinetik-Komponente | abgelehnt (kein Tag) |
| Speer aus einer anderen Mod mit Tag `#minecraft:spears` und Komponente | angenommen |

Propwash nutzt `accepts` im Wartungsmenü („Zubehör“) und beim Abgleich; die Halterung selbst prüft beim Treffer noch
einmal.

**Beladung** (`payloadGrams`): Holz 180 g, Stein 260 g, Kupfer 280 g, Eisen 300 g, Gold 340 g, Diamant 280 g,
Netherit 360 g. Für andere Speere liefert die Halterung nichts, dann rechnet Propwash mit seinem eigenen Item-Gewicht.

---

## 3. Anzeige (B2)

Den Speer zeichnet Propwash aus dem Slot: `SpearBehavior#display(0)` liefert ein festes `ItemDisplay` aus `SpearPose`.

| Wert | Bedeutung |
|---|---|
| Versatz (0; −10,34; −36) mm | Achse der Schellen aus dem Modell der Halterung (`tools/assets`, y −10,34 mm), Mitte des Speers 36 mm vor dem Anbaupunkt |
| Gieren −90°, Rollen 45° | das flache Item-Modell (Diagonale von links unten nach rechts oben) liegt längs unter dem Rahmen, Spitze nach vorn |
| Maßstab 0,6 | Item-Modell 96 mm, Speer etwa 127 mm lang |

Damit liegt das Ende des Speers 27,6 mm hinter dem Anbaupunkt (hintere Schelle bei +23,4 mm), die Spitze 99,6 mm davor.
Mit der Neigung der Halterung (30°) liegt die Spitze am `freestyle_5` etwa 147 mm vor der Mitte des Rahmens, auf Höhe
der Vorderkante der vorderen Propeller (etwa 145 mm), am `long_range_7` etwa 151 mm vor der Mitte, die Propeller reichen
dort bis etwa 194 mm. Die Spitze ist auch der Punkt, den der Server für Treffer prüft (`SpearStrikes#tip`,
`MountPose#at`), Anzeige und Treffer liegen also an derselben Stelle.

Die Anleitung EN/DE (§4) nennt das so: „etwa 10 cm vor der Mitte der Halterung – an einer 5″ etwa auf Höhe der vorderen
Propeller, an einer 7″ ragen die Propeller weiter nach vorn“ (Befund SPEAR3-2). `SpearGuideTipTest` prüft Zahl und
Vergleich gegen `SpearPose` und die Rahmen-Daten aus Propwash.

Abweichung vom Design (§2.1.2 „die Spitze ragt etwa 25 cm vor die Nase“): Bei Maßstab 0,6 ist der Speer nur etwa 127 mm
lang; 25 cm vor der Nase hinge er frei vor der Drohne und nicht mehr in den Schellen. Maßstab 0,6 und der Sitz in den
Schellen haben Vorrang. Damit der Speer beim Rammen trotzdem zuerst trifft, wie es die Spitze vor der Nase vorsieht,
sticht er auch beim Körpertreffer der Drohne (§4 Punkt 14, Befund AUTO20-2).

**Gerade hängen:** Propwash neigt Zubehör um die Neigung der Halterung, solange die Teil-Definition nicht
`"hangs_straight": true` setzt (am 5-Zöller unten 30°, am Whoop 90°). Die Neigung wirkt auf das Modell der Halterung, auf
die Anzeige des Speers und auf `MountPose`. Die Teil-Definition `data/mobdefense/propwash_parts/accessory/spear_mount.json`
gehört dem Modul `core`; siehe §8.

---

## 4. Treffer am Server

`SpearBehavior#tick` läuft am Server jeden Tick für jede Drohne mit Speer-Halterung (`AccessoryBehavior#tick`).

1. Ohne Speer mit Kinetik-Komponente im Slot: nichts, der gemerkte Stand der Drohne wird gelöscht.
2. **Strecke:** Spitze jetzt (`MountPose#at`) und Spitze im letzten Tick; fehlt der letzte Stand (erster Tick, Lücke über
   2 Ticks, anderes Level), gilt Spitze − Tempo × 0,05 s. Länger als 8 Blöcke (Teleport): kein Treffer.
3. **Ziele:** jedes Lebewesen (Teile des Enderdrachen zählen als der Drache), dessen Hitbox, vergrößert um den Rand der
   Komponente `minecraft:attack_range` (Vanilla 0,125), die Strecke schneidet oder den Anfang enthält; nach Abstand
   sortiert. Nicht: die Drohne, Zuschauer, Tote, Ziele mit `isInvulnerableToPiercingWeapon` oder ohne
   `canBeHitByProjectile`, der Pilot selbst.
4. **Spieler** nur, wenn `payloads_hit_players` an ist, ein Pilot fliegt und Vanilla ihn den Spieler treffen lässt
   (`canHarmPlayer`: Gamerule `pvp` und Teams). Ohne Pilot (Heimflug, lose Drohne) trifft der Speer keine Spieler.
5. **Sperre:** jedes Ziel höchstens einmal je `contact_cooldown_ticks` der Komponente (Vanilla 10); die Sperre gilt ab der
   Berührung, wie bei Vanilla, auch wenn die Schwellen nicht erreicht sind. Nur im Speicher.
6. **Tempo:** `v` = Tempo der Drohne (`AccessoryContext#velocity`, Propwash misst die Strecke je Tick) entlang der Nase
   (`DroneView#forward`, nicht entlang der Halterung, die Propwash ohne `hangs_straight` neigt), `v_rel` = max(0,
   `v` − Tempo des Ziels entlang der Nase). Tempo des Ziels wie Vanilla (`KineticWeapon.getMotion`).
7. **Schwellen** der Komponente, die Drohne zählt wie ein Spieler (Faktor 1): Schaden ab `min_relative_speed` der
   Schadensbedingung (Vanilla 4,6 m/s), Rückstoß ab `min_speed` der Rückstoßbedingung (Vanilla 5,1 m/s), Abwerfen von
   Reitern ab `min_speed` der Abwurfbedingung (Vanilla 9 bis 14 m/s je Material). Die Zeitgrenzen der Bedingungen
   (`max_duration_ticks`, beim Spieler die Dauer des Anlaufs) gelten an der Drohne nicht.
8. **Schaden (E8):** `floor((1 + floor(v_rel × damage_multiplier)) × spear_damage_percent / 100)`, höchstens 15; mit dem
   Standard 100 % also der Vanilla-Speerschaden bei gleichem Tempo bis zur Grenze (Befund OWNM-4, vorher 50 %). Danach
   wie Vanilla die Verzauberungen auf Schaden (`EnchantmentHelper#modifyDamage`, z. B. Schärfe). Schadensart aus der
   Komponente `minecraft:damage_type` des Speers (Vanilla `minecraft:spear`), Verursacher = Pilot (ohne Pilot keiner),
   direkte Ursache = Drohne.
9. **Rückstoß** 0,4 entlang der Nase plus Rückstoß-Verzauberung; **Abwerfen** wie Vanilla (nicht bei
   `#cannot_be_dismounted_by_item_usage`).
10. **Haltbarkeit:** −`item_damage_per_attack` der Komponente `minecraft:weapon` (Vanilla 1) bei jedem Treffer, der eine
    Schwelle erreicht; Haltbarkeit-Verzauberung wirkt, ein Pilot im Kreativmodus nutzt nichts ab. Ein zerbrochener Speer
    verschwindet aus dem Slot, mit dem Bruch-Ton der Komponente `minecraft:break_sound` und Item-Partikeln.
11. **Nachwirkungen** der Verzauberungen (Verbrennung) nach einem Treffer mit Schaden. Vanilla verlangt für Verbrennung
    und die Verlangsamung von Nemesis der Gliederfüßer einen direkten Treffer (Verursacher = direkte Ursache). Die
    Nachwirkungen werten den Stich deshalb als direkten Treffer des Verursachers (Pilot, ohne Pilot keiner); Schaden,
    Rückstoß und Todesnachricht behalten die Schadensquelle aus Punkt 8 mit der Drohne als direkter Ursache.
12. **Rückmeldung** (`SpearFeedback#hit`, Befund OWNM-4) bei jedem Stich, der Schaden, Rückstoß oder Abwerfen auslöst:
    - **Ton** aus `hit_sound` der Kinetik-Komponente (`item.spear.hit`, Holz `item.spear_wood.hit`) am Ziel, für alle
      Spieler in Hörweite außer dem Piloten und zusätzlich direkt an den Piloten (`ClientboundSoundPacket`). Der Körper
      des Piloten steht oft weit weg (FPV); die Hörweite von 16 Blöcken gilt für ihn deshalb nicht, gehört wird der Ton
      an der Kamera der Drohne.
    - **Partikel** am Ziel: 12 Krit-Partikel und wie Vanilla Schadensanzeige-Partikel (halbe verlorene Lebenspunkte,
      ab mehr als 2). Umstehende sehen sie bis 32 Blöcke, der Pilot bis 512 Blöcke von seinem Körper.
    - **OSD-Hinweis** `HIT`/`TREFFER` (`mobdefense:warning/hit`, Ton „Akzent“) für 1,5 s, nur beim Piloten, über das
      Paket `mobdefense:notice` (`docs/osd-ux.md` §2).
    Ohne Pilot (Heimflug, lose Drohne) gibt es Ton und Partikel für Umstehende, aber keinen Hinweis.
13. **Fortschritt** „Lanzenflug“ (`mobdefense:lance_flight`, `docs/osd-ux.md` §5): Stirbt ein Mob (`Mob`, also keine
    Spieler und keine Rüstungsständer) am Schaden eines Stichs, bekommt der Pilot den Fortschritt (Kriterium `done`).
    Ohne Pilot (Heimflug, lose Drohne) bekommt ihn niemand.
14. **Rammen** (Befund AUTO20-2): Propwash prüft Zusammenstöße mit Lebewesen an der Hülle der Drohne, einem Quader um
    Rahmen und Propeller. An großen Rahmen reicht diese Hülle weiter nach vorn als die Speerspitze (7″: Hülle 0,19 m,
    Spitze etwa 0,15 m vor der Mitte, im schnellen Flug mit gesenkter Nase noch weniger). Die Drohne prallt dann ab,
    bevor die Spitze das Ziel erreicht, und nur der Körpertreffer (`propwash:drone_strike`) zählt. Deshalb hört die
    Halterung auf Propwashs Körpertreffer (`PropwashEvents.DRONE_HIT_ENTITY`, nur mit Pilot): Liegt das Ziel vor der Nase
    – der Punkt seiner Hitbox, der der Drohnenmitte am nächsten liegt, höchstens 60° neben `DroneView#forward`
    (`SpearRam`) –, sticht der Speer mit denselben Regeln wie in Punkt 3 bis 13. Sperre und Ziele teilt er mit der
    Strecke der Spitze, ein Ziel wird also höchstens einmal je `contact_cooldown_ticks` getroffen, egal auf welchem Weg.
    Tempo ist das Tempo der Drohne beim Kontakt, wie Propwash es meldet (`EntityHit#relativeVelocity` plus Tempo des
    Ziels). Der Stich kommt vor dem Körpertreffer; dessen Schaden zählt danach wie bei Vanilla nur, soweit er den Stich
    übersteigt (Unverwundbarkeitszeit). Seitliche Kontakte und Ziele über oder hinter der Drohne bleiben reine
    Körpertreffer.

Beispiele mit dem Eisenspeer (`damage_multiplier` 0,95), Standard 100 % und Regel auf 50 %:

| `v_rel` | Vanilla | Drohne (100 %) | Drohne (50 %) | Rückstoß | Abwerfen |
|---|---|---|---|---|---|
| 4,0 m/s | – | – | – | – | – |
| 5,0 m/s | 5 | 5 | 2 | – | – |
| 10 m/s | 10 | 10 | 5 | ja | – |
| 15 m/s | 15 | 15 | 7 | ja | ja |
| 30 m/s | 29 | 15 (Grenze) | 14 | ja | ja |
| 40 m/s | 38 | 15 (Grenze) | 15 (Grenze) | ja | ja |

Netheritspeer (1,2) mit 10 m/s: 13 Schaden (vorher mit 50 %: 6); ein Dorfbewohner (20 Lebenspunkte) fällt nach zwei
Kontakten. Bestehende Welten behalten den gespeicherten Wert der Regel; dort hilft
`/gamerule mobdefense:spear_damage_percent 100`.

Welt-Regel `spear_damage_percent` 0 schaltet nur den Schaden ab; Rückstoß, Abwerfen und Abnutzung bleiben.

---

## 5. Welt-Regeln

| Regel | Wirkung auf den Speer |
|---|---|
| `spear_damage_percent` (0–100, Standard 100) | Anteil am Vanilla-Schaden |
| `payloads_hit_players` (Standard aus) | Speer trifft Spieler nur, wenn an; Vanilla-PvP gilt zusätzlich |

---

## 6. JUnit

| Test | Prüft |
|---|---|
| `spear.core.SpearDamageTest` | Vanilla-Formel mit Float-Multiplikator wie Vanilla, Beispiele aus dem Design (50 %: 15 m/s → 7, 30 m/s → 14; Standard 100 %: 10 m/s → 10, 15 m/s → 15), Standard gleich Vanilla bis zur Grenze, Obergrenze 15 für alle sieben Speere, 0–80 m/s und 0–100 %, Anteil und Klemmung, unter 100 % weniger als Vanilla, kein Schaden unter 4,6 m/s, Rückstoß ab 5,1 m/s, Abwerfen je Material, `v_rel` entscheidet den Schaden, `v` den Rückstoß, 0 % ohne Schaden, fehlende Bedingungen, kaputte Zahlen |
| `spear.core.SpearCoreTest` | Gewichte der sieben Speere, unbekannte Items, Sperre je Ziel wie Vanilla, Speicher bleibt klein, Lage des Speers in den Schellen |
| `spear.core.SpearRamTest` | Befund AUTO20-2: Dorfbewohner aus dem Prüflauf an der Hülle der 7″ liegt vor der Nase, gesenkte Nase (40°) zählt noch, Ziele seitlich, oben und hinten nicht, Kegel endet bei 60°, Drohnenmitte in der Hitbox nimmt die Mitte des Ziels, ohne Nase nichts |
| `spear.OwnmSpearTest` | Befund OWNM-4: Netheritspeer mit 10 m/s macht mit dem Standard 13 wie Vanilla, ein Dorfbewohner fällt nach zwei Kontakten; Hinweis `HIT` hinten an `net.Notice` angehängt (`THREAT` behält seine Nummer), Ton „Akzent“, Texte `HIT`/`TREFFER` in ASCII |
| `spear.MdTxtPlayerTextsTest` | Befund MDTXT-1: Spielertexte (Sprachdateien EN/DE aller Module, Anleitung EN/DE, README, CHANGELOG, Modrinth-Texte) folgen den Standards aus `RuleDefaults` – Speer mit vollem Vanilla-Speerschaden (Flugschul-Hinweis „full vanilla spear damage“ / „voller Vanilla-Speerschaden“, nirgends „halb“ oder „schwächer als in der Hand“), Fortschritt überall „Drone Joust“ (deutsch „Lanzenflug“, nie „Lance flight“), TNT standardmäßig erlaubt und die Welt-Regel „TNT aus dem Dispenser“ schaltet es ab (Regeltext mit „Aus:“, Flugschul-Hinweis, Anleitung, README, Modrinth); die Zeile `allow_tnt` der Regeltabellen in der Anleitung gleicht dem Text im Spiel. Die Dateien sind Eingaben von `:common:test` |
| `spear.SpearGuideTipTest` | Befund SPEAR3-2: Abschnitt Speer-Halterung der Anleitung EN/DE nennt die Spitze dort, wo das Spiel sie zeichnet und prüft (`SpearPose`: 99,6 mm vor der Mitte der Halterung, im Text „etwa 10 cm“, jede cm-Angabe im Abschnitt höchstens 0,5 cm daneben), nie „vor die Nase“; der Vergleich mit den Propellern stimmt mit den Rahmen-Daten aus Propwash (`freestyle_5`: Spitze höchstens 10 mm neben der Vorderkante der vorderen Propeller, `long_range_7`: Propeller mindestens 20 mm weiter vorn, Spitze etwa 0,15 m, Propeller etwa 0,19 m wie in §4 Punkt 14) |

---

## 7. Selbsttest und dedizierter Server

**S8** (`dev/spear/client/SpearSteps`, Modul `spear`):

```sh
<slot-skript> ./gradlew :common:mergeLang :fabric:runClientSelftest -PmobdefenseSelftestOnly=spear/ -PmobdefenseScreenshots=true
<slot-skript> ./gradlew :common:mergeLang :neoforge:runClientSelftest -PmobdefenseSelftestOnly=spear/
```

1. Halterung nimmt nur Speere (Tabelle in §2), Beladung, Anzeige.
2. Drohne `freestyle_5` mit Speer-Halterung, ohne Pilot, ohne Schwerkraft auf Zombiehöhe; ein Zombie ohne KI mit
   Feuerresistenz steht 3 Blöcke vor der Nase. Ein Server-Takt hält das Tempo der Drohne entlang der Nase fest.
3. Anlauf 15 m/s: Zombie verliert die berechneten 15 ±1 (Rüstung 2 zieht etwa 0,2 ab), wird entlang der Nase gestoßen,
   Schadensart `minecraft:spear`, direkte Ursache die Drohne, Haltbarkeit −1.
4. Anlauf 5 m/s: 5 ±1 Schaden, kein Rückstoß (unter 5,1 m/s).
5. Anlauf 4 m/s: die Drohne fliegt durch den Zombie, kein Schaden, kein Stoß, keine Abnutzung.
6. Anlauf 15 m/s mit abgenutztem Speer: Schaden, der Speer zerbricht und der Slot ist leer.
7. Foto `spear_drone` (EN und DE): Drohne mit Eisenspeer seitlich auf Augenhöhe, ohne HUD.

**S8 Speer-Verbrennung** (eigener Schritt, gleiches Modul, Filter auch `'S8 Speer-Verbrennung'`): Ziel ist ein
Wüstenzombie ohne KI mit Feuerresistenz, der in der Sonne nicht von selbst brennt.

1. Anlauf 15 m/s ohne Pilot mit unverzaubertem Eisenspeer: Schaden wie in S8, der Wüstenzombie brennt nicht.
2. Anlauf 15 m/s ohne Pilot mit Eisenspeer und Verbrennung II: gleicher Schaden, der Wüstenzombie brennt (etwa 150
   Ticks nach dem Anlauf, Vanilla 160), Schadensart `minecraft:spear`, direkte Ursache die Drohne, kein Verursacher.
3. Stich über `SpearStrikes#stab` mit dem Spieler als Pilot und Verbrennung II: etwa 15 Schaden, der Wüstenzombie brennt
   160 Ticks, Verursacher der Pilot, direkte Ursache die Drohne.

**S8 Treffer-Rückmeldung** (`dev/spear/client/SpearFeedbackSteps`, gleiches Modul, Filter auch `'S8 Treffer'`, Befund
OWNM-4): 7″ (`long_range_7`) mit Netheritspeer im FPV mit dem Spieler als Pilot, schwebt; danach steht der Körper des
Piloten 30 Blöcke hinter der Drohne (außer Hörweite des Ziels), ein Dorfbewohner ohne KI schwebt 1,4 Blöcke vor der Nase.

1. Stich über `SpearStrikes#stab` mit 10 m/s: −13 Leben, beim Piloten Krit- und Schadensanzeige-Partikel
   (`SpearFeedback#last`), im OSD `TREFFER` bzw. `HIT` (`OsdNotices`), am Client der Ton `item.spear.hit` mehr als 16
   Blöcke vom Spieler entfernt (`SoundEventListener`).
2. Foto `spear_fpv_hit` (nur mit Bildschirmfotos, deutsch): FPV-Bild mit Hinweis und Partikeln.
3. Nach 25 Ticks Stich 2 mit 10 m/s: der Dorfbewohner ist besiegt.
4. Die Speerspitze (`SpearStrikes#lastTip`) liegt im Sichtfeld der Kamera (`DroneAccess#cameraPose`): 88 mm vor der
   Linse, 6,3° unter der Bildmitte bei 101° Sichtfeld. Das ist nur die Lage; ob der Speer im FPV-Bild auch gezeichnet
   wird, prüft „S8 Speer im FPV-Bild“ (unten, Befund AUTO20-1).

Ohne den Fix (Lauf vor dem Commit) fehlten Ton und Hinweis beim Piloten, und der Dorfbewohner hatte nach zwei Stichen
noch 8 Leben (2 × 6).

**S8 Rammen im echten Flug** (`dev/spear/client/SpearRamSteps`, gleiches Modul, Filter auch `'S8 Rammen'`, Befund
AUTO20-2): 7″ (`long_range_7`) mit Netheritspeer im FPV mit dem Spieler als Pilot, gesteuert über die Flughilfe des
Selbsttests (Winkelmodus, Höhe halten, Sollgeschwindigkeit 12 m/s entlang der Nase). Ein Dorfbewohner ohne KI und ohne
Schwerkraft schwebt 14 Blöcke vor der Nase, die Füße 1 m unter der Flughöhe; bis 4 m vor der Drohne bleibt er auf ihrer
Fluglinie. Fünf Anflüge, jeder entlang der Nase, die die Drohne gerade hat; nach jedem Kontakt schaltet der Schritt die
Drohne wieder scharf (vorher aufrichten), fliegt zurück, und ein gefallener Dorfbewohner wird ersetzt.

1. Jeder Anflug mit Kontakt (Körpertreffer von Propwash oder Lebensverlust) muss einen Speer-Stich haben
   (`SpearFeedback#last` auf diesen Dorfbewohner).
2. Mindestens vier Kontakte, mindestens ein Dorfbewohner fällt.
3. Das Protokoll nennt je Anflug Tempo, Lebenspunkte vorher und nachher, Körpertreffer mit Tempo entlang der Nase und
   die letzte Schadensart.

Die Flughilfe erreicht mit der 7″ etwa 8 m/s, die Nase ist dabei gesenkt; entlang der Nase bleiben 6 bis 8 m/s, der
Netheritspeer macht nach der Vanilla-Formel also 8 bis 10 je Stich. Ein Dorfbewohner fällt deshalb nach zwei oder drei
Stichen; ein Kriterium „nach zwei Kontakten besiegt“ hat der Schritt nicht, weil das allein am Tempo hängt.

Ohne den Stich beim Rammen (Lauf vor dem Fix, Fabric, Nase voran mit 7,9 m/s entlang der Nase) hatten 2 von 4
Kontakten keinen Speer-Stich, nur den Körpertreffer `propwash:drone_strike` (2,6 und 4,1). Mit dem Fix: 5 von 5
Kontakten mit Speer-Stich, je 10 Schaden, zwei Dorfbewohner fielen nach je zwei Kontakten.

**S8 Speer im FPV-Bild** (`dev/spear/client/SpearFpvSteps`, Befund AUTO20-1, **gesperrt**: die Klasse ist fertig, die
Anmeldezeile fehlt, siehe §8): 7″ mit Netheritspeer steht im FPV am Boden vor einer Wand aus glattem Stein (3 Blöcke vor
der Nase, 9 breit, 7 hoch), HUD aus. Drei Bilder im Vergleich, jeweils der Bereich unter der Bildmitte (Breite 30–70 %,
Höhe 50–100 %), ein Bildpunkt zählt als anders ab 24 in einem Farbkanal:

1. mit Speer gegen ohne Speer (der Speer wird am Server aus dem Slot genommen),
2. ohne Speer zweimal hintereinander (Rauschen),
3. Kontrolle: ein Speer-Item (`ItemEntity` ohne Schwerkraft) genau an der Speerspitze gegen ohne Speer.

Grenze ist 0,5 % oder das Dreifache des Rauschens. Liegt die Kontrolle darunter, ist die Messung ungültig (FAIL); liegt
der Vergleich mit Speer darunter, FAIL „Speer im eigenen FPV-Bild nicht gezeichnet“. Stand mit Propwash 0.6.0-dev.10
(Fabric): mit Speer 0,00 % anders, Kontrolle 30,92 %, Rauschen 0,00 % – FAIL, deshalb gesperrt.

**Fortschritt Lanzenflug** (Schritt in `dev/ui/client/CovMdAUiSteps#registerLanceFlight`, Modul `covmda_ui`, Filter auch
`'Fortschritt Lanzenflug'`): Fortschritte zurücknehmen, Zombie mit 1 Leben vor der Speer-Drohne, Stich über
`SpearStrikes#stab` mit dem Spieler als Pilot; danach ist „Lanzenflug“ erledigt.

**Server-Prüfungen** (laufen im Server-Rauchtest beider Loader, `./gradlew :fabric:runServerSmoke`):

- „Speer-Halterung nimmt nur Speere“ (Tick 0): wie S8 Schritt 1.
- „D3 Speer: Bereich laden“ (Tick 10): lädt den Chunk 6 Blöcke neben dem Spawn dauerhaft (an einem frischen Server ist
  der Entity-Bereich sonst nicht zugänglich und die abgestellte Drohne nicht auffindbar).
- „D3 Speer: payloads_hit_players × PvP“ (Tick 60): zwei Testspieler am dedizierten Server (Pilot und Ziel, ohne
  Netzverbindung), Eisenspeer mit 15 m/s, Aufruf `SpearStrikes#stab` mit dem Testspieler als Pilot:

| `payloads_hit_players` | `pvp` | Ergebnis |
|---|---|---|
| aus | aus | kein Treffer (`PLAYER_RULE`) |
| aus | an | kein Treffer (`PLAYER_RULE`) |
| an | aus | kein Treffer (`PVP`) |
| an | an | 15 Schaden, Schadensart `minecraft:spear`, Verursacher der Pilot, direkte Ursache die Drohne, Haltbarkeit −1 |

  Dazu: ohne Pilot kein Treffer auf Spieler (`NO_ATTACKER`), der eigene Pilot nie (`SELF`), ein Zombie mit beiden Regeln
  aus verliert etwa 15. Die Prüfung stellt beide Gamerules zurück, entfernt Drohne und Zombie und gibt den Chunk frei.

D3 prüft die Regeln am echten `ServerPlayer`, aber ohne echten Flug: Ein Pilot lässt sich über die Propwash-API nicht am
Server setzen. Den Weg „Pilot der Drohne → Verursacher“ deckt `SpearStrikes#tick` über `AccessoryContext#pilot` ab.

---

## 8. Offene Punkte

- **Speer im eigenen FPV-Bild (Befunde OWNM-4 und AUTO20-1, API-Befund an Propwash):** Die Spitze liegt im Bild (S8
  Treffer-Rückmeldung: an der 7″ 6,3° unter der Bildmitte), Propwash zeichnet im eigenen FPV aber kein Zubehör: Die Drohne
  ist dort verborgen (`QuadcopterRenderer#extractRenderState`: `hidden = own`), und `AccessoryDisplayState#extract` und
  `#submit` übergehen dann jede B2-Anzeige; gezeichnet werden nur die Propeller (`FpvProps`). Das Add-on kann das über
  die API nicht ändern, und eigener Render-Code ist nach B2 nicht vorgesehen. Sobald Propwash die Anzeigen im eigenen FPV
  zeichnet und Mob Defense gegen diese Version baut, wird „S8 Speer im FPV-Bild“ freigegeben: in
  `dev/spear/SpearModule#clientSteps` die Zeile `SpearFpvSteps.register(steps);` nach `SpearRamSteps.register(steps);`
  anhängen (dazu der Import `de.lardtom.mobdefense.dev.spear.client.SpearFpvSteps`).

- `spear_mount.json` ohne `"hangs_straight": true`: Am 5-Zöller zeigen Halterung und Speer 30° nach oben geneigt, am
  Whoop senkrecht; die Trefferstelle folgt der Anzeige. Das Feld gibt es seit Propwash 0.6.0-dev.4 (acc-hang); die Datei
  gehört dem Modul `core`. Mit dem Feld (ein Versuch ohne Commit) liegt der Speer waagerecht unter dem Rahmen, Spitze
  nach vorn, und S8 bleibt grün.

- **S8 Rammen im echten Flug auf NeoForge einmal FAIL** (Prüflauf zu Befund MDTXT-1, nur Textänderungen): Anflug 2
  „Kontakt, Tempo 8,3 m/s, Leben 10,0 → 0,0, Speer-Stich nein, kein Körpertreffer, letzte Schadensart -“, also 4 von 5
  Kontakten mit Speer-Stich. Der Dorfbewohner fiel, der Schritt hat aber weder Stich noch Körpertreffer noch Schadensart
  erfasst. Die Wiederholung allein (`-PmobdefenseSelftestOnly='S8 Rammen'`) war PASS mit 5 von 5, Fabric im selben
  Prüflauf ebenfalls PASS. Ob der Schritt den tödlichen Kontakt verliert, wenn der gefallene Dorfbewohner ersetzt wird,
  ist nicht untersucht.
