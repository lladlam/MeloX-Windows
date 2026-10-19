import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.gradle.plugin)
    alias(libs.plugins.kotlin.plugin.compose)
}

// Compile the exact pinned geometry sources against our Compose version. Capsule's
// published binary is Android-only; neither artifact may upgrade the desktop UI stack.
val pinnedGlassGeometrySources = configurations.create("pinnedGlassGeometrySources") { isTransitive = false }
dependencies {
    add(pinnedGlassGeometrySources.name, "io.github.kyant0:capsule:2.1.3:sources@jar")
    add(pinnedGlassGeometrySources.name, "io.github.kyant0:shapes:1.2.0:sources@jar")
}
val extractPinnedGlassGeometry by tasks.registering(Sync::class) {
    from(provider { pinnedGlassGeometrySources.files.map { zipTree(it) } }) {
        include("**/*.kt")
        exclude("**/PathSegment.kt") // Local adapter replaces Android Path/RectF calls.
        eachFile { path = path.removePrefix("commonMain/") }
        filter { line: String ->
            line.replace(Regex("import androidx\\.annotation\\.(FloatRange|IntRange)"), "")
                .replace(Regex("@(?:param:)?(FloatRange|IntRange)\\([^)]*\\)\\s*"), "")
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/pinnedGlassGeometry"))
    doFirst {
        val hashes = mapOf(
            "capsule-2.1.3-sources.jar" to "219abe78d1c13b11d3fa976b527e4988e85c8b59ecabdb3b245293712fcfac79",
            "shapes-1.2.0-sources.jar" to "014405aac8faf374a9ee2fcd27aec7540a15562de3a1cc90c7835b6cca728738",
        )
        pinnedGlassGeometrySources.files.forEach { source ->
            val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(hash == hashes[source.name]) { "Pinned glass geometry source hash mismatch: ${source.name}" }
        }
    }
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir(extractPinnedGlassGeometry)
            resources.srcDir("src/main/resources")
            dependencies {
                implementation(project(":shared"))
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.okhttp)
                implementation(libs.org.json)
                implementation(compose.ui)
                implementation(compose.runtime)
                implementation(compose.material3)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "melox.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "MeloX-Desktop"
            packageVersion = "1.0.0"
            description = "MeloX Music Player for Windows"
            vendor = "MeloX"
            modules(
                "java.instrument",
                "java.management",
                "java.prefs",
                "java.sql",
                "jdk.unsupported",
                "jdk.crypto.ec",
                "jdk.localedata",
                "jdk.accessibility",
                "jdk.httpserver"
            )
            windows {
                menuGroup = "MeloX"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                perUserInstall = true
                dirChooser = true
            }
        }
    }
}

// Production JVM flags (jpackage java-options) must be the flags every replay runs under.
val productionJvmArgs = listOf("-Xms16m", "-Xmx1g", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8")

tasks.register<JavaExec>("captureUi") {
    group = "verification"
    description = "Capture the production app offscreen and replay coordinate clicks."
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXVisualCaptureKt")
    jvmArgs(providers.gradleProperty("captureJvmArgs").orNull?.split(' ')?.filter(String::isNotBlank)
        ?: productionJvmArgs)
    systemProperty("java.awt.headless", "true")
    systemProperty("user.home", providers.gradleProperty("captureHome").getOrElse(layout.buildDirectory.dir("parity/home").get().asFile.absolutePath))
    val argumentFile = providers.gradleProperty("captureArgsFile").orNull
    args(if (argumentFile != null) file(argumentFile).readLines(Charsets.UTF_8)
        else providers.gradleProperty("captureArgs").getOrElse("").split(' ').filter { it.isNotBlank() })
}

tasks.register<JavaExec>("auditLyricRaster") {
    group = "verification"
    description = "Compare production individual glyphs against their own shaped text layout at Windows DPI scales."
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXLyricRasterAuditKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("user.home", layout.buildDirectory.dir("verification/lyric-raster-home").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("verification/lyric-raster").get().asFile.absolutePath)
}

tasks.register<JavaExec>("auditFontMetrics") {
    group = "verification"
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXFontMetricsAuditKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("user.home", layout.buildDirectory.dir("verification/font-metrics-home").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("verification/font-metrics").get().asFile.absolutePath)
}

tasks.register<JavaExec>("auditArtworkMemory") {
    group = "verification"
    description = "Stress the production decoded artwork budget and record real heap/resident measurements."
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXArtworkMemoryAuditKt")
    systemProperty("java.awt.headless", "true")
    args(layout.buildDirectory.dir("verification/artwork-memory").get().asFile.absolutePath)
}

tasks.register<JavaExec>("auditArtworkDecode") {
    group = "verification"
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXArtworkDecodeAuditKt")
    systemProperty("java.awt.headless", "true")
    args(layout.buildDirectory.file("verification/artwork-decode.json").get().asFile.absolutePath)
}

tasks.register<JavaExec>("auditFlowingLight") {
    group = "verification"
    description = "Verify actual production Flowing Light motion, freeze/resume, wrap and native disposal."
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXFlowingLightAuditKt")
    jvmArgs(productionJvmArgs)
    systemProperty("java.awt.headless", "true")
    systemProperty("user.home", layout.buildDirectory.dir("verification/flowing-light-home").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("verification/flowing-light").get().asFile.absolutePath)
}

tasks.register<JavaExec>("auditSettingsIcons") {
    group = "verification"
    description = "Check every pinned settings row icon against the pinned Android SF Symbol mapping."
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXSettingsIconAuditKt")
    systemProperty("java.awt.headless", "true")
    args(rootProject.layout.projectDirectory.file("parity/3f877ea/evidence/settings-pages-source-audit.json").asFile.absolutePath,
        layout.buildDirectory.file("verification/settings-icons.json").get().asFile.absolutePath)
}

tasks.register<JavaExec>("auditDisplayAwake") {
    group = "verification"
    description = "Acquire/release the real Windows display power request on one owner thread."
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXDisplayAwakeAuditKt")
    args(layout.buildDirectory.file("verification/display-awake.json").get().asFile.absolutePath)
}

tasks.register<JavaExec>("auditImmersiveWindow") {
    group = "verification"
    description = "Verify native fullscreen and restoration of floating/maximized/user-controlled placement."
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXImmersiveWindowAuditKt")
    args(layout.buildDirectory.file("verification/immersive-window.json").get().asFile.absolutePath)
}

tasks.register<JavaExec>("auditGlassOptics") {
    group = "verification"
    description = "Verify real Skia glass optics pixels, stable filter reuse and native ownership."
    dependsOn("desktopMainClasses")
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("melox.validation.MeloXGlassOpticsAuditKt")
    jvmArgs(productionJvmArgs)
    systemProperty("java.awt.headless", "true")
    systemProperty("user.home", layout.buildDirectory.dir("verification/glass-home").get().asFile.absolutePath)
    args(layout.buildDirectory.file("verification/glass-optics.json").get().asFile.absolutePath)
}
