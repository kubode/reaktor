buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        }
    }
    dependencies {
        classpath(kotlin("gradle-plugin", "1.5.30-RC-161"))
        classpath("com.android.tools.build:gradle:7.1.0-alpha03")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:10.0.0")
    }
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        }
    }
    group = "com.github.kubode.reaktor"
    version = "0.1.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
