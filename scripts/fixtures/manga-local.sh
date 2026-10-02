#!/usr/bin/env bash
# Genera el manga de prueba de la fuente local y lo sube al emulador.
#
# Cada página lleva grabado su capítulo y su número, así que una captura de
# pantalla dice en qué página está el lector sin fiarse de lo que muestra la
# app. Ver docs/TESTING.md, «Fixture de manga».
#
# Uso: scripts/fixtures/manga-local.sh [serial]   (por defecto emulator-5554)
# Requiere: ImageMagick (magick), zip, adb.

set -euo pipefail

SERIAL="${1:-emulator-5554}"
TITLE="Manga de prueba (fixture)"
CHAPTERS=3
PAGES=6
DEST="/sdcard/Documents/local/$TITLE"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
out="$work/$TITLE"
mkdir -p "$out"

colors=("#3b5b92" "#8a3b5b" "#3b7a4a")

for c in $(seq 1 "$CHAPTERS"); do
    pages="$work/cap$c"
    mkdir -p "$pages"
    for p in $(seq 1 "$PAGES"); do
        magick -size 800x1200 "xc:${colors[$(((c - 1) % ${#colors[@]}))]}" \
            -fill white -gravity center \
            -pointsize 96 -annotate +0-120 "Capítulo $c" \
            -pointsize 160 -annotate +0+80 "$p / $PAGES" \
            "$pages/$(printf '%03d' "$p").png"
    done
    (cd "$pages" && zip -q -0 "$out/Capítulo $(printf '%02d' "$c").cbz" ./*.png)
done

magick -size 600x900 xc:"#1f2937" -fill white -gravity center \
    -pointsize 72 -annotate +0-60 "Manga de" \
    -pointsize 72 -annotate +0+40 "prueba" \
    "$out/cover.jpg"

cat > "$out/details.json" <<EOF
{
  "title": "$TITLE",
  "author": "Zenyomi",
  "artist": "Zenyomi",
  "description": "Fixture para la regresión de manga. $CHAPTERS capítulos de $PAGES páginas; cada página lleva su capítulo y su número.",
  "genre": ["Fixture"],
  "status": 2
}
EOF

adb -s "$SERIAL" shell "rm -rf '$DEST'"
adb -s "$SERIAL" push "$out" /sdcard/Documents/local/ >/dev/null
adb -s "$SERIAL" shell "ls -l '$DEST'"
