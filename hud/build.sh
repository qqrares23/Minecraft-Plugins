#!/bin/sh
# Builds the plugin jar inside Docker (no local JDK needed).
set -e
cd "$(dirname "$0")"
docker run --rm -v "$PWD:/w" -w /w -v hud-gradle:/home/gradle/.gradle \
  gradle:9.8.0-jdk25-noble sh -c 'gradle --no-daemon -q build; s=$?; chown -R '"$(id -u):$(id -g)"' /w/build /w/.gradle 2>/dev/null; exit $s'
ls -l build/libs/*.jar
