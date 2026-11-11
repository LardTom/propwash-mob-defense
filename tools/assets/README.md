# Assets von Mob Defense (Stil-API `mdstyle`)

Alle Teilmodelle, Texturen, Item-Modelle, Render-Definitionen, Lack-Varianten und das Mod-Icon unter
`common/src/main/resources/assets/mobdefense/` kommen aus diesem Ordner (`docs/design.md` §5). Nichts davon ist von Hand
gezeichnet oder aus Vanilla oder anderen Mods kopiert: Die Texturen malen die Maler der Basis (`paint.plastic`, `carbon`,
`steel`, …) und drei eigene Maler in `mdstyle/decals.py` (Stein der Stirnplatte, Slot-Fenster, Kleinteile), das Icon zeichnet
`mdstyle/icon.py` mit Pillow aus Linien und Flächen. Einzige Ausnahme ist die Vorderseite der Dispenser-Halterung: Sie
verweist im Modell auf die Vanilla-Textur `minecraft:block/dispenser_front` (keine Kopie, Owner-Befund OWNM-3, damit
Ressourcenpakete sie mit ändern). Der Generator liest keine Bilddateien; `style_generate.py --verify`
erzeugt alles neu und vergleicht Byte für Byte mit den Ressourcen, jede fremde oder von Hand geänderte Datei ist ein
Befund.

| Pfad | Inhalt |
|---|---|
| `propwash_style/` | unveränderte Kopie des Propwash-Teilegenerators (`README.md` mit Quelle und Stand, `SHA256SUMS`) |
| `mdstyle/` | Stil-API von Mob Defense auf dieser Kopie (Tabelle unten) |
| `style_generate.py` | erzeugt die drei Zubehörteile und das Icon, prüft danach (Stilprüfung) |
| `preview.py` | Vorschaubilder: jedes Teil aus vier Richtungen mit Lack-Varianten, alle drei an Propwash-Rahmen |
| `compare_render.py` | Vergleichsbild gegen Propwash-Zubehör (GUI, beide Hände, dritte Person, Boden, Item-Rahmen, gemeinsamer Maßstab) |
| `logcheck.py` | prüft das `latest.log` eines Client-Rauchtests auf fehlende Modelle und Texturen |

## Aufrufe

```sh
python3 tools/assets/style_generate.py               # Vorschau nach build/style-preview/assets, mit Prüfung
python3 tools/assets/style_generate.py --write       # in die Mod-Ressourcen (ersetzt nur die eigenen Dateien)
python3 tools/assets/style_generate.py --verify      # Ressourcen = Generator? plus Prüfung, ändert nichts
python3 tools/assets/style_generate.py --check-only common/src/main/resources/assets
python3 tools/assets/preview.py                      # docs/asset-sheets/mobdefense_parts.png, mobdefense_on_drone.png
python3 tools/assets/preview.py --minecraft-assets <client-jar>   # Vanilla-Texturen aus einem anderen Jar
python3 tools/assets/compare_render.py               # docs/asset-sheets/style_compare_accessory.png
python3 tools/assets/logcheck.py fabric/runs/smoke-client/logs/latest.log
```

Benötigt `python3` mit numpy und Pillow. Vorschau, Vergleich und die Gegenprobe am Greifer lesen die Propwash-Assets aus
dem Jar `propwash-common` in mavenLocal (Nummer aus `gradle.properties`), sonst `--propwash-assets <Ordner oder Jar>`
bzw. `PROPWASH_ASSETS`. Die Vanilla-Vorderseite holen beide aus dem Client-Jar im Gradle-Cache
(`~/.gradle/caches/fabric-loom/<minecraft_version>/minecraft-client.jar`), sonst `--minecraft-assets` bzw. `MINECRAFT_ASSETS`.

## Teile und Maße

Quelle der Maße ist die Teil-Definition `data/mobdefense/propwash_parts/accessory/<id>.json` (`slot`, `size_mm`). Solange
es sie nicht gibt, nimmt `mdstyle/parts.py` die Werte aus `docs/design.md` §2.1 und meldet das beim Lauf. Teil-Definitionen
ohne Vorlage sind ein Befund. Maßstab und Anbaupunkte wie Propwash (`docs/ASSETS.md` der Basis): 1 Modell-Einheit = 8 mm,
Anbaupunkt (8, 8, 8), oben +Y, Nase −Z; die Lage relativ zum Anbaupunkt folgt `AccessoryType#boxMinMm`/`boxMaxMm` der
Basis (vorn: y −Höhe…0, z −Tiefe…0; unten: y −Höhe…0, z ±Tiefe/2; oben: y 5…5 + Höhe über der Gelenkachse).

| Vorlage | Teil | Halterung | Maße B×H×T | Aufbau | Lack |
|---|---|---|---|---|---|
| `mdstyle/dispenser.py` | `dispenser_mount` | vorn | 62 × 58 × 64 mm | Stirnplatte aus Stein (eigener Maler an den Rändern), Vorderseite mit Verweis auf `minecraft:block/dispenser_front` (ganze Textur, `VANILLA_FACES`), dunkles TPU-Gehäuse mit vier Slot-Fenstern je Seite und Fuß, Servo oben (Greifer-Etikett der Basis, Zahnrad, Horn), zwei Haltelaschen hinten oben bis z = 0, drei Servo-Kabel | Gehäuse und Laschen `tpu` (`#262628`), Servo `accessory` |
| `mdstyle/spear.py` | `spear_mount` | unten | 24 × 18 × 90 mm | Carbon-Schiene 90 mm mit TPU-Endkappen, zwei TPU-Schellen (Bohrung 7 mm, Gummi-Auflage, Rippen) mit Spannhebel und Bolzen | Schellen `tpu` (`#d6333a`), Hebel `accessory` |
| `mdstyle/booster.py` | `rocket_booster` | oben | 30 × 22 × 70 mm | Gelenkfinger wie die Action-Cams (greifen in den Sockel `mount_top`), TPU-Wiege mit Grundplatte, Bugstopp und zwei Klammern (V-Auflage, Lippen), Zünder am Heck mit zwei Kupferkontakten, roter LED und Warnstreifen | Wiege `tpu` (`#262628`), Zünder `accessory` |

Lage für die Stränge mit Verhalten (Werte relativ zum Anbaupunkt, mm):

- Dispenser: Mündung Mitte (0, −29, −64) = `MountPose#front()` der Basis (Mitte der Vorderseite der Box).
- Speer: Achse der Bohrungen y −10,34, x 0, Schellen bei z −28,8 und +23,4. Den Speer zeichnet Propwash aus dem Slot (B2).
- Booster: Achse der Wiege y 15,9 (Innenweite 17,4 mm), Klammern bei z −19,6 und +11,2, Zünder z 28…35 (Heck). Die Rakete
  zeichnet Propwash aus dem Slot (B2).

## Stilregeln (wie Propwashs Greifer)

- **Bausteine:** nur `kit.Model` mit `box`, `cylinder`, `cylinder_x` und den Malern der Basis; Kabel mit `stacks.lead`,
  Gelenkfinger mit `accessories.camera_fingers`, Servo-Etikett `accessories.servo_label`. Eigene Materialien heißen
  `md_*` (sonst bricht der Lauf ab).
- **Texeldichte und Textur:** Startdichte, Mindestdichte, Texturgröße, GUI-Drehung und GUI-Füllung liest
  `mdstyle/display.py` aus `accessories.build_gripper()` der Kopie (Dichte 10 Texel je 8 mm, Textur 64 × 64, GUI
  10/210/0, Füllung 14). Der Packer der Basis verringert die Dichte in 10-%-Schritten, bis alles in 64 × 64 passt:
  Dispenser 3,87, Speer-Halterung 4,78, Booster 6,56. Verdeckte Flächen sind weggelassen bzw. halb aufgelöst (Rückseite der
  Stirnplatte 0,35, Rückseite des Gehäuses 0,5).
- **Palette:** Grundfarben der Basis (Carbon `#1b1d20`, TPU schwarz `#262628`, TPU rot `#d6333a`, Stahl, Gummi,
  Kabel), Akzent von Mob Defense `#6aa336` (Moosgrün) nur für die Zubehör-Teile (Servo, Spannhebel, Zünder) und die
  Slot-Fenster. Stein `#7e8083` mit Fugen `#3c3e41` nur auf der Stirnplatte.
- **Lackierbar wie der Greifer:** genau zwei Lack-Gruppen je Teil, Kanal `tpu` (Gehäuse, Schellen, Wiege – wie die
  Grundplatte des Greifers) und Kanal `accessory` (Servo, Hebel, Zünder – wie der Servo des Greifers); Referenzen nicht
  zu hell (`paintable.check_references`). Stein, Mündung, Carbon und Metall bleiben unlackiert.
- **Anzeige-Transformationen:** `kit.compute_display` der Basis mit den Greifer-Werten, Item-Modell
  `assets/mobdefense/items/<id>.json` aus `kit.item_definition` (Fall `none` → Drohnen-Modell, sonst mit Vor-Skalierung).
  Die Stilprüfung rechnet beides aus den Elementen nach und prüft die Methode vorher am Greifer selbst (aus dem
  Propwash-Jar: Anzeige und Item-Modell kommen gleich heraus).

## Ausgabe (auch für den Web-Export)

| Datei | Inhalt |
|---|---|
| `models/part/accessory/<id>.json`, `<id>_paint.json` | Drohnen-Modell und Lack-Variante (Tönung über `tintindex`) |
| `textures/item/part/accessory/<id>.png`, `<id>_paint.png` | 64 × 64, Lack-Variante in Referenz-Grau; die Dispenser-Halterung unter `textures/block/part/accessory/`, weil Minecraft ein Item-Modell nur aus einem Atlas backt und ihre Vorderseite aus dem Block-Atlas kommt |
| `items/<id>.json` | Item-Modell (Inventar-Symbol, Hände, Boden, Item-Rahmen, Regal, Kopf) |
| `items/drone_render/accessory/<id>.json`, `<id>_paint.json` | Render-Definition für Drohne, Werkbank, Flugschule und Web-Export, Lack mit Standard-Tönung |
| `drone_paint.json` | Lack-Gruppen je Teil (`accessory/<id>`) |
| `icon.png` | Mod-Icon 128 × 128 (Drohne von oben mit Dispenser an der Nase, Pfeil, Rotorringe im Akzent) |

Der Web-Export der Basis (`/propwash webexport mobdefense`, `AssetIndex`) findet je Teil genau diese Pfade: Item-Modell,
Render-Definition samt Variante `_paint`, Lack-Eintrag `mobdefense:accessory/<id>` und über die Modelle die Texturen;
die Stilprüfung verlangt sie für jedes Teil.

## Prüfung (`mdstyle/check.py`)

- Kopie der Basis unverändert (`SHA256SUMS`), keine Datei ohne Prüfsumme; Gegenprobe der Anzeige am Greifer.
- Modelle: nur unter `models/part/accessory/`, `credit` von Mob Defense, Elemente innerhalb −16…32, nur eigene
  Texturen und Verweise aus `writer.VANILLA_TEXTURES` (ohne Datei in den Ressourcen; diese Flächen zählen nicht zur
  Texeldichte), Texturen 64 × 64 wie der Greifer, alle Anzeige-Kontexte, Skalen ≤ 4, `gui_light: side`, Anzeige gleich
  `kit.compute_display` mit Greifer-Werten, Texeldichte höchstens die des Greifers und im Median mindestens 3.
- Items: jedes Modell vorhanden und im eigenen Namensraum; Item-Modell gleich `kit.item_definition`.
- Je Teil: Modell, Item-Modell, Render-Definition, Lack-Modell und Lack-Render-Definition mit passender Tönung,
  Lack-Gruppen aus der Palette in den Kanälen `tpu` und `accessory`, Modell innerhalb der Maße der Halterung (±1,5 mm,
  oben dürfen die Gelenkfinger bis 3 mm unter das Gelenk) und mindestens 85 % jeder Maßangabe.
- Keine Textur ohne Modell; kein `sounds.json` und keine Sound-Dateien (nur Vanilla-Sound-Events: `block.dispenser.dispense`,
  `block.dispenser.fail`, `item.spear.hit`, `item.spear_wood.hit`, `entity.firework_rocket.launch`,
  `entity.firework_rocket.blast`, Explosionen); Icon 128 × 128 und nicht mehr der Platzhalter des Gerüsts; die
  Generator-Module lesen keine Bild- oder Archivdateien.
- `--verify`: Ressourcen Byte für Byte gleich einem frischen Lauf.

## Client-Rauchtest

`./gradlew :fabric:runClientSmoke` über das Slot-Skript (startet eine flache Welt und beendet sich), danach
`python3 tools/assets/logcheck.py fabric/runs/smoke-client/logs/latest.log`: Warnungen und Fehler beim Laden von Modellen,
Item-Modellen, Texturen und Atlanten mit `mobdefense` sind Befunde, andere werden nur aufgelistet.

## Bekannte Grenze

Propwash neigt jedes Zubehör außer dem eigenen Greifer um die Neigung der Halterung (`AccessoryType#hangsStraight`
gilt nur für `propwash:gripper`) und streckt Zubehör unten zusätzlich in x auf die Länge der Halterung (Regel der
LED-Streifen). Die Speer-Halterung erscheint an einem 5-Zöller deshalb um 30° geneigt und 1,42-fach verbreitert, der
Booster um 22° nach oben geneigt (`docs/asset-sheets/mobdefense_on_drone.png`, Zeilen 1–3 gegen Zeile 4). Die Modelle
sind gerade gebaut; das gehört in die Propwash-API (eigene Angabe „hängt gerade“ für Add-on-Zubehör).
