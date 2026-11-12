import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

// Keep in step with android/app/build.gradle.kts.
val appVersion = "1.3.0"
val parlaServer = providers.gradleProperty("parlaServer").getOrElse("https://parla.mirranet.de")

group = "de.mirranet.parla"
version = appVersion

kotlin {
    // Engine and data layer are shared with the Android app.
    sourceSets["main"].kotlin.srcDir("../shared/src/main/kotlin")
}

// Libraries the app needs on every platform. The platform-specific Compose/Skiko runtime is added separately.
val appLibraries = listOf(
    compose.material3,
    "org.jetbrains.compose.material:material-icons-core:1.7.3",
    "com.squareup.okhttp3:okhttp:4.12.0",
    // Android ships org.json; on the desktop it comes from Maven.
    "org.json:json:20240303",
    "org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0",
)

// Runtime classpath of the Windows build: same libraries, but with the Windows Compose/Skiko runtime
// instead of the one for the build machine. Lets the Linux build server produce the Windows installer.
val windowsRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // Resolve the same (JVM) variants as the normal runtime classpath.
    val source = configurations.runtimeClasspath.get().attributes
    attributes {
        source.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST")
            attribute(key as Attribute<Any>, source.getAttribute(key)!!)
        }
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM),
        )
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    appLibraries.forEach { implementation(it) }

    windowsRuntime(compose.desktop.windows_x64)
    appLibraries.forEach { windowsRuntime(it) }
    windowsRuntime("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
}

tasks.processResources {
    val props = mapOf("version" to appVersion, "server" to parlaServer)
    inputs.properties(props)
    filesMatching("parla.properties") { expand(props) }
}

compose.desktop {
    application {
        mainClass = "de.mirranet.parla.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        // jpackage runs on the JDK that runs Gradle; scripts/setup-jdk.sh picks a 17–21 one.

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "Parla"
            packageVersion = appVersion
            description = "Parla – Englisch und Französisch lernen"
            vendor = "MirraNET"
            copyright = "© MirraNET"
            // Only the JDK modules the app needs (OkHttp TLS, sound, Swing).
            modules("java.logging", "java.naming", "jdk.crypto.ec", "java.desktop", "jdk.unsupported")

            linux {
                packageName = "parla"
                debMaintainer = "niklas.linz@mirranet.de"
                menuGroup = "Education"
                appCategory = "education"
                shortcut = true
                iconFile.set(project.file("packaging/parla.png"))
            }
            windows {
                // Never change: lets newer .msi versions replace older installs.
                upgradeUuid = "6f3b2c4e-8a1d-4c7e-9b52-2d0e7a4f91c3"
                menuGroup = "Parla"
                perUserInstall = true
                shortcut = true
                dirChooser = true
                iconFile.set(project.file("packaging/parla.ico"))
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

// Installers with the same naming as the APK: Parla-<version>-linux.deb / Parla-<version>-windows.msi /
// Parla-<version>-windows-setup.exe
val distDeb by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn("packageDeb")
    from(layout.buildDirectory.dir("compose/binaries/main/deb")) { include("*.deb") }
    into(layout.buildDirectory.dir("dist"))
    rename { "Parla-$appVersion-linux.deb" }
}

val distMsi by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn("packageMsi")
    from(layout.buildDirectory.dir("compose/binaries/main/msi")) { include("*.msi") }
    into(layout.buildDirectory.dir("dist"))
    rename { "Parla-$appVersion-windows.msi" }
}

// ---------------------------------------------------------------- Windows installer built on Linux
// jpackage can only build a .msi on Windows. distWindows builds a Windows installer on any machine instead:
// the app jar + Windows libraries, a bundled Windows Java runtime (Temurin JRE 21) and a small Parla.exe
// launcher, packed into a setup.exe with NSIS (makensis). See scripts/build-windows-installer.sh.

val windowsApp by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Collects the app jar and its Windows libraries in build/windows/app/lib."
    from(tasks.jar)
    from(windowsRuntime)
    into(layout.buildDirectory.dir("windows/app/lib"))
}

val distWindows by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds build/dist/Parla-<version>-windows-setup.exe (needs makensis, curl or wget, unzip)."
    dependsOn(windowsApp)
    val appDir = layout.buildDirectory.dir("windows/app").get().asFile
    val outFile = layout.buildDirectory.file("dist/Parla-$appVersion-windows-setup.exe").get().asFile
    inputs.dir(appDir)
    inputs.dir("packaging/windows")
    outputs.file(outFile)
    doFirst { outFile.parentFile.mkdirs() }
    commandLine("sh", "scripts/build-windows-installer.sh", appVersion, appDir.path, outFile.path)
}
