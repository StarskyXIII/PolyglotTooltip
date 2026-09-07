plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.141"
    id("idea")
}

val modId = "polyglottooltip"
val modVersion = "1.6.0"
val requiredJava = if (sc.current.parsed >= "26") 25 else 21

stonecutter {
    replacements.string(sc.current.parsed >= "26", "minecraft_identifier") {
        replace("ResourceLocation", "Identifier")
    }
    replacements.string(sc.current.parsed >= "26", "minecraft_util_package") {
        replace("import net.minecraft.Util;", "import net.minecraft.util.Util;")
    }
    replacements.string(sc.current.parsed >= "26", "ae_key_rendering_package") {
        replace("appeng.api.client.AEKeyRendering", "appeng.client.api.AEKeyRendering")
    }
    replacements.string(sc.current.parsed >= "26", "gui_graphics_extractor") {
        replace("GuiGraphics", "GuiGraphicsExtractor")
        replace("method = \"renderTooltip\"", "method = \"extractHoveredTooltip\"")
    }
    replacements.string(sc.current.parsed >= "26", "rs_platform_resource") {
        replace("getResourceForRecipeMods()", "getAutocraftingResource()")
    }
    replacements.string(sc.current.parsed >= "26", "rs_fluid_resource") {
        replace("getResourceForRecipeMods()", "getFluidResource()")
    }
    replacements.string(sc.current.parsed >= "26", "ae_fluid_renderer") {
        replace(
            "appeng.init.client.InitStackRenderHandlers${'$'}FluidKeyRenderHandler",
            "appeng.client.renderer.keytypes.FluidKeyRenderer"
        )
    }
}

group = "com.starskyxiii.polyglottooltip"
version = modVersion
base.archivesName = "$modId-${sc.current.version}-neoforge"

neoForge {
    version = property("loader.version") as String

    runs {
        register("client") {
            client()
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com/") {
        name = "BlameJared"
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
    }
    maven("https://maven.creeperhost.net/") {
        name = "RefinedMods"
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.appliedenergistics:appliedenergistics2:${property("ae2.version")}")
    compileOnly("com.refinedmods.refinedstorage:refinedstorage-common:${property("refined_storage.version")}")
    compileOnly("mezz.jei:jei-${sc.current.version}-common-api:${property("jei.version")}")
    compileOnly("mezz.jei:jei-${sc.current.version}-neoforge-api:${property("jei.version")}")
    compileOnly("com.blamejared.searchables:Searchables-common-${sc.current.version}:1.0.2")
    compileOnly("maven.modrinth:jade:${property("jade.version")}")

    val opencc4jVersion = "1.14.0"
    implementation("com.github.houbb:opencc4j:$opencc4jVersion")
    jarJar("com.github.houbb:opencc4j:$opencc4jVersion")
    jarJar("com.github.houbb:heaven:0.13.0")
    jarJar("com.github.houbb:nlp-common:0.0.5")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(requiredJava)
sourceSets.main {
    resources.srcDir("src/main/templates")
}
sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava
    }
    test {
        useJUnitPlatform()
    }
    processResources {
        val values = mapOf(
            "mod_version" to modVersion,
            "minecraft_version" to sc.current.version,
            "neoforge_minimum" to project.property("neoforge.minimum"),
            "java_level" to "JAVA_$requiredJava",
            "jei_fluid_mixin" to if (sc.current.parsed >= "26") "" else
                "    \"jei.JeiFluidHelperMixin\",",
            "rs2_mekanism_mixins" to if (sc.current.parsed >= "26") "" else
                "    \"rs2.Rs2MekanismChemicalGridResourceMixin\",\n" +
                "    \"rs2.Rs2MekanismChemicalResourceRenderingMixin\","
        )
        inputs.properties(values)
        filesMatching(listOf("META-INF/neoforge.mods.toml", "polyglottooltip.mixins.json")) {
            expand(values)
        }
    }
    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
