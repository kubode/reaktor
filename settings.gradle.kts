pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "reaktor-root"
include(":sample:androidApp")
include(":sample:shared")
include(":reaktor")
