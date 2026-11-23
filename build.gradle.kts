import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

group = "com.boumusic.desktop"
version = "0.1.8-beta"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.desktop.linux_x64)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    // SMB (Windows y Linux Samba)
    implementation("com.hierynomus:smbj:0.13.0")

    // FTP y FTPS
    implementation("commons-net:commons-net:3.10.0")

    // WebDAV y HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Audio: decodificadores Java Sound nativos (MP3, OGG Vorbis, WAV, FLAC)
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7.4")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
    implementation("org.jflac:jflac-codec:1.5.2")

    // JSON para sincronización y backup con Boumusic Android
    implementation("org.json:json:20240303")

    // Coroutines Swing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.boumusic.desktop.MainKt"
        jvmArgs("-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1")
        nativeDistributions {
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb
            )
            packageName = "Boumusic"
            packageVersion = "0.1.0"
            description = "Boumusic Desktop - Reproductor de Música Ultraligero para Windows y Linux"
            vendor = "Boumusic"
            copyright = "© 2026 Boumusic"

            windows {
                menuGroup = "Boumusic"
                upgradeUuid = "a5948f21-72a3-487e-8c31-9a738bf80a2b"
                shortcut = true
            }
            linux {
                packageName = "boumusic"
                shortcut = true
            }
        }
    }
}
