# Modrinth- und CurseForge-Listing — Propwash: Mob Defense 1.0.0

Alles, was die Projektseiten brauchen. Beschreibung: [`description.md`](description.md) (Englisch, als
Projektbeschreibung einfügen) und [`description_de.md`](description_de.md) (Deutsch, für eine übersetzte Seite oder als
Spoiler-Abschnitt). Changelog für den Versions-Upload: [`CHANGELOG.md`](../../CHANGELOG.md), Abschnitt 1.0.0.
Anleitung für Spieler: [`docs/guide.md`](../guide.md) und [`docs/guide.de.md`](../guide.de.md).

**Stand 1.0.0:** Das CurseForge-Projekt gibt es schon: 1796706, `propwash-mob-defense`
(https://www.curseforge.com/minecraft/mc-mods/propwash-mob-defense); dorthin zeigen auch die Metadaten (`homepage_url`
in `gradle.properties`, im Spiel in der Übersicht „Add-ons passen nicht zum Server“ und in Sharecode-Hinweisen
anklickbar). Das Modrinth-Projekt `propwash-mob-defense` entsteht beim ersten Hochladen als Entwurf. Die Kurzkennung im
Add-on-Verzeichnis der Website (geplant 3) vergibt die Website bei der Aufnahme.

## Projekt

| Feld | Wert |
|---|---|
| Name | Propwash: Mob Defense |
| Slug | `propwash-mob-defense` (Modrinth und CurseForge) |
| Projekttyp | Mod |
| Icon | `common/src/main/resources/assets/mobdefense/icon.png` (128 × 128) |
| Lizenz | PolyForm Shield 1.0.0 mit zusätzlichen Erlaubnissen (Modrinth: eigene Lizenz `LicenseRef-PolyForm-Shield-1.0.0`, Link auf `LICENSE`), Doku MIT (`LICENSE-MIT`), siehe `NOTICE` |
| Client | Pflicht |
| Server | Pflicht |
| Links | Quellcode `https://github.com/LardTom/propwash-mob-defense`, Issues `https://github.com/LardTom/propwash-mob-defense/issues`, Wiki `https://propwashfpv.com/docs/`, Discord `https://discord.gg/bNhkSXYSgZ`, Spenden (Ko-fi) `https://ko-fi.com/propwashfpv`; CurseForge `https://www.curseforge.com/minecraft/mc-mods/propwash-mob-defense` (Startseite in den Metadaten) |

### Kurzbeschreibung (≤ 256 Zeichen)

Englisch (213 Zeichen):

> Three accessories for Propwash FPV drones: a dispenser mount that fires arrows, potions, wind charges and TNT, a spear
> mount and a rocket booster. Hostile mobs react to drones, world rules keep servers in control.

Deutsch (224 Zeichen, falls eine übersetzte Kurzbeschreibung gewünscht ist):

> Drei Zubehörteile für Drohnen aus Propwash FPV: Dispenser-Halterung für Pfeile, Tränke, Windkugeln und TNT,
> Speer-Halterung und Raketen-Booster. Feindliche Mobs reagieren auf Drohnen, Welt-Regeln geben Servern die Kontrolle.

Die Beschreibung in `gradle.properties` (`description`, im Mod-Menü) bleibt wie sie ist.

### Kategorien

| | |
|---|---|
| Hauptkategorien (max. 3) | Equipment, Mobs, Technology |
| Weitere Kategorien | Game Mechanics, Adventure |

Suchbegriffe, die Kurzbeschreibung und Beschreibung abdecken: drone, FPV, dispenser, spear, firework, rocket boost,
mobs, mob AI, combat drone, Propwash, add-on.

## Versionen

Je Loader eine Version hochladen; beide entstehen aus demselben Commit.

| Feld | Fabric | NeoForge |
|---|---|---|
| Datei | `fabric/build/libs/mobdefense-fabric-26.3-1.0.0.jar` | `neoforge/build/libs/mobdefense-neoforge-26.3-1.0.0.jar` |
| Versionsnummer (Vorschlag) | `1.0.0+fabric` | `1.0.0+neoforge` |
| Versionsname (Vorschlag) | Mob Defense 1.0.0 (Fabric) | Mob Defense 1.0.0 (NeoForge) |
| Loader | Fabric | NeoForge |
| Spielversionen | 26.3 | 26.3 |
| Abhängigkeiten | Propwash FPV ≥ 0.6.0 (`0.6.0+fabric`) — Pflicht; Fabric API — Pflicht | Propwash FPV ≥ 0.6.0 (`0.6.0+neoforge`) — Pflicht |
| Kanal (Vorschlag) | Beta, solange Propwash Beta ist | Beta |
| Changelog | `CHANGELOG.md`, Abschnitt 1.0.0 | ebenso |

Die `-sources`-Jars nicht als Hauptdatei hochladen. Mindestversionen aus `gradle.properties`: Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, NeoForge 26.3.0.10-beta, Java 25, Propwash FPV 0.6.0 (Add-on-API 1.8). Die Jar-Namen folgen
`mod_id` und `version`; vor dem Upload mit `ls fabric/build/libs neoforge/build/libs` prüfen. Nur Jars aus dem
Release-Lauf hochladen, nie ein `-dev.N` oder `-rc.N` als 1.0.0.

## Galerie

Die Fotos in `docs/screenshots/selftest/` stammen aus dem Selbsttest (flache Testwelt) und belegen die Funktionen; für
die Projektseite taugen davon vor allem die Fenster und das OSD. Die übrigen Galeriebilder entstehen nach der Shotliste
(Lieferung außerhalb des Repositorys) in gebauten Welten. Reihenfolge wie unten, das erste ist hervorgehoben.

| # | Datei | Titel (en) | Beschreibung (en) |
|---|---|---|---|
| 1 ★ hervorgehoben | *neu: 5″ mit Dispenser feuert Pfeile auf eine Zombie-Gruppe bei Nacht, FPV mit OSD* | A dispenser on your drone | Arrows in continuous fire from the dispenser mount – aim with the whole drone, fire on accessory action 1. |
| 2 | *neu: Drohne mit allen drei Teilen an der Werkbank* | Three accessories | Dispenser mount in front, spear below, rocket booster on top – real parts with weight and drag. |
| 3 | *neu: Windkugel gegen eine Wand, Third Person* | Recoil is real | A wind charge from the dispenser pushes the drone back hard. |
| 4 | *neu: Skelette nehmen die Drohne unter Beschuss, FPV* | They shoot back | Skeletons lead their shots – every hostile mob reacts to drones in its own way. |
| 5 | *neu: Phantom-Sturzangriff bei Nacht* | Never fly alone at night | Phantoms circle above high drones and swoop down. |
| 6 | *neu: Vorbeiflug mit dem Speer an einem Zombie* | Drone Joust | The spear hits by itself, with vanilla spear damage. |
| 7 | `booster_burn.png` | Elytra boost, but drone | One firework rocket, 2 g along the nose. |
| 8 | `ui_osd_editor_320x240_list.png` | Your OSD | Dispenser, slots, booster and spear durability in the OSD editor group Add-ons. |
| 9 | *neu: Tab Welt-Regeln, Abschnitt Mob Defense* | Server rules decide | Ten world rules for mobs, TNT, griefing, buckets, ammo and damage. |
| 10 | `ui_flight_school_spear.png` | Learn it in the flight school | Flight school chapters for all three accessories. |

Die Bilder mit `_de` im Namen zeigen dieselben Szenen auf Deutsch (Prüfung der Übersetzung und Beschriftungsbreiten);
sie gehören nicht in die Galerie. Für Bild 9 gibt es im Repository kein aktuelles Foto (die alten zeigen noch die frühere
Überschrift, siehe `docs/testing.md`).

Bilder in der Beschreibung: Banner, Überschriften und GIFs liegen im öffentlichen Repository unter `.github/listing/`;
die Beschreibung verlinkt sie als `https://raw.githubusercontent.com/LardTom/propwash-mob-defense/main/.github/listing/<datei>`.
Sie erscheinen, sobald das Repository öffentlich ist (beim Release kommt GitHub vor Modrinth). Text für CurseForge:
[`../curseforge/description.md`](../curseforge/description.md).