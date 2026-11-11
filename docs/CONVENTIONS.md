# Konventionen – Propwash: Mob Defense

Gilt für alle Stränge. Vorbild: die Konventionen von Propwash FPV und Just More Parts.

## Code
- **Keine Kommentare** in `common/src`, `fabric/src`, `neoforge/src` (auch nicht in Tests und `devtest`). Erklärungen
  gehören in `docs/`. Vor jedem Commit: `python3 tools/strip_comments.py common/src fabric/src neoforge/src`.
- Pakete: `de.lardtom.mobdefense.<modul>` (Logik), `<modul>.client` (nur Client), `<modul>.core` (portabel),
  Loader-Glue in `de.lardtom.mobdefense.fabric|neoforge`. Keine Wildcard-Imports, `final` und Records wo möglich.
- **Portable Kerne** (`*/core`): weder Minecraft noch Propwash, nur Java-21-Sprachmittel; `:common:checkPortable` prüft.
- **Nur die öffentliche Propwash-API** (`de.lardtom.propwash.api.**` und die freigegebenen Theme-Klassen). Keine Mixins
  oder Reflection in Propwash. Mixins nur in Vanilla-Klassen, als Accessor oder kleine Injektion unter `mixin/`.
- Fehlt etwas in der API: Lücke mit genauer Signatur in `docs/design.md` §3.3 eintragen und an den Propwash-Strang melden.
- Minecraft-, Fabric- und NeoForge-APIs nur aus den 26.3-Quellen in Propwashs `.ref/`.

## Namensraum und Daten
- Alles unter `mobdefense`, nie unter `propwash` (reserviert). Erlaubt sind daneben nur Tag-Ergänzungen unter
  `minecraft` und `c`; `NamespaceTest` prüft.
- Teile als JSON unter `data/mobdefense/propwash_parts/accessory/`, PW3-Tabelle nur hinten erweitern.

## Sprache
- Jeder Strang pflegt `common/src/lang/<modul>/de_de.json` und `en_us.json`; `:common:mergeLang` erzeugt
  `assets/mobdefense/lang/*.json` (eingecheckt, nie von Hand ändern).
- Deutsch **mit Umlauten und ß**, typografische Anführungszeichen „…“, Zoll als `″`, Dezimalkomma. OSD-Texte nur ASCII,
  deshalb Wörter ohne Umlaute wählen statt zu ersetzen.
- Keine Marken, keine echten Namen, keine lokalen Pfade. Autor und Marke: nur „lardtom“. Keine Hinweise auf Werkzeuge,
  mit denen Code oder Texte entstanden sind.

## Git
- `main` ist die Integration, Stränge in eigenen Worktrees auf `feature/<strang>`. **Nie pushen, kein Remote.**
- Commit-Nachrichten deutsch, Autor aus der Repo-Einstellung (`lardtom`), keine Co-Autoren- oder Sitzungszeilen.
- Nur eigene Dateien ändern (Besitz-Tabelle in `docs/design.md` §11).
