import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

// Keep the entire Android main vendored network/model implementation intact.
// The tar is a deterministic git archive, not a download performed during builds.
val extractAndroidInnerTube by tasks.registering(Sync::class) {
    from(tarTree(resources.gzip("vendor/android-94e17c9.tar.gz"))) {
        include("android/innertube/src/main/kotlin/**/*.kt")
        eachFile { path = path.removePrefix("android/innertube/src/main/kotlin/") }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/androidMain"))
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir(extractAndroidInnerTube)
            dependencies {
                api("io.ktor:ktor-client-core:3.5.2")
                implementation("io.ktor:ktor-client-okhttp:3.5.2")
                implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
                implementation("io.ktor:ktor-client-encoding:3.5.2")
                implementation("org.brotli:dec:0.1.2")
            }
        }
    }
}
