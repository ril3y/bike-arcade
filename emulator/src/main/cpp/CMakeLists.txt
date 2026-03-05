cmake_minimum_required(VERSION 3.22.1)
project("retro_jni" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(retro_jni SHARED
    libretro_jni.cpp
)

# Security and warning flags
target_compile_options(retro_jni PRIVATE
    -Wall -Wextra -Werror
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
)

# Link against Android libraries
target_link_libraries(retro_jni
    android
    log
    dl       # dlopen / dlsym / dlclose
)

# Strip in release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set_target_properties(retro_jni PROPERTIES
        LINK_FLAGS "-s"
    )
endif()
