plugins {
    id("net.neoforged.moddev") version "2.0.141"
}

group = "com.starskyxiii.polyglottoolchainprobe"
version = "0.1.0"

val requiredJava = if (sc.current.parsed >= "26") 25 else 21
base.archivesName = "polyglottoolchainprobe-${sc.current.version}-neoforge-${property("loader.version")}"

neoForge {
    version = property("loader.version") as String
    mods {
        register("polyglottoolchainprobe") {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.houbb:opencc4j:1.14.0")
    jarJar("com.github.houbb:opencc4j:1.14.0")
    jarJar("com.github.houbb:heaven:0.13.0")
    jarJar("com.github.houbb:nlp-common:0.0.5")
}

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
        filesMatching(listOf("META-INF/neoforge.mods.toml", "polyglottoolchainprobe.mixins.json")) { expand(values) }
        exclude("fabric.mod.json", "META-INF/mods.toml", "pack.mcmeta")
    }
    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }
}
