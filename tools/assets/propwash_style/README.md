# propwash_style — unveränderte Kopie des Propwash-Teilegenerators

Quelle: **Propwash FPV 0.6.0 in Entwicklung**, Ordner `tools/assets/parts` auf dem Integrationszweig `integration`
(Stand `49f4911c` vom 2026-10-19). Gegenüber dem Release `v0.5.2` (`18d8ad2c`), gegen das Mob Defense heute noch
baut, unterscheidet sich nur `stacks.py`: dort kam mit `2aa4ffda` (2026-10-18) die Variante `_mounted` für Stacks mit
Stecker dazu. Mob Defense nutzt sie nicht (nur Zubehör).

Letzte Änderung je Datei in der Quelle:

| Datei | Commit | Datum |
|---|---|---|
| `accessories.py` | `60b12f73` | 2026-10-03 |
| `addon.py` | `4bbcbb0a` | 2026-10-08 |
| `addon_preview.py` | `66fbf79e` | 2026-09-29 |
| `batteries.py` | `287b6ff6` | 2026-10-09 |
| `ducts.py` | `4bbcbb0a` | 2026-10-08 |
| `frames.py` | `287b6ff6` | 2026-10-09 |
| `kit.py` | `8ef51fb5` | 2026-10-06 |
| `layout.py` | `c1c1806c` | 2026-10-14 |
| `motors.py` | `4fc1597b` | 2026-09-24 |
| `paint.py` | `82c7d303` | 2026-09-24 |
| `paintable.py` | `287b6ff6` | 2026-10-09 |
| `props.py` | `4dda8ba3` | 2026-10-11 |
| `render.py` | `dcce1438` | 2026-09-24 |
| `stacks.py` | `2aa4ffda` | 2026-10-18 |
| `video.py` | `81c0d27a` | 2026-09-23 |

Die Dateien sind **byte-gleich** mit der Quelle (Prüfsummen in `SHA256SUMS`, geprüft von
`tools/assets/style_generate.py` bei jedem Lauf; eine Datei ohne Prüfsumme ist ebenfalls ein Befund). Hier wird nichts
geändert: Regeln von Mob Defense stehen ausschließlich in `tools/assets/mdstyle/` (Stil-API, siehe
`tools/assets/README.md`).

| Datei | Inhalt |
|---|---|
| `kit.py` | Modell-Baukasten: `Model`, `box`, `cbox`, `bar`, `cylinder` (Achteck aus zwei um 45° versetzten Kastensätzen), `cylinder_x`, `ring`, Textur-Packer (64 × 64, Dichte in Texel je 8 mm, verringert in 10-%-Schritten, bis alles passt), `compute_display` (Anzeige-Transformationen aller Item-Kontexte), `item_definition`, JSON-Ausgabe |
| `paint.py` | Palette und Maler: `carbon`, `alu`, `plastic`, `wire`, `steel`, `rubber`, TPU-Farben, Schrift, Rauschen, Kanten |
| `paintable.py` | Lack-Varianten (`_paint`), Kanäle und Referenz-Grau, `drone_paint.json` |
| `accessories.py` | Zubehör der Basis: Greifer (`build_gripper`, Vorbild für Dichte, GUI-Drehung, Füllung und Lack), LED-Streifen, Action-Cams, Sockel `mount_top`; daraus genutzt: `camera_fingers`, `servo_label`, `register_materials` |
| `motors.py`, `props.py`, `batteries.py`, `stacks.py`, `frames.py`, `video.py`, `ducts.py` | Vorlagen der übrigen Kategorien (gebraucht, weil `addon.py` und `paintable.py` sie laden; `stacks.lead` für Kabel) |
| `layout.py` | Rahmen-Layout und Presets der eingebauten Teile (für die Vorschau an Propwash-Rahmen) |
| `render.py` | Software-Rasterer der Vorschauen (nutzen `tools/assets/preview.py` und `compare_render.py`) |
| `addon.py`, `addon_preview.py` | Addon-Generator der Basis (Mob Defense nutzt daraus `Output`, `write_paint`, `register_materials`) |

Nicht kopiert: `generate.py`, `preview.py`, `paint_preview.py`, `validate.py`, `part_bases.py` und `addon_example/`
(schreiben oder prüfen nur Propwash-eigene Ressourcen).

## Aktualisieren

1. Die Dateien der Tabelle aus dem neuen Propwash-Stand unverändert hierher kopieren (nur lesen in Propwash).
2. `shasum -a 256 *.py > SHA256SUMS` in diesem Ordner, Quelle, Stand und Tabelle oben anpassen.
3. `python3 tools/assets/style_generate.py --write`, danach `--verify`, `python3 tools/assets/preview.py` und
   `python3 tools/assets/compare_render.py` laufen lassen und die Bilder in `docs/asset-sheets/` ansehen; brechen Aufrufe
   aus `mdstyle`, dort anpassen (nie hier).

Benötigt ein `python3` mit numpy und Pillow (wie der Generator in Propwash).
