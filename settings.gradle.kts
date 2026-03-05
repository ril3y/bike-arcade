pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BikeArcade"

include(":app")
include(":emulator")
include(":metrics")
include(":overlay")
include(":ui")
include(":media")
include(":settings")
