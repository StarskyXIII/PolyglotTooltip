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
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("loader.version")}")
    implementation("com.github.houbb:opencc4j:1.14.0")
    include("com.github.houbb:opencc4j:1.14.0")
    include("com.github.houbb:heaven:0.13.0")
    include("com.github.houbb:nlp-common:0.0.5")
}

loom {
    mixin.defaultRefmapName = "polyglottoolchainprobe.refmap.json"
}

val requiredJava = if (sc.current.parsed >= "1.20.5") 21 else 17
java.toolchain.languageVersion = JavaLanguageVersion.of(requiredJava)

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava
    }
    processResources {
        val values = mapOf(
            "mod_version" to project.version,
            "minecraft_version" to sc.current.version,
            "loader_version" to project.property("loader.version"),
            "java_level" to "JAVA_$requiredJava",
            "refmap_entry" to ""
        )
        inputs.properties(values)
        filesMatching(listOf("fabric.mod.json", "polyglottoolchainprobe.mixins.json")) { expand(values) }
        exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")
    }
}
