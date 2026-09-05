#!/bin/bash
#
# Generates the app icon that jDeploy ships on every platform.
#
#   in:  resources/icon-source.png  — the exported artwork (tile shape, transparent outside)
#   out: icon.png                   — 1024x1024, full-bleed squircle, 8-bit sRGB
#        icon.icns                  — the same artwork as a 10-size icns (see 3)
#
# jDeploy embeds icon.png verbatim as the single 'ic10' slice of icon.icns, so this one file has
# to be right. Three separate things are handled here.
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
#    The gate is a CENTRED SQUARE opaque region — non-square is what breaks it, and a faint-alpha
#    halo around the artwork is enough. A stray halo alone does not plate it, nor does 16-bit depth.
#
#    The harness normalises every accepted row to 206, so it says nothing about the size the Dock
#    draws. The Dock does not scale an inset up: it draws the image 1:1 into the tile, and the
#    ~100px margin conforming apps bake in is what lines them up (Firefox ships 824x830+100+100,
#    Docker 206x208+25+25). Full-bleed measured 121px in the Dock beside IntelliJ's 99px.
#
#    So icon.png stays FULL BLEED — it is also the repo's own artwork (the README, a file manager
#    drawing the project folder's icon.png on its row), where a margin just renders it small. The
#    Mac-facing outputs (app_icon.png, icon.icns) are inset to Apple's MAC_BODY/1024 grid instead.
#
# 2. The source artwork was cut out of a background imperfectly and carries a dirty rim: a 1px
#    black line, then 4-5px of salmon, all fully opaque. It hid while the icon was being plated
#    (drawn small) and is obvious once the icon renders full size, so SHAVE trims it off. Measured
#    contamination on the silhouette ring: 39% at the source edge, 13% at 2px in, 0% from 5px in.
#
# 3. Correct geometry is necessary but NOT sufficient. jDeploy writes icon.icns with a single
#    'ic10' (1024px) slice, and the Dock and Cmd-Tab switcher still plate a one-slice icns even
#    when the geometry conforms. Apps that render full size ship a size family — IntelliJ IDEA has
#    11 slices and no modern asset at all, which rules out Tahoe requiring an Icon Composer
#    '.icon'/Assets.car. Replacing the installed bundle's icon.icns with the 10-size file this
#    script builds fixed it. NOTE: NSWorkspace.icon(forFile:) reports a one-slice icns as correct
#    at every size, so it cannot detect this — only the Dock shows it, and only after
#    scripts/reset-icon.sh, since macOS re-caches the plated composite immediately.
#
#    icon.icns below does NOT reach a build: 'npx jdeploy package' copies only icon.png (and the
#    splashes) into jdeploy-bundle/, and the installer regenerates the icns from icon.png on the
#    user's machine. Verified 2026-08-10 — a root icon.icns and icon-<size>.png files are both
#    ignored by the packager. So it is only good for patching an already-installed bundle:
#      cp icon.icns "$HOME/Applications/Android Device Manager.app/Contents/Resources/icon.icns"
#    Fresh installs still get the one-slice icns until jDeploy itself is fixed.
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
MAC_BODY=824  # Apple's macOS icon grid: artwork inset to 824 inside the 1024 tile

[[ -f "$SRC" ]] || { echo "ERROR: source artwork not found: $SRC" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# the real artwork is the fully-opaque region; anything fainter is halo left by the exporter
read -r AW AH AX AY <<< "$(magick "$SRC" -alpha extract -threshold 98% -format '%@' info: | sed 's/[x+]/ /g')"

# largest square of the source that clears the dirty rim, kept even so the crop stays centred
CROP=$(( (AW < AH ? AW : AH) - 2 * SHAVE ))
CROP=$(( CROP - CROP % 2 ))
RADIUS=$(( CANVAS * 2237 / 10000 ))   # Apple corner radius: 22.37% of the finished tile

# the shaved body is smaller than the canvas, so it is scaled up to fill it. That is not a loss:
# macOS was already scaling the inset up to tile size at render time, and doing it here with
# Lanczos rather than at draw time is the same picture or better.
magick "$SRC" -crop "${CROP}x${CROP}+$(( AX + (AW - CROP) / 2 ))+$(( AY + (AH - CROP) / 2 ))" \
  +repage -filter Lanczos -resize "${CANVAS}x${CANVAS}!" -depth 8 "PNG32:$TMP/art.png"

# squircle mask at the full canvas — the artwork's own corners land on it, since the source tile
# already uses the same 22.37% radius
magick -size "$(( CANVAS * SUPER ))x$(( CANVAS * SUPER ))" xc:black -fill white \
  -draw "roundrectangle 0,0,$(( CANVAS * SUPER - 1 )),$(( CANVAS * SUPER - 1 )),$(( RADIUS * SUPER )),$(( RADIUS * SUPER ))" \
  -resize "${CANVAS}x${CANVAS}" -depth 8 "PNG:$TMP/mask.png"

magick "PNG32:$TMP/art.png" "PNG:$TMP/mask.png" -alpha off -compose copyopacity -composite \
  -strip -depth 8 "PNG32:$OUT"

# verify the things that silently break macOS
depth=$(magick identify -format '%[depth]' "$OUT")
ctype=$(magick identify -verbose "$OUT" | sed -n 's/.*png:IHDR.color_type: *//p' | head -1)
opaque=$(magick "$OUT" -alpha extract -threshold 25% -format '%@' info:)
want="${CANVAS}x${CANVAS}+0+0"
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

# a one-slice icns gets plated no matter how good the geometry is, so build the full size family
if command -v iconutil >/dev/null; then
  SET="$TMP/icon.iconset"; mkdir -p "$SET"

  # the Dock draws the tile 1:1, so the Mac assets take Apple's inset - icon.png stays full bleed
  magick "$OUT" -filter Lanczos -resize "${MAC_BODY}x${MAC_BODY}" -background none -gravity center \
    -extent "${CANVAS}x${CANVAS}" -strip -depth 8 "PNG32:$TMP/mac.png"

  for spec in "16 icon_16x16" "32 icon_16x16@2x" "32 icon_32x32" "64 icon_32x32@2x" \
              "128 icon_128x128" "256 icon_128x128@2x" "256 icon_256x256" "512 icon_256x256@2x" \
              "512 icon_512x512" "1024 icon_512x512@2x"; do
    set -- $spec
    magick "PNG32:$TMP/mac.png" -resize "$1x$1" -strip -depth 8 "PNG32:$SET/$2.png"
  done
  cp "$SET/icon_512x512@2x.png" src/main/resources/images/app_icon.png
  echo "wrote src/main/resources/images/app_icon.png (dock icon set at runtime by AppController, inset ${MAC_BODY}/${CANVAS})"
  iconutil -c icns "$SET" -o icon.icns
  echo "wrote icon.icns ($(python3 -c "
import struct
d=open('icon.icns','rb').read(); off=8; n=0
while off+8<=len(d):
    ln=struct.unpack('>I',d[off+4:off+8])[0]
    if ln<8: break
    n+=1; off+=ln
print(n)") slices)"
fi

echo "run scripts/reset-icon.sh to clear the macOS icon cache before checking the result"
