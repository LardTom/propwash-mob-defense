# Dispenser-Halterung, Payloads und Griefing (Strang md-dispenser)

Grundlage: `docs/design.md` §2.1.1, §2.1.4, §2.3, §2.5, §3.3 (G2–G5, G7, G10, G11), §4.1, §8 und die Entscheidungen E3–E7,
E16, E17. Dieses Dokument beschreibt, was gebaut ist, wie es zusammenhängt und wie es getestet wird.

---

## 1. Bedienung

| Aktion | Wirkung |
|---|---|
| Zubehör-Aktion 1 („Dispenser feuern“, Standard Ziffernblock 9) | feuert den gewählten Slot; ist er leer, den nächsten belegten (`SlotCycle.firing`). Ein kurzer Druck feuert genau einmal. Gehalten folgt der zweite Schuss erst nach dem Anlauf (Standard 500 ms), danach feuert der Dispenser im Abstand der Einstellung (Standard 200 ms) weiter. Zwischen zwei Schüssen liegt immer mindestens der Abstand (§1.1), auch bei schnellen Einzeldrücken und Auslösern im Takt |
| Zubehör-Aktion 2 („Nächster Slot“, Standard Ziffernblock 0) | wählt den nächsten belegten Slot nach dem Slot, der gerade feuern würde (`SlotCycle.next`) |

- Die Namen kommen über `AccessoryBehavior#actionNameKey` (G2): Aktion 1 `mobdefense.action.dispenser.fire`,
  Aktion 2 `mobdefense.action.dispenser.next_slot`, Aktionen 3 und 4 leer (Aktion 3 gehört dem Booster).
- Der Rechtsklick beendet wie bei jeder Drohne den Flug (E4); gefeuert wird nur über Aktion 1.
- Vier Slots, Grenzen je Slot aus `AmmoTable`: höchstens 16 bzw. die Stapelgröße, TNT 4, Wurf- und Verweiltränke 1,
  Eimer 1. Drohnen-Items passen nicht hinein.

### 1.1 Schussfolge (Tab „Mob Defense“ im K-Menü, Befund OWNM-1)

| Einstellung | Bereich | Standard | Wirkung |
|---|---|---|---|
| Anlauf bis zum Dauerfeuer | 200–2000 ms (4–40 Ticks) | 500 ms | Zeit zwischen erstem und zweitem Schuss, solange Aktion 1 gehalten wird |
| Abstand im Dauerfeuer | 200–2000 ms (4–40 Ticks) | 200 ms | kürzeste Zeit zwischen zwei Schüssen, im Dauerfeuer wie bei einzelnen Auslösern |

- Die Untergrenze von 200 ms ist fest (Takt des Vanilla-Dispensers). Verlangt die Welt-Regel `dispenser_cooldown_ticks`
  mehr, gilt sie für beide Werte (`FireCadence.Setting#effectiveDelay`/`effectiveInterval`); der Tab zeigt die Untergrenze
  dieser Welt und färbt den Hinweis, wenn sie über einer Einstellung liegt.
- **Mindestdauer immer (Befund OWNC5B-1, Owner „Mindestdauer immer einhalten“):** Jede Auslösung von Aktion 1 hält den
  größten Wert aus Welt-Regel (ab 1 Tick), fester Mindestzeit (4 Ticks, `FireCadence.MIN_TICKS`) und eingestelltem Abstand
  ein – gehalten, einzeln gedrückt oder von außen über `AccessoryRegistry#triggerAction` (etwa Wegpunkt-Aktionen im
  Takt eines Flugplaners). Einzelne Auslöser nehmen den Abstand des auslösenden Spielers
  (`AccessoryAction#player`; beim Flugplaner der Besitzer, auch ohne Pilot). Ein Anlauf unter dem Abstand gilt als
  Abstand (`effectiveDelay` ≥ `effectiveInterval`). Vorher begrenzte bei Einzeldrücken nur die Welt-Regel: mit
  `dispenser_cooldown_ticks` 1 feuerte ein Takt von 2 Ticks alle 2 Ticks (100 ms), und ein Abstand von 1000 ms galt nur
  beim Halten.
- Gespeichert wird je Rechner in `config/mobdefense-client.json` (`dispenser_delay_ticks`, `dispenser_interval_ticks`).
  Der Client schickt die Werte beim Betreten jeder Welt und nach dem Speichern mit dem Paket `mobdefense:dispenser_cadence`
  (C2S, zwei VarInts) an den Server; ohne Mob Defense am Server geht nichts raus. Der Server klemmt die Werte auf
  4–40 Ticks und merkt sie sich je Spieler nur im Speicher (`DispenserCadences`, beim Serverstart geleert). Ohne Paket
  oder ohne Pilot gilt der Standard.
- Ablauf am Server (`DispenserBehavior`, portabler Kern `dispenser.core.FireCadence`): Der Druck feuert sofort, wenn die
  Abklingzeit es erlaubt, und setzt den nächsten Schuss auf jetzt + Anlauf; fiel der Druck in die Abklingzeit, kommt der
  erste Schuss, sobald sie vorbei ist, und danach der Anlauf; wird vorher losgelassen, verfällt der Druck. Jeder weitere
  Schuss setzt jetzt + Abstand. Die Haltedaten liegen nur im Speicher (`DispenserHolds`) und gehen beim Loslassen weg.

## 2. Ablauf eines Schusses

`DispenserBehavior` (B1) fragt zuerst den portablen Kern `FireGate`:

1. **Abklingzeit:** vor `ready_at` passiert nichts (kein Ton). Nach jedem Versuch – Schuss oder Ablehnung – gilt
   `ready_at = jetzt + max(4, dispenser_cooldown_ticks, Abstand)` (`FireCadence.nextReady`, Abstand aus §1.1); auch mit
   Welt-Regel 0 oder 1 also höchstens ein Versuch je 4 Ticks.
2. **Slot:** gewählter Slot, sonst der nächste belegte; alle leer → `EMPTY` (Warnung NO AMMO).
3. **Regeln:** TNT ohne `allow_tnt` → `TNT_LOCKED`; Eimer mit Flüssigkeit ohne `allow_fluid_buckets` → `BUCKET_LOCKED`;
   alles, was Blöcke verändert (Eimer, leerer Eimer, Knochenmehl, Feuerzeug, Schere, Glowstone, Wachs, Pinsel,
   Glasflasche, Wasserflasche, Kürbis, Witherskelettschädel, Shulkerkisten, Items fremder Mods mit eigenem
   Dispenser-Verhalten) ohne `drone_griefing` → `GRIEFING_LOCKED` (Warnung NO PERMISSION).
4. **Werfen:** `AccessoryContext#dispense(slot, DispenseOptions.defaults().consume(real_ammo).allowFluids(…).applyRecoil(false))`.
   `allowFluids` ist nur für Eimer mit Flüssigkeit an, wenn `allow_fluid_buckets` und `drone_griefing` an sind.
   Rechte (`DENIED`), Eigentreffer-Schutz, Tempo von Geschossen, TNT und Items sowie die Größe des Rückstoßes
   (`recoilNs()` = Masse × Mündungstempo) kommen aus Propwash (G3–G5, G11) und werden nicht nachgebaut; was nach dem
   Schutz von Propwash noch an die eigene Drohne kommt, fängt `OwnShotGuard` ab (§4). Den Stoß selbst gibt
   `DispenserRecoil` (§2.1).
5. **Ergebnis:** Erfolg (`FIRED`, `PLACED`, `USED`) markiert Geschoss bzw. TNT als Payload (§4). Jede Ablehnung spielt
   `block.dispenser.fail` an der Mündung und setzt die Warnung für 2 s: `EMPTY` → NO AMMO, `DENIED` → NO PERMISSION,
   `REFUSED` bei Eimern → BUCKET LOCKED, bei TNT (Vanilla `tnt_explodes` aus) → TNT LOCKED, sonst und `BLOCKED` →
   BARREL BLOCKED.

### 2.1 Rückstoß (Befunde OWNM-2, AUTO21-1 und RC3P2-1)

Ursache des Befunds (Selbsttest „Wurftrank im Schwebeflug“ vor dem Fix): Der Wurftrank traf die Drohne nicht (flog
13,9 m, kein Schaden), das Rückstoß-Tempo passte zu Masse und Tempo (9,63 N·s an 2,08 kg, 4,72 m/s zurück). Die 5″ kippte
aber um +131,5° mit bis zu 1302 °/s, weil Propwash den Stoß am Hebel ab dem Schwerpunkt **ohne** Beladung ansetzt
(`MountPoses.center` mit `drone.build()`), das Flugmodell die Drohne aber um den Schwerpunkt **mit** Beladung dreht: Vier
Tränke (1,4 kg) in der Halterung ziehen ihn fast bis auf Höhe des Laufs, der echte Hebel ist dort ein Viertel so lang.

- `DispenserRecoil` wirft mit `applyRecoil(false)` und gibt `recoilNs()` selbst über `DronePhysics#impulse` entgegen
  `MountPose#forward()` ins Flugmodell. Größe und Richtung bleiben wie bei Propwash (Masse × Mündungstempo).
- **Alle Geschosse** (Tränke, Erfahrungsfläschchen, Pfeile, Schneebälle, Eier, Feuerkugeln, Raketen, Items und seit
  RC3P2-1 auch die Windkugel): Der Stoß greift am Schwerpunkt an (Angriffspunkt `Vec3.ZERO`), die Drohne wird
  zurückgestoßen, aber nicht gedreht (Befund AUTO21-1, E26, E27).
- **Windkugel** (`AbstractWindCharge`): Propwash meldet seit RC3P-2 1,8 N·s (0,05 kg × Mündungstempo 20 m/s = 1,0 N·s
  plus 0,8 N·s Druckwelle; vorher 3,5 N·s mit 2,5 N·s Druckwelle), und ihr Windstoß trifft Mobs, Spieler und Drohnen halb
  so stark wie eine Vanilla-Windkugel. Bis RC3P2-1 griff ihr Stoß an der Mündung an, mit dem Hebel ab dem Schwerpunkt mit
  Beladung (E24, portabler Kern `RecoilLever`). Damit überschlug sich der schwebende 3″-Cinewhoop auch mit 1,8 N·s
  (+125,6°, bis 1356 °/s) und war nicht mehr fliegbar. Seit E27 greift auch die Windkugel am Schwerpunkt an: der Cinewhoop
  fliegt 3,35 m/s zurück und bleibt waagerecht (höchstens 0,0°), die 5″ 2,8 bis 2,9 m/s. Den Hebel nur für die Druckwelle
  anzusetzen hieße, Propwashs Anteil nachzubauen, denn die API meldet nur den ganzen Rückstoß. `RecoilLever` ist entfallen.
- Warum nicht der Hebel für alles (Befund AUTO21-1, Gegenprüfung G-OWNM-1): Mit vier Tränken zieht die Beladung den
  Schwerpunkt fast bis auf die Höhe des Laufs, mit jedem Wurf wird der Hebel länger und die Drohne leichter. Mit dem Hebel
  kam die 5″ beim 1. Wurf (4 geladen, 2,08 kg) auf +17,8° und 342 °/s, beim 2. auf +32,8° und 559 °/s, beim 3. auf +73,7°
  und 1147 °/s, beim letzten (1,03 kg) überschlug sie sich (+168,5°, 3688 °/s). Dazu behält Propwashs Flugmodell beim
  Wechsel der Beladung den Drehimpuls; mit der kleineren Trägheit nach dem Wurf steigt die Drehrate. Am Schwerpunkt: alle vier Würfe 0,0 bis 0,1° und höchstens 2 °/s, zurück 4,6 / 5,3 / 6,7 / 9,1 m/s (= 9,63 N·s geteilt
  durch die Masse vor dem Wurf).
- API-Befund an Propwash (accessory-gaps, unverändert offen): `AccessoryDispenser.Shot#recoil` rechnet den Hebel ab dem
  Schwerpunkt ohne Beladung. Mob Defense bleibt bei `applyRecoil(false)`, weil Wurfgeschosse gar keinen Hebel haben sollen.

`DispenserLog` hält je Drohne die letzten 32 Versuche (Status, Warnung, Mündung, `forward`, Drohnentempo, Abwurftempo)
für die Tests; das Spiel selbst liest ihn nicht.

## 3. Daten an der Drohne, OSD und Warnungen

| Schlüssel (B6, `TRACKING`) | Inhalt | Größe |
|---|---|---|
| `mobdefense:dispenser` | `{selected: Byte 0–3, cooldown_until: Spielzeit}` (Feldnamen wie design.md §4.2) | 39 Byte |

`selected` reist mit dem Drohnen-Item (Speichern, Aufheben, wieder Abstellen); das Zerlegen in der Werkbank gibt die
Munition ins Inventar zurück, eine neu gebaute Drohne startet mit Slot 1. Die Beladung zählt über `payloadGrams`:
Gramm aus `AmmoTable` (design §2.1.4), sonst `AccessoryRegistry#itemGrams`.

Das OSD (Elemente `mobdefense:dispenser` und `mobdefense:dispenser_slots`, Warnungen) gehört dem Strang md-osd-ux
(`docs/osd-ux.md` §1.1 und §2): Er liest Inhalt der Slots und `selected`/`cooldown_until` über den Codec dieses
Schlüssels. Die Warnung zu jedem abgelehnten Versuch (`FireGate.Notice`: `NO_AMMO`, `BUCKET_LOCKED`, `TNT_LOCKED`,
`BARREL_BLOCKED`, `NO_PERMISSION`) schickt `DispenserNotices` über `net.Notices.send` an den Piloten (Paket
`mobdefense:notice`, höchstens 4 je Sekunde und Pilot, davon höchstens 3 für die Hinweise des Dispensers, einer bleibt für
`THREAT` frei, Befund AUTO3-1). Ohne Pilot (Aktion über `triggerAction` an einer abgestellten Drohne) gibt
es keine Warnung. Die Fortschritte „Rückstoß“ und „Luftunterstützung“ von md-osd-ux vergibt `payload.PayloadAwards`.

## 4. Payloads (`payload/**`)

- **Markierung:** Geschosse und TNT aus dem Dispenser tragen den Entity-Tag `mobdefense.payload` (wird mit der Entity
  gespeichert); Verweilwolken aus markierten Tränken ebenso.
- **Gegen Spieler** (`PayloadShield`): Schaden mit einem markierten direkten Verursacher an Spielern wird verworfen,
  solange `payloads_hit_players` aus ist oder Vanilla-PvP (`minecraft:pvp`) aus ist. Angemeldet über
  `ServerLivingEntityEvents.ALLOW_DAMAGE` (Fabric) bzw. `LivingIncomingDamageEvent` (NeoForge). Vanilla-PvP gilt
  ohnehin, weil der Pilot Besitzer ist.
- **Trank-Effekte** (`EffectShield`, `PotionPayloads`, `mixin/AbstractThrownPotionMixin`): Der Mixin legt sich um
  `onHitAsPotion`. Bei geschütztem Spieler werden schädliche Effekte (Kategorie `HARMFUL`, nicht sofort wirkend) nach
  dem Wurf auf den Stand davor zurückgesetzt; Sofortschaden fängt `PayloadShield`. Heilung und alle positiven Effekte
  wirken immer. Verweilwolken werden markiert; ihre Wirkung prüft `EffectShield` im Takt der Wolke (alle 5 Ticks,
  Stand einen Tick davor).
- **Eigene Geschosse** (`OwnShotGuard`): Seit Propwash 0.6.0-dev.8 (Fix MDA-4) fliegt jedes Geschoss aus `dispense`
  für seine ganze Lebensdauer durch die eigene Drohne, auch wenn sie es einholt; Schaden durch eigene Geschosse zerlegt
  sie nicht. Bis 0.6.0-dev.7 schützte Propwash nur, bis das Geschoss die Hülle verlassen hatte, höchstens 10 Ticks (G4).
  Holte die Drohne ein eigenes Geschoss danach ein, etwa eine Windkugel
  (ohne Schwerkraft, bleibt in der Flugbahn) im schnellen Vorwärtsflug oder nach dem Zünden des Boosters, schrieb
  Propwash den Treffer dem Piloten zu und zerlegte die Drohne wie bei einem Schlag. Als zweite Sicherung merkt sich `OwnShotGuard` zu jedem
  Geschoss aus dem Dispenser die Drohne, die es abgefeuert hat, und bricht über `PropwashEvents.DRONE_DAMAGED` jeden
  Schaden an genau dieser Drohne ab, dessen direkter Verursacher eines ihrer Geschosse ist. Windstoß und Rückstoß wirken
  weiter; andere Drohnen, Spieler und Mobs trifft das Geschoss wie bisher. TNT zählt nicht dazu.
- **Griefing:** `mixin/PrimedTntMixin` setzt bei markiertem TNT und `drone_griefing` aus die Explosion auf
  `ExplosionInteraction.NONE` (Schaden an Lebewesen bleibt). `FireballGuard` lässt markierte Feuerkugeln ohne
  `drone_griefing` vor einem Block-Einschlag erlöschen (Ton `block.fire.extinguish`), damit kein Feuer entsteht;
  Treffer auf Lebewesen bleiben. Was Mobs an Blöcken tun, folgt weiter `mobGriefing` (E17).
- Mixin-Konfiguration `mobdefense.dispenser.mixins.json` (Paket `de.lardtom.mobdefense.mixin`), angemeldet in
  `fabric.mod.json` und `neoforge.mods.toml`.

## 5. Backport 1.21.1

- `dispenser.core` (`SlotCycle`, `AmmoTable`, `FireGate`, `FireCadence`) nutzt nur Java 21,
  `:common:checkPortable` übersetzt es ohne Minecraft. Der Tab „Mob Defense“ (`client/MobDefenseSettingsTab`) gehört
  nicht zum Backport; dort gilt ohne Paket der Standard (Anlauf 500 ms, Abstand 200 ms).
- `DispenserImportTest` prüft, dass `dispenser/**`, `payload/**` und die beiden Mixins nichts aus `spear`, `booster`,
  `mob`, `sculk` und nichts aus Propwash außerhalb der API importieren; der Kern importiert nur `java.*`.
- Ersatzdateien im Backport: `AbstractThrownPotionMixin` (1.21.1 hat nur `ThrownPotion`), Item-Ids statt Klassen wo möglich.

## 6. Tests

| Art | Was |
|---|---|
| JUnit | `FireGateTest` (Abklingzeit, Makro, `real_ammo`, Eimer und TNT, Griefing, Status → Warnung), `FireCadenceTest` (Anlauf und Abstand, §1.1; Mindestdauer bei Einzeldrücken und Auslösern im Takt von 1 bis 20 Ticks, OWNC5B-1), `DispenserRecoilTest` (Wurftränke bei 4 bis 1 geladenen und Windkugel am Schwerpunkt, §2.1), `WindChargeRecoilTest` (S2 und S4 erwarten den Windkugel-Rückstoß, den Propwash meldet, ohne eigene Zahl; Toleranz 5 % + 0,01 N·s; S4 verlangt mindestens 50 % von Impuls/Masse, ohne Stoß, mit Messrauschen, ohne gemeldeten Impuls oder ohne Masse rot), `WindChargeTextsTest` (kein Spielertext sagt, eine Windkugel kippe oder überschlage die Drohne; die Modrinth-Texte nennen sie fliegbar; E26 verweist auf E27), `DispenserFrontTextureTest` (Vorderseite, §9), `SlotCycleTest`, `AmmoTableTest` (Design-Tabelle, jedes Vanilla-Item mit Dispenser-Verhalten mit Kürzel und Grenze, Arten), `DispenserStateTest` (Codec mit den Feldern `selected` und `cooldown_until`), `DispenserUnderFrameTest` (Einrücken, §10), `PayloadMassBenchTest` (Analyse aus Propwash; mit der TNT-Beladung aus S7 bleibt die 5″ bei Schub/Gewicht ab 1,15, darunter meldet Propwash `cannot_hover`), `DispenserImportTest` |
| Selbsttest `ownm_dispenser/` | Einzelschuss, Anlauf beim Halten, Tab „Mob Defense“ (GUI 854×480 und 320×240, EN und DE), Wurftrank im Schwebeflug, Vorderseite, Auslöser im Takt wie der Flugplaner (OWNC5B-1) (`docs/testing.md`, Abschnitt Owner-Playtest) |
| Selbsttest `dispenser/` | S2 Beladen und Feuern (Wartung mit Zubehör-Inventar, FPV, Dauerfeuer, Aktion 2, Richtung, Verbrauch, Rückstoß, Slot und Anzahl am Client), S3 Eimer, S4 Windkugel am Cinewhoop (spürbar zurück: Tempo nach hinten mindestens 50 % von Impuls/Masse mit dem Impuls aus Propwash und der Masse vor dem Schuss; kein Überschlag, Neigung unter 30°), S5 Tränke, S6 Spieler × PvP, S7 TNT und Griefing (5″ mit sechs TNT, zwei Plattformen), „Kompass fest“ (Test-Eigenschaft 45° vor dem Flug, die 5″ mit zwei TNT kommt nach 6 m/s Vorwärtsflug unter `HoverAssist.hold` zur Ruhe, der Flug hat einen gesunden Kompass, die Eigenschaft ist danach wieder 45°), S13 Speichern und Aufheben (5″ mit Dispenser und Raketen-Booster: Munition, gewählter Slot und Rakete samt Flugdauer und Sternen nach einem echten Neustart der Welt am Server und am Client, nach dem Aufheben am Item, nach dem Abstellen und beim Zerlegen in der Werkbank, die Munition und Rakete zurückgibt), „Unter dem Rahmen“ (§10: an jedem Rahmen mit Halterung vorn eingerückt, am Rahmen, Schuss aus der Mündung, Seitenbild) |
| Server-Rauchtest | Verhalten und Munition, D3 (vier Spieler × PvP-Kombinationen mit Pfeil, Schadens-, Gift- und Heiltrank), D5 (Makro 50/s), D7 (Spawnschutz, Abenteuermodus, Gegenprobe; hat der Server keinen Spawnschutz wie der D6-Server, setzt D7 ihn für die Prüfung auf 16 und danach zurück) |

Hilfen im Quell-Set `devtest`: `DispenserRig` setzt Drohnen auf die Kante eines Sockels, damit die Mündung frei ist,
`DispenserRig.muzzle` liest die Mündung aus Propwash über einen Schuss mit leerem Dispenser (EMPTY, `MountPose.front`), `Guests` legt Testspieler ohne Client an, `HoverAssist` ist eine Flughilfe nur für den Selbsttest (Höhe, Position,
Tempo halten), `Pilot.fly` startet jeden Selbsttest-Flug mit einem Kompassfehler von 0° (Test-Eigenschaft
`propwash.test.compassErrorDeg` aus Propwashs `docs/api-compass.md` §4, gesetzt vor dem Flugstart, danach wie vorher;
`CompassProperty` setzt und stellt zurück), `WorldRestart` speichert die Welt, verlässt sie (der integrierte Server endet) und öffnet sie neu; S13
prüft danach, dass ein neuer Server läuft und die Drohne aus dem Speicherstand geladen ist.

## 7. Bekannte Grenzen

- Eine abgestellte Drohne feuert: Propwash hebt den Schuss im Stand um die Hubhöhe (Unterkante der Hitbox minus tiefste
  Ecke der Halterung) an, die Geschosse starten über dem Boden (§10). Steht ein voller Block vor der Mündung, meldet
  Propwash `BLOCKED` (Warnung BARREL BLOCKED).
- Propwash rechnet die volle Beladung ins Flugmodell, bis Halterung und Beladung zusammen 20 kg wiegen
  (`AccessoryPayload.MAX_LADEN_GRAMS`). Schon acht TNT (4,8 kg) halten eine 5″ am Boden (Schub/Gewicht 1,02), sechs
  (3,6 kg) fliegen mit 1,31 noch.
- Wie viel eine Drohne trägt, bestimmt die Physik von Propwash (design §2.1.4). Mit Halterung und vier Tränken bzw. vier
  TNT kommt die 5″ auf Schub/Gewicht 2,69 bzw. 1,82, die Long-Range-7″ mit Li-Ion auf 2,05 bzw. 1,44 und eine 7″ mit
  LiPo (`b6s_1300`) auf 3,40 bzw. 2,33.
- Vanilla streut Pfeile aus dem Dispenser mit etwa 2,6° Standardabweichung; einzelne Pfeile liegen öfter mehr als 5°
  neben `forward` (Windkugeln etwa 2,9°). Der Selbsttest verlangt je Geschoss höchstens 10° und ab drei Schüssen im
  Mittel höchstens 5°.
- Der kleinste Rahmen mit Halterung vorn ist der 3″-Cinewhoop (`whoop_65` hat nur unten eine Halterung).

## 8. Stand der Prüfung (20.10.2026, Propwash 0.6.0-dev.4, auf main mit md-osd-ux, md-mobs-1 und md-spear)

| Prüfung | Fabric | NeoForge |
|---|---|---|
| `:common:test` (gesamt) und `checkPortable` | 140 von 140 grün | – |
| Selbsttest `dispenser/` (S2–S7, S13, Aufräumen) | 8 von 8 PASS, 16 Fotos EN/DE | 8 von 8 PASS |
| Server-Rauchtest mit D3, D5, D7 | PASS, keine Client-Klasse | PASS, keine Client-Klasse |

- D3: nur mit `payloads_hit_players` an und PvP an treffen Pfeil (10 → 7), Schadenstrank (10 → 4) und Gifttrank; in den
  drei anderen Kombinationen kein Schaden und kein Gift; der Heiltrank heilt in allen vier (10 → 14).
- D5: 100 Drücke in 2 s, 47 nimmt Propwashs Ratenbegrenzung an, 10 Schüsse im Abstand von 4 Ticks, Auslöser verbunden.
- D7: im Spawnschutz und im Abenteuermodus sind TNT, Wassereimer, Feuerzeug, Knochenmehl und Kürbis `DENIED`, kein Block
  verändert, Munition bleibt; Gegenprobe im Überlebensmodus außerhalb: TNT `PLACED`, Pfeil `FIRED`.

Nachtrag 22.10.2026 (Propwash 0.6.0-dev.7, API-Stand B): `:common:test` 166 von 166 grün, `checkPortable` grün;
Selbsttest `dispenser/` Fabric und NeoForge je 8 von 8 PASS, S7 steigt mit sechs TNT auf 30 m und wirft vier TNT im
Dauerfeuer und zwei auf die Plattformen; Server-Rauchtest beider Loader PASS mit 26 Prüfungen, keine Client-Klasse;
`runServerD6` (Fabric) PASS, D7 dort unter vorübergehendem Spawnschutz 16.

## 9. Vorderseite (Befund OWNM-3, 24.10.2026)

- Die Vorderseite der Halterung (Element `front_plate`, Nordseite, im Drohnen- und im Lack-Modell) verweist auf
  `minecraft:block/dispenser_front` mit der ganzen Textur (`uv` 0…16). Mob Defense liefert keine Kopie; Ressourcenpakete,
  die den Vanilla-Dispenser ändern, ändern die Halterung mit. Das eigene Mündungs-Element davor ist entfallen, die Öffnung
  zeigt die Vanilla-Textur.
- Minecraft 26.3 backt ein Item-Modell nur aus einem Atlas. Deshalb liegen die eigenen Texturen der Dispenser-Halterung im
  Block-Atlas (`textures/block/part/accessory/dispenser_mount.png` und `_paint.png`, Generator `tools/assets/mdstyle/writer.py`);
  Speer-Halterung und Booster bleiben unter `textures/item/part/`.
- Im Web-Export steht der Verweis unter `manifest.json#external_refs`; der Web-Konfigurator der Website rechnet bisher mit
  genau einer Textur je Modell (offen für den Website-Strang, sobald Mob Defense dort erscheint).

Stand der Prüfung 24.10.2026 (Propwash 0.6.0-dev.8, Fix OWNM-1 bis OWNM-3, auf main mit F-OWNM-4): `:common:test` 254
Tests, 253 grün, 1 gesperrt; Selbsttest `dispenser/` und `ownm_dispenser/` Fabric (22 Fotos) und NeoForge je 13 von 13 PASS;
`covmda_dispenser/` und `covmdb_dispenser/` NeoForge 27 von 27, Fabric 27 von 28; Server-Rauchtest beider Loader PASS.

Stand nach dem Fix OWNC5B-1 (27.10.2026, Propwash 0.6.0-dev.9): Ursache war, dass `DispenserBehavior` nach jedem Versuch nur
`ready_at = jetzt + max(1, dispenser_cooldown_ticks)` setzte; feste Mindestzeit und eingestellter Abstand wirkten nur im
Halte-Takt (`tick()`). JUnit `FireCadenceTest` und der Selbsttest-Schritt „Auslöser im Takt wie der Flugplaner“ waren vor
dem Fix rot (3 von 261 Tests; Fabric und NeoForge 24 Pfeile bei 24 Auslösern im Abstand 2 statt 4 und 6 Ticks), danach
`:common:test` 261 Tests, 260 grün, 1 gesperrt; Selbsttest `dispenser/` und `ownm_dispenser/` Fabric (mit Fotos) 15 von
15, NeoForge 14 von 14 PASS (neuer Schritt: 12, 8 und 6 Pfeile im Abstand 4, 6 und 8 Ticks); `covmda_dispenser/` und `covmdb_dispenser/`
NeoForge 27 von 27, Fabric 27 von 28 („Dauerfeuer auf Zombies“ 1 Treffer, im zweiten Lauf allein PASS mit 3 Treffern);
Server-Rauchtest beider Loader PASS (D5 unverändert 10 Schüsse, kleinster Abstand 4 Ticks).

- „Dauerfeuer auf Zombies“ (covmda) bleibt wackelig und ist seit dem Hebel aus §2.1 öfter rot (11 Läufe mit dem Fix: 6 grün,
  3× kein Treffer, 2× 15° Neigung 3 s nach der Salve; auf dem Stand vor dem Fix 2 von 2 grün, nur mit dem neuen Hebel 3 von 4).
  Mit dem kürzeren, realistischen Hebel kippt jeder Pfeil die Nase weniger nach unten, und in etwa jeder dritten Salve fliegen
  die ersten Pfeile über den Zombie-Block, den der Schritt nach einem einzelnen Probeschuss stellt. Vorschlag: Zombies über
  9,5 bis 15,5 m oder Höhe aus den ersten Pfeilen der Salve.


Stand nach dem Fix AUTO21-1 (28.10.2026, Propwash 0.6.0-dev.10): Der Schritt „Wurftrank im Schwebeflug“ wirft jetzt alle
vier Tränke nacheinander. Vor dem Fix (Hebel für jeden Wurf) FAIL ab dem 2. Wurf (Fabric +17,8° / +32,8° / +73,7° /
+168,5°, 342 / 559 / 1147 / 3688 °/s), `DispenserRecoilTest` 2 von 3 Fällen rot. Nach dem Fix (§2.1, E26) Fabric und
NeoForge alle vier Würfe +0,0° und höchstens 2 °/s, die Windkugel zum Vergleich weiter +51,6 bis +51,9° (Hebel, E24), S4
kippt den Cinewhoop weiter (+177,1°). `:common:test` 265 Tests, 264 grün, 1 gesperrt; `checkPortable` und `build` grün;
Selbsttest `dispenser/`, `ownm_dispenser/`, `covmda_dispenser/` und `covmdb_dispenser/` NeoForge 40 von 40, Fabric (mit
Fotos) 39 von 40 (S7 „kommt nicht zur Ruhe“ nach dem Vorwärtsflug, TNT ohne Rückstoß, allein wiederholt PASS);
Server-Rauchtest beider Loader PASS mit 26 Prüfungen.

- „Dauerfeuer auf Zombies“: Pfeile drehen die Drohne seit E26 nicht mehr, die Nase bleibt in der Salve auf der Höhe des
  Probeschusses. Fabric und NeoForge je 4 Treffer (25 %), PASS.

Stand nach dem Fix P-MD-2 (02.11.2026, Propwash 0.6.0-rc.1): S13 lädt eine 5″ mit Dispenser und Raketen-Booster (5 Pfeile,
1 Heiltrank, Slot 3 leer, 2 TNT, Slot 2 gewählt, Rakete mit Flugdauer 2 und einem Stern), speichert die Welt, verlässt
sie und öffnet sie neu, statt die Entity nur per NBT zu speichern und zu laden. Gegenprobe mit einer vorübergehenden
Störung (eine aus dem Speicherstand geladene Drohne verliert die Rakete): der alte S13 PASS, der neue FAIL „Rakete keine
statt 1×firework_rocket (Flugdauer 2, Sterne 1)“. Ohne Störung Selbsttest `dispenser/` und `booster/` (die Booster-Schritte
laufen nach dem Neustart wie im Vollauf) Fabric und NeoForge je 11 von 11 PASS; Welt nach 6 Ticks gespeichert und nach
28 bis 29 Ticks neu geöffnet, Munition, Slot 2 und Rakete am Server und am Client gleich, am Item, nach dem Abstellen und
in der Werkbank, die Munition und Rakete zurückgibt.

Stand nach dem Fix RC3P2-1 (04.11.2026, Propwash-Integrationsstand mit RC3P-2, `integration` 4c64969d2): Vor dem Fix FAIL
in S2 und S4 „Rückstoß 1.80 N·s statt 3.50“ (fest verdrahtet 0,05 × 20 + 2,5), JUnit `WindChargeRecoilTest` rot (3,5
statt 1,8 N·s). Mit der Erwartung aus Propwash, aber noch mit dem Hebel überschlug sich der Cinewhoop in S4 (+125,6°,
1356 °/s), `DispenserRecoilTest` rot mit Angriffspunkt (0; −0,014; −0,032). Nach dem Fix (E27) S4 auf Fabric und
NeoForge 3,35 m/s zurück, Neigung höchstens 0,0°, Drehrate höchstens 1 °/s; S2 5″ 2,87 bzw. 2,88 m/s zurück, +0,0°;
„Windkugel zum Vergleich“ (`ownm_dispenser/`) 2,30 bzw. 2,25 m/s, +0,0° (vorher +51,6 bis +51,9°); „Windkugel fängt
sich“ (`covmdb_dispenser/`) 2,34 m/s bei 1,80 N·s an 0,775 kg (101 %), Lage höchstens 0,9°. Selbsttest `dispenser/`
Fabric (mit Fotos) und NeoForge je 8 von 8 PASS, `ownm_dispenser/` je 7 von 7 (Fabric im ersten Lauf „Wurftrank im
Schwebeflug“ mit 26,1 m/s knapp außerhalb der Toleranz, im zweiten PASS); `covmda_dispenser/` und `covmdb_dispenser/`
bis auf „Lauf blockiert und leer“ und „Zwei Piloten (simuliert)“ PASS; beide nicht von RC3P2-1. „Lauf blockiert und
leer“ scheitert gegen diesen Propwash-Stand auch ohne den Fix und läuft mit Propwash 0.6.0-rc.2 grün; „Zwei Piloten
(simuliert)“ wackelt unabhängig vom Propwash-Stand, auch mit 0.6.0-rc.2 (Ursachen und Fix: Stand nach dem Fix RC3P4).
Server-Rauchtest beider Loader PASS mit 26 Prüfungen.

Stand nach dem Fix RC3P4 (05.11.2026, Propwash-Integrationsstand `integration` 7c4ae08ae, Gegenprobe mit 0.6.0-rc.2):

- S4 (RC3P4-1): „spürbar zurückgestoßen“ verlangte nur eine Tempoänderung über 0 und lief auch ohne Stoß grün (Gegenprobe
  mit abgeschaltetem Impuls: PASS mit 0,00 m/s). Jetzt muss das Tempo nach hinten um mindestens 50 % von Impuls/Masse
  zunehmen, mit dem Impuls aus dem `DispenseResult` von Propwash und der Masse der Drohne vor dem Schuss
  (`ExpectedRecoil.noticeable`). Integrationsstand: 3,35 m/s (NeoForge 3,36 m/s), nötig 1,68 m/s bei 1,80 N·s an 0,537 kg;
  rc.2: 6,51 m/s, nötig 3,26 m/s bei 3,50 N·s. Gegenprobe mit abgeschaltetem Impuls jetzt FAIL „Tempo 0.01 m/s zurück
  (nötig 1.68 m/s …): Rückstoß nicht spürbar“.
- „Lauf blockiert und leer“ (RC3P4-3): Der Test setzte die Wand fest vor den Sockel, bevor der Flug startete; die 5″ ragte
  mit der Hitbox (Halbbreite 14,5 cm, Mitte der Drohne 10 cm vor der Kante) 4,5 cm in die Wand. Seit Propwash F-VINE1-1 (nach
  rc.2) schiebt Propwash eine Drohne, die in Blöcken steckt, beim Flugstart auf dem kürzesten Weg heraus: 4,5 cm zurück,
  die Mündung lag danach 1,7 cm vor der Wand statt 2,8 cm darin, Propwash feuerte zu Recht. Die Mündung sitzt 12,8 cm
  vor der Mitte der Drohne und damit innerhalb der Hitbox; eine geflogene 5″ kann ihre Mündung deshalb nicht in einen vollen
  Block drücken. Jetzt liest der Test nach dem Flugstart die Mündung über einen leeren Schuss (`DispenserRig.muzzle`), setzt
  den Stein in diesen Block und den darüber und schießt im selben Tick. Integrationsstand Mündung +2,8 cm über der Kante,
  rc.2 +3,6 cm (Anker vorn 8 mm weiter vorn), beide BLOCKED.
- „Zwei Piloten (simuliert)“ (RC3P4-4): Drohne B flog autonom mit dem Testspieler md_cov_pilot_b als Besitzer. Der steht in
  keiner Spielerliste, Propwash beendet den Flug deshalb nach dem Start mit `OWNER_OFFLINE` (gemessen 15 Ticks danach), B
  landet und zählt für die Mob-Reaktionen nicht mehr. Ob die Zuteilung zu B vorher noch gesehen wurde, hing vom Zeitpunkt
  ab, daher wackelte der Schritt auch mit rc.2. Jetzt fliegt B mit dem Spieler des Clients als Besitzer, der Schritt
  wartet, bis beide Drohnen fliegen und B ruhig schwebt (35 bis 36 Ticks), und setzt dann je fünf Zombies vor jede Drohne.
  Fabric 5 Läufe hintereinander PASS mit je 5 und 5, dazu je ein Lauf auf NeoForge und mit rc.2.
- Spielertexte (RC3P4-2): Modrinth EN und DE sagen jetzt, die Windkugel stoße Cinewhoop und 5″ spürbar zurück und beide
  blieben fliegbar; E26 verweist auf E27. JUnit `WindChargeTextsTest` prüft die Spielertexte.

Tests gegen den Integrationsstand: `:common:test` und `fastcheck test` 283 Tests, 282 grün, 1 gesperrt; `build`,
`checkPortable` und `fastcheck all` grün; Selbsttest `dispenser/` und `ownm_dispenser/` Fabric und NeoForge je 14 von 14,
`covmda_dispenser/` und `covmdb_dispenser/` je 27 von 27 PASS; Server-Rauchtest beider Loader PASS mit 26 Prüfungen.
Gegen 0.6.0-rc.2: `build` mit 282 grünen Tests, S4, „Lauf blockiert und leer“ und „Zwei Piloten“ auf Fabric PASS.

Stand nach dem Fix AUTO58-2 (05.11.2026, Propwash 0.6.0-rc.2; Kompass-Code gleich im Integrationsstand `integration`
4386c3b58): S7 kam nach dem TNT-Vorwärtsflug in 600 Ticks nicht zur Ruhe (Fabric 1 von 3, „5.66 m/s waagerecht,
-0.19 m/s senkrecht“). Ursache: Propwash würfelt je Flug einen Kompassfehler, in etwa jedem zehnten Flug ein Störfeld mit
21,5–52,5°, und ab etwa 30° kreist das Positionshalten (Toilet Bowl, Propwash `docs/api-compass.md` §1). `HoverAssist.hold`
läuft über `holdPosition`; der Regler fordert höchstens 4 m/s, die Drohne fliegt um den Fehler versetzt mit rund
4 m/s / cos(Fehler) im Kreis (bei 45° 5,66 m/s), im Vorwärtsflug entsprechend schneller als befohlen (TNT-Tempo bis
8,6 m/s bei 6 m/s). Nachgestellt mit 45° für den ganzen Lauf: S7 Fabric FAIL „kommt nicht zur Ruhe: 5.80 m/s
waagerecht, 0.02 m/s senkrecht, Kompassfehler +45.4°“, TNT 8,44 m/s bei Propwash-Tempo 8,69 m/s.

- Fix: `Pilot.fly` legt vor dem Flugstart einen Kompassfehler von 0° fest und stellt die Eigenschaft wieder her, sobald
  das Flugmodell läuft (`Pilot.land` zur Sicherheit auch); alle Schritte mit `Pilot.fly` fliegen damit ohne gewürfelten
  Kompassfehler. „ruhig“ nennt in S7 und in „Kompass fest“ den Kompassfehler.
- Neuer Schritt „Kompass fest“: ohne Fix Fabric FAIL „kommt nicht zur Ruhe: 5.62 m/s waagerecht, -0.11 m/s senkrecht,
  Kompassfehler +43.3°“, NeoForge 5,91 m/s bei +44,5°; mit Fix PASS (Kompassfehler im Flug −0,5 bis −0,6°, gesund).
  Mit 45° für den ganzen Lauf auch S7 PASS (Kompassfehler −0,0° und −0,3°).

Tests (nach dem Rebase auf `main` mit F-AUTO58-1): `:common:test` und `fastcheck test` 287 Tests, 286 grün, 1 gesperrt;
`build`, `checkPortable` und `fastcheck all` grün; Auswahl des Befunds „dispenser/,Lauf blockiert,Zwei Piloten“ Fabric und
NeoForge je 11 von 11 PASS (S7 hält bei 0,2 bis 0,6 m/s, Kompassfehler −0,7 bis +1,5°). Vor dem Rebase: Selbsttest
`dispenser/` und `ownm_dispenser/` Fabric (mit Fotos) und NeoForge je 15 von 15, `covmda_dispenser/` und
`covmdb_dispenser/` je 27 von 27 PASS; Fabric `spear/`, `covmdb_booster/`, `covmda_ui/` 18 von 18, `mob/` und
`covmdc_mob/` 44 von 45 („Mob je Art: Wither“ mit Server-Autonomie ohne `Pilot.fly`, allein wiederholt PASS);
Server-Rauchtest beider Loader PASS mit 26 Prüfungen.

---

## 10. Unter dem Rahmen (Befund FB2M-1, 09.11.2026)

Owner nach dem Spieltest von 0.6.0-rc.3: Das Dispenser-Modul hing als ganzer Block vor der Drohne, es „müsste bei jeder
Drohne so 90 % weiter hinten sein“. Ursache war die Regel für den Platz vorn in Propwash: Der Quader liegt ganz vor und
unter dem Anker (z −Tiefe bis 0, y −Höhe bis 0), der Anker sitzt an der Vorderkante der Bodenplatte. Propwash bietet seit
der Add-on-API 1.8 (F-FB1-9, `0.6.0-dev.14`) zwei Felder zum Einrücken; Mob Defense nutzt sie:

```json
"slot": "front", "size_mm": [62.0, 58.0, 64.0], "recess_mm": 58.0, "drop_mm": 1.0
```

- **`recess_mm` 58** = 0,9 × 64 mm Tiefe (gerundet). Das Gehäuse sitzt unter der Bodenplatte, vor den Anker ragen nur noch
  6 mm, also die Steinfront (`front_plate`, 7,7 mm tief). Mündung (`MountPose#front`), Mitte, Flugmodell (Masse,
  Trägheit, Schwerpunkt, Bodenpunkte) und Zeichenposition rückt Propwash mit; das Modell bleibt am Anker entworfen.
- **`drop_mm` 1** statt der wörtlichen Vorgabe „Oberkante an die Unterseite der Bodenplatte“ (≈ 3 mm): Die Anker vorn liegen
  je Rahmen verschieden hoch. Bei den meisten Rahmen liegt der Anker an der Oberkante der Platte (y 0 bzw. −0,3), bei
  `cinewhoop_3`, den Just-More-Parts-Cinewhoops `cw_25_duct` und `cw_35_duct` und den drei Camera-Drones-Rahmen an
  der Unterseite. Ein Wert gilt für alle; bei 3 mm bliebe an diesen sechs Rahmen 2,4 bis 3 mm Luft (Propwash verlangt
  höchstens 1 mm, an `cinewhoop_3` scheitert dann Propwashs Selbsttest „Zubehör am Rahmen“). 1 mm ist der größte Wert, der
  an allen 25 Rahmen mit Halterung vorn höchstens 1 mm Luft lässt; an den übrigen steckt die Oberkante 1 bis 3 mm in der
  Platte.
- **Propwash-API 1.8** ist nötig (`propwash_api_version=1.8`, `MobDefense.REQUIRED_API = "1.8"`). Propwash bis 0.6.0-rc.3
  kennt die Felder nicht, ignoriert sie mit Warnung und hinge den Dispenser wieder vor die Nase; `requireVersion` bricht
  dort mit klarer Meldung ab.

Gemessen an den Modellen (JUnit `DispenserUnderFrameTest`, Selbsttest „Unter dem Rahmen“), Propwash 0.6.0-dev.14, Just More
Parts 1.2.0-rc.3, Camera Drones 1.0.0-rc.3:

| | ohne Einrücken | mit 58 / 1 mm |
|---|---|---|
| vor dem Anker | 64 mm | 6 mm (Steinfront) |
| Luft zum Rahmen | 0 bis 9,5 mm (Just-More-Parts-Anker 8 mm vor der Platte) | 0 bis 1,0 mm |
| Oberseite vom Rahmen bedeckt | 0 bis 58 % | 30 bis 100 % |
| Rahmen steckt im eingerückten Teil | – | 0 bis 5,8 mm (Arme, Platte, gewollt) |
| Speer-Halterung (Platz unten, 30° geneigt) im Gehäuse | 10,8–12,1 mm (Propwash), 0–9 mm (JMP) | 27,4–28,4 mm (Propwash), 0–28,1 mm (JMP) |

Grenzen, die bleiben (nur Darstellung, Flugwerte ändern sich nicht):

- **Speer-Halterung:** Sie hängt am Platz unten direkt hinter dem Anker vorn und steckt jetzt bis 28 mm tief im Gehäuse;
  der Speer selbst tritt oben an der Steinfront aus (Seitenbild `dispenser_under_propwash_freestyle_5_spear`). Beide
  zusammen sind weiter erlaubt.
- **Akku unten:** An `race_5` und `race_5_stretched` liegt der Akku unter dem Rahmen und ragt in den hinteren Teil des
  Gehäuses (Propwash misst 29 mm, frei bis 23 mm Einrücken).
- **Kamera-Rahmen:** Der Dispenser nimmt dort den Platz des Gimbals ein (beide vorn); `cine_frame` steckt mit dem Rumpf
  3,5 mm im hinteren Teil des Gehäuses.

Tests:

- JUnit `dispenser.DispenserUnderFrameTest`: `recess_mm` = 0,9 × Tiefe, `drop_mm` über 0 und höchstens 1 mm, der Builder
  der API nimmt beide Werte; an jedem Rahmen mit Halterung vorn aus den Modellen (Propwash aus dem Klassenpfad, Just More
  Parts und Camera Drones nur mit `MOBDEFENSE_JMP_JAR=<jar>` bzw. `MOBDEFENSE_CAMDRONES_JAR=<jar>`): höchstens 10 % der Tiefe
  vor dem Anker, höchstens 1 mm Luft, höchstens 2 mm Überschneidung vor dem Anker, Oberseite zu mindestens 25 % vom Rahmen
  bedeckt; die Tiefe der Speer-Halterung im Gehäuse steht im Log. Die Messung (`dev.dispenser.UnderFrame`) liest Elemente,
  Drehung und `transformation` der Item-Modelle wie Propwash (1 Modell-Einheit = 8 mm).
- Selbsttest `dispenser/` „Unter dem Rahmen“ (`UnderFrameSteps`): baut den Dispenser an jeden angemeldeten Rahmen mit
  Halterung vorn (Vorlage des Rahmens, sonst baubare Teile über `BuildAccess#check`), stellt ihn auf einen Steinblock, prüft
  aus der `MountPose` des Schusses Mündung (höchstens 6,4 mm vor dem Anker) und Mitte (58 − 32 mm dahinter, 29 + 1 mm
  darunter), den Pfeil an der Mündung (im Stand hebt Propwash ihn um die Hubhöhe an) und die Lage am Rahmenmodell wie die
  JUnit-Messung, und macht je Rahmen ein Seitenbild `dispenser_under_<namensraum>_<rahmen>` (dazu `freestyle_5` mit
  Speer-Halterung). Just More Parts und Camera Drones nur mit ihrem Jar im Run-Ordner `mods/`. `DispenserLog.Entry` trägt
  dafür die `MountPose` des Schusses.
- `DispenserRig.onPillar` setzt die Drohne so auf die Kante, dass die Mündung wie bisher 2,8 cm über die Kante ragt
  (`DispenserRig.muzzleReach` aus Anker, Tiefe und `recess_mm`, mindestens 3 cm Auflage); ohne Einrücken ist das genau der
  alte Abstand von 10 cm.

Ohne Fix: JUnit 2 von 2 rot („recess_mm = 0,9 × Tiefe 64.0 mm ==> expected: <58.0> but was: <0.0>“, alle Rahmen
„vor dem Anker 64.0 mm“), Selbsttest Fabric FAIL an allen 25 Rahmen („Mündung 64.0 mm vor dem Anker (erlaubt 0 bis
6.4 mm)“). Mit Fix: JUnit grün, Selbsttest Fabric und NeoForge PASS an 4 Propwash-, 18 Just-More-Parts- und 3
Camera-Drones-Rahmen.

API-Befund an Propwash (accessory-gaps, offen): Eine abgestellte Drohne mit eingerücktem Zubehör, die in der Luft
steht, sinkt nur mit 8 mm je Tick (`onGround` true, Tempo 0) statt zu fallen; ohne Einrücken fällt sie. Aufgefallen in
„Windkugel an der Wand“, das seinen Platz beim Anmelden statt zur Laufzeit nahm und die Drohne 62 Blöcke über dem Boden
abstellte (jetzt behoben).
