buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.0")
        classpath("com.android.tools.build:gradle:4.2.0")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:10.0.0")
    }
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    repositories {
        google()
        mavenCentral()
    }
    group = "com.github.kubode.reaktor"
    version = "0.1.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
