#!/bin/bash
# BikeArcade deploy script
# Usage: ./scripts/deploy.sh

set -e

ADB="adb -s 192.168.1.156:5555"

echo "=== BikeArcade Deploy ==="

# Ensure device is connected
adb connect 192.168.1.156:5555 2>/dev/null || true
sleep 1

# Build debug APK
echo "Building debug APK..."
cd "$(dirname "$0")/.."
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi

echo "APK size: $(du -h "$APK_PATH" | cut -f1)"

# Install on device
echo "Installing on device..."
$ADB install -r "$APK_PATH"

# Launch the app
echo "Launching BikeArcade..."
$ADB shell am start -n com.bikearcade.app/.MainActivity

echo ""
echo "=== Deploy Complete ==="
echo "BikeArcade should now be running on the Bowflex console."
