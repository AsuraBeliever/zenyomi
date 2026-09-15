#!/usr/bin/env bash
#
# Runs Gradle, retrying only when the failure was a dependency that could not be fetched.
#
# Eleven of this project's dependencies come from JitPack — the mpv library and ffmpeg
# among them — and JitPack builds artifacts on demand. It intermittently answers "not
# found" for something it served a minute earlier, which cost four manual relaunches on
# the v0.13.0 release alone. Relaunching by hand is the only reason those releases were
# slow, so the relaunch happens here instead.
#
# The retry is gated on the resolution error itself, deliberately: a compile error has to
# fail on the first attempt. Retrying a real failure three times would turn a two-minute
# red build into a ten-minute one and teach everyone to ignore it.
set -uo pipefail

ATTEMPTS=${ATTEMPTS:-3}
DELAY=${DELAY:-60}
GRADLE=${GRADLE:-./gradlew}

log=$(mktemp)
trap 'rm -f "$log"' EXIT

for attempt in $(seq 1 "$ATTEMPTS"); do
    if "$GRADLE" "$@" 2>&1 | tee "$log"; then
        exit 0
    fi

    if ! grep -qE 'Could not (find|resolve|GET|HEAD)|Failed to transform' "$log"; then
        echo "::error::Build failed for something other than dependency resolution. Not retrying."
        exit 1
    fi

    if [ "$attempt" -eq "$ATTEMPTS" ]; then
        echo "::error::A dependency still could not be resolved after $ATTEMPTS attempts."
        echo "::error::Check https://www.jitpack.io before looking at the code."
        exit 1
    fi

    echo "::warning::Attempt $attempt could not resolve a dependency. Retrying in ${DELAY}s."
    sleep "$DELAY"
done
