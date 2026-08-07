#!/bin/bash
#
# Generates the app icon that jDeploy ships on every platform.
#
#   in:  resources/icon-source.png  — the finished tile artwork, any size
#   out: icon.png                   — 1024x1024, 824 squircle, 8-bit sRGB
#
# jDeploy embeds icon.png verbatim as the single 'ic10' slice of icon.icns, so this one file
# has to be right. macOS 26 (Tahoe) checks the icon's opaque region against its standard tile
# and, when it doesn't conform, draws the artwork shrunk onto a grey fallback plate — the icon
# then looks ~30% too small in the Dock and Cmd-Tab switcher. Measured on 26.5.1 through
# NSWorkspace.icon(forFile:), rendered into a 256px box where a native icon is 206x206:
#
#   opaque region                            result
#   828x796 squircle, off-centre by 2px      PLATED, artwork 140x140
#   824x824 squircle, centred                accepted, 206x206
#   1000x1000 squircle, centred              accepted, 206x206
#   1024x1024 full-bleed, sharp corners      accepted, 206x206
#
# So the gate is a CENTRED SQUARE opaque region; macOS scales any conforming inset up to fill
# the tile. Non-square is what breaks it — a stray faint-alpha halo does not, and neither does
# 16-bit depth. The 824/1024 inset (Apple's icon grid) is kept for the other platforms: Windows
# and Linux never inset artwork, so the margin has to be baked in.
#
# Two ImageMagick traps, both silent: any composite on a Q16 build emits 16-bit PNG, and an
# achromatic tile colour makes it write greyscale (which desaturates the artwork). Every step
# below forces -depth 8 and the PNG32: prefix.

set -euo pipefail

cd "$(dirname "$0")/.."

command -v magick >/dev/null || { echo "ERROR: ImageMagick not found (brew install imagemagick)" >&2; exit 1; }

SRC=resources/icon-source.png
OUT=icon.png

CANVAS=1024                       # jDeploy fills the 1024 'ic10' slot
BODY=824                          # Apple icon grid: 824 of 1024
RADIUS=$(( BODY * 2237 / 10000 )) # Apple corner radius: 22.37% of the body
OFFSET=$(( (CANVAS - BODY) / 2 ))

[[ -f "$SRC" ]] || { echo "ERROR: source artwork not found: $SRC" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# artwork cropped to its real bounds, ignoring any faint alpha halo the exporter left
bbox=$(magick "$SRC" -channel A -threshold 25% +channel -alpha extract -threshold 25% -format '%@' info:)
magick "$SRC" -channel A -threshold 25% +channel \
  -crop "$bbox" +repage -resize "${BODY}x${BODY}!" \
  -depth 8 "PNG32:$TMP/art.png"

# squircle mask at the exact grid size — this is what macOS checks
magick -size "${BODY}x${BODY}" xc:none \
  -fill white -draw "roundrectangle 0,0,$((BODY-1)),$((BODY-1)),$RADIUS,$RADIUS" \
  -depth 8 "PNG32:$TMP/mask.png"

magick "PNG32:$TMP/art.png" "PNG32:$TMP/mask.png" -alpha off -compose copyopacity -composite \
  -depth 8 "PNG32:$TMP/body.png"

# centred on the transparent 1024 canvas
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

echo "wrote $OUT ($(magick identify -format '%wx%h' "$OUT"), ${depth}-bit, color_type=$ctype, opaque=$opaque)"
echo "run scripts/reset-icon.sh to clear the macOS icon cache before checking the result"
