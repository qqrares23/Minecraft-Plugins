#!/bin/sh
# Generates the icons and zips the resource pack inside Docker -> build/pack.zip (+ build/preview.png).
set -e
cd "$(dirname "$0")"
docker build -q -t packgen . >/dev/null
docker run --rm -v "$PWD/..:/src" -w /src/resource-pack packgen \
  sh -c 'python3 make_pack.py; s=$?; chown -R '"$(id -u):$(id -g)"' build 2>/dev/null; exit $s'
ls -l build/pack.zip
