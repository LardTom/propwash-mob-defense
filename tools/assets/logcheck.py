import argparse
import re
import sys
from pathlib import Path

PROBLEM = re.compile(
    r"missing (block |item )?(model|models|texture|textures)|unable to (load|resolve|find|parse)|failed to (load|parse|resolve)"
    r"|couldn't (load|parse|find)|no model (loaded|found)|using missing|not found|invalid (model|item)",
    re.IGNORECASE)
LEVEL = re.compile(r"/(WARN|ERROR|FATAL)\]")
RESOURCE = re.compile(r"(model|texture|textures|sprite|item|items|atlas|resource)", re.IGNORECASE)
LOADED = re.compile(r"Loaded \d+ |Created: \d+x\d+x\d+ ", re.IGNORECASE)


def scan(lines, namespace):
    ours, other = [], []
    loaded = 0
    for i, line in enumerate(lines):
        if LOADED.search(line):
            loaded += 1
        if not LEVEL.search(line):
            continue
        block = line + "".join(lines[i + 1:i + 4])
        if not (PROBLEM.search(block) and RESOURCE.search(block)):
            continue
        if f"{namespace}:" in block or f"/{namespace}/" in block:
            ours.append(line.rstrip())
        else:
            other.append(line.rstrip())
    return ours, other, loaded


def main(argv=None):
    parser = argparse.ArgumentParser(description="Prüft ein latest.log des Client-Rauchtests auf fehlende Modelle und "
                                                 "Texturen (Warnungen und Fehler beim Laden der Ressourcen).")
    parser.add_argument("log", help="Pfad zu logs/latest.log")
    parser.add_argument("--namespace", default="mobdefense")
    args = parser.parse_args(argv)
    file = Path(args.log)
    if not file.exists():
        print(f"{file}: fehlt")
        return 2
    lines = file.read_text(encoding="utf-8", errors="replace").splitlines(keepends=True)
    ours, other, loaded = scan(lines, args.namespace)
    for line in ours:
        print(f"BEFUND {line}")
    for line in other:
        print(f"andere Mod oder Vanilla: {line}")
    print(f"{file}: {len(lines)} Zeilen, {loaded} Lade-Meldungen, {len(ours)} Befunde für {args.namespace}, "
          f"{len(other)} weitere Ressourcen-Warnungen")
    print(f"RESSOURCEN {'OK' if not ours else 'FEHLER'}")
    return 1 if ours else 0


if __name__ == "__main__":
    sys.exit(main())
