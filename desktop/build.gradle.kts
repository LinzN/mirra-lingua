import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Android ships org.json; on the desktop it comes from Maven.
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
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

// Installers with the same naming as the APK: Parla-<version>-linux.deb / Parla-<version>-windows.msi
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
