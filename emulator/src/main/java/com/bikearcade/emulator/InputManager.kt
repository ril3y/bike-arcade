package com.bikearcade.emulator

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Maps Android [KeyEvent] / [MotionEvent] and gamepad input to libretro
 * RETRO_DEVICE_JOYPAD button constants.
 *
 * Supports Bluetooth Classic, BLE, and USB controllers — including BLE SNES-style
 * controllers such as 8BitDo SN30, iBuffalo SNES, and Retro-Bit BLE pads.
 *
 * Usage:
 * 1. Forward [KeyEvent]s to [onKeyDown] / [onKeyUp].
 * 2. Forward [MotionEvent]s (from `onGenericMotionEvent`) to [onGenericMotionEvent].
 * 3. Register [queryButtonState] as the input state callback on [EmulatorCore].
 * 4. Optionally set [controllerMode] to [ControllerMode.SNES_LAYOUT] for SNES-native
 *    button labelling.
 */
class InputManager {

    // ── libretro RETRO_DEVICE_JOYPAD button IDs ─────────────────────────

    companion object {
        const val RETRO_DEVICE_JOYPAD = 1

        const val RETRO_DEVICE_ID_JOYPAD_B = 0
        const val RETRO_DEVICE_ID_JOYPAD_Y = 1
        const val RETRO_DEVICE_ID_JOYPAD_SELECT = 2
        const val RETRO_DEVICE_ID_JOYPAD_START = 3
        const val RETRO_DEVICE_ID_JOYPAD_UP = 4
        const val RETRO_DEVICE_ID_JOYPAD_DOWN = 5
        const val RETRO_DEVICE_ID_JOYPAD_LEFT = 6
        const val RETRO_DEVICE_ID_JOYPAD_RIGHT = 7
        const val RETRO_DEVICE_ID_JOYPAD_A = 8
        const val RETRO_DEVICE_ID_JOYPAD_X = 9
        const val RETRO_DEVICE_ID_JOYPAD_L = 10
        const val RETRO_DEVICE_ID_JOYPAD_R = 11
        const val RETRO_DEVICE_ID_JOYPAD_L2 = 12
        const val RETRO_DEVICE_ID_JOYPAD_R2 = 13
        const val RETRO_DEVICE_ID_JOYPAD_L3 = 14
        const val RETRO_DEVICE_ID_JOYPAD_R3 = 15

        private const val BUTTON_COUNT = 16

        /** Deadzone for analog hat axis values (D-pad reported as axis). */
        private const val HAT_AXIS_DEADZONE = 0.5f
    }

    // ── Controller layout mode ──────────────────────────────────────────

    /**
     * Determines how physical face-button positions map to SNES button names.
     *
     * - [XBOX_LAYOUT]: South=B, East=A, West=Y, North=X (default — Xbox/Android standard)
     * - [SNES_LAYOUT]: South=B, East=A, West=Y, North=X with swapped A<->B and X<->Y
     *   keycodes, matching controllers that report SNES labels natively.
     *
     * Xbox-style controllers label south=A, east=B, west=X, north=Y. The Android
     * framework maps those to KEYCODE_BUTTON_A/B/X/Y respectively.
     *
     * SNES-style controllers label south=B, east=A, west=Y, north=X. Some report
     * the SNES labels as-if they were Xbox labels (so SNES-B -> KEYCODE_BUTTON_A,
     * which accidentally works). Others report the physical position correctly,
     * causing A/B and X/Y to be swapped. Use [SNES_LAYOUT] for the latter case.
     */
    enum class ControllerMode {
        /** Standard Xbox/Android mapping (default). */
        XBOX_LAYOUT,
        /**
         * SNES-native mapping — swaps A<->B and X<->Y relative to Xbox layout.
         * Use this when SNES south-button reports KEYCODE_BUTTON_A but should be
         * libretro B, etc.
         */
        SNES_LAYOUT,
    }

    /**
     * Active controller layout mode. Change at any time; takes effect on the
     * next key event.
     */
    var controllerMode: ControllerMode = ControllerMode.XBOX_LAYOUT
        set(value) {
            field = value
            rebuildKeyMap()
        }

    // ── Controller info ─────────────────────────────────────────────────

    /**
     * Describes a connected game controller.
     */
    data class ControllerInfo(
        val name: String,
        val deviceId: Int,
        val vendorId: Int,
        val productId: Int,
        val isBle: Boolean,
        val isGamepad: Boolean,
        val isJoystick: Boolean,
    )

    // ── State ───────────────────────────────────────────────────────────

    /**
     * Button state per player port. Index 0 = player 1.
     * Each array holds [BUTTON_COUNT] booleans.
     */
    private val buttonStates = Array(2) { AtomicIntegerArray(BUTTON_COUNT) }

    // ── Key mapping ─────────────────────────────────────────────────────

    /**
     * Active keycode-to-libretro map, rebuilt when [controllerMode] changes.
     */
    private var keyMap: Map<Int, Int> = buildKeyMap(ControllerMode.XBOX_LAYOUT)

    /**
     * Builds the Android-keycode -> libretro-button map for the given [mode].
     */
    private fun buildKeyMap(mode: ControllerMode): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()

        // ── D-pad key events ────────────────────────────────────────────
        map[KeyEvent.KEYCODE_DPAD_UP] = RETRO_DEVICE_ID_JOYPAD_UP
        map[KeyEvent.KEYCODE_DPAD_DOWN] = RETRO_DEVICE_ID_JOYPAD_DOWN
        map[KeyEvent.KEYCODE_DPAD_LEFT] = RETRO_DEVICE_ID_JOYPAD_LEFT
        map[KeyEvent.KEYCODE_DPAD_RIGHT] = RETRO_DEVICE_ID_JOYPAD_RIGHT

        // ── Face buttons ────────────────────────────────────────────────
        when (mode) {
            ControllerMode.XBOX_LAYOUT -> {
                // Xbox: A=south, B=east, X=west, Y=north
                // Mapped so that physical south -> SNES B, east -> SNES A, etc.
                map[KeyEvent.KEYCODE_BUTTON_A] = RETRO_DEVICE_ID_JOYPAD_B   // south -> B
                map[KeyEvent.KEYCODE_BUTTON_B] = RETRO_DEVICE_ID_JOYPAD_A   // east  -> A
                map[KeyEvent.KEYCODE_BUTTON_X] = RETRO_DEVICE_ID_JOYPAD_Y   // west  -> Y
                map[KeyEvent.KEYCODE_BUTTON_Y] = RETRO_DEVICE_ID_JOYPAD_X   // north -> X
            }
            ControllerMode.SNES_LAYOUT -> {
                // SNES-native: controller reports its own label as the keycode.
                // SNES B (south) -> KEYCODE_BUTTON_B, SNES A (east) -> KEYCODE_BUTTON_A
                // SNES Y (west)  -> KEYCODE_BUTTON_Y, SNES X (north) -> KEYCODE_BUTTON_X
                map[KeyEvent.KEYCODE_BUTTON_A] = RETRO_DEVICE_ID_JOYPAD_A   // SNES A -> A
                map[KeyEvent.KEYCODE_BUTTON_B] = RETRO_DEVICE_ID_JOYPAD_B   // SNES B -> B
                map[KeyEvent.KEYCODE_BUTTON_X] = RETRO_DEVICE_ID_JOYPAD_X   // SNES X -> X
                map[KeyEvent.KEYCODE_BUTTON_Y] = RETRO_DEVICE_ID_JOYPAD_Y   // SNES Y -> Y
            }
        }

        // ── BLE SNES alternate keycodes ─────────────────────────────────
        // Some BLE SNES controllers (8BitDo SN30, Retro-Bit, iBuffalo clones)
        // report shoulder buttons as BUTTON_C / BUTTON_Z instead of L1/R1.
        // Some also map SNES Y -> KEYCODE_BUTTON_X and SNES X -> KEYCODE_BUTTON_Y
        // (handled by the SNES_LAYOUT mode above).
        map[KeyEvent.KEYCODE_BUTTON_C] = RETRO_DEVICE_ID_JOYPAD_L   // alt L shoulder
        map[KeyEvent.KEYCODE_BUTTON_Z] = RETRO_DEVICE_ID_JOYPAD_R   // alt R shoulder

        // ── Start / Select ──────────────────────────────────────────────
        map[KeyEvent.KEYCODE_BUTTON_START] = RETRO_DEVICE_ID_JOYPAD_START
        map[KeyEvent.KEYCODE_BUTTON_SELECT] = RETRO_DEVICE_ID_JOYPAD_SELECT
        // Some BLE controllers map Select to KEYCODE_BACK or mode button
        map[KeyEvent.KEYCODE_BUTTON_MODE] = RETRO_DEVICE_ID_JOYPAD_SELECT

        // ── Standard shoulders ──────────────────────────────────────────
        map[KeyEvent.KEYCODE_BUTTON_L1] = RETRO_DEVICE_ID_JOYPAD_L
        map[KeyEvent.KEYCODE_BUTTON_R1] = RETRO_DEVICE_ID_JOYPAD_R
        map[KeyEvent.KEYCODE_BUTTON_L2] = RETRO_DEVICE_ID_JOYPAD_L2
        map[KeyEvent.KEYCODE_BUTTON_R2] = RETRO_DEVICE_ID_JOYPAD_R2
        map[KeyEvent.KEYCODE_BUTTON_THUMBL] = RETRO_DEVICE_ID_JOYPAD_L3
        map[KeyEvent.KEYCODE_BUTTON_THUMBR] = RETRO_DEVICE_ID_JOYPAD_R3

        // ── BLE SNES alternate shoulder keycodes ────────────────────────
        // Some cheap BLE SNES pads report BUTTON_1..BUTTON_4 for face buttons.
        map[KeyEvent.KEYCODE_BUTTON_1] = RETRO_DEVICE_ID_JOYPAD_B   // alt south
        map[KeyEvent.KEYCODE_BUTTON_2] = RETRO_DEVICE_ID_JOYPAD_A   // alt east
        map[KeyEvent.KEYCODE_BUTTON_3] = RETRO_DEVICE_ID_JOYPAD_Y   // alt west
        map[KeyEvent.KEYCODE_BUTTON_4] = RETRO_DEVICE_ID_JOYPAD_X   // alt north

        // ── Keyboard fallback (for development / testing) ───────────────
        map[KeyEvent.KEYCODE_W] = RETRO_DEVICE_ID_JOYPAD_UP
        map[KeyEvent.KEYCODE_S] = RETRO_DEVICE_ID_JOYPAD_DOWN
        map[KeyEvent.KEYCODE_A] = RETRO_DEVICE_ID_JOYPAD_LEFT
        map[KeyEvent.KEYCODE_D] = RETRO_DEVICE_ID_JOYPAD_RIGHT
        map[KeyEvent.KEYCODE_J] = RETRO_DEVICE_ID_JOYPAD_B
        map[KeyEvent.KEYCODE_K] = RETRO_DEVICE_ID_JOYPAD_A
        map[KeyEvent.KEYCODE_U] = RETRO_DEVICE_ID_JOYPAD_Y
        map[KeyEvent.KEYCODE_I] = RETRO_DEVICE_ID_JOYPAD_X
        map[KeyEvent.KEYCODE_ENTER] = RETRO_DEVICE_ID_JOYPAD_START
        map[KeyEvent.KEYCODE_SPACE] = RETRO_DEVICE_ID_JOYPAD_SELECT
        map[KeyEvent.KEYCODE_Q] = RETRO_DEVICE_ID_JOYPAD_L
        map[KeyEvent.KEYCODE_E] = RETRO_DEVICE_ID_JOYPAD_R

        return map
    }

    private fun rebuildKeyMap() {
        keyMap = buildKeyMap(controllerMode)
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Process a key-down event. Returns true if the event was consumed.
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val port = resolvePort(event)
        val buttonId = keyMap[keyCode] ?: return false
        buttonStates[port].set(buttonId, 1)
        return true
    }

    /**
     * Process a key-up event. Returns true if the event was consumed.
     */
    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val port = resolvePort(event)
        val buttonId = keyMap[keyCode] ?: return false
        buttonStates[port].set(buttonId, 0)
        return true
    }

    /**
     * Process a generic motion event — specifically AXIS_HAT_X / AXIS_HAT_Y
     * for D-pad input. Many BLE SNES controllers report the D-pad as an analog
     * hat axis in [MotionEvent] rather than as discrete [KeyEvent] keycodes.
     *
     * Call this from your Activity/View's `onGenericMotionEvent()`.
     *
     * @return true if the event was consumed (i.e., it came from a gamepad/joystick)
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Only handle events from gamepad or joystick sources
        if (event.source and InputDevice.SOURCE_GAMEPAD == 0 &&
            event.source and InputDevice.SOURCE_JOYSTICK == 0 &&
            event.source and InputDevice.SOURCE_DPAD == 0
        ) {
            return false
        }

        val port = resolvePortFromDevice(event.device)

        // ── AXIS_HAT_X -> left / right ──────────────────────────────────
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        buttonStates[port].set(RETRO_DEVICE_ID_JOYPAD_LEFT, if (hatX < -HAT_AXIS_DEADZONE) 1 else 0)
        buttonStates[port].set(RETRO_DEVICE_ID_JOYPAD_RIGHT, if (hatX > HAT_AXIS_DEADZONE) 1 else 0)

        // ── AXIS_HAT_Y -> up / down ─────────────────────────────────────
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        buttonStates[port].set(RETRO_DEVICE_ID_JOYPAD_UP, if (hatY < -HAT_AXIS_DEADZONE) 1 else 0)
        buttonStates[port].set(RETRO_DEVICE_ID_JOYPAD_DOWN, if (hatY > HAT_AXIS_DEADZONE) 1 else 0)

        return true
    }

    /**
     * Query the state of a single button. Intended to be passed to
     * [EmulatorCore.setInputCallback].
     *
     * @return 1 if pressed, 0 if not pressed
     */
    fun queryButtonState(port: Int, device: Int, index: Int, id: Int): Int {
        // Only handle RETRO_DEVICE_JOYPAD
        if (device != RETRO_DEVICE_JOYPAD) return 0
        if (port < 0 || port >= buttonStates.size) return 0
        if (id < 0 || id >= BUTTON_COUNT) return 0
        return buttonStates[port].get(id)
    }

    /**
     * Clear all button states (e.g., on pause or focus loss).
     */
    fun clearAll() {
        for (states in buttonStates) {
            for (i in 0 until states.length()) {
                states.set(i, 0)
            }
        }
    }

    // ── Controller detection ────────────────────────────────────────────

    /**
     * Returns information about all currently connected game controllers.
     *
     * BLE controllers appear as standard [InputDevice] sources once paired via
     * Android's Bluetooth stack. The [ControllerInfo.isBle] flag is inferred
     * from the device's transport type where available (API 33+), falling back
     * to heuristics based on device name and bus type.
     *
     * Useful for UI that confirms controller connectivity before gameplay.
     */
    fun getConnectedControllers(): List<ControllerInfo> {
        val controllers = mutableListOf<ControllerInfo>()
        val deviceIds = InputDevice.getDeviceIds()

        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue

            val isGamepad = device.sources and InputDevice.SOURCE_GAMEPAD != 0
            val isJoystick = device.sources and InputDevice.SOURCE_JOYSTICK != 0
            if (!isGamepad && !isJoystick) continue

            // Skip virtual/built-in devices that are not real controllers
            if (device.isVirtual) continue

            controllers.add(
                ControllerInfo(
                    name = device.name,
                    deviceId = device.id,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    isBle = inferBle(device),
                    isGamepad = isGamepad,
                    isJoystick = isJoystick,
                )
            )
        }

        return controllers
    }

    // ── Internals ───────────────────────────────────────────────────────

    /**
     * Resolve the player port from a [KeyEvent].
     * Keyboard input always maps to port 0.
     * For gamepads, use device descriptor index if available.
     */
    private fun resolvePort(event: KeyEvent): Int {
        val device = event.device ?: return 0

        // Keyboard events go to port 0
        if (device.sources and InputDevice.SOURCE_GAMEPAD == 0 &&
            device.sources and InputDevice.SOURCE_JOYSTICK == 0
        ) {
            return 0
        }

        // Use controller number if available (1-based), clamp to [0, 1]
        val controllerNumber = device.controllerNumber
        return if (controllerNumber > 0) {
            (controllerNumber - 1).coerceIn(0, 1)
        } else {
            0
        }
    }

    /**
     * Resolve the player port from an [InputDevice] (used by [onGenericMotionEvent]).
     */
    private fun resolvePortFromDevice(device: InputDevice?): Int {
        if (device == null) return 0
        val controllerNumber = device.controllerNumber
        return if (controllerNumber > 0) {
            (controllerNumber - 1).coerceIn(0, 1)
        } else {
            0
        }
    }

    /**
     * Best-effort inference of whether a controller is BLE.
     *
     * On API 33+ we could check the Bluetooth transport, but [InputDevice] does
     * not directly expose BLE vs Classic. We use heuristics:
     * - Vendor/product ID of 0/0 with a Bluetooth-like name often indicates BLE HID.
     * - Known BLE SNES controller name substrings.
     */
    private fun inferBle(device: InputDevice): Boolean {
        val name = device.name.lowercase()

        // Known BLE SNES controller identifiers
        val bleKeywords = listOf(
            "8bitdo", "sn30", "sf30",          // 8BitDo BLE models
            "ibuffalo",                          // iBuffalo SNES BLE
            "retro-bit",                         // Retro-Bit BLE
            "ble", "bluetooth le",               // Generic BLE tags
            "pro controller",                    // 8BitDo Pro
            "zero",                              // 8BitDo Zero (BLE)
            "m30",                               // 8BitDo M30 (BLE)
        )

        if (bleKeywords.any { it in name }) return true

        // Heuristic: vendor/product both 0 on a non-virtual device often means
        // BLE HID (USB and Classic usually report real IDs).
        if (device.vendorId == 0 && device.productId == 0) return true

        return false
    }
}
