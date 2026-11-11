import argparse
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mdstyle import check, icon, parts, paths, templates, writer


def generate(out, only=None, icons=True, with_icon=True):
    w = writer.Writer(out, icons=icons)
    definitions = parts.load(paths.PARTS, set(only) if only else None)
    w.clean([key for _, key, _, _ in definitions])
    for category, key, definition, source in definitions:
        w.part(templates.build(key, definition))
    report = w.finish(merge=bool(only))
    if with_icon and not only:
        w.binary(Path(out) / paths.NS / "icon.png", icon.png_bytes())
        report.append(("icon", "icon.png", f"{icon.SIZE}×{icon.SIZE}"))
    return w, definitions, report


def reference_problems():
    try:
        source = paths.propwash_assets()
    except SystemExit as e:
        print(f"Hinweis: {e}")
        return []
    problems = check.check_reference(source)
    if not problems:
        print(f"Greifer aus {source.name}: Anzeige-Transformationen und Item-Modell mit kit.compute_display nachgerechnet, gleich")
    return problems


def print_problems(problems):
    for line in problems:
        print(line)
    print(f"STIL-PRÜFUNG {'OK' if not problems else f'{len(problems)} Befunde'}")
    return 1 if problems else 0


def describe(definitions, report):
    for category, key, definition, source in definitions:
        origin = "Teil-Definition" if source == "data" else "Maße aus docs/design.md §2.1 (Teil-Definition fehlt noch)"
        print(f"{category}/{key}: {parts.slot_of(definition)}, {definition['size_mm']} mm, {origin}")
    for category, key, note in report:
        print(f"  {category}/{key}: {note}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Erzeugt Modelle, Texturen, Item-Modelle, Lack-Varianten und das Icon von "
                                                 "Mob Defense im Propwash-Stil (Stil-API mdstyle) und prüft sie.")
    parser.add_argument("--only", action="append", choices=templates.KEYS, help="nur dieses Teil (mehrfach möglich)")
    parser.add_argument("--out", default=str(paths.PREVIEW_ASSETS),
                        help="Ziel-Ordner assets (Standard: build/style-preview/assets, --write: Mod-Ressourcen)")
    parser.add_argument("--write", action="store_true", help="in common/src/main/resources/assets schreiben")
    parser.add_argument("--check-only", metavar="ORDNER", help="nichts erzeugen, nur einen assets-Ordner prüfen")
    parser.add_argument("--verify", action="store_true",
                        help="neu in einen Zwischenordner erzeugen, Byte für Byte mit den Mod-Ressourcen vergleichen und "
                             "die Ressourcen prüfen (ändert nichts)")
    args = parser.parse_args(argv)
    if args.check_only:
        root = Path(args.check_only)
        return print_problems(check.vendored_intact() + reference_problems()
                              + check.check(root, icon_file=root / paths.NS / "icon.png"))
    if args.verify:
        with tempfile.TemporaryDirectory(prefix="mdstyle-") as tmp:
            w, definitions, report = generate(Path(tmp))
            describe(definitions, report)
            problems = check.vendored_intact() + reference_problems() + w.problems
            problems += check.same_tree(tmp, paths.ASSETS_ROOT)
            problems += check.check(paths.ASSETS_ROOT)
            print(f"{paths.NS}: Mod-Ressourcen gegen frisch erzeugte Dateien verglichen")
            return print_problems(problems)
    out = paths.ASSETS_ROOT if args.write else Path(args.out)
    w, definitions, report = generate(out, args.only)
    describe(definitions, report)
    print(f"{paths.NS}: {len(definitions)} Teile im Propwash-Stil nach {out} ({len(w.out.written)} Dateien, "
          f"{len(w.out.paint_groups)} Lack-Varianten)")
    icon_file = out / paths.NS / "icon.png"
    problems = check.vendored_intact() + reference_problems() + w.problems
    problems += check.check(out, full=not args.only, icon_file=icon_file)
    return print_problems(problems)


if __name__ == "__main__":
    sys.exit(main())
