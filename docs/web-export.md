# Web-Export – Datenpaket für Website und Web-Konfigurator

| | |
|---|---|
| Format | `web-export` **1** (Feld `format` in `manifest.json`), Aufbau wie bei Just More Parts, beschränkt auf den Namensraum `mobdefense` |
| Erzeugung | `./gradlew webExport` (= `:fabric:runWebExport`, auch `:neoforge:runWebExport`): dedizierter Entwicklungs-Server mit Propwash und Mob Defense, schreibt nach `build/web-export/`, beendet sich; Ergebnis `PASS …` in `fabric/runs/web-export/mobdefense-selftest-result.txt` |
| Code | Quell-Set `devtest`, Paket `de.lardtom.mobdefense.dev.export` (`WebExport`, `PackFiles`, `LangTable`, `ExportJson`, `Problems`), Einstieg `dev.WebExportRun` (Property `mobdefense.webexport=<zielordner>`) |
| Quellen | öffentliche Propwash-API (`parts()`, `partItems()`), `rules.core.RuleDefaults`, das Gamerule-Register und die Dateien des Mod-Jars über die Datenpakete des Servers (`assets/mobdefense/**`, `data/mobdefense/**`) |

Der Web-Konfigurator lädt dieses Paket zusätzlich zu dem von Propwash bzw. Just More Parts; die Website baut daraus die
Tabelle der Welt-Regeln (`docs/design.md` §7.1).

---

## 1. Grundregeln

- UTF-8, JSON mit 2 Leerzeichen Einzug, Zeilenende `\n`, Schlüssel in `snake_case`, Ids immer voll (`mobdefense:…`).
- Texte als Objekt `{"de_de": "…", "en_us": "…"}`; fehlt ein Schlüssel, steht dort `null`, `problems.json` nennt ihn.
- Gleiche Mods ergeben gleiche Dateien, außer `manifest.json#generated_at`.
- Neue Felder dürfen in Format 1 dazukommen; Umbenennen oder Entfernen heißt Format 2.
- Vor dem Schreiben werden alle Ausgaben aus §2 im Zielordner gelöscht.

---

## 2. Dateien

```
build/web-export/
  manifest.json      format, generated_at, generator, minecraft, mods, propwash_api, namespace, languages, counts,
                     fingerprint (Teile-Fingerabdruck von Propwash), external_refs, files
  parts.json         Teile von Mob Defense
  rules.json         Welt-Regeln
  recipes.json       Rezepte aus data/mobdefense/recipe/
  share.json         PW3-Tabelle (data/mobdefense/propwash_share/table.json) oder null
  lang/de_de.json    alle Texte von Mob Defense
  lang/en_us.json
  assets/mobdefense/ Modelle, Item-Definitionen, Texturen, Symbol (ohne lang/)
  problems.json      Fehler und Warnungen
```

### 2.1 `parts.json`

Je Teil (sortiert nach `sort`, dann Id) wie bei Just More Parts: `id`, `category`, `builtin` (immer `false`), `name`
(`part.mobdefense.<pfad>`), `short_name` (`null`), `help` je Sprache (`mobdefense.help.<pfad>.summary`, `what.1…8`,
`use.1…8`), `basics` (`mass_g`, `stack_size`, `sort`, `creative`, `durability`), `data` (`PropwashApi.parts().toJson`,
also genau das JSON-Schema von Propwash) und `model` (`kind` `model` oder `procedural`, `item`
= `assets/mobdefense/items/<pfad>.json`, `drone` = `assets/mobdefense/items/drone_render/<kategorie>/<pfad>.json`,
`textures` = alle darüber erreichten Texturen).

### 2.2 `rules.json`

`{"section": "mobdefense", "section_name": {…}, "rules": [...]}`, je Regel aus `RuleDefaults` in deren Reihenfolge:
`id`, `type` (`boolean` oder `integer`), `default`, `min`, `max` (bei Schaltern `null`), `registered` (Gamerule im
laufenden Spiel angemeldet), `name` (`gamerule.mobdefense.<pfad>`) und `description` (`….description`).

### 2.3 `recipes.json`

`[{"id": "mobdefense:<pfad>", "recipe": <json der datei>}]`, sortiert nach Pfad.

---

## 3. Prüfungen

Fehler (Ergebnis `FAIL`): Sprachdatei fehlt oder ist kein flaches Text-Objekt, JSON nicht lesbar, Teil ohne Item, Verweis
eines Modells oder einer Item-Definition auf eine fehlende Datei im eigenen Namensraum, Gamerule `mobdefense:*` ohne
Eintrag in `RuleDefaults`, angemeldete Regel mit anderem Standard oder Bereich als `RuleDefaults`, PW3-Tabelle nicht
lesbar.

Warnungen: Schlüssel nur in einer Sprache, Name oder Hilfe eines Teils fehlt, Item-Modell fehlt, Texte einer
angemeldeten Regel fehlen. Verweise in andere Namensräume (`minecraft`, `propwash`) prüft der Export nicht; sie stehen
in `manifest.json#external_refs`.

---

## 4. Stand und offene Punkte

Lauf vom 28.10.2026 (Propwash 0.6.0-dev.10, API 1.7.0, Fabric): `PASS 3 Teile, 11 Welt-Regeln, 3 Rezepte, 22 Asset-Dateien,
115 Texte, 0 Warnungen`; `counts.rules_registered` 10 (`sculk_hears_drones` ist nicht angemeldet, Texte `null`),
`external_refs` nur `minecraft:textures/block/dispenser_front`. Die Tabellen der Welt-Regeln in `docs/guide.md` und
`docs/guide.de.md` sind aus `rules.json` dieses Laufs erzeugt.

- `rules.json#section_name` kommt aus `mobdefense.name` („Propwash: Mob Defense“), im Tab „Welt-Regeln“ heißt der
  Abschnitt aber „Mob Defense“ (`mobdefense.world_rules.section`). Bis der Export den Schlüssel des Abschnitts nimmt,
  sollte die Website „Mob Defense“ als Überschrift setzen.
