pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        version("1.21.1-neoforge", "1.21.1").buildscript = "build.neoforge.gradle.kts"
        version("26.1.2-neoforge", "26.1.2").buildscript = "build.neoforge.gradle.kts"
        vcsVersion = "1.21.1-neoforge"
    }
}

rootProject.name = "polyglottooltip"
