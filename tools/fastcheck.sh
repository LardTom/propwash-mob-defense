#!/bin/bash
set -u
MAIN="$(cd "$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")" && pwd)"
if [ -n "${MD_JDK:-}" ]; then
  JDK=$MD_JDK/bin
elif [ -n "${JAVA_HOME:-}" ]; then
  JDK=$JAVA_HOME/bin
elif command -v brew >/dev/null 2>&1 && [ -x "$(brew --prefix openjdk@25 2>/dev/null)/bin/javac" ]; then
  JDK=$(brew --prefix openjdk@25)/bin
else
  JDK=$(dirname "$(command -v javac)")
fi
TARGET=${1:-common}
ROOT=$(pwd)
OUT=$(mktemp -d /tmp/md-fastcheck.XXXXXX)
trap 'rm -rf "$OUT"' EXIT

CP=$MAIN/.classpath
if [ -f "$ROOT/.classpath/common.txt" ]; then
  CP=$ROOT/.classpath
fi
need_classpath() {
  if [ ! -f "$CP/common.txt" ]; then
    echo "Klassenpfad fehlt: im Arbeitsbaum einmal 'JAVA_HOME=${JDK%/bin} ./gradlew writeClasspath' ausführen" >&2
    exit 2
  fi
}

sources() {
  find "$ROOT/$1/src/main/java" "$ROOT/$1/src/devtest/java" "$ROOT/$1/src/jointest/java" -name '*.java' 2>/dev/null
}

compile() {
  local name=$1 cp=$2
  shift 2
  mkdir -p "$OUT/$name"
  "$JDK/javac" -proc:none -nowarn -encoding UTF-8 -Xmaxerrs 400 -d "$OUT/$name" -cp "$cp" "$@" 2>&1
  return ${PIPESTATUS[0]}
}

json_check() {
  python3 - "$ROOT" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
bad = 0
for base in ["common/src/main/resources", "common/src/lang", "common/src/devtest/resources", "fabric/src/main/resources",
             "fabric/src/jointest/resources", "neoforge/src/main/resources", "neoforge/src/jointest/resources"]:
    for path in sorted((root / base).rglob("*.json")):
        text = path.read_text(encoding="utf-8")
        if "${" in text:
            continue
        try:
            json.loads(text)
        except ValueError as e:
            bad += 1
            print(f"JSON-Fehler: {path.relative_to(root)}: {e}")
sys.exit(1 if bad else 0)
PY
}

portable() {
  local files
  files=$(find "$ROOT/common/src/main/java" -path '*/core/*' -name '*.java' 2>/dev/null)
  [ -z "$files" ] && { echo "keine portablen Kerne"; return 0; }
  mkdir -p "$OUT/portable" "$OUT/empty"
  "$JDK/javac" -proc:none -encoding UTF-8 --release 21 -implicit:none -d "$OUT/portable" -cp "$OUT/empty" -sourcepath "$OUT/empty" $files 2>&1
  return ${PIPESTATUS[0]}
}

status=0
case "$TARGET" in
  common)
    need_classpath
    json_check || status=1
    compile common "$(cat "$CP/common.txt")" $(sources common) || status=1 ;;
  fabric)
    need_classpath
    compile fabric "$(cat "$CP/fabric.txt")" $(sources common) $(sources fabric) || status=1 ;;
  neoforge)
    need_classpath
    compile neoforge "$(cat "$CP/neoforge.txt")" $(sources common) $(sources neoforge) || status=1 ;;
  checkPortable|portable)
    portable || status=1 ;;
  all)
    for t in common fabric neoforge checkPortable; do "$0" "$t" || status=1; done ;;
  test)
    need_classpath
    compile common "$(cat "$CP/common.txt")" $(sources common) || exit 1
    tests=$(find "$ROOT/common/src/test/java" -name '*.java' 2>/dev/null)
    [ -z "$tests" ] && { echo "keine Tests"; exit 0; }
    cp_test="$OUT/common:$ROOT/common/src/main/resources:$(cat "$CP/common.txt"):$(cat "$CP/common-test.txt")"
    compile tests "$cp_test" $tests || exit 1
    [ -d "$ROOT/common/src/test/resources" ] && cp_test="$ROOT/common/src/test/resources:$cp_test"
    console=$CP/junit-console.jar
    [ -f "$console" ] || console=$MAIN/.classpath/junit-console.jar
    shift
    "$JDK/java" -Dmobdefense.resources="$ROOT/common/src/main/resources" -jar "$console" execute --disable-banner --details=summary \
      -cp "$OUT/tests:$cp_test" ${@:---scan-class-path} 2>&1 | tail -"${MD_TAIL:-40}"
    status=${PIPESTATUS[0]} ;;
  *)
    echo "Aufruf: tools/fastcheck.sh [common|fabric|neoforge|checkPortable|all|test [junit-optionen]]" >&2; exit 2 ;;
esac
[ $status -eq 0 ] && echo "FASTCHECK $TARGET OK" || echo "FASTCHECK $TARGET FEHLER"
exit $status
