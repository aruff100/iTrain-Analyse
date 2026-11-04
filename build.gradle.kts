import java.security.MessageDigest
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "2.0.1"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "4.1.1"
}

group = "com.example"
version = "1.1"

repositories {
    mavenCentral()
}

java {
    toolchain {
        // Wie die Schwesterprojekte: Liberica JDK 25 (BellSoft).
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.BELLSOFT
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("**/build-info.properties") {
        expand("version" to project.version.toString())
    }
    // Bearbeitbare Texte (Uebersetzungen, Hilfe) aus dem Projektordner "texte".
    from("texte") {
        include("*.properties", "*.txt")
        into("com/example/itrain_analyse")
    }
    // Mitgelieferte Prompt-Fassung (dieselben Dateien wie auf dem Webserver),
    // damit das Programm auch ohne Internet beim ersten Start einen Prompt hat.
    from("server/analyse-daten") {
        include("Prompt_*.txt")
        into("com/example/itrain_analyse")
    }
    from("server/analyse-daten/manifest.properties") {
        rename { "prompt-manifest.properties" }
        into("com/example/itrain_analyse")
    }
}

// Erzeugt server/analyse-daten/manifest.properties neu (Dateiname und SHA-256 je
// Prompt-Gruppe). Nach jedem Austausch einer Prompt-Datei ausfuehren und dann
// Prompt-Datei UND manifest.properties auf den Webserver laden.
tasks.register("promptManifest") {
    group = "itrain-analyse"
    description = "Erzeugt server/analyse-daten/manifest.properties"
    doLast {
        val dir = file("server/analyse-daten")
        val gruppen = linkedMapOf("DE_NL" to "Prompt_Analyse_DE_NL", "EN_FR_ES_IT" to "Prompt_Analysis_EN_FR_ES_IT",
            "ANFAENGER_DE_NL" to "Prompt_Anfaenger_DE_NL", "ANFAENGER_EN_FR_ES_IT" to "Prompt_Beginners_EN_FR_ES_IT")
        val sb = StringBuilder("# Prompt-Dateien fuer iTrain-Analyse. Erzeugt von \"gradlew promptManifest\" - nicht von Hand aendern.\n")
        for ((key, praefix) in gruppen) {
            val datei = dir.listFiles { f -> f.name.startsWith(praefix) && f.name.endsWith(".txt") }
                ?.maxByOrNull { it.lastModified() }
                ?: throw GradleException("Keine Datei $praefix*.txt in $dir")
            val md = MessageDigest.getInstance("SHA-256")
            val hex = md.digest(datei.readBytes()).joinToString("") { "%02x".format(it) }
            sb.append("$key.datei=${datei.name}\n$key.sha256=$hex\n")
        }
        file("server/analyse-daten/manifest.properties").writeText(sb.toString())
        println(sb)
    }
}

application {
    mainModule.set("com.example.itrain_analyse")
    mainClass.set("com.example.itrain_analyse.Launcher")
}

// JavaFX laedt native Bibliotheken ueber System.load() - seit JDK 24 ohne
// diese Angabe mit Warnung (siehe iTrain-Logistik/build.gradle.kts).
val ENABLE_NATIVE_ACCESS = "--enable-native-access=javafx.graphics"

tasks.named<JavaExec>("run") {
    jvmArgs(ENABLE_NATIVE_ACCESS)
}

javafx {
    version = "25.0.4"
    modules = listOf("javafx.controls")
}

val junitVersion = "5.12.1"

dependencies {
    // PDF-Handbuch seitenweise in Text umwandeln.
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    testImplementation("org.junit.jupiter:junit-jupiter:${junitVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages", "--bind-services"))

    launcher {
        // Muss mit jpackage.imageName uebereinstimmen (siehe iTrain_import_export).
        name = "iTrain-Analyse"
        jvmArgs = listOf(ENABLE_NATIVE_ACCESS)
    }

    jpackage {
        val os = OperatingSystem.current()
        val osLabel = when {
            os.isWindows -> "Windows"
            os.isMacOsX -> "macOS"
            else -> "Linux"
        }
        imageName = "iTrain-Analyse"
        installerName = "iTrain-Analyse-$osLabel"
        appVersion = project.version.toString()
        vendor = "Andre Ruff"
        jvmArgs = listOf(ENABLE_NATIVE_ACCESS)
        // Programmsymbol (Anwender 03.11.2026), Quelle packaging/icons/icon.svg
        icon = when {
            os.isWindows -> file("packaging/icons/app-icon.ico").path
            os.isMacOsX -> file("packaging/icons/app-icon.icns").path
            else -> file("packaging/icons/app-icon-256.png").path
        }
        installerType = when {
            os.isWindows -> "msi"
            os.isMacOsX -> "dmg"
            else -> "deb"
        }
        if (os.isWindows) {
            installerOptions = listOf("--win-menu", "--win-shortcut", "--win-dir-chooser")
        } else if (os.isLinux) {
            installerOptions = listOf("--linux-shortcut")
        }
    }
}
