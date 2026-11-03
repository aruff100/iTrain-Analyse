// Erlaubt Gradle, das per Toolchain geforderte JDK (Liberica 25) selbst
// herunterzuladen - gleiches Vorgehen wie in iTrain-Logistik und iTrain_import_export.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "iTrain-Analyse"
