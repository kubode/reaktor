plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

repositories {
    maven {
        // kotest
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

kotlin {
    android()
    ios()

    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0-native-mt")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:4.6.0.235-SNAPSHOT")
                implementation("app.cash.turbine:turbine:0.5.0")
            }
        }
        val androidMain by getting
        val androidTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.0-native-mt")
                implementation("androidx.test:runner:1.3.0")
                implementation("androidx.test:rules:1.3.0")
                implementation("androidx.test.ext:junit:1.1.2")
                implementation("org.robolectric:robolectric:4.5.1")
            }
        }
        val iosMain by getting
        val iosTest by getting
    }
}

android {
    compileSdkVersion(30)
    defaultConfig {
        minSdkVersion(24)
        targetSdkVersion(30)
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}
