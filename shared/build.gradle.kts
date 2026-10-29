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
                implementation("org.xerial:sqlite-jdbc:3.53.4.0")
                // Recognition runs the pinned JS/WASM only when requested; no external Node install.
                implementation("com.caoccao.javet:javet:6.0.2")
                // CPU ONNX Runtime matches Android's model protocol; weights are downloaded separately.
                implementation("com.microsoft.onnxruntime:onnxruntime:1.31.0")
                implementation("org.apache.commons:commons-compress:1.27.1")
                val javetOs = if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux"
                runtimeOnly("com.caoccao.javet:javet-v8-$javetOs-x86_64:6.0.2")
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

// The three public model packs are fetched separately, never committed. Windows
// CI runs this executable explicitly using the production test classpath.
tasks.register<JavaExec>("verifyAutoSyncModelContracts") {
    dependsOn("desktopTestClasses")
    classpath = tasks.named<org.gradle.api.tasks.testing.Test>("desktopTest").get().classpath
    mainClass.set("melox.autosync.AutoSyncRealModelContract")
    args(providers.gradleProperty("autoSyncFixtures").orElse("").get())
    maxHeapSize = "512m"
    systemProperty("user.home", layout.buildDirectory.dir("parity/test-home").get().asFile.absolutePath)
}

tasks.register<JavaExec>("verifyAutoSyncJapaneseDictionary") {
    dependsOn("desktopTestClasses")
    classpath = tasks.named<org.gradle.api.tasks.testing.Test>("desktopTest").get().classpath
    mainClass.set("melox.autosync.AutoSyncJapaneseDictionaryContract")
    args(providers.gradleProperty("autoSyncDictionaryArchive").orElse("").get())
    providers.gradleProperty("autoSyncDictionaryFrontend").orNull?.let { library ->
        args(library, project.file("src/desktopTest/resources/autosync/ja_g2p_vectors.json").absolutePath)
    }
    systemProperty("user.home", layout.buildDirectory.dir("parity/test-home").get().asFile.absolutePath)
}

tasks.register<JavaExec>("verifyAutoSyncJapaneseFrontend") {
    dependsOn("desktopTestClasses")
    classpath = tasks.named<org.gradle.api.tasks.testing.Test>("desktopTest").get().classpath
    mainClass.set("melox.autosync.AutoSyncJapaneseFrontendContract")
    args(providers.gradleProperty("autoSyncJapaneseLibrary").orElse("").get(),
        providers.gradleProperty("autoSyncJapaneseDictionary").orElse("").get(),
        project.file("src/desktopTest/resources/autosync/ja_g2p_vectors.json").absolutePath)
    systemProperty("user.home", layout.buildDirectory.dir("parity/test-home").get().asFile.absolutePath)
}
