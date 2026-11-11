# Propwash: Mob Defense – Design

| | |
|---|---|
| Stand | 17.10.2026, Design und Repo-Gerüst (Phase 0) |
| Add-on | „Propwash: Mob Defense“, Mod-Id `mobdefense`, Paket `de.lardtom.mobdefense`, Marke lardtom |
| Ziel-Basis | Propwash FPV 0.6.0 (Add-on-API 1.7, Netzprotokoll 17), Minecraft 26.3, Fabric und NeoForge, Java 25 |
| Gerüst | baut heute gegen Propwash 0.5.2 (API 1.6) aus mavenLocal; Umstieg auf `0.6.0-dev.2` (API-Stand A), Schlussprüfung gegen das fertige 0.6.0 |
| Quellen | Owner-Notiz zu Mob Defense (12.10.), Ideenliste mit Owner-Kommentaren (`addon-idee-payloads.md`), Roadmap-Notizen bis 17.10. abends, Discord-Auswahl 17.10., 0.6.0-Entwürfe `addon-api-namespace.md` und `README.md` (Design-Runde 0.6.0), API 1.6 im Integrationszweig, Backport-Plan 1.21.1, Just More Parts als Vorbild |

Begriffe: „Halterung“ = Anbaupunkt der Drohne (`top`, `bottom`, `front`); „Zubehör“ = Bauteil auf einer Halterung;
„Aktion n“ = Propwashs Zubehör-Aktion 1–4; „API-Stand A/B“ = `0.6.0-dev.2` (Typen ohne Verhalten) bzw. `0.6.0-dev.4`
(alle Funktionen). Ids ohne Namensraum gehören zu `mobdefense`.

---

## 1. Ziel und Owner-Zitate

### 1.1 Ziel

Mit Vanilla-Mitteln wird aus einer FPV-Drohne ein Werkzeug gegen Mobs, und die Mobs wehren sich. Drei Zubehörteile
(Dispenser vorne, Speer unten, Raketen-Booster oben) sind echte Propwash-Bauteile mit Gewicht und Luftwiderstand. Die
Beladung wiegt mit, eine voll beladene Drohne fliegt spürbar träger. Jeder feindliche Mob hat eine eigene Reaktion auf
Drohnen. Server bekommen Welt-Regeln für alles, was Spieler oder Welt verändern kann. Das Add-on kommt ohne Mixins in
Propwash-Interna aus und nutzt nur `de.lardtom.propwash.api` (Mixins nur in Vanilla-Klassen, Abschnitt 4.1).

### 1.2 Owner-Zitate (wörtlich aus den Kommentaren der Ideenliste)

Hinweis: Wird das Repo veröffentlicht, ersetzt eine sinngemäße Fassung diesen Abschnitt (wie Propwash-Entscheidung E31).

- Name: „ich glaube Mob Defense klingt am besten“
- Dispenser: „der dispenser ist immer nach vorne gerichtet. zudem hat der dispenser 4 statt 9 slots“
- Windkugel: „solange man den wind charge mit dem dispenser feuert ja“
- Abwurf-Schacht und Eimer: „das lassen wir erstmal raus (das mit dem eimer ebenfalls weil man sonst einfach in der luft
  irgendwelche wasser / lava säulen erstellen könnte oder?“
- Wurftränke: „mit im dispenser“
- Speer: „hier sollten es explizit speere sein“
- Rakete: „das finde ich bisschen lustig das machen wir, man kann aber nur eine rakete dran machen“
- Mobs: „zombies sollten angelockt werden, skelette sollten auf die drohne schießen, phantome sollten die drohne jagen,
  blazes und ghasts schießen auf die drohne usw. also quasi jedes hostile mob soll eine interaktion mit der drohne haben“
- Regeln: „und mob interaction sollte auch togglebar sein“
- Sculk: „joa vielleicht aber absolut kein muss“
- Technik: „eher für 0.6.0“
- Abgelehnt: Schild „finde ich nicht gut“, Scheinwerfer „finde nich nicht gut“, Glocke „finde ich nicht gut (aber wenn
  man mit einer drohne gegen eine glocke fliegt sollte diese glocke läuften)“, Köder „auch nicht cool“, Leine „nein“,
  Netz „ne“, Angel „nein“, Totem „nein“
- Ablauf (17.10. abends): „parallelisier alles so weit wie möglich“

Aus den Notizen (sinngemäß): Name soll nicht nach Krieg klingen; jedes Zubehör hat Gewicht und Luftwiderstand; die
Notiz nennt die Use-Taste als Auslöser (umgesetzt über Zubehör-Aktion 1, E4); Wasser- und Lavaeimer standardmäßig gesperrt, eine eigene Welt-Regel kann sie erlauben;
Speer-Schaden je nach Tempo wie bei Vanilla, aber schwächer als in der Hand (überholt: seit dem Owner-Playtest voller
Vanilla-Speerschaden, E8); Welt-Regeln für Payloads gegen Spieler,
TNT-Abwurf, Mob-Griefing durch Drohnen, Mob-Interaktion, echte Munition und Abklingzeiten. Die Glocke läutet seit
Propwash 0.5.2 bereits (`docs/design/drone-snags.md` §6), dafür ist hier nichts zu tun.

---

## 2. Inhalt im Detail

### 2.1 Zubehör

| Id | Name (DE / EN) | Halterung | Masse leer | Maße B×H×T | Stirnfläche | Strom | Inventar |
|---|---|---|---|---|---|---|---|
| `dispenser_mount` | Dispenser-Halterung / Dispenser Mount | `front` | 150 g | 62 × 58 × 64 mm | 0,0036 m² | 1,2 W (Servo) | 4 Slots |
| `spear_mount` | Speer-Halterung / Spear Mount | `bottom` | 40 g | 24 × 18 × 90 mm | 0,0004 m² | 0 W | 1 Slot, nur Speere |
| `rocket_booster` | Raketen-Booster / Rocket Booster | `top` | 28 g | 30 × 22 × 70 mm | 0,0005 m² | 0,2 W (Zünder) | 1 Slot, genau eine Rakete |

Alle drei sind normale Bauteile (`data/mobdefense/propwash_parts/accessory/<id>.json`, `"format": 1`): Werkbank,
Analyse, Flugmodell, Lackierung, Sharecode und Web-Konfigurator bekommen sie ohne Sonderweg. Die Beladung zählt über
`AccessoryBehavior#payloadGrams` (Tabelle 2.1.4), der Rest fällt auf `AccessoryRegistry#itemGrams` zurück.

#### 2.1.1 Dispenser-Halterung

- **Immer nach vorn.** Die Mündung ist `MountPose#front()`, die Richtung genau `MountPose#forward()`. Keine Drehung,
  kein Zielen außer mit der ganzen Drohne.
- **Unter dem Rahmen** (Owner 09.11., Befund FB2M-1): `recess_mm` 58 (0,9 × Tiefe) und `drop_mm` 1 aus Propwashs
  Add-on-API 1.8. Das Gehäuse sitzt an jedem Rahmen unter der Bodenplatte, vor den Anker ragt nur die Steinfront (6 mm).
  Die Absenkung ist 1 mm statt bis zur Unterseite der Platte, weil die Anker vorn je Rahmen an der Ober- oder Unterseite
  der Platte liegen und sonst an sechs Rahmen Luft bliebe (`docs/dispenser.md` §10). Mob Defense verlangt dafür API 1.8.
- **4 Slots**, je Slot höchstens 16 Stück bzw. die Stapelgröße des Items; TNT höchstens 4; Tränke und Eimer 1.
- **Auslösen:** Zubehör-Aktion 1, an der Funke auf einem Taster oder Schalter, auf der Tastatur Ziffernblock 9 (frei
  belegbar, auch auf eine Maus-Seitentaste). Der Rechtsklick beendet wie bei jeder Drohne den Flug (E4). Ein kurzer Druck
  feuert genau einmal; gehalten folgt der zweite Schuss nach dem Anlauf (Standard 500 ms), danach feuert der Dispenser im
  eingestellten Abstand (Standard 200 ms) weiter, beides im Tab „Mob Defense“ des K-Menüs einstellbar, nie unter 200 ms und
  nie unter der Welt-Regel `dispenser_cooldown_ticks` (E23, Owner-Playtest 23.10.). Aktion 2 wählt den nächsten belegten
  Slot (`SlotCycle.next`). Gefeuert wird der gewählte Slot, ist er leer, der nächste belegte (`SlotCycle.firing`).
- **Alles, was ein Dispenser kann**, über B1 (`AccessoryContext#dispense`):
  - Geschosse: alle Pfeile (auch Spektral- und Trankpfeile), Schneebälle, Eier, Wurf- und Verweiltränke,
    Erfahrungsfläschchen, Windkugeln, Feuerkugeln, Feuerwerksraketen (als Geschoss). Mündungstempo und Streuung wie beim
    Vanilla-Dispenser, Drohnentempo addiert, Besitzer = Pilot.
  - TNT: gezündet an der Mündung, fällt mit dem Tempo der Drohne (G5). Standardmäßig erlaubt; die Welt-Regel `allow_tnt`
    schaltet es ab (`TNT LOCKED`).
  - Alles andere nach Vanilla am Block vor der Mündung: Knochenmehl, Schere, Feuerzeug, Rüstung anlegen, Kürbis und
    Schädel setzen, Shulkerkiste setzen, Glowstone in den Seelenanker, Wachs, Pinsel, Boote und Loren, leerer Eimer
    schöpft. Was kein Verhalten hat, fliegt als Item nach vorn (Vanilla-Standard).
  - Wasser-, Lava- und Pulverschnee-Eimer sowie alle Eimer mit Tieren (Tag `dispenser_fluid_buckets`) setzen keine
    Flüssigkeit, solange `allow_fluid_buckets` aus ist (B1 `allowFluids(false)`, Ergebnis `REFUSED`, Warnung im OSD).
- **Rückstoß** aus dem Impuls des Geschosses (B1 `recoilNs`, Masse × Mündungstempo): Pfeil etwa 0,4 N·s, Wurftrank 9,6 N·s,
  Windkugel 0,05 kg × Mündungstempo plus 0,8 N·s Druckwelle, zusammen 1,8 N·s (Werte aus Propwash; stößt eine Whoop und
  eine 5″ spürbar zurück, beide bleiben fliegbar), TNT und Items ohne nennenswerten Rückstoß. Der Windstoß einer Windkugel
  aus dem Dispenser trifft Mobs, Spieler und Drohnen halb so stark wie eine Vanilla-Windkugel (Propwash).
  Mob Defense setzt den Stoß selbst an: alle Geschosse samt Windkugel stoßen die Drohne am Schwerpunkt zurück, ohne sie zu
  drehen (E26, E27).
- **Abklingzeit** `dispenser_cooldown_ticks` (Standard 4 Ticks wie der Vanilla-Dispenser). Propwashs Ratenbegrenzung
  (20 Aktionen je Sekunde) schützt den Server zusätzlich.
- **Echte Munition** (`real_ammo`, Standard an): ein Schuss verbraucht ein Item. Aus: der Slot muss belegt sein, das
  Item bleibt (B1 `consume(false)`, G11).
- **Töne:** Vanilla `block.dispenser.dispense` und `block.dispenser.fail` (leer, gesperrt, Lauf blockiert).

#### 2.1.2 Speer-Halterung

- Nimmt ausdrücklich **nur Speere**: Item-Tag `spear_mount_accepts` (enthält `#minecraft:spears`) **und** Komponente
  `minecraft:kinetic_weapon`. Dreizack, Schwerter und Stöcke werden abgelehnt. Modded Speere mit Tag und Komponente gehen.
- Der Speer liegt unter dem Rahmen, Spitze nach vorn, sichtbar für alle (B2), Darstellung im Maßstab 0,6, die Spitze
  ragt etwa 25 cm vor die Nase.
- **Treffer:** Am Server prüft das Add-on jeden Tick die Strecke der Speerspitze vom letzten zum jetzigen Tick
  (`MountPose`, Drohnentempo) gegen Lebewesen. Getroffen wird jedes Ziel höchstens einmal je `contact_cooldown_ticks` der
  Komponente (Vanilla 10). Rammt die Drohne ein Lebewesen mit der Nase voran (Körpertreffer von Propwash,
  `DRONE_HIT_ENTITY`, Ziel höchstens 60° neben der Nase), sticht der Speer ebenfalls, mit derselben Sperre: Die Hülle
  großer Rahmen reicht weiter nach vorn als die Spitze, sonst prallte die Drohne ab, bevor der Speer trifft (Befund
  AUTO20-2).
- **Schaden wie bei Vanilla:** Vanilla rechnet `1 + floor(v_rel × damage_multiplier)` mit `v_rel` in m/s
  (Blöcke je Sekunde) entlang der Blickrichtung. Mob Defense nimmt `v_rel` entlang der Nase, rechnet dieselbe Formel mit
  den Werten der Komponente und multipliziert mit `spear_damage_percent` (Standard 100 %, bis 24.10. 50 %; Steuerung
  nach Owner-Playtest, Befund OWNM-4), höchstens 15 Schaden. Schaden nur ab `min_relative_speed` der Schadensbedingung
  (Vanilla 4,6 m/s), Rückstoß und Abwerfen von Reitern ab den jeweiligen Schwellen der Komponente. Beispiel Eisenspeer
  (0,95): 15 m/s → 15; 30 m/s → 29 → 15 (Grenze); mit der Regel auf 50 %: 7 und 14.
- Schadensart `minecraft:spear`, Angreifer = Pilot, direkte Ursache = Drohne; PvP-Regeln und `payloads_hit_players`
  gelten. Verzauberungen auf Schaden und Feuer wirken, `lunge` nicht. Haltbarkeit −1 je Treffer, ein zerbrochener Speer
  verschwindet mit Vanilla-Ton.
- Töne aus der Speer-Komponente (`item.spear.hit`, `item.spear_wood.hit`), für Umstehende und direkt beim Piloten (auch
  wenn sein Körper außer Hörweite steht); dazu Krit-Partikel am Ziel und im OSD kurz `HIT`/`TREFFER` (Befund OWNM-4).

#### 2.1.3 Raketen-Booster

- Genau **eine** Feuerwerksrakete (`booster_accepts`, Stapel 1).
- Aktion 3 zündet: Schub entlang der Nase (`DronePhysics#push`, rahmenrelativ, Angriffspunkt Heck) wie bei der Elytra:
  gleiche Beschleunigung für jede Drohnengröße, 2 g, also `F = m × 19,6 N`, höchstens 60 N. Brenndauer nach
  Flugdauer der Rakete 1/2/3: 1,25 / 1,75 / 2,25 s. Eine 5″ (0,65 kg) wird in 1,25 s etwa 25 m/s schneller.
- Danach ist der Slot leer, Nachladen nur im Wartungsmenü (also nach der Landung). Keine Abklingzeit nötig.
- Rakete mit Sternen: Am Ende der Brenndauer explodiert sie wie an der Elytra, Schaden `5 + 2 × Sterne` an der Drohne
  und an Lebewesen im Umkreis nach Vanilla.
- Sichtbar: Rakete oben, Spitze nach hinten (B2); beim Brennen Funken und Rauch am Heck (Client, aus dem Zustand in B6),
  Töne `entity.firework_rocket.launch` und `entity.firework_rocket.blast`.

#### 2.1.4 Gewichte der Beladung

| Item | g | Item | g |
|---|---|---|---|
| Pfeil (alle Arten) | 20 | Windkugel | 50 |
| Schneeball, Ei | 60 | Feuerkugel | 80 |
| Wurf-, Verweiltrank | 350 | Feuerwerksrakete | 60 |
| Erfahrungsfläschchen | 300 | TNT | 600 |
| Knochenmehl | 15 | Eimer leer / voll | 250 / 1250 |
| Speer Holz / Stein / Kupfer | 180 / 260 / 280 | Speer Eisen / Gold / Diamant / Netherit | 300 / 340 / 280 / 360 |

Vier Tränke oder vier TNT machen jede Drohne deutlich träger. Am besten trägt sie eine 7″ mit LiPo, die Long-Range-7″
mit Li-Ion-Akku schlechter als die 5″. Die Werte liegen im portablen Kern (`dispenser.core.AmmoTable`,
`spear.core.SpearMass`) und werden per JUnit gegen Schub-Prüfstände getestet (Abschnitt 6.1).

### 2.2 Rezepte (Propwash-Stil)

Geformte Rezepte, Kategorie `equipment`, Gruppe `propwash_accessory` (gleiche Gruppe wie Greifer und LED-Streifen im
Rezeptbuch), freigeschaltet über Fortschritte, sobald man eine Zutat besitzt. Zutaten aus Vanilla und den
Propwash-Zwischenteilen (`propwash:circuit_board`, `propwash:copper_coil`, `propwash:carbon_plate`); nur gelesen, nie
im Namensraum `propwash` registriert.

| Ergebnis | Muster | Schlüssel |
|---|---|---|
| Dispenser-Halterung | ` B ` / `NDN` / ` C ` | B Platine, N Eisennugget, D Dispenser, C Kupferspule (Servo) |
| Speer-Halterung | `N N` / `PPP` | N Eisennugget (Schellen), P Carbonplatte (Schiene) |
| Raketen-Booster | `NFN` / ` P ` | N Kupfernugget, F Feuerzeug (Zünder), P Carbonplatte |

Alle drei Muster sind gegen alle Propwash-0.6.0- und JMP-Rezepte eindeutig (JUnit `RecipeUniquenessTest`).

### 2.3 Welt-Regeln (Tab „Welt-Regeln“, Abschnitt „Mob Defense“)

Angemeldet über B5 (`PropwashApi.worldRules()`), Texte `gamerule.mobdefense.<pfad>` und `….description`, bearbeitbar im
Einzelspieler und für Operatoren. Die Standardwerte stehen im portablen Kern `rules.core.RuleDefaults` (im Gerüst schon
vorhanden und getestet). Die Überschrift `mobdefense.world_rules.section` ist in EN und DE nur der Name „Mob Defense“, ohne
„Propwash:“, wie Propwash es für Add-on-Abschnitte vorsieht; sie gleicht damit dem Rückfall ohne Schlüssel (Mod-Name ohne
„Propwash:“) und dem Kreativ-Reiter.

| Regel | Typ | Standard | Wirkung |
|---|---|---|---|
| `mobs_react_to_drones` | an/aus | an | Mob-Interaktion insgesamt (Owner: abschaltbar) |
| `mob_reaction_range` | 8–64 | 24 | Reichweite in Blöcken, in der Mobs Drohnen bemerken |
| `mob_damage_to_drones_percent` | 0–400 | 100 | Schaden, den Mobs Drohnen zufügen |
| `payloads_hit_players` | an/aus | aus | schädliche Payloads (Geschosse, schädliche Tränke, Speer, TNT-Schaden) treffen Spieler; Heil- und andere positive Tränke wirken immer; Vanilla-PvP gilt zusätzlich |
| `allow_tnt` | an/aus | an | TNT aus dem Dispenser |
| `drone_griefing` | an/aus | an | Payloads dürfen Blöcke verändern (TNT-Sprengschaden, Feuer aus Feuerkugel und Feuerzeug, gesetzte Blöcke, Flüssigkeiten). Vanilla `tnt_explodes` gilt zusätzlich |
| `allow_fluid_buckets` | an/aus | aus | Wasser-, Lava- und Pulverschnee-Eimer aus dem Dispenser |
| `real_ammo` | an/aus | an | Schüsse verbrauchen Munition |
| `dispenser_cooldown_ticks` | 0–100 | 4 | Abklingzeit des Dispensers |
| `spear_damage_percent` | 0–100 | 100 | Anteil am Vanilla-Speerschaden |
| `sculk_hears_drones` | an/aus | an | nur mit dem optionalen Sculk-Strang (2.7) |

Was Mobs **an Blöcken** tun, wenn sie auf Drohnen reagieren (Creeper-Explosion), folgt wie immer Vanilla `mobGriefing`.

### 2.4 Tasten und Schalterfunktionen

| Funktion | Standard | Weg |
|---|---|---|
| Dispenser feuern | Aktion 1: Taster oder Schalter der Funke, Tastatur Ziffernblock 9; frei belegbar, auch auf Maus-Seitentasten | Aktion 1, Name über G2 |
| Nächster Slot | Aktion 2 (Ziffernblock 0) | Aktion 2 |
| Raketen-Booster zünden | Aktion 3 (Ziffernblock Enter) | Aktion 3 |
| FPV beenden | wie bei jeder Drohne: `key.propwash.drone_exit` (Standard Y) oder Rechtsklick | Propwash |

- Aktion 4 bleibt frei. Die Belegung im Tab „Modi“ zeigt die Bedeutung („Zubehör-Aktion 1 – Dispenser feuern“, G2).
- Beim ersten Flug mit Dispenser zeigt Propwash einmal den Hinweis mit Name und aktueller Belegung jeder Aktion (G2),
  etwa „Dispenser feuern: Ziffernblock 9“; ist Aktion 1 weder an der Funke noch auf einer Taste belegt, verweist er auf
  den Tab „Modi“. Doku und Flugschul-Szene empfehlen, Aktion 1 auf einen Taster der Funke zu legen (Dauerfeuer: Schalter).

### 2.5 OSD und HUD

OSD-Elemente über A2 (`PropwashClientApi.registerOsdElement`), im OSD-Editor in der Gruppe „Add-ons“ verschiebbar,
Text nur in druckbarem ASCII. Kürzel für Munition sind sprachneutral: `ARW` Pfeil, `SPC` Spektralpfeil, `TIP` Trankpfeil,
`SNW` Schneeball, `EGG` Ei, `SPL` Wurftrank, `LNG` Verweiltrank, `XPB` Erfahrungsfläschchen, `WND` Windkugel, `FIR`
Feuerkugel, `RKT` Rakete, `TNT`, `BKT` Eimer, `BON` Knochenmehl, `ITM` sonstiges.

| Element | Standard | Beispiel | Inhalt |
|---|---|---|---|
| `mobdefense:dispenser` | an, Spalte 1, Zeile 12 | `DSP 2 ARW 12` | gewählter Slot, Kürzel, Anzahl; `*` während der Abklingzeit; `DSP --` leer |
| `mobdefense:dispenser_slots` | aus | `[ARW]SPL WND ---` | alle vier Slots, gewählter in Klammern |
| `mobdefense:booster` | an, Spalte 1, Zeile 13 | `RKT RDY` / `RKT 1.2` / `RKT ---` | bereit, Restbrenndauer, leer |
| `mobdefense:spear` | aus | `SPR 87%` | Haltbarkeit |

Warnungen (A2 `registerOsdWarning`, 2 s sichtbar): `NO AMMO`, `BUCKET LOCKED`, `TNT LOCKED`, `BARREL BLOCKED`,
`NO PERMISSION`; optional `THREAT` (ein Mob zielt gerade auf die Drohne, 2.6). Deutsche Texte ohne Umlaute, weil der
OSD-Zeichensatz nur ASCII kann; die Wörter sind so gewählt, dass kein Ersatz nötig ist: `KEINE MUNITION`,
`EIMER GESPERRT`, `TNT GESPERRT`, `LAUF BLOCKIERT`, `KEIN RECHT`, `BEDROHUNG`. Dazu der Hinweis `HIT`/`TREFFER`
(1,5 s, Ton „Akzent“) nach jedem wirksamen Speer-Stich (Befund OWNM-4). Die Elemente wirken auch im Sichtflug
(A2 `OsdContext`).

### 2.6 Mobs reagieren auf Drohnen

**Grundregeln**

- Reagiert wird nur auf Drohnen, die scharf sind (in der Luft oder gelandet) oder im Autopilot fliegen (Heimflug, autonomer
  Flug). Abgestellte Drohnen bleiben unbehelligt, sonst zerlegen Zombies nachts jede geparkte Drohne; ebenso Drohnen, die
  mit der Fernbedienung verbunden, aber nicht scharf sind (auch nach einem Absturz). Beim Entschärfen enden laufende Angriffe
  und `THREAT` (Entscheidung 06.11.2026, Befund FB1M-1).
- Spieler bleiben das erste Ziel: Hat ein Mob ein Spielerziel, wechselt er nur zur Drohne, wenn sie weniger als halb so
  weit weg ist. Ohne Ziel nimmt er die nächste Drohne in `mob_reaction_range` mit Sichtlinie.
- Je Drohne reagieren höchstens die 12 nächsten Mobs; gesucht wird alle 10 Ticks, über die Entity-Id gestaffelt.
- Treffer an der Drohne laufen über Propwashs `DRONE_DAMAGED` und werden mit `mob_damage_to_drones_percent` skaliert;
  Explosionen und Windkugeln stoßen die Drohne über B3.
- Zuordnung über Entity-Tags `mobdefense:reacts/<art>`, damit Datenpakete und andere Mods ihre Mobs eintragen können.
  Ein JUnit-Test stellt sicher, dass jeder feindliche Vanilla-Mob in genau einem Tag oder auf der Ausnahmeliste steht.

| Art (Tag) | Mobs | Reaktion | Bedingung |
|---|---|---|---|
| `chase` | `zombie`, `husk`, `drowned` ohne Dreizack, `zombie_villager`, `zombie_nautilus` | werden angelockt, laufen bzw. schwimmen unter die Drohne, schlagen zu | Unterkante ≤ 2,5 Blöcke über dem Kopf oder gelandet |
| `brute` | `zombified_piglin` (nur wütend), `vindicator`, `piglin_brute`, `ravager`, `hoglin`, `zoglin`, `wither_skeleton` | wie `chase`; Ravager brüllt (Stoß), Hoglin und Zoglin schleudern die Drohne nach oben | wie `chase` |
| `archer` | `skeleton`, `stray`, `bogged`, `parched`, `illusioner`, `pillager`, `piglin` mit Armbrust | zielen mit Vorhalt (`DroneAccess#aim`) und schießen ihre eigenen Pfeile | Sichtlinie, 4–24 Blöcke |
| `fireball` | `blaze` (drei kleine), `ghast` (großer, explodiert) | schießen mit Vorhalt | Blaze ≤ 16, Ghast ≤ 64 Blöcke |
| `wind` | `breeze` | schießt Windkugeln, die Drohne wird weggestoßen | ≤ 16 Blöcke |
| `bullet` | `shulker` | Shulker-Geschoss folgt der Drohne (Vanilla nimmt jedes Entity als Ziel) | ≤ 16 Blöcke |
| `potion` | `witch` | wirft Schadenstränke; Treffer im Umkreis 2 schaden der Drohne | ≤ 10 Blöcke |
| `trident` | `drowned` mit Dreizack | wirft den Dreizack | ≤ 16 Blöcke |
| `caster` | `evoker` | Fangzähne unter tief fliegenden Drohnen, beschwört Vexe | Drohne ≤ 2 Blöcke über Boden bzw. ≤ 12 Blöcke |
| `flyer` | `phantom`, `vex` | jagen die Drohne in der Luft; Phantome mit Sturzangriff | Phantom: Drohne > 10 Blöcke über Boden oder nachts im Freien |
| `creeper` | `creeper` | läuft unter die tief fliegende Drohne und zündet | Drohne ≤ 4 Blöcke über Boden, Abstand ≤ 3 |
| `jumper` | `slime`, `magma_cube`, `sulfur_cube`, `spider`, `cave_spider` | springen an tiefe Drohnen, Spinnen klettern Wände hoch | Drohne ≤ 3 Blöcke über Boden oder an einer Wand |
| `beam` | `guardian`, `elder_guardian` | Laser auf Drohnen über oder im Wasser, Schaden nach 80 Ticks | ≤ 16 Blöcke |
| `stare` | `enderman`, `creaking` | Enderman: wird wütend, wenn die FPV-Kamera ihm ins Gesicht sieht, teleportiert sich zur tiefen Drohne oder greift sonst den Piloten an. Creaking: erstarrt unter dem Kamerablick, sonst läuft es zur Drohne | Kamerablick (G6) |
| `boss` | `wither` | schießt Wither-Schädel auf Drohnen | ≤ 32 Blöcke |
| `ground` | `silverfish`, `endermite` und jeder andere `Enemy` ohne Tag (auch aus Mods) | greifen gelandete oder ganz tief schwebende Drohnen in Reichweite an | Drohne ≤ 1 Block über Boden |
| `warden` (optional) | `warden` | hört Drohnen (2.7) und greift mit dem Schallstoß an | nur mit Sculk-Strang |
| Ausnahme | `ender_dragon`, `giant` | keine eigene Reaktion; der Flügelschlag des Drachen stößt Drohnen schon über B3 | – |

Umsetzung: Mobs mit `GoalSelector` bekommen beim Laden (Fabric `ServerEntityEvents.ENTITY_LOAD`, NeoForge
`EntityJoinLevelEvent`) ein Ziel-Goal der jeweiligen Art. Brain-Mobs (`piglin`, `piglin_brute`, `hoglin`, `zoglin`,
`breeze`, `creaking`, `warden`) steuert ein Reaktions-Takt am Server über die Erinnerung `WALK_TARGET` und direkte
Angriffe. Weil Drohnen keine `LivingEntity` sind, setzt kein Goal `Mob#setTarget`; der Drohnen-Zustand je Mob liegt in
einer schwachen Tabelle des Add-ons.

### 2.7 Sculk und Warden (optional, kein Muss)

Fliegende Drohnen senden alle 20 Ticks eine Vibration `GameEvent.ELYTRA_GLIDE` an ihrer Position, wenn ihre Lautstärke
(Schätzung aus Masse und Tempo; G8 ist nicht in API 1.7) mindestens 0,35 beträgt: leise Whoops schleichen, eine X-Class nicht. Wolle
dämpft wie in Vanilla. Wardens werden über die Vibration wütend auf die Drohne und greifen mit dem Schallstoß an
(eigene Linie aus Partikeln und Schaden, weil der Vanilla-Schallstoß nur Lebewesen trifft). Regel `sculk_hears_drones`.

### 2.8 Fortschritte, Kreativ-Tab, Flugschule

- Kreativ-Reiter über `PropwashApi.extensionTab("mobdefense")` wie bei JMP, Titel „Propwash: Mob Defense“. Propwashs
  eigener Tab zeigt die Teile nicht: Propwash nimmt Items von Add-ons mit eigenem Reiter dort heraus.
- Flugschul-Kapitel „Mob Defense“ mit drei Szenen (Dispenser beladen und feuern, Speer, Booster), mit den Items
  verknüpft (`linkFlightSchoolItems`).
- Vier Fortschritte (Kann, Strang `md-osd-ux`): „Luftunterstützung“ (Heiltrank aus der Drohne trifft einen Mitspieler),
  „Lanzenflug“ (Mob mit der Speer-Halterung besiegt), „Nachbrenner“ (Raketen-Boost), „Rückstoß“ (Windkugel aus dem
  Dispenser). Kriterium `minecraft:impossible`, vergeben im Code.

### 2.9 Verhalten auf Servern

- Das Add-on muss auf **Server und Client** liegen (Teile im Beitritts-Abgleich). Fehlt es beim Client, zeigt Propwash
  die Übersicht „Propwash: Mob Defense fehlt“ samt Version.
- Alles, was Welt oder Spieler verändert, läuft am Server: Aktionen kommen von Propwash geprüft an (Pilot, Epoche, Rate).
- Rechte: Blöcke setzen, zünden, schöpfen und sprengen nur, wo der Pilot bauen darf (Spawnschutz, Abenteuermodus,
  `mayInteract`, G3); sonst `NO PERMISSION` im OSD.
- PvP: Besitzer aller Payloads ist der Pilot. Mit Vanilla-PvP aus trifft nichts Spieler. `payloads_hit_players` filtert
  zusätzlich schädliche Payloads; positive Trankeffekte wirken immer (Heilen von Mitspielern ist ausdrücklich gewollt).
- Last: Mob-Suche gestaffelt (2.6), höchstens 64 Mob-Auswertungen je Tick und Level, keine Suche ohne scharfe Drohne oder
  Drohne im Autopilot.
- Dedizierter Server: kein Client-Code im gemeinsamen Pfad (Lehre aus dem Noclip-Befund), geprüft im Server-Rauchtest.
- Zuschauer einer Drohne sehen Speer und Rakete (B2); OSD-Elemente sieht nur, wer das Videobild hat.

---

## 3. Benötigte Propwash-API

### 3.1 Was API 1.6 schon hat (Propwash 0.5.2)

| Baustein | Nutzung |
|---|---|
| Teile als JSON (`parts().loadBundled`, `partItems().registerItems`), `AccessoryDefinition` mit Masse, Maßen, Stirnfläche, Strom | alle drei Zubehörteile |
| `AccessorySlot.FRONT/TOP/BOTTOM`, `MountPose` (`front`, `forward`, `up`, `right`, `at`) | Mündung, Speerspitze, Booster-Heck |
| `AccessoryBehavior` (`slots`, `accepts`, `maxStackSize`, `payloadGrams`, `onAction`, `tick`), `AccessoryContext` (`inventory`, `pilot`, `velocity`, `physics`, `actionHeld`) | Inventare, Dauerfeuer, Speer-Prüfung je Tick |
| Zubehör-Aktionen 1–4, Event `ACCESSORY_ACTION` | Feuern, Slot wählen, Booster |
| `DronePhysics#impulse`, `#push` (Grenzen 50 N·s, 500 N, 30 s) | Booster-Schub |
| `DroneAccess#view`, `dronesNear`, `nearestDrone`, `aim`, `obstacleDistance` | Mob-Reaktionen, Vorhalt |
| `DRONE_HIT_ENTITY`, `DRONE_DAMAGED` (`setAmount`, `cancel`) | Schaden durch Mobs skalieren; Speer-Stich beim Rammen (Befund AUTO20-2) |
| `AccessoryRegistry#contents` (Client synchronisiert), `itemGrams` | OSD, Gewichte |
| `extensionTab`, `registerFlightSchoolScene`, `linkFlightSchoolItems`, `requireVersion` | Reiter, Flugschule, Versionsprüfung |

### 3.2 Was der 0.6.0-Entwurf dazu bringt (API 1.7, `addon-api-namespace.md`)

| Baustein | Nutzung in Mob Defense |
|---|---|
| B1 `AccessoryContext#dispense` mit `DispenseOptions` (`speedScale`, `inaccuracy`, `allowFluids`, `applyRecoil`) und `DispenseResult` | Herzstück des Dispensers, auch im Backport |
| B2 `AccessoryBehavior#display(slot)` → `ItemDisplay` | Speer und Rakete sichtbar |
| B3 Event `DRONE_KNOCKBACK`, Stöße durch Explosionen und Windkugeln | Ghast, Creeper, Breeze, TNT, Windkugel |
| B4 `DroneAccess#loaded(ServerLevel)` | Reaktions-Takt, Sculk |
| B5 `PropwashApi.worldRules()` | alle Regeln aus 2.3 im Tab „Welt-Regeln“ |
| B6 `PropwashApi.droneData()` | gewählter Slot und Abklingzeit, Booster-Zustand (`Sync.TRACKING`) |
| A1 Namen der Zubehör-Aktionen (`actionNameKey`) und Hinweis beim ersten Flug (G2) | „Dispenser feuern“, „Nächster Slot“, „Booster zünden“ |
| B1-Ergänzungen: Rechte (`DENIED`), `consume`, Schutz vor eigenen Treffern, Tempo von TNT und Items, `triggerAction` (G3–G5, G7, G11) | Dispenser auf Servern, `real_ammo`, Tests auf dem dedizierten Server |
| B4 `DroneAccess#cameraPose` (G6) | Enderman und Creaking |
| B5 `presetValue` (G9) | Werte in der Voreinstellung `training` |
| A2 OSD-Elemente und -Warnungen | 2.5 |
| B10 `PILOT_CHANGED` | Booster und Dauerfeuer beim Pilotenwechsel beenden |
| C1/C2 Namensraum-Regeln | Mod-Id `mobdefense`, keine `propwash:`-Ids |
| PW3-Tabelle `data/mobdefense/propwash_share/table.json` (`sharecodes-pw3.md`) | Sharecodes mit Mob-Defense-Teilen, Kurzkennung 3 bei Aufnahme ins Verzeichnis |

Nicht genutzt: A1-Schalter (nur die Namen der Zubehör-Aktionen), A3 (Kollisionsregeln), B7–B9 (autonomer Flug, Operator,
Gimbal).

### 3.3 Lücken für API 1.7 und ihr Stand

Gemeldet an den 0.6.0-Strang API 1.7; seit 17.10. stehen die Ergebnisse im 0.6.0-Entwurf (`addon-api-namespace.md`
§3.19, dortige Entscheidungen E14, E35, E36, E39, E40). „Muss“ blockiert Mob Defense 1.0, „Kann“ hat einen Ausweg im Add-on.

| Nr. | Lücke | Ergebnis in API 1.7 | Strang | Rang |
|---|---|---|---|---|
| G1 | Use-Taste löst Zubehör aus | **nicht übernommen** (0.6.0-E14 bleibt): Der Rechtsklick beendet im Flug weiter das FPV. Gefeuert wird über Zubehör-Aktion 1 an Funke oder Taste; dafür zeigt Propwash die Namen der Aktionen und beim ersten Flug die aktuelle Belegung (G2) | – | entfällt |
| G2 | Bedeutung der Aktionen | `AccessoryBehavior#actionNameKey(int number)` → `Optional<String>`; Tab „Modi“ zeigt „Zubehör-Aktion 1 – Dispenser feuern“; Propwash zeigt beim ersten Flug einmal je Zubehörtyp Name und Belegung jeder Aktion | `api17-contract`, `addon-switches` | Muss (ersetzt G1) |
| G3 | Bau-Rechte beim Werfen | übernommen: `dispense` prüft vor TNT, Flüssigkeiten und Block-Verhalten `mayInteract`, Abenteuermodus und Spawnschutz des Piloten an der Mündung; Status `DispenseResult.Status.DENIED` | `accessory-gaps` | Muss |
| G4 | Eigene Geschosse treffen die eigene Drohne nicht | übernommen: Schutz, bis das Geschoss die Hülle verlassen hat, höchstens 10 Ticks | `accessory-gaps` | Muss |
| G5 | TNT und Items erben das Drohnentempo | übernommen: Drohnentempo plus Vanilla-Auswurf in `forward()` | `accessory-gaps` | Muss |
| G6 | Kamerablick am Server | übernommen: `DroneAccess#cameraPose(Entity drone)` → `Optional<CameraPose>`, Record `CameraPose(Vec3 eye, Vec3 look, Vec3 up, double verticalFovDegrees)` aus Linse, Uptilt und Sichtfeld des Baus | `api17-contract`, `accessory-gaps` | Muss |
| G7 | Aktion am Server auslösen | übernommen: `AccessoryRegistry#triggerAction(Entity drone, int number, boolean pressed, ServerPlayer actor)` → `boolean`; Rechte prüft der Aufrufer | `accessory-gaps` | Kann |
| G8 | Lautstärke der Drohne | **nicht übernommen** (0.6.0-E39: das Klangmodell läuft nur auf dem Client); Ausweg in 2.7: Schätzung aus Masse und Tempo über `DroneView` | – | Kann |
| G9 | Add-on-Regeln in Voreinstellungen | übernommen, typisiert: `WorldRules#presetValue(WorldRule<T> rule, String presetId, T value)` für die eingebauten Voreinstellungen `realistic`, `training`, `arcade` (eine Voreinstellung „Rennen“ gibt es nicht); geplant: `training` schaltet `mobs_react_to_drones` aus | `addon-world-rules` | Kann |
| G10 | Treffer durch Mobs | übernommen als Prüfschritt im Selbsttest (Nahkampf, Pfeile, Feuerbälle, Dreizack, Shulker-Geschosse; `DRONE_DAMAGED` mit dem Mob als Verursacher) | `accessory-gaps` | Muss (nur Bestätigung) |
| G11 | Werfen ohne Verbrauch | übernommen: `DispenseOptions#consume(boolean)` (Standard an) | `accessory-gaps` | Muss |

Kein Bedarf (geprüft): eigene Halterung hinten, Zubehör-Kontext außerhalb der Rückrufe, Haken für Trank-Effekte
(Vanilla-Seite im Add-on), Add-on-Regeln in Zonen (später wünschenswert, in 1.7 bewusst nicht drin).

---

## 4. Architektur, Datenformate, Netzwerk, Kompatibilität

### 4.1 Pakete

| Paket | Inhalt | Seite |
|---|---|---|
| `de.lardtom.mobdefense` | `MobDefense` (Init, Ids, Versionsprüfung) | gemeinsam |
| `dispenser` | `DispenserBehavior` (B1), `DispenserOsd` (Client-Unterpaket) | gemeinsam/Client |
| `dispenser.core` | **portabel**: `SlotCycle` (vorhanden), `AmmoTable` (Kürzel, Gramm, Stapelgrenzen), `FireGate` (Abklingzeit, Munition, Eimer, TNT, Rechte-Ergebnis) | rein Java |
| `spear`, `spear.core` | `SpearBehavior` (Spitzen-Prüfung je Tick), `SpearDamage` und `SpearMass` (portabel) | gemeinsam |
| `booster`, `booster.core` | `BoosterBehavior`, `BoostProfile` (Brenndauer, Schub, portabel), `client.BoosterParticles` | gemeinsam/Client |
| `mob`, `mob.reaction` | `DroneReactions` (Takt, Budget), eine Klasse je Art aus 2.6, `ReactionTags` | Server |
| `rules`, `rules.core` | `MobDefenseRules` (B5-Anmeldung), `RuleSpec`/`RuleDefaults` (vorhanden, portabel) | gemeinsam |
| `payload` | Filter „Payloads gegen Spieler“, Griefing-Prüfung | Server |
| `sculk` | optional (2.7) | Server |
| `net` | nur `ThreatPayload` (optional) | beide |
| `client` | `MobDefenseClient`, OSD-Anmeldung, Hinweise | Client |
| `mixin` | nur Vanilla-Ziele: `MobAccessor` (`goalSelector`), `PhantomAccessor`, `AbstractThrownPotionMixin` (Effekt-Filter), `PrimedTntMixin` (Griefing) | gemeinsam |
| `platform` | `Platform`, `Services` (vorhanden) | gemeinsam |
| `fabric`, `neoforge` | Einstiegspunkte, Loader-Ereignisse, Plattform-Dienst (vorhanden) | Loader |

Regeln: keine Kommentare im Code, keine Imports aus Propwash außerhalb von `de.lardtom.propwash.api` und den
freigegebenen Theme-Klassen, keine Mixins in Propwash. Die `*.core`-Pakete kennen weder Minecraft noch Propwash und
nutzen nur Java-21-Sprachmittel; `./gradlew :common:checkPortable` übersetzt sie mit `--release 21` ohne Klassenpfad
(läuft in `check`).

### 4.2 Datenformate

- Teile: `data/mobdefense/propwash_parts/accessory/{dispenser_mount,spear_mount,rocket_booster}.json`.
- Tags: Items `dispenser_fluid_buckets`, `spear_mount_accepts`, `booster_accepts`; Entity-Typen `reacts/<art>` und
  `ignores_drones`.
- Drohnen-Daten (B6, `Sync.TRACKING`, je unter 32 Byte):
  - `mobdefense:dispenser` = `{selected: 0–3, cooldown_until: Spielzeit}`
  - `mobdefense:booster` = `{ends_at, flight, stars, thrust_n}`
  - Treffer-Sperren des Speers nur im Speicher.
- PW3: `data/mobdefense/propwash_share/table.json`, Einträge nur hinten anhängen: 1 `dispenser_mount`,
  2 `spear_mount`, 3 `rocket_booster`; kein Ersatzteil (die Halterung bleibt leer, wenn das Add-on fehlt).
  Inhalte (Munition) gehören nicht in Sharecodes.
- Sprachen: `common/src/lang/<modul>/{de_de,en_us}.json`, zusammengeführt von `:common:mergeLang` (wie JMP).

### 4.3 Netzwerk

Fast alles läuft über Propwash: Zubehör-Inhalte, Drohnen-Daten, Welt-Regeln und Aktionen sind dort schon
synchronisiert und geprüft. Eigenes Paket nur für die optionale Warnung `THREAT`: S2C `mobdefense:threat`
(Entity-Id der Drohne, Art, Dauer in Ticks), höchstens 4 je Sekunde und Pilot, nur an den Piloten. Dazu C2S
`mobdefense:dispenser_cadence` (Anlauf und Abstand in Ticks, je VarInt) beim Betreten einer Welt und nach dem Speichern
im Tab „Mob Defense“; der Server klemmt auf 4–40 Ticks und hält die Werte je Spieler nur im Speicher (E23).

### 4.4 Kompatibilität

| Thema | Festlegung |
|---|---|
| Propwash | ab 0.6.0 (`requireVersion("mobdefense", "1.7")`); Fabric `"propwash": ">=0.6.0-"`, NeoForge `versionRange = "[0.6.0-alpha,)"`, von Anfang an und unverändert bis zum Release (nehmen `0.6.0-dev.N`, `0.6.0-rc.N` und `0.6.0` an, 0.6.0-Übersicht §3.2); nicht `>=0.6.0`, `[0.6.0,)` oder `[0.6.0-dev.2,)` |
| Minecraft / Loader | 26.3 (`[26.3, 26.4)`), Fabric Loader ≥ 0.19.5 mit Fabric API, NeoForge 26.3.0.10-beta+, Java 25 |
| Seiten | Server und Client Pflicht |
| Just More Parts | verträglich; alle JMP-Rahmen mit Halterung vorn tragen den Dispenser |
| Kamera-Add-on | teilt Halterungen (Gimbal vorn, Action-Cam oben); Empfehlung an den Kamera-Strang: benannte Schalter (A1) statt Aktion 1–3 |
| Andere Mods | Mixins nur als Accessoren und kleine Injektionen in Vanilla; Mobs ohne `GoalSelector` laufen über den Reaktions-Takt |
| Alte Welten | ohne Add-on ersetzt Propwash die Teile durch Platzhalter, B6-Daten bleiben erhalten |
| Versionen | intern `1.0.0-dev.N`, vor dem Release `-rc.N`, Release 1.0.0 |

---

## 5. Assets und Sounds

- **Pipeline wie JMP:** `tools/assets/propwash_style/` ist eine unveränderte Kopie von Propwashs `tools/assets/parts`
  (README mit Stand, `SHA256SUMS`), darauf `tools/assets/mdstyle/` mit drei Vorlagen. Gleiche Bausteine, Texeldichte,
  16er-Texturen, Palette und Anzeige-Transformationen (`kit.compute_display`) wie Propwashs Greifer.
- **Dispenser-Halterung:** dunkles TPU-Gehäuse mit Stirnplatte, deren Vorderseite auf die Vanilla-Textur
  `minecraft:block/dispenser_front` verweist (keine Kopie, Ressourcenpakete ändern sie mit, E25; Ränder der Platte aus
  eigenen Pixeln), Servo oben, zwei Haltelaschen; lackierbar wie der Greifer (Servo = Zubehör, Gehäuse = Platte).
- **Speer-Halterung:** Carbon-Schiene mit zwei TPU-Schellen; den Speer selbst zeichnet Propwash aus dem Slot (B2).
- **Raketen-Booster:** TPU-Wiege mit zwei Klammern und Zünder; die Rakete zeichnet Propwash (B2).
- Item-Symbole, Item-Modelle (`assets/mobdefense/items/`), Web-Export-Modelle für den Konfigurator, Stilprüfung und
  Vergleichsbilder (`docs/asset-sheets/`) wie in JMP `docs/design/asset-style.md`. Das Icon im Gerüst ist ein Platzhalter.
- **Sounds:** nur Vanilla-Sound-Events (Dispenser, Feuerwerk, Speer, Explosionen), keine eigenen Dateien.

---

## 6. Tests

Ziel: Der Owner muss nichts selbst testen außer Gefühl und Aussehen (6.4).

### 6.1 JUnit (`./gradlew :common:test`, ohne Spielstart)

| Test | Prüft |
|---|---|
| `SlotCycleTest` (vorhanden) | Slot-Wahl, leerer Dispenser, genau 4 Slots |
| `RuleDefaultsTest` (vorhanden) | Owner-Standards (Eimer aus, TNT an, Mobs an, echte Munition, 4 Ticks, Speer < 100 %), eindeutige Namen |
| `NamespaceTest` (vorhanden) | Mod-Id `mobdefense`, Ressourcen nur unter `mobdefense`, `minecraft`, `c` |
| `AmmoTableTest` | jedes Vanilla-Item mit Dispenser-Verhalten hat Kürzel (≤ 3 ASCII), Gramm > 0, Stapelgrenze |
| `FireGateTest` | Abklingzeit, `real_ammo`, Eimer- und TNT-Sperre, leerer Slot, Rechte-Ergebnis |
| `SpearDamageTest` | Vanilla-Formel aus Komponentenwerten, × Prozent, Obergrenze 15, Schwellen |
| `BoostProfileTest` | Brenndauer 1,25/1,75/2,25 s, `F = m × 19,6`, Grenze 60 N |
| `PayloadMassBenchTest` | 5″ mit vollem Dispenser (Pfeile) schwebt mit Schub/Gewicht ≥ 1,5; vier TNT machen die 5″ deutlich träger, mit sechs TNT (S7) schwebt sie noch; die Long-Range-7″ mit Li-Ion schwebt mit vier TNT und vier Tränken; im Vergleich (`CovMdAPayloadTest`) trägt eine 7″ mit LiPo vier Tränke oder vier TNT am besten, die Long-Range-7″ schlechter als die 5″ |
| `ReactionTagsTest` | jeder feindliche Vanilla-Mob steht in genau einem `reacts/*`-Tag oder auf der Ausnahmeliste |
| `RecipeUniquenessTest` | Muster eindeutig gegen Propwash- und JMP-Rezepte aus dem Klassenpfad |
| `LangTest` | gleiche Schlüssel DE/EN, Deutsch mit Umlauten (kein ae/oe/ue-Ersatz), OSD-Texte nur ASCII |
| `PartJsonTest` | Teile-JSON gültig, Halterungen richtig, Werte im Bereich von `AccessoryDefinition` |
| `Pw3TableTest` | Tabelle nur hinten erweitert (Baseline-Datei), Namensraum stimmt |
| `checkPortable` (Gradle) | portable Kerne mit `--release 21` ohne Minecraft |

### 6.2 Selbsttest (Quell-Set `devtest`, Fabric und NeoForge, flache Kreativwelt, Bildschirmfotos)

Schritte heißen `mobdefense: <was>`, räumen hinter sich auf und legen Fotos unter `docs/screenshots/selftest/` ab.

| Nr. | Schritt | Erwartung | Foto |
|---|---|---|---|
| S1 | Teile und Rezepte | drei Teile, Items, Rezepte, Werkbank zeigt Masse und Stirnfläche | Werkbank-Analyse |
| S2 | Dispenser beladen und feuern | Pfeil, Heiltrank, Windkugel, TNT in Slots 1–4; jeder Schuss erzeugt das Geschoss innerhalb 5° von `forward`, Anzahl sinkt, Rückstoß kommt an | Wartungsmenü, FPV mit OSD |
| S3 | Eimer gesperrt | `REFUSED`, Warnung; mit Regel wird Wasser gesetzt; Regel zurück | OSD-Warnung |
| S4 | Windkugel-Rückstoß | schwebender 3″-Cinewhoop wird beim Schuss spürbar zurückgestoßen (Tempo nach hinten nimmt um mindestens 50 % von Impuls/Masse zu, Impuls wie von Propwash gemeldet, Masse der Drohne vor dem Schuss) und bleibt fliegbar: kein Überschlag, Neigung unter 30° | Third Person |
| S5 | Tränke | Wurf-Heiltrank schadet einem Zombie, Verweiltrank legt eine Wolke | Wolke |
| S6 | Payloads gegen Spieler | zweiter Testspieler: Regel aus kein Schaden, Heiltrank heilt; Regel an mit PvP Schaden | – |
| S7 | TNT und Griefing | Regel aus `REFUSED`; an: TNT fällt mit Drohnentempo; `drone_griefing` aus: Blockzahl unverändert | Krater / kein Krater |
| S8 | Speer | 15 m/s gegen Zombie: Schaden wie berechnet ±1; unter 4,6 m/s kein Schaden; Dreizack abgelehnt | Speer an der Drohne |
| S9 | Booster | zweite Rakete abgelehnt, Zündung: Tempo steigt wie berechnet, Slot leer; Rakete mit Sternen schadet | Funken am Heck |
| S10 | Mobs je Art | `chase` nähert sich, `archer` trifft eine schwebende Drohne in 5 Schüssen mindestens einmal, `fireball`, `wind`, `creeper`, `flyer` (Phantom über 10 Blöcken), `stare` (Enderman), `bullet`; Regel aus: keine Reaktion | ein Foto je Art |
| S11 | OSD und Editor | Elemente in der Gruppe „Add-ons“, GUI 320×240 und 854×480, DE und EN | Editor, OSD |
| S12 | Welt-Regeln | Abschnitt „Mob Defense“ mit allen Regeln, DE und EN, Operator kann ändern | Tab |
| S13 | Speichern und Aufheben | Munition, gewählter Slot und Rakete bleiben über Neustart, Aufheben und Werkbank | – |
| S14 | Sculk (optional) | 5″ löst einen Sculk-Sensor aus, Whoop im Schwebeflug nicht | – |

### 6.3 Dedizierter Server (beide Loader)

| Nr. | Prüfung |
|---|---|
| D1 | Server-Rauchtest: Start mit Add-on, Regeln angemeldet, PW3-Tabelle gelesen, kein `ERROR` im Log, keine Client-Klasse geladen |
| D2 | Client mit und ohne Add-on: Beitritt klappt bzw. Übersicht „fehlt“ (Foto) |
| D3 | Zwei Spieler: `payloads_hit_players` × PvP (vier Kombinationen) mit Pfeil, Schadens- und Heiltrank, Speer |
| D4 | Zuschauer sieht Speer und Rakete, aber kein fremdes OSD |
| D5 | Dauerfeuer per Makro 50/s: höchstens ein Schuss je Abklingzeit, kein Kick |
| D6 | Last: 10 Drohnen, 50 Mobs: MSPT steigt um höchstens 2 ms |
| D7 | Rechte: Spawnschutz und Abenteuermodus, keine Blöcke gesetzt, kein TNT-Krater (G3) |
| D8 | Welt mit Add-on, Neustart ohne Add-on, wieder mit: Drohnen vollständig, Daten erhalten |

### 6.4 Prüfablauf ohne Owner

Je Phase: JUnit und `checkPortable` grün, Selbsttest beider Loader PASS mit Fotos, D1–D8 PASS, zwei unabhängige
Prüf-Agenten sichten Fotos und Logs, Fix-Runden bis nichts Mittleres offen ist. Erst dann geht die Testliste (7.2) an
den Owner.

---

## 7. Website, Doku, Testliste, Shotliste

### 7.1 Website und Doku

- Eintrag im offiziellen Add-on-Verzeichnis auf propwashfpv.com (Kurzkennung 3), Seite „Mob Defense“ EN/DE: Zubehör mit
  Bildern, Bedienung, Welt-Regeln (Tabelle aus dem Web-Export erzeugt, nicht von Hand), Mob-Tabelle, FAQ („Geht das auf
  Servern?“, „Kann ich Spieler treffen?“, „Warum sind Eimer gesperrt?“).
- Web-Konfigurator: Web-Export wie JMP (`./gradlew webExport`: Teile, Modelle, Texte, Regeln), Laden des Add-on-Jars im
  Browser.
- Modrinth- und CurseForge-Texte EN/DE unter `docs/modrinth/`, README, CHANGELOG im Spieler-Stil (2–3 Highlights, Neu,
  Kompatibilität), Flugschul-Kapitel im Spiel.

### 7.2 Testliste für den Owner (Entwurf, etwa 15 Minuten, nur Gefühl und Aussehen)

| Nr. | Schritt | Erwartung | Dauer |
|---|---|---|---|
| T1 | 5″ mit Dispenser, Pfeile im Dauerfeuer auf Zombies | fühlt sich treffsicher an, Rückstoß spürbar, nicht übertrieben | 3 min |
| T2 | Windkugel aus dem Dispenser gegen eine Wand | kräftiger Stoß, Drohne bleibt fangbar | 1 min |
| T3 | Heiltrank auf einen Mitspieler bzw. Testspieler | trifft dort, wo man zielt | 1 min |
| T4 | Speer gegen Zombie im Vorbeiflug | Treffer glaubwürdig, Schaden fair | 2 min |
| T5 | Raketen-Boost im Geradeausflug | Elytra-Gefühl, Funken sehen gut aus | 2 min |
| T6 | Skelette, Phantom in der Nacht, Blaze im Nether | Mobs wirken lebendig, nicht unfair | 4 min |
| T7 | Modelle in Werkbank, an der Drohne, im Inventar | passen zu Propwash-Teilen | 2 min |

### 7.3 Shotliste (Entwurf)

Drehwelten liegen als Zip bereit: Zombie-Feld bei Nacht, Skelett-Schlucht, Nether-Festung, Testgelände mit Mauer.

| Nr. | Feature | Hook (Englisch) | Szene | Einstellung | Dauer |
|---|---|---|---|---|---|
| R1 | Dispenser | „Your drone has a dispenser now“ | Pfeile im Dauerfeuer auf eine Zombie-Gruppe | FPV mit OSD | 15 s |
| R2 | Windkugel-Rückstoß | „Recoil is real“ | Windkugel, Drohne fliegt rückwärts, Fang | FPV, dann Third Person | 12 s |
| R3 | Heiltrank | „Air support“ | Mitspieler in Not, Trank aus der Luft | Third Person | 15 s |
| R4 | Speer | „Drone Joust“ | Vorbeiflug, Zombie fliegt um | FPV, Zeitlupe | 12 s |
| R5 | Raketen-Booster | „Elytra boost, but drone“ | Zündung, Funken, Geschwindigkeit im OSD | FPV | 12 s |
| R6 | Skelette schießen zurück | „They shoot back“ | Pfeile zischen am Bild vorbei, Treffer | FPV | 15 s |
| R7 | Phantome jagen | „Never fly alone at night“ | Phantom-Sturzangriff von oben | FPV | 15 s |
| R8 | Enderman | „Don't look at him“ | Kamera schwenkt auf Enderman, Teleport | FPV | 12 s |
| R9 | TNT-Abwurf | „Server rules decide“ | Regel im Tab (standardmäßig an), Abwurf, Krater | Bildschirm und Third Person | 18 s |
| R10 | Ghast und Breeze | „The Nether fights back“ | Feuerball und Windkugel stoßen die Drohne | FPV | 15 s |

YouTube-Vorstellung (4–6 min): Einstieg mit R6/R7, die drei Zubehörteile mit Bau in der Werkbank und Gewicht, Mobs im
Überblick, Welt-Regeln für Server, Installation (Propwash ab 0.6.0 nötig), Abschluss mit Sharecode eines Builds.

---

## 8. Backport 1.21.1: nur der Dispenser

Vorgabe (Owner 15.10. und 17.10.): Der Backport hat keine Add-on-API und bekommt aus Mob Defense nur den Dispenser.
Der Backport-Plan übernimmt Inhalte per Sync aus dem Tag des Add-ons und behält die Namensräume (Sharecodes bleiben
austauschbar).

- **Was mitgeht:** `dispenser_mount` (Teil, Rezept, Modell, Texturen, Texte), `dispenser/**` samt `dispenser.core`,
  `rules.core` und die Regeln `allow_fluid_buckets`, `allow_tnt`, `payloads_hit_players`, `drone_griefing`, `real_ammo`,
  `dispenser_cooldown_ticks`, das OSD-Element `mobdefense:dispenser`, der Effekt-Filter für Tränke.
- **Was nicht mitgeht:** Speer (Speere gibt es in 1.21.1 nicht), Booster, Mob-Reaktionen, Sculk.
- **Portabel halten:**
  - Logik in `dispenser.core`, `rules.core` ohne Minecraft- und Propwash-Typen, nur Java-21-Syntax, geprüft durch
    `checkPortable`.
  - `DispenserBehavior` nutzt nur `AccessoryBehavior`, `AccessoryContext#dispense` und `MountPose`; im Backport stehen
    dahinter dieselben internen Klassen (`AccessoryDispenser`).
  - Items über Ids und Tags statt Klassen; keine 26.x-Neuheiten im Dispenser-Pfad.
  - Dispenser-Dateien importieren nichts aus `spear`, `booster`, `mob`, `sculk` (JUnit-Test prüft die Imports).
  - Bekannte Unterschiede, die der Backport über Ersatzdateien löst: `Identifier` → `ResourceLocation`,
    Item-Modell-Definitionen (`assets/<ns>/items/`), Gamerule-Anmeldung, Trank-Klassen (26.x teilt `ThrownPotion` auf),
    Rezept ohne Kupfernugget (betrifft nur den Booster).
- **Lieferung:** Mit jedem Release-Tag liegt `backport/content.json` bei (Ids und Pfade für das Sync-Werkzeug).

---

## 9. Risiken

| Risiko | Wirkung | Gegenmittel |
|---|---|---|
| API 1.7 verspätet oder ändert sich | Bau stockt | Logik in portablen Kernen, Bau gegen API-Stand A, Verhalten erst gegen Stand B prüfen |
| Spieler suchen das Feuern auf dem Rechtsklick | Flug endet statt Schuss | Hinweis mit Belegung beim ersten Flug (G2), Name im Tab „Modi“, Flugschul-Szene und Doku nennen Aktion 1 |
| Verhalten zu G2–G7, G9–G11 kommt spät (Signaturen ab API-Stand A, Verhalten ab Stand B) | Rechte, Selbsttreffer, TNT-Gefühl, Endermen, `real_ammo` | Auswege bis Stand B: Add-on prüft Rechte selbst, Mündung weiter vorn, Item zurücklegen |
| Vanilla-KI sehr verschieden (Goal- und Brain-Mobs) | Aufwand, Fehler je Mob | Arten statt Einzelfälle, Reaktions-Takt als gemeinsamer Weg, Tag-Test |
| Serverlast durch viele Mobs und Drohnen | Lag | Budget je Tick, Staffelung, Messung D6 |
| Balance: Dispenser auf Drohne zu stark | Mobs zu leicht, Griefing | Gewicht, Abklingzeit, echte Munition, Regeln, PvP standardmäßig aus |
| Mixins in Vanilla kollidieren mit KI-Mods | Absturz oder tote Reaktionen | nur Accessoren und kleine Injektionen, Reaktion fällt bei Fehler für den Mob weg (Log einmal) |
| Speer clippt durch Wände | Optik | Maßstab 0,6, kurze Überlänge, nur Darstellung |
| Name nah am Community-Add-on „Payloads“ | Verwechslung | eigener Name und Namensraum; Hinweis im Discord |
| Backport driftet ab | doppelte Pflege | portabler Kern, `checkPortable`, `content.json` |
| Owner-Zitate in einem öffentlichen Repo | Konvention E31 | Abschnitt 1.2 vor Veröffentlichung sinngemäß ersetzen |

---

## 10. Entscheidungen (was – warum – verworfen)

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| E1 | Mod-Id `mobdefense`, Paket `de.lardtom.mobdefense` | passt zu den Beispielen im 0.6.0-Entwurf und zum 1.6-Entwurf, kurz im PW3-Code, kein Präfix `propwash` | `propwash_mobdefense` (wirkt wie der reservierte Namensraum, lang), `md` (zu allgemein) |
| E2 | Drei Teile auf drei Halterungen: Dispenser vorn, Speer unten, Booster oben | Owner-Vorgaben für Dispenser und Speer, oben bleibt frei | neue Halterung hinten (Kern, PW3, alle Rahmen), Booster im Dispenser |
| E3 | Gewählter Slot feuert, Aktion 2 wechselt | Pilot behält die Kontrolle | Zufalls-Slot wie beim Vanilla-Dispenser |
| E4 | Feuern nur über Zubehör-Aktion 1 (Funke oder Taste); der Rechtsklick beendet weiter den Flug; Namen und Hinweis über G2 | FPV-Piloten haben die Hände an der Funke; einheitlicher Ausstieg bei jeder Drohne; im 0.6.0-Entwurf nach der Meldung von G1 so entschieden (E14, E35) | Use-Taste löst aus (G1, `useKeyAction`) |
| E5 | Nummerierte Aktionen 1–3 statt benannter Schalter | gibt es seit 1.6, Belegungen bleiben, Backport identisch | A1-Schalter (bräuchte Zubehör-Kontext im Ereignis, Backport anders) |
| E6 | Eimer werden nur beim Setzen von Flüssigkeit gesperrt, leerer Eimer schöpft | Owner-Grund sind Säulen in der Luft, Schöpfen schadet nicht | Eimer ganz sperren; eigener Eimerhalter aus dem 1.6-Entwurf (Eimer-Abwurf gestrichen) |
| E7 | `payloads_hit_players` standardmäßig aus, positive Tränke immer | „Mob Defense“, Server-freundlich, Heilen ist erwünscht | Standard an |
| E8 | Speerschaden = Vanilla-Formel × Welt-Regel (Standard 100 %, Steuerung 24.10., Befund OWNM-4), höchstens 15 | „wie Vanilla“; im Owner-Playtest lebte ein Dorfbewohner mit 50 % nach drei zügigen Kontakten (Netheritspeer, 7″) noch | 50 % (ursprünglich „wie Vanilla, aber schwächer“), eigene Tabelle je Material (1.6-Entwurf) |
| E9 | Speer nur mit Tag `#minecraft:spears` und Kinetik-Komponente | „explizit Speere“, modded Speere gehen | Dreizack, Schwerter |
| E10 | Booster: 2 g Beschleunigung, Brenndauer nach Flugdauer, Sterne explodieren | Elytra-Gefühl auf jeder Größe | fester Schub 12 N (Whoop-Rakete, X-Class merkt nichts), Tempo setzen |
| E11 | Genau eine Rakete | Owner | mehrere Ladungen |
| E12 | Mob-Reaktionen über Tags und Arten | Datenpakete und Mods, testbar | Sonderfall je Klasse |
| E13 | Spieler bleiben erstes Ziel, geparkte Drohnen sind tabu | Mobs sollen Spieler nicht ignorieren, kein nächtliches Zerlegen geparkter Drohnen | Drohne immer bevorzugt |
| E14 | Welt-Regeln über B5 im Tab „Welt-Regeln“ | Grundsatz Operator-GUI (0.6.0 E24) | eigener Konfigurator-Tab |
| E15 | Nur Vanilla-Sounds | keine Lizenz- und Asset-Last | eigene Dateien |
| E16 | Kein Kamikaze, TNT nur als Abwurf | Roadmap: kein Sprengstoff in der Basis, Mob Defense regelt den Abwurf | Explosion beim Crash |
| E17 | `drone_griefing` deckt Payloads ab, Mob-Explosionen folgen `mobGriefing` | klare Zuständigkeit | eine Regel für beides |
| E18 | Gerüst baut jetzt gegen 0.5.2, Umstieg bei API-Stand A | parallel arbeiten (Owner 17.10.) | warten auf 0.6.0 |
| E19 | Portable Kerne mit `checkPortable` | Backport ohne Doppelarbeit | Präprozessor, späteres Umschreiben |
| E20 | Lizenz wie JMP (PolyForm Shield, Doku MIT), ab der ersten Version | gleiche Regeln wie das andere offizielle Add-on | eigene Lizenz |
| E21 | THREAT-Warnung und Fortschritte als Kann | nett, aber nicht nötig für 1.0 | Pflicht |
| E22 | Sculk und Warden als letzter, optionaler Strang | Owner „kein Muss“ | gar nicht planen |
| E23 | Persönliche Schussfolge im Tab „Mob Defense“ des K-Menüs: Anlauf bis zum Dauerfeuer (Standard 500 ms) und Abstand (Standard 200 ms), je 200–2000 ms, Welt-Regel als Untergrenze, per Paket an den Server | Owner-Playtest 23.10.: ein einzelnes TNT war schwer abzuwerfen, Schussfolge soll einstellbar sein (OWNM-1) | längere feste Abklingzeit für alle, Anlauf als Welt-Regel (gilt dann für jeden Spieler) |
| E24 | Rückstoß am Hebel ab dem Schwerpunkt mit Beladung, angesetzt von Mob Defense (`applyRecoil(false)`, `DronePhysics#impulse`); seit E26 nur noch für den Windstoß der Windkugel, seit E27 gar nicht mehr | Owner-Playtest 23.10.: nach Wurftränken kurz unsteuerbar; Ursache war der Hebel ab dem Schwerpunkt ohne Beladung, nicht ein Treffer durch den eigenen Trank (OWNM-2) | langsamere Tränke (weicht vom Vanilla-Dispenser ab), Stoß durch den Schwerpunkt (unrealistisch) |
| E25 | Vorderseite der Dispenser-Halterung verweist auf `minecraft:block/dispenser_front`, die Texturen der Halterung liegen im Block-Atlas | Owner-Playtest 23.10.: echte Vanilla-Textur, damit Ressourcenpakete sie mit ändern (OWNM-3); Minecraft backt ein Item-Modell nur aus einem Atlas | eigene Pixel (vorher), Kopie der Vanilla-Textur |
| E26 | Rückstoß von Wurfgeschossen (Tränke, Flaschen, Pfeile, Schneebälle, Eier, Feuerkugeln, Raketen) am Schwerpunkt: Masse × Tempo entgegen dem Lauf, ohne Drehung; die Windkugel behielt den Hebel aus E24 bis E27 (seit E27 ebenfalls am Schwerpunkt) | Gegenprüfung G-OWNM-1 und Befund AUTO21-1: mit dem Hebel aus E24 wird der Hebel mit jedem Wurf länger und die Drohne leichter, die 5″ kam beim 2. Trank auf +33°, beim 3. auf +74° und überschlug sich mit dem letzten (+169°, 3688 °/s); am Schwerpunkt alle vier Würfe unter 1° | Hebel für alles (nur bei voller Halterung steuerbar), Stoß über die Zeit verteilt (unrealistisch langer Wurf), Windkugel ebenfalls am Schwerpunkt (eine Whoop kippt dann nicht mehr, §2.1, S4; seit E27 doch so) |
| E27 | Auch die Windkugel stößt die Drohne am Schwerpunkt zurück: `recoilNs()` aus Propwash (1,8 N·s) entgegen dem Lauf, ohne Drehung; der Hebel aus E24 entfällt | Owner 04.11. (rc.3, Befund RC3P-2, Folge-Fix RC3P2-1): Windkugeln aus dem Werfer sanfter, die Drohne soll fliegbar bleiben. Mit dem Hebel überschlug sich der 3″-Cinewhoop auch mit 1,8 statt 3,5 N·s (+125,6°, 1356 °/s); am Schwerpunkt 3,35 m/s zurück, Neigung 0,0° | Hebel wie bisher (Whoop überschlägt sich), Hebel nur für die Druckwelle (die API meldet nur den ganzen Rückstoß, den Anteil der Druckwelle müsste Mob Defense nachbauen) |

Abgelehnt vom Owner und nicht geplant: Abwurf-Schacht für Blöcke, Eimer-Abwurf, Schild, Spektral-Scheinwerfer, Glocke als
Zubehör, Ziegenhorn- oder Notenblock-Köder, Leine, Spinnweben-Netz, Angel, Totem.

---

## 11. Arbeitspakete und Stränge

Konto laut Roadmap (17.10. abends): MAX3 baut Mob Defense nach der Backport-Vorarbeit und übernimmt Prüfrunden.
Jeder Strang arbeitet in einem eigenen Worktree auf `feature/<strang>`, `main` ist die Integration, nie pushen.

| Strang | Inhalt | Dateien (Besitz) | Braucht | MC-Test |
|---|---|---|---|---|
| `md-scaffold` | Repo, Build, Gerüst, Design (diese Runde, erledigt) | Wurzel, `build-logic/`, `*/build.gradle`, `docs/design.md` | – | nein |
| `md-core-data` | Teile-JSON, Rezepte, Tags, PW3-Tabelle, Regeln (B5), Sprachmodul `core`, Kreativ-Reiter | `data/mobdefense/**`, `rules/**`, `common/src/lang/core/`, `MobDefense.java` | Stand A | kurz (S1, S12), 10 min |
| `md-dispenser` | Dispenser, OSD-Element, Effekt-Filter, Griefing | `dispenser/**`, `payload/**`, `mixin/AbstractThrownPotionMixin`, `mixin/PrimedTntMixin`, `common/src/lang/dispenser/` | Stand A, Verhalten B1 ab Stand B | ja (S2–S7), 45 min |
| `md-spear` | Speer-Halterung | `spear/**`, `common/src/lang/spear/` | Stand A, B2 | ja (S8), 15 min |
| `md-booster` | Booster, Partikel | `booster/**`, `common/src/lang/booster/` | Stand A, B2, B6 | ja (S9), 15 min |
| `md-mobs-1` | Reaktions-Takt, Tags, Arten `chase`, `brute`, `archer`, `fireball`, `creeper`, `flyer` | `mob/**` (Rahmen und diese Arten), `mixin/MobAccessor`, `mixin/PhantomAccessor`, `data/mobdefense/tags/entity_type/**` | Stand A, B3, B4 | ja (S10 Teil 1), 60 min |
| `md-mobs-2` | übrige Arten inkl. `stare` (G6), `beam`, `caster`, `boss`, `ground` | `mob/reaction/<art>` dieser Arten | `md-mobs-1` gemergt | ja (S10 Teil 2), 45 min |
| `md-assets` | Stil-Kopie, Vorlagen, Modelle, Texturen, Symbole, Icon | `tools/assets/**`, `assets/mobdefense/{models,textures,items}/**`, `docs/asset-sheets/` | – (startet sofort) | Fotos, 15 min |
| `md-osd-ux` | OSD-Anmeldung, Hinweise, Fortschritte, THREAT-Paket, Flugschule | `client/**`, `net/**`, `data/mobdefense/advancement/**`, `common/src/lang/ui/` | Stand A, A2 | ja (S11), 20 min |
| `md-devtest` | Selbsttest-Läufer, Server-Rauchtest, Fotos, Web-Export (aus JMP übernommen) | `common/src/devtest/**`, `tools/fastcheck.sh` | – (startet sofort) | führt alle Läufe aus |
| `md-sculk` | optional (2.7) | `sculk/**` | `md-mobs-1` | ja (S14), 15 min |
| `md-docs-web` | Doku, Modrinth-Texte, CHANGELOG, Testliste, Shotliste, Website-Beitrag | `docs/**` (außer `design.md`), `README.md`, `CHANGELOG.md` | Funktionsstopp | Web-Export |

**Ablauf**

1. Jetzt (gegen 0.5.2): `md-assets` und `md-devtest` starten; Lücken G1–G11 sind gemeldet, Stand in §3.3 (G1 und G8 nicht übernommen).
2. API-Stand A (`0.6.0-dev.2`): Umstieg in `gradle.properties` (Version, Bereiche, `REQUIRED_API` 1.7), dann parallel
   `md-core-data`, `md-dispenser`, `md-spear`, `md-booster`, `md-mobs-1`, `md-osd-ux`; Dateien überschneiden sich nicht.
3. Danach `md-mobs-2` und optional `md-sculk`.
4. API-Stand B (`0.6.0-dev.4`): Integration, voller Selbsttest beider Loader, D1–D8, Fix-Runden.
5. Fertiges 0.6.0: Schlussprüfung, `md-docs-web`, Release 1.0.0 nach `release/1.0.0/` (nur lokal), Tag für den Backport.
