#!/bin/sh
# Copies the shared classes in common/src into every plugin as <plugin package>.common
# (each jar gets its own copy, so every plugin still works alone). Run after editing common/src.
set -e
cd "$(dirname "$0")/.."
for pair in weapon-skills:weaponskills paths:paths professions:professions magic-enchants:magicenchants \
            mighty-mobs:mightymobs hud:hud item-cleaner:itemcleaner; do
  dir=${pair%%:*}; pkg=${pair##*:}
  out="$dir/src/main/java/com/raresb/$pkg/common"
  mkdir -p "$out"
  for f in common/src/*.java; do
    sed "s/^package com\.raresb\.common;/package com.raresb.$pkg.common;/" "$f" > "$out/$(basename "$f")"
  done
done
echo "Synced $(ls common/src/*.java | wc -l) shared classes into 7 plugins."
