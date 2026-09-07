plugins {
    id("net.minecraftforge.gradle") version "7.0.36"
    id("net.minecraftforge.renamer") version "1.1.7"
    id("net.minecraftforge.jarjar") version "0.2.3"
}

group = "com.starskyxiii.polyglottoolchainprobe"
version = "0.1.0"
base.archivesName = "polyglottoolchainprobe-${sc.current.version}-forge-${property("loader.version")}"

jarJar.register()

minecraft {
    mappings("official", sc.current.version)
}

renamer {
    enableMixinRefmaps {
        config("polyglottoolchainprobe.mixins.json")
        refMap = "polyglottoolchainprobe.refmap.json"
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:${sc.current.version}-${property("loader.version")}"))
    annotationProcessor("org.spongepowered:mixin:0.8.7:processor")
    implementation("com.github.houbb:opencc4j:1.14.0")
    add("jarJar", "com.github.houbb:opencc4j:1.14.0")
    add("jarJar", "com.github.houbb:heaven:0.13.0")
    add("jarJar", "com.github.houbb:nlp-common:0.0.5")
}

renamer.mappings.from(minecraft.dependency.toSrgFile)

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 17
    }
    processResources {
        val values = mapOf(
            "mod_version" to project.version,
            "minecraft_version" to sc.current.version,
            "loader_version" to project.property("loader.version"),
            "java_level" to "JAVA_17",
            "refmap_entry" to "  \"refmap\": \"polyglottoolchainprobe.refmap.json\",\n"
        )
        inputs.properties(values)
        filesMatching(listOf("META-INF/mods.toml", "polyglottoolchainprobe.mixins.json")) { expand(values) }
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    }
    withType<Jar>().configureEach {
        manifest.attributes("MixinConfigs" to "polyglottoolchainprobe.mixins.json")
    }
    named("compileJava") {
        dependsOn("stonecutterGenerate")
    }
}

val productionJar = renamer.classes(tasks.named<Jar>("jarJar")) {
    mappings(renamer.mixin.generatedMappings)
    archiveClassifier = "production"
}

tasks.build {
    dependsOn(productionJar)
}
