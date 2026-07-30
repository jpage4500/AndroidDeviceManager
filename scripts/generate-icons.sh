#!/bin/bash
#
# Generates the app icon that jDeploy ships on every platform.
#
#   in:  resources/icon-source.png  — the bare artwork, no tile, no padding
#   out: icon.png                   — 1024x1024 squircle tile, 8-bit sRGB
#
# WHY THIS EXISTS
#
# jDeploy uses a single icon.png (next to package.json) for all six installers
# (mac/win/linux x arm64/x64). It does NOT generate a size family: it embeds this
# PNG verbatim as the one and only 'ic10' slice of Contents/Resources/icon.icns,
# and macOS downsamples it on the fly. So this one file has to be right.
#
# macOS 26 (Tahoe) masks app icons into a standard squircle tile, but only accepts
# artwork that already reads as a conforming tile. Anything else — a bare logo, a
# silhouette with transparent margins, or an inset square with SHARP corners — gets
# shrunk onto a default near-white plate (RGB 249,249,246) and ends up visibly
# smaller than its neighbours in the Dock and the Cmd-Tab switcher. Measured on
# 26.5.1 by rendering through NSWorkspace.icon(forFile:):
#
#   opaque region                              corners          result
#   973x787 folder silhouette, uneven margins  folder-shaped    PLATED (too small)
#   824x824 centred, even margin               sharp square     PLATED (too small)
#   824x824 centred, even margin               squircle         full-bleed, correct
#   1024x1024 no margin                        sharp square     full-bleed, correct
#
# So the squircle is the gate, not the margin — macOS is happy at any inset. The
# 824/1024 inset (Apple's "icon grid") is chosen for the OTHER platforms' sake:
# Windows and Linux never inset your artwork, so the margin has to be baked in, and
# pre-Tahoe macOS draws the image into the Dock slot without normalising it, where a
# full-bleed icon would read oversized next to system icons.
#
# TWO IMAGEMAGICK TRAPS, both of which silently break the output:
#   - 16-bit-per-channel PNG gets PLATED even with perfect geometry. IM's Q16 build
#     emits 16-bit after any composite, so every step forces -depth 8.
#   - a white/transparent tile is achromatic, so ImageMagick writes it as a GRAYSCALE
#     PNG, and compositing the artwork onto it then desaturates the artwork (the green
#     robot comes out grey). '-type TrueColorAlpha' does NOT prevent this. Only the
#     PNG32: output prefix does, so every write below uses it.

set -euo pipefail

cd "$(dirname "$0")/.."

command -v magick >/dev/null || { echo "ERROR: ImageMagick not found (brew install imagemagick)" >&2; exit 1; }

SRC=resources/icon-source.png
OUT=icon.png

CANVAS=1024                       # jDeploy fills the 1024 'ic10' slot
BODY=824                          # Apple icon grid: 824 of 1024
RADIUS=$(( BODY * 2237 / 10000 )) # Apple corner radius: 22.37% of the body
ART=$(( BODY * 88 / 100 ))        # artwork inset inside the tile — tune this knob
TILE_COLOR=white

[[ -f "$SRC" ]] || { echo "ERROR: source artwork not found: $SRC" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 1. the squircle tile
magick -size "${BODY}x${BODY}" xc:none \
  -fill "$TILE_COLOR" -draw "roundrectangle 0,0,$((BODY-1)),$((BODY-1)),$RADIUS,$RADIUS" \
  -depth 8 "PNG32:$TMP/tile.png"

# 2. artwork scaled to fit, centred on the tile
magick "PNG32:$TMP/tile.png" \
  \( "$SRC" -resize "${ART}x${ART}" -background none -gravity center -extent "${BODY}x${BODY}" \) \
  -compose over -composite \
  -depth 8 "PNG32:$TMP/body.png"

# 3. tile centred on the transparent 1024 canvas
magick -size "${CANVAS}x${CANVAS}" xc:none \
  "PNG32:$TMP/body.png" -gravity center -composite \
  -depth 8 "PNG32:$OUT"

# verify the three things that silently break macOS
depth=$(magick identify -format '%[depth]' "$OUT")
bbox=$(magick "$OUT" -alpha extract -format '%@' info:)
ctype=$(magick identify -verbose "$OUT" | sed -n 's/.*png:IHDR.color_type: *//p' | head -1)
[[ "$depth" == 8 ]] || { echo "ERROR: $OUT is ${depth}-bit; macOS will plate it" >&2; exit 1; }
[[ "$ctype" == 6* ]] || { echo "ERROR: $OUT is not RGBA (color_type=$ctype); artwork will be desaturated" >&2; exit 1; }
[[ "$bbox" == "${BODY}x${BODY}+$(( (CANVAS-BODY)/2 ))+$(( (CANVAS-BODY)/2 ))" ]] \
  || { echo "ERROR: unexpected opaque bbox $bbox" >&2; exit 1; }

echo "wrote $OUT  ($(magick identify -format '%wx%h' "$OUT"), ${depth}-bit, color_type=$ctype, body=$bbox)"
