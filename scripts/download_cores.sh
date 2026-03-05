#!/bin/bash
# Download libretro cores and push to device
# Usage: ./scripts/download_cores.sh

set -e

ADB="adb -s 192.168.1.156:5555"
CORES_DIR="$(dirname "$0")/../cores"
mkdir -p "$CORES_DIR"

adb connect 192.168.1.156:5555 2>/dev/null || true

echo "=== Downloading Libretro Cores ==="

# Detect device architecture
ARCH=$($ADB shell getprop ro.product.cpu.abi | tr -d '\r')
echo "Device architecture: $ARCH"

# Use armeabi-v7a as fallback if arm64 cores aren't available
CORE_ARCH="armeabi-v7a"
if [ "$ARCH" = "arm64-v8a" ]; then
    CORE_ARCH="arm64-v8a"
fi

BASE_URL="https://buildbot.libretro.com/nightly/android/latest/$CORE_ARCH"

# Download FCEUmm (NES)
echo "Downloading FCEUmm (NES core)..."
curl -L -o "$CORES_DIR/fceumm_libretro_android.so.zip" "$BASE_URL/fceumm_libretro_android.so.zip"
cd "$CORES_DIR" && unzip -o fceumm_libretro_android.so.zip && cd -

# Download Snes9x (SNES)
echo "Downloading Snes9x (SNES core)..."
curl -L -o "$CORES_DIR/snes9x_libretro_android.so.zip" "$BASE_URL/snes9x_libretro_android.so.zip"
cd "$CORES_DIR" && unzip -o snes9x_libretro_android.so.zip && cd -

# Push to device
echo "Pushing cores to device..."
$ADB shell "mkdir -p /storage/emulated/0/BikeArcade/cores"
$ADB push "$CORES_DIR/fceumm_libretro_android.so" "/storage/emulated/0/BikeArcade/cores/"
$ADB push "$CORES_DIR/snes9x_libretro_android.so" "/storage/emulated/0/BikeArcade/cores/"

echo ""
echo "Cores on device:"
$ADB shell "ls -la /storage/emulated/0/BikeArcade/cores/"
echo ""
echo "Done! Cores are ready for BikeArcade."
