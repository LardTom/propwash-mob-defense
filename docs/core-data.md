# Kern-Daten – Teile, Rezepte, Tags, PW3-Tabelle, Welt-Regeln, Kreativ-Reiter

Grundlage: `docs/design.md` §2.1–§2.3, §2.8, §4.2, §4.4 und §6. Dieses Dokument beschreibt, was das Modul `core` anlegt,
wo es liegt und wie es geprüft wird.

---

## 1. Propwash-Version

| Feld in `gradle.properties` | Wert | Bedeutung |
|---|---|---|
| `propwash_version` | `0.6.0-dev.3` | genau die Nummer, die Propwash an Mob Defense meldet; jeden späteren Wechsel macht die Integration |
| `propwash_version_range` | `[0.6.0,)` | NeoForge, ab dem Release 1.0.0 (davor `[0.6.0-alpha,)` für `0.6.0-dev.N` und `0.6.0-rc.N`) |
| `propwash_fabric_range` | `>=0.6.0` | Fabric, gleiche Bedeutung (davor `>=0.6.0-`) |
| `propwash_api_version` | `1.8` | Add-on-API (1.8 seit Befund FB2M-1: `recess_mm`/`drop_mm` der Dispenser-Halterung) |

Zur Laufzeit prüft `MobDefense.init()` die API mit `PropwashApi.requireVersion("mobdefense", "1.8")`
(`MobDefense.REQUIRED_API`). Nie `>=0.6.0`, `[0.6.0,)` oder `[0.6.0-dev.N,)` eintragen: Diese Bereiche lehnen
Entwicklungs- und Release-Kandidaten ab.

---

## 2. Anmeldung

`MobDefense.init()` (Fabric `onInitialize`, NeoForge Mod-Konstruktor) in dieser Reihenfolge:

1. `PropwashApi.parts().loadBundled("mobdefense")` – die drei Teile aus JSON,
2. `PropwashApi.partItems().registerItems("mobdefense")` – je Teil ein Item `mobdefense:<id>`,
3. `MobDefenseRules.register()` – die Welt-Regeln über B5,
4. `MobDefenseTab.init()` – der Kreativ-Reiter.

Die Ids stehen als Konstanten in `MobDefense` (`DISPENSER_MOUNT`, `SPEAR_MOUNT`, `ROCKET_BOOSTER`, Liste `PARTS`).

---

## 3. Teile (`data/mobdefense/propwash_parts/accessory/`)

| Datei | Halterung | Masse | `size_mm` (B, H, T) | `frontal_area_m2` | `flight_battery_w` | `sort` |
|---|---|---|---|---|---|---|
| `dispenser_mount.json` | `front` | 150 g | 62, 58, 64 (dazu `recess_mm` 58, `drop_mm` 1: unter dem Rahmen, `docs/dispenser.md` §10) | 0,0036 | 1,2 (Servo) | 0 |
| `spear_mount.json` | `bottom` | 40 g | 24, 18, 90 | 0,0004 | 0 | 1 |
| `rocket_booster.json` | `top` | 28 g | 30, 22, 70 | 0,0005 | 0,2 (Zünder) | 2 |

Format 1 wie Propwashs Greifer. Modelle und Render-Definitionen liefert `md-assets`
(`assets/mobdefense/items/<id>.json`, `items/drone_render/accessory/<id>.json` und `<id>_paint.json`). Namen:
`part.mobdefense.<id>`, Kurztext im Tooltip: `mobdefense.help.<id>.summary`. Weitere Hilfezeilen (`.what.N`, `.use.N`)
legen die Modul-Stränge in ihrem Sprachmodul an.

---

## 4. Rezepte (`data/mobdefense/recipe/`)

Geformt, Kategorie `equipment`, Gruppe `propwash_accessory` (wie Greifer und LED-Streifen im Rezeptbuch):

| Ergebnis | Muster | Schlüssel |
|---|---|---|
| Dispenser-Halterung | ` B ` / `NDN` / ` C ` | B `propwash:circuit_board`, N `minecraft:iron_nugget`, D `minecraft:dispenser`, C `propwash:copper_coil` |
| Speer-Halterung | `N N` / `PPP` | N `minecraft:iron_nugget`, P `propwash:carbon_plate` |
| Raketen-Booster | `NFN` / ` P ` | N `minecraft:copper_nugget`, F `minecraft:flint_and_steel`, P `propwash:carbon_plate` |

Freischaltung: `data/mobdefense/advancement/recipes/tools/<id>.json` (wie Propwash), erfüllt, sobald man eine der
Zutaten besitzt. Die Fortschritte des Spiels (`md-osd-ux`) liegen daneben, nicht unter `recipes/`.

---

## 5. Item-Tags (`data/mobdefense/tags/item/`)

| Tag | Inhalt | Nutzung |
|---|---|---|
| `dispenser_fluid_buckets` | Wasser-, Lava- und Pulverschnee-Eimer; Eimer mit Kabeljau, Kugelfisch, Lachs, Tropenfisch, Axolotl und Kaulquappe; optional `#c:buckets/water`, `lava`, `powder_snow`, `entity_water` | Dispenser verweigert sie ohne `allow_fluid_buckets` |
| `spear_mount_accepts` | `#minecraft:spears` | Speer-Halterung (zusätzlich prüft der Speer-Strang die Komponente `minecraft:kinetic_weapon`) |
| `booster_accepts` | `minecraft:firework_rocket` | Raketen-Booster |

Der Schwefelwürfel-Eimer steht bewusst nicht in `dispenser_fluid_buckets`: Er setzt keine Flüssigkeit, nur den Mob
(Design E6: gesperrt wird nur das Setzen von Flüssigkeit). Leerer Eimer und Milch fehlen ebenfalls.
Entity-Tags (`tags/entity_type/**`) gehören `md-mobs-1`.

---

## 6. PW3-Tabelle (`data/mobdefense/propwash_share/table.json`)

```json
{
  "format": 1,
  "namespace": "mobdefense",
  "parts": {
    "accessory": [
      {"id": "dispenser_mount", "since": "1.0.0"},
      {"id": "spear_mount", "since": "1.0.0"},
      {"id": "rocket_booster", "since": "1.0.0"}
    ]
  }
}
```

- Nummern im Sharecode: 1 Dispenser-Halterung, 2 Speer-Halterung, 3 Raketen-Booster. Neue Teile nur hinten anhängen,
  nie umsortieren oder löschen (Propwash `docs/sharecode/tables.md`).
- Kein `fallback`: Ohne Mob Defense bleibt die Halterung leer, statt ein fremdes Zubehör einzusetzen.
- Kein `short_id`: Die Kurzkennung vergibt erst das offizielle Verzeichnis (geplant 3); bis dahin meldet Propwashs
  Prüfwerkzeug jede Kennung, die nicht im Verzeichnis-Abzug steht, als `SHORT_ID_UNKNOWN`.
- Vergleichsdatei: `common/src/test/resources/sharecode/mobdefense-baseline.json`, erzeugt mit
  `ShareTableCheck freeze`. Nach jedem Release neu einfrieren.

Prüfen von Hand (Klassenpfad aus `./gradlew writeClasspath`):

```sh
java -cp "$(cat .classpath/common-test.txt)" de.lardtom.propwash.api.share.ShareTableCheck check \
  --resources common/src/main/resources --version 1.0.0 --baseline common/src/test/resources/sharecode/mobdefense-baseline.json
```

---

## 7. Welt-Regeln (B5)

`rules.MobDefenseRules.register()` meldet die Regeln aus `rules.core.RuleDefaults.CORE` in der Reihenfolge des
Designs (§2.3) über `PropwashApi.worldRules()` an: zehn Regeln, `sculk_hears_drones` (`RuleDefaults.OPTIONAL`) meldet
erst der optionale Sculk-Strang mit `MobDefenseRules.register(RuleDefaults.SCULK_HEARS_DRONES)` an.

| Zugriff | Wirkung |
|---|---|
| `MobDefenseRules.flag(level, RuleDefaults.ALLOW_TNT)` | Schalter lesen (Server: Wert der Welt, Client: abgeglichener Wert, ohne Welt der Standard) |
| `MobDefenseRules.number(level, RuleDefaults.DISPENSER_COOLDOWN_TICKS)` | Zahl lesen |
| `MobDefenseRules.flag(spec)` / `number(spec)` | die `WorldRule` selbst |

Voreinstellungen (`RuleDefaults.PRESET_VALUES`, portabel in `rules.core.PresetValue`):

| Regel | Realistisch | Training | Arcade |
|---|---|---|---|
| `mobs_react_to_drones` | an | **aus** | an |

Alle übrigen Regeln haben keinen Eintrag und bleiben beim Anwenden einer Voreinstellung unverändert. Realistisch und
Arcade schalten die Mobs wieder ein, sonst bliebe nach „Training“ jede andere Voreinstellung ohne Mob-Reaktionen.

Texte: `gamerule.mobdefense.<pfad>` und `….description`, Überschrift des Abschnitts `mobdefense.world_rules.section` =
„Mob Defense“ (EN und DE). Wie bei Propwash steht dort nur der Name, ohne „Propwash:“ (`docs/addon-world-rules.md` §3 in
Propwash); ohne den Schlüssel zeigt Propwash den Mod-Namen ohne „Propwash:“, also dieselbe Überschrift. `LangTest` und
S12 prüfen das.
In Regel-Texten kein einzelnes `%` (Minecraft verwirft solche Texte); „Prozent“ ausschreiben.

---

## 8. Kreativ-Reiter

`MobDefenseTab.init()`: `PropwashApi.extensionTab("mobdefense")` mit der Dispenser-Halterung als Symbol und Zubehör
zuerst (`TabSection.ACCESSORIES`). Titel aus `itemGroup.mobdefense` = „Mob Defense“, Propwash setzt „Propwash: “ davor.
`MobDefenseTab` meldet die drei Teile zusätzlich über `PropwashApi.creativeTab()` an; Propwashs eigener Tab zeigt sie
trotzdem nicht, weil Propwash Items von Add-ons mit eigenem Reiter dort herausnimmt (seit 0.6.0-dev.10). Spieler finden
sie nur im Reiter „Propwash: Mob Defense“ (Anleitung §1, geprüft von `GuideCreativeTabTest`).

---

## 9. Tests

### 9.1 JUnit (`./gradlew :common:test`, `tools/fastcheck.sh test`)

| Test | Prüft |
|---|---|
| `data.PartJsonTest` | genau die drei Teile, Werte aus §2.1 über `PropwashApi.parts().fromJson`, Stirnfläche ≤ B × H, JSON-Rundweg, Modelle und Namen |
| `data.RecipeUniquenessTest` | Propwash-Stil, Zutaten vorhanden, Freischaltung je Zutat, Muster eindeutig (auch gespiegelt und gegen formlose Rezepte) gegen Propwash und Vanilla vom Klassenpfad und die Rezepte von Just More Parts 1.1.1 (Abzug `test/resources/recipes/justmoreparts-1.1.1.json`) |
| `data.Pw3TableTest` | `ShareTableCheck` aus Propwash ohne Befund gegen die Vergleichsdatei, Nummern 1–3, kein Ersatzteil, keine Kurzkennung, Verschieben wird erkannt |
| `rules.core.RuleDefaultsTest` | Owner-Standards, Tabelle aus §2.3, `CORE` ohne Sculk, Voreinstellungen, Ids wie `WorldRules.PRESETS` |
| `NamespaceTest` | Ordner nur `mobdefense`, `minecraft`, `c`; Daten verweisen nur auf `mobdefense`, `minecraft`, `c`, `propwash`; nichts wird im Namensraum `propwash` erzeugt |
| `data.LangTest` | gleiche Schlüssel DE/EN je Modul, kein Schlüssel in zwei Modulen, Deutsch mit Umlauten, ß und „…“, kein einzelnes `%`, OSD-Texte (Schlüssel mit Abschnitt `osd`) nur druckbares ASCII, Kern-Texte vorhanden |
| `MobDefenseTabTest` | Reihenfolge, Symbol, Name des Reiters |
| `GuideCreativeTabTest` | Befund MDDOC-1: Titel des Reiters = Propwashs `itemGroup.propwash.extension` (aus dem Propwash-Jar) mit `itemGroup.mobdefense`, EN und DE „Propwash: Mob Defense“; der Satz zum Kreativmodus in Anleitung EN/DE (§1) nennt genau diesen einen Reiter, die Anleitung nirgends Propwashs eigenen Reiter |

### 9.2 Selbsttest (Modul `core`, `-PmobdefenseSelftestOnly=core/`)

| Schritt | Prüft | Fotos |
|---|---|---|
| S1 Teile und Rezepte | Teile mit Halterung, Masse, Item; jedes Werkbank-Muster ergibt das Teil; Analyse freestyle_5 mit allen drei Teilen +218 g, Höchsttempo sinkt durch die Stirnfläche, +1,4 W; Werkbank zeigt den Bau; Tooltips mit Kurztext und Masse | `core_workbench` |
| S12 Welt-Regeln | zehn Regeln in Reihenfolge und mit Standard, Texte EN/DE, Spieler mit Operator-Recht ändert per `/gamerule`, Wert außerhalb des Bereichs abgelehnt, `/propwash rules preset training` schaltet `mobs_react_to_drones` aus, `realistic` wieder an, Tab „Welt-Regeln“ enthält alle zehn Regeln | `core_world_rules`, `core_world_rules_end` |

Auf NeoForge die Fotos mit Ordner aufrufen (`-PmobdefenseScreenshots=neoforge/runs/selftest-client/screenshots/selftest`);
mit `=true` liest der Läufer das Spielverzeichnis, bevor es dort existiert.

### 9.3 Server-Rauchtest (D1)

Das Modul `core` hängt vier Prüfungen an: Teile, Items und Rezepte; Item-Tags; Welt-Regeln über B5; Kreativ-Reiter.

### 9.4 D8 – Welt ohne Add-on neu starten (beide Loader)

```sh
<slot-skript> ./gradlew :fabric:runServerD8Prepare :fabric:runServerD8NoAddon :fabric:runServerD8Verify
<slot-skript> ./gradlew :neoforge:runServerD8Prepare :neoforge:runServerD8NoAddon :neoforge:runServerD8Verify
```

1. `runServerD8Prepare` (mit Mob Defense): neue flache Welt in `<loader>/runs/d8-server`, Chunk 0,0 dauerhaft geladen,
   eine abgestellte freestyle_5 mit allen drei Teilen und Drohnen-Daten `mobdefense:d8_probe`, eine Kiste mit einem
   Drohnen-Item gleichen Baus, `allow_tnt` aus. Merkt sich alles in `d8-prepare.properties`.
2. `runServerD8NoAddon` (nur Propwash, Quell-Set `jointest`, auf NeoForge ist nur `mobdefense_jointest` geladen): lädt
   dieselbe Welt, stoppt sich 20 s nach „Done“ über die Konsole. Gradle prüft, dass Mob Defense nicht geladen war und
   der Drohnen-Chunk (`world/dimensions/minecraft/overworld/entities/r.0.0.mca`) neu gespeichert wurde, und schreibt
   `d8-noaddon.txt`.
3. `runServerD8Verify` (wieder mit Mob Defense): Drohne mit allen drei Teilen und gleicher Masse, Drohnen-Daten
   erhalten, Drohnen-Item in der Kiste mit vollem Bau und Namen; ob `allow_tnt` den Wert behält, steht als Hinweis im
   Ergebnis.

Ergebnis je Phase in `<loader>/runs/d8-server/mobdefense-selftest-result.txt`.

Stand mit Propwash `0.6.0-dev.7`: D8 besteht auf Fabric und NeoForge. Teile, Masse, Drohnen-Daten und Drohnen-Item
bleiben erhalten, `allow_tnt` behält seinen Wert, der Lauf ohne Add-on hat keine Fehlerzeile im Log. NeoForge warnt
beim Start ohne Add-on einmal `mobdefense (version … -> MISSING)`. Mit `0.6.0-dev.3` schlug D8 noch fehl, weil Propwash
für ein fehlendes Zubehör vorn einen Platzhalter einsetzte, der nicht an die Halterung passt.
