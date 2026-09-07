plugins {
    id("dev.kikugie.loom-back-compat")
}

group = "com.starskyxiii.polyglottoolchainprobe"
version = "0.1.0"
base.archivesName = "polyglottoolchainprobe-${sc.current.version}-fabric-${property("loader.version")}"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("loader.version")}")
    implementation("com.github.houbb:opencc4j:1.14.0")
    include("com.github.houbb:opencc4j:1.14.0")
    include("com.github.houbb:heaven:0.13.0")
    include("com.github.houbb:nlp-common:0.0.5")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
    }
    processResources {
        val values = mapOf(
            "mod_version" to project.version,
            "minecraft_version" to sc.current.version,
            "loader_version" to project.property("loader.version"),
            "java_level" to "JAVA_25",
            "refmap_entry" to ""
        )
        inputs.properties(values)
        filesMatching(listOf("fabric.mod.json", "polyglottoolchainprobe.mixins.json")) { expand(values) }
        exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")
    }
}
