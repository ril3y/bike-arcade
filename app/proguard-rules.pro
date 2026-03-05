# BikeArcade ProGuard rules

# Keep JNI native methods (name-based lookup requires exact signatures)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep LibretroBridge (JNI callbacks from native code)
-keep class com.bikearcade.emulator.LibretroBridge { *; }
