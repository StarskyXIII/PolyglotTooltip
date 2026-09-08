pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        version("1.20.1-forge", "1.20.1").buildscript = "build.forge.gradle.kts"
        version("1.20.1-fabric", "1.20.1").buildscript = "build.fabric.gradle.kts"
        version("1.21.1-neoforge", "1.21.1").buildscript = "build.neoforge.gradle.kts"
        version("1.21.1-fabric", "1.21.1").buildscript = "build.fabric.gradle.kts"
        version("26.1.2-neoforge", "26.1.2").buildscript = "build.neoforge.gradle.kts"
        version("26.1.2-fabric", "26.1.2").buildscript = "build.fabric26.gradle.kts"
        vcsVersion = "1.21.1-neoforge"
    }
}

rootProject.name = "polyglottooltip"
