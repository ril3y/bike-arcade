#!/bin/bash
# Push test ROMs to the Bowflex device
# Usage: ./scripts/push_roms.sh

ADB="adb -s 192.168.1.156:5555"
REPO_DIR="$(dirname "$0")/.."

adb connect 192.168.1.156:5555 2>/dev/null || true
sleep 1

# Create ROM directory
$ADB shell "mkdir -p /storage/emulated/0/BikeArcade/roms"

echo "Pushing test ROMs..."

# Push NES ROM
if [ -f "$REPO_DIR/1943_ The Battle of Midway.zip" ]; then
    echo "  -> 1943: The Battle of Midway (NES)"
    $ADB push "$REPO_DIR/1943_ The Battle of Midway.zip" "/storage/emulated/0/BikeArcade/roms/"
fi

# Push SNES ROM
if [ -f "$REPO_DIR/Legend of Zelda, The_ A Link to the Past.zip" ]; then
    echo "  -> Legend of Zelda: A Link to the Past (SNES)"
    $ADB push "$REPO_DIR/Legend of Zelda, The_ A Link to the Past.zip" "/storage/emulated/0/BikeArcade/roms/"
fi

echo ""
echo "ROMs on device:"
$ADB shell "ls -la /storage/emulated/0/BikeArcade/roms/"
echo ""
echo "Done! ROMs are ready for BikeArcade."
