import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.gradle.plugin)
    alias(libs.plugins.kotlin.plugin.compose)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.kotlinx.coroutines)
                implementation(libs.okhttp)
                implementation(compose.ui)
                implementation(compose.runtime)
                implementation(compose.material3)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "melox.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Melox"
            packageVersion = "1.0.0"
        }
    }
}
