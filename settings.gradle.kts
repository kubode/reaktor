pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "reaktor-root"
include(":androidApp")
include(":shared")
include(":reaktor")
