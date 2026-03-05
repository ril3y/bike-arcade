# BikeArcade -- NES/SNES Fitness Overlay Emulator

**Product Requirements Document (PRD)**

Version: 1.0\
Author: Riley\
Target Platform: Android 9 (Bowflex / Velocore Console)\
Display: 1920x1080 LCD\
Hardware: Rockchip SoC, 2GB RAM

------------------------------------------------------------------------

# 1. Product Overview

BikeArcade is an Android application that allows users to play classic
NES and SNES games while cycling on a smart exercise bike. The
application renders retro games in their native aspect ratio while
displaying real-time fitness metrics in a modern sidebar dashboard.

The goal is to transform stationary cycling into an engaging
entertainment experience by combining:

• Retro gaming\
• Real-time fitness telemetry\
• Music control\
• Workout analytics

The system is designed specifically for hacked Bowflex / Velocore
consoles running Android.

------------------------------------------------------------------------

# 2. Goals

Primary goals:

• Run NES and SNES games smoothly\
• Display real-time bike metrics without interrupting gameplay\
• Use unused screen space created by 4:3 game aspect ratio\
• Provide a visually pleasing fitness dashboard\
• Allow music control and ride analytics

Secondary goals:

• Modular architecture for future console support (PS1 etc) • Future
game mechanics integration (pedal affects gameplay)

------------------------------------------------------------------------

# 3. Target Hardware

Console Specifications:

CPU: Rockchip ARM SoC\
RAM: 2GB\
GPU: Mali series GPU\
OS: Android 9\
Resolution: 1920x1080 touchscreen

Connectivity:

• BLE FTMS support • Serial bridge via TCP port 9999 • WiFi networking •
ADB access

------------------------------------------------------------------------

# 4. System Architecture

System components:

1.  Emulator Engine
2.  Bike Metrics Service
3.  HUD Overlay Renderer
4.  Media Controller
5.  Game Launcher
6.  Settings Manager

Architecture diagram:

Bike Sensors ↓ BikeMetricsService ↓ OverlayHUD ↓ EmulatorCore

------------------------------------------------------------------------

# 5. Emulator Engine

Supported consoles:

NES\
SNES

Recommended cores:

NES: FCEUmm\
SNES: Snes9x

Possible platforms:

Option A: RetroArch core integration\
Option B: Standalone libretro runtime\
Option C: Embedded Snes9x / FCEU source

Recommendation:

Use **Libretro cores** with a lightweight custom frontend.

Advantages:

• clean modular design • easy to add new consoles later • high
performance

------------------------------------------------------------------------

# 6. Display Layout

Screen resolution: 1920x1080

Retro games use 4:3 aspect ratio.

Typical game resolution:

NES: 256x240\
SNES: 256x224

Scaled resolution options:

4x scale → 1024x960\
5x scale → 1280x1120 (cropped)

Recommended layout:

Game Area: 1280x960 HUD Sidebar: 640x1080

Layout:

| GAME \| SIDEBAR \|

------------------------------------------------------------------------

# 7. Sidebar HUD Components

The right sidebar provides cycling metrics and system controls.

Sections:

1.  Now Playing
2.  Live Metrics
3.  Graphs
4.  Ride Stats
5.  System Controls

Example layout:

  ---------------------------------
  Now Playing Track / Artist Play
  Pause Skip
  ---------------------------------
  Heart Rate: 142 BPM Cadence: 88
  RPM Power: 215 W Speed: 18 mph

  ---------------------------------

Power Graph Cadence Graph

  ---------------------------------
  Distance: 3.1 mi Time: 15:22
  Calories: 210
  ---------------------------------
  System Controls HUD Toggle
  Settings

  ---------------------------------

------------------------------------------------------------------------

# 8. Bike Metrics Service

Responsible for retrieving workout telemetry.

Sources:

Option 1: BLE FTMS protocol Option 2: Serial controller via TCP 9999

Metrics captured:

Heart rate\
Cadence\
Power\
Speed\
Distance\
Elapsed time

Update frequency:

5--10 Hz

Smoothing:

Exponential moving average to reduce flicker.

------------------------------------------------------------------------

# 9. Overlay Renderer

The overlay renders the sidebar and HUD elements.

Rendering approach:

Android Canvas or Jetpack Compose.

Recommended:

Jetpack Compose for rapid UI development.

UI layers:

FrameLayout EmulatorSurface SidebarOverlay

Performance target:

HUD refresh ≤ 10ms Game FPS stable at 60 FPS

------------------------------------------------------------------------

# 10. Audio & Music Integration

Music sources:

• Spotify • YouTube Music • Local files

Capabilities:

Play\
Pause\
Skip\
Volume

Implementation:

Android MediaSession API.

------------------------------------------------------------------------

# 11. Game Controls

Input methods:

• Bluetooth controller • USB controller • Touch controls (optional)

Possible bike integrations:

Future feature:

Pedaling speed affects gameplay.

------------------------------------------------------------------------

# 12. Game Library

ROM formats supported:

NES: .nes\
SNES: .smc .sfc

ROM storage location:

/storage/emulated/0/BikeArcade/roms/

Metadata:

Game name\
Artwork\
Last played

------------------------------------------------------------------------

# 13. Settings

User configurable options:

Game scale Sidebar width HUD opacity Metric units (mph/kph) Audio source

------------------------------------------------------------------------

# 14. Performance Targets

Game FPS: 60 fps

HUD update rate: 10 fps

Memory usage:

\< 600MB total

CPU load:

\< 40%

------------------------------------------------------------------------

# 15. Future Features

Planned expansions:

PS1 emulation Workout achievements Online leaderboards Strava sync
Multiplayer racing games AI training coach

------------------------------------------------------------------------

# 16. Security Considerations

Application runs on a jailbroken console.

Key considerations:

Avoid interfering with system launcher Avoid breaking bike firmware
communications Ensure safe BLE communication

------------------------------------------------------------------------

# 17. Development Roadmap

Phase 1 -- Prototype • Run SNES emulator • Display static overlay

Phase 2 -- Telemetry • Connect bike metrics • Render HUD values

Phase 3 -- UI • Graphs • Music controls • Settings menu

Phase 4 -- Optimization • GPU scaling • Smooth metrics

Phase 5 -- Release • Installable APK • Documentation

------------------------------------------------------------------------

# 18. Technology Stack

Android SDK\
Kotlin\
Libretro cores\
Jetpack Compose\
Bluetooth FTMS API

------------------------------------------------------------------------

# 19. Repository Structure

BikeArcade/

app/ emulator/ metrics/ overlay/ ui/ media/ settings/

------------------------------------------------------------------------

# 20. Conclusion

BikeArcade transforms stationary cycling into an interactive retro
gaming experience. By leveraging unused screen space and real-time bike
telemetry, the application enhances workout engagement while maintaining
smooth gameplay performance.
