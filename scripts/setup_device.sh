#!/bin/bash
# BikeArcade device setup script
# Usage: ./scripts/setup_device.sh

ADB="adb -s 192.168.1.156:5555"

echo "=== BikeArcade Device Setup ==="

# Connect to device
adb connect 192.168.1.156:5555
sleep 1

# Create required directories
echo "Creating directories..."
$ADB shell "mkdir -p /storage/emulated/0/BikeArcade/roms"
$ADB shell "mkdir -p /storage/emulated/0/BikeArcade/cores"
$ADB shell "mkdir -p /storage/emulated/0/BikeArcade/system"
$ADB shell "mkdir -p /storage/emulated/0/BikeArcade/saves"

echo "Directories created:"
$ADB shell "ls -la /storage/emulated/0/BikeArcade/"

echo ""
echo "=== Next Steps ==="
echo "1. Download libretro cores for ARM:"
echo "   - FCEUmm (NES): https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/fceumm_libretro_android.so.zip"
echo "   - Snes9x (SNES): https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/snes9x_libretro_android.so.zip"
echo ""
echo "2. Push cores to device:"
echo "   $ADB push fceumm_libretro_android.so /storage/emulated/0/BikeArcade/cores/"
echo "   $ADB push snes9x_libretro_android.so /storage/emulated/0/BikeArcade/cores/"
echo ""
echo "3. Push ROM files:"
echo "   $ADB push your_game.nes /storage/emulated/0/BikeArcade/roms/"
echo "   $ADB push your_game.smc /storage/emulated/0/BikeArcade/roms/"
echo ""
echo "Setup complete!"
