# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**BikeArcade** is an Android application that combines retro game emulation (NES/SNES) with real-time fitness telemetry on a hacked Bowflex/Velocore exercise bike console.

- **Target**: Android 9, Rockchip SoC, 2GB RAM, 1920x1080 LCD
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Emulation**: Libretro cores (FCEUmm for NES, Snes9x for SNES)
- **Bike connectivity**: BLE FTMS protocol (primary) or serial bridge via TCP port 9999 (requires JRNY running or root)

## Bike Connectivity Notes

- **BLE FTMS** — bike advertises FTMS (UUID 0x1826) over BLE, any app with BT permissions can connect
- **TCP 9999 serial bridge** — CONFIRMED WORKING:
  - `/dev/ttyS4` is owned by `root:system` (crw-rw----) — opened by `nautiluslauncher` (system UID 1000)
  - The `nautiluslauncher` runs the `SerialPortServer` which bridges `/dev/ttyS4` to TCP 9999
  - **Critical**: If BikeArcade replaces nautiluslauncher as HOME app, Android sets nautiluslauncher to `stopped=true` which kills ALL its components. Must launch nautiluslauncher once to un-stop it and bring up the serial bridge.
  - Once un-stopped, the bridge comes up automatically on boot

## Architecture

Six core components:
1. **Emulator Engine** — Libretro core integration with custom lightweight frontend
2. **Bike Metrics Service** — Telemetry from BLE FTMS or TCP serial (5-10 Hz, EMA smoothed)
3. **HUD Overlay Renderer** — Sidebar dashboard rendered via Jetpack Compose over emulator surface
4. **Media Controller** — Android MediaSession API for Spotify/YouTube Music/local files
5. **Game Launcher** — ROM browser for .nes/.smc/.sfc files from `/storage/emulated/0/BikeArcade/roms/`
6. **Settings Manager** — Game scale, sidebar width, HUD opacity, units, audio source

## Display Layout

Games render at 4:3 in a 1280x960 area; the remaining 640x1080 right sidebar shows fitness metrics (HR, cadence, power, speed, distance, time), power/cadence graphs, music controls, and system controls.

## Test ROM

- `1943_ The Battle of Midway.zip` — NES ROM for testing
- `Legend of Zelda, The_ A Link to the Past.zip` — SNES ROM for testing

## ADB Connection

```
adb -s 192.168.1.156:5555 shell
```

## Performance Constraints

- Game FPS: 60fps, HUD refresh: ≤10ms
- Memory: <600MB total, CPU: <40%
- Must not interfere with system launcher or bike firmware communications

## Planned Repository Structure

```
BikeArcade/
  app/
  emulator/
  metrics/
  overlay/
  ui/
  media/
  settings/
```
