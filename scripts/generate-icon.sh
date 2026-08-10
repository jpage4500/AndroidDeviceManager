#!/bin/bash
#
# Generates the app icon that jDeploy ships on every platform.
#
#   in:  resources/icon-source.png  — the exported artwork (tile shape, transparent outside)
#   out: icon.png                   — 1024x1024, centred squircle, 8-bit sRGB
#
# jDeploy embeds icon.png verbatim as the single 'ic10' slice of icon.icns, so this one file has
# to be right. Two separate things are handled here.
#
# 1. macOS 26 (Tahoe) checks the icon's opaque region against its standard tile and, when it
#    doesn't conform, draws the artwork shrunk onto a grey fallback plate — the icon then looks
#    ~30% too small in the Dock and Cmd-Tab switcher. Measured on 26.5.1 through
#    NSWorkspace.icon(forFile:), rendered into a 256px box where a native icon is 206x206:
#
#      opaque region                          result
#      828x796 squircle, off-centre by 2px    PLATED, artwork 140x140
#      812x812 squircle, centred              accepted, 206x206
#      824x824 squircle, centred              accepted, 206x206
#      1024x1024 full-bleed, sharp corners    accepted, 206x206
#
#    The gate is a CENTRED SQUARE opaque region; macOS scales any conforming inset up to fill the
#    tile. Non-square is what breaks it — a faint-alpha halo around the artwork is enough to do it.
#    A stray halo alone does not plate it, and neither does 16-bit depth.
#
# 2. The source artwork was cut out of a background imperfectly and carries a dirty rim: a 1px
#    black line, then 4-5px of salmon, all fully opaque. It hid while the icon was being plated
#    (drawn small) and is obvious once the icon renders full size, so SHAVE trims it off. Measured
#    contamination on the silhouette ring: 39% at the source edge, 13% at 2px in, 0% from 5px in.
#
# Two ImageMagick traps, both silent: any composite on a Q16 build emits 16-bit PNG, and an
# achromatic tile colour makes it write greyscale (which desaturates the artwork). Every step below
# forces -depth 8 and the PNG32: prefix. '-draw roundrectangle' also does not antialias, so the
# mask is drawn at 4x and scaled down.

set -euo pipefail

cd "$(dirname "$0")/.."

command -v magick >/dev/null || { echo "ERROR: ImageMagick not found (brew install imagemagick)" >&2; exit 1; }

SRC=resources/icon-source.png
OUT=icon.png

CANVAS=1024   # jDeploy fills the 1024 'ic10' slot
SHAVE=6       # px trimmed off each side to clear the source's dirty rim
SUPER=4       # mask supersampling factor

[[ -f "$SRC" ]] || { echo "ERROR: source artwork not found: $SRC" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# the real artwork is the fully-opaque region; anything fainter is halo left by the exporter
read -r AW AH AX AY <<< "$(magick "$SRC" -alpha extract -threshold 98% -format '%@' info: | sed 's/[x+]/ /g')"

# largest centred square that clears the dirty rim, kept even so it centres exactly on the canvas
BODY=$(( (AW < AH ? AW : AH) - 2 * SHAVE ))
BODY=$(( BODY - BODY % 2 ))
RADIUS=$(( BODY * 2237 / 10000 ))   # Apple corner radius: 22.37% of the body
OFFSET=$(( (CANVAS - BODY) / 2 ))

magick "$SRC" -crop "${BODY}x${BODY}+$(( AX + (AW - BODY) / 2 ))+$(( AY + (AH - BODY) / 2 ))" \
  +repage -depth 8 "PNG32:$TMP/art.png"

# squircle mask at the exact body size — this is what macOS checks
magick -size "$(( BODY * SUPER ))x$(( BODY * SUPER ))" xc:black -fill white \
  -draw "roundrectangle 0,0,$(( BODY * SUPER - 1 )),$(( BODY * SUPER - 1 )),$(( RADIUS * SUPER )),$(( RADIUS * SUPER ))" \
  -resize "${BODY}x${BODY}" -depth 8 "PNG:$TMP/mask.png"

magick "PNG32:$TMP/art.png" "PNG:$TMP/mask.png" -alpha off -compose copyopacity -composite \
  -depth 8 "PNG32:$TMP/body.png"

# centred on the transparent canvas
magick -size "${CANVAS}x${CANVAS}" xc:none \
  "PNG32:$TMP/body.png" -gravity center -composite \
  -strip -depth 8 "PNG32:$OUT"

# verify the things that silently break macOS
depth=$(magick identify -format '%[depth]' "$OUT")
ctype=$(magick identify -verbose "$OUT" | sed -n 's/.*png:IHDR.color_type: *//p' | head -1)
opaque=$(magick "$OUT" -alpha extract -threshold 25% -format '%@' info:)
want="${BODY}x${BODY}+${OFFSET}+${OFFSET}"
[[ "$depth" == 8 ]] || { echo "ERROR: $OUT is ${depth}-bit" >&2; exit 1; }
[[ "$ctype" == 6* ]] || { echo "ERROR: $OUT is not RGBA (color_type=$ctype); artwork will be desaturated" >&2; exit 1; }
[[ "$opaque" == "$want" ]] || { echo "ERROR: opaque region is $opaque, need $want or macOS will plate it" >&2; exit 1; }

# verify the dirty rim is gone: no black/red pixels left on the outer edge of the silhouette
if command -v python3 >/dev/null; then
  python3 - "$OUT" <<'PY' || exit 1
import subprocess, sys
P = {}
for line in subprocess.run(["magick", sys.argv[1], "-depth", "8", "txt:-"],
                           capture_output=True, text=True).stdout.splitlines()[1:]:
    try:
        pos, rest = line.split(":", 1); x, y = map(int, pos.split(","))
        h = rest.split("#")[1]
        P[(x, y)] = (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16))
    except Exception:
        pass
opaque = {p for p, v in P.items() if v[3] >= 200}
ring = {p for p in opaque
        if any((p[0]+dx, p[1]+dy) not in opaque for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)))}
def dirty(v):
    r, g, b, _ = v
    return (r > 90 and r - g > 35 and r - b > 35) or max(r, g, b) < 45
n = sum(1 for p in ring if dirty(P[p]))
if n:
    print(f"ERROR: {n} black/red pixels on the icon edge - raise SHAVE", file=sys.stderr)
    sys.exit(1)
print(f"edge is clean ({len(ring)} silhouette pixels checked)")
PY
fi

echo "wrote $OUT ($(magick identify -format '%wx%h' "$OUT"), ${depth}-bit, color_type=$ctype, opaque=$opaque)"
echo "run scripts/reset-icon.sh to clear the macOS icon cache before checking the result"
