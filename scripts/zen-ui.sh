# Ayudas para manejar Zenyomi en el emulador por adb. Se cargan con `source`, desde bash o zsh:
#
#   export OUT=<carpeta de capturas>; source scripts/zen-ui.sh
#   (con `OUT=… source`, bash y zsh deshacen la variable al acabar el source)
#   zen_launch                      # abre la app (la reinicia si ya estaba abierta)
#   ui_tap "Add to library"         # toca el nodo cuyo texto o content-desc es exactamente ese
#   ui_tapdesc Manga                # solo por content-desc: la barra inferior, no los chips
#   ui_texts                        # textos y content-desc de la pantalla
#   ui_shot 1-biblioteca            # captura a $OUT/1-biblioteca.png
#
# Lo usa la skill zen-test. Ver docs/TESTING.md.

ZEN_SERIAL="${ZEN_SERIAL:-emulator-5554}"
ZEN_PKG="${ZEN_PKG:-app.zenyomi.dev}"
OUT="${OUT:-$(mktemp -d)}"
mkdir -p "$OUT"
export PATH="$PATH:$HOME/Android/Sdk/platform-tools"

# Función y no variable: zsh no parte "$A" en palabras.
a() { adb -s "$ZEN_SERIAL" "$@"; }

zen_launch() {
    a shell am force-stop "$ZEN_PKG"
    a shell monkey -p "$ZEN_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep "${1:-6}"
}

# Un BACK de más cierra la app sin avisar, y todo lo que se lea después es el launcher.
zen_focused() {
    a shell dumpsys window | grep -m1 mCurrentFocus | grep -q "$ZEN_PKG" ||
        { echo "LA APP NO TIENE EL FOCO" >&2; return 1; }
}

ui_dump() {
    a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    a exec-out cat /sdcard/ui.xml >"$OUT/ui.xml"
}

ui_texts() {
    ui_dump
    grep -o -E '(text|content-desc)="[^"]+"' "$OUT/ui.xml" | sort -u
}

ui_tapdesc() { ZEN_MATCH=desc ui_tap "$@"; }

ui_tap() {
    zen_focused || return 1
    ui_dump
    local xy
    xy=$(python3 - "$1" "$OUT/ui.xml" "${ZEN_MATCH:-any}" <<'PY'
import re, sys, xml.etree.ElementTree as ET
target, path, mode = sys.argv[1:4]
for node in ET.parse(path).iter("node"):
    if (mode != "desc" and node.get("text") == target) or node.get("content-desc") == target:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
    [ -z "$xy" ] && { echo "NO ENCONTRADO: $1" >&2; return 1; }
    a shell input tap "${xy% *}" "${xy#* }"
    sleep "${2:-2}"
}

ui_shot() {
    a exec-out screencap -p >"$OUT/$1.png"
    echo "$OUT/$1.png"
}

# Cuenta los crashes del logcat actual; debe dar 0.
zen_crashes() {
    a logcat -d >"$OUT/logcat.txt"
    grep -cE 'Fatal signal|FATAL EXCEPTION|NoSuchMethodError|Abort message' "$OUT/logcat.txt"
}
