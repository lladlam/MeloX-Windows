import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.gradle.plugin)
    alias(libs.plugins.kotlin.plugin.compose)
}

// Playback/settings regressions must not overwrite the developer's real queue/session.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val testHome = layout.buildDirectory.dir("parity/test-home")
    systemProperty("user.home", testHome.get().asFile.absolutePath)
    doFirst { testHome.get().asFile.mkdirs() }
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
                implementation(libs.compose.ui)
                implementation(libs.compose.runtime)
                implementation(libs.compose.material3)
                implementation(libs.okhttp)
                implementation("net.java.dev.jna:jna-platform:5.17.0")
                implementation(project(":innertube"))
                implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.4") {
                    exclude(group = "com.google.protobuf")
                }
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.okhttp)
                implementation(libs.org.json)
                implementation(libs.okhttp.dnsoverhttps)
                implementation("com.ibm.icu:icu4j:77.1")
                implementation("xyz.gianlu.librespot:librespot-lib:1.6.5")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val desktopTest by getting {
            // Exercise the decoder shipped with the app, including clean LFS checkouts.
            resources.srcDir(rootProject.file("desktop/src/main/resources"))
            resources.include("decoder/**")
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.coroutines.test)
                implementation("com.squareup.okhttp3:mockwebserver:5.3.2")
            }
        }
    }
}
