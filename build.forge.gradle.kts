plugins {
    id("java-library")
    id("maven-publish")
    id("net.minecraftforge.gradle") version "7.0.36"
    id("net.minecraftforge.renamer") version "1.1.7"
    id("net.minecraftforge.jarjar") version "0.2.3"
    id("idea")
}

val modId = "polyglottooltip"
val modVersion = "2.0"

stonecutter {
    replacements.string(false, "minecraft_identifier") {
        replace("ResourceLocation", "Identifier")
    }
    replacements.string(false, "minecraft_util_package") {
        replace("import net.minecraft.Util;", "import net.minecraft.util.Util;")
    }
    replacements.string(false, "ae_key_rendering_package") {
        replace("appeng.api.client.AEKeyRendering", "appeng.client.api.AEKeyRendering")
    }
    replacements.string(false, "gui_graphics_extractor") {
        replace("GuiGraphics", "GuiGraphicsExtractor")
        replace("method = \"renderTooltip\"", "method = \"extractHoveredTooltip\"")
    }
    replacements.string(false, "rs_platform_resource") {
        replace("getResourceForRecipeMods()", "getAutocraftingResource()")
    }
    replacements.string(false, "rs_fluid_resource") {
        replace("getResourceForRecipeMods()", "getFluidResource()")
    }
    replacements.string(false, "ae_fluid_renderer") {
        replace(
            "appeng.init.client.InitStackRenderHandlers${'$'}FluidKeyRenderHandler",
            "appeng.client.renderer.keytypes.FluidKeyRenderer"
        )
    }
    replacements.string(sc.current.parsed <= "1.20.1", "forge_neoforge_package") {
        replace("net.neoforged.neoforge", "net.minecraftforge")
        replace("net.neoforged.bus.api", "net.minecraftforge.eventbus.api")
        replace("net.neoforged.fml", "net.minecraftforge.fml")
        replace("net.neoforged.api", "net.minecraftforge.api")
        replace("ModConfigSpec", "ForgeConfigSpec")
    }
    replacements.string(true, "keybinds_list_package") {
        replace(
            "net.minecraft.client.gui.screens.options.controls.KeyBindsList",
            "net.minecraft.client.gui.screens.controls.KeyBindsList"
        )
    }
}

group = "com.starskyxiii.polyglottooltip"
version = modVersion
base.archivesName = "$modId-${sc.current.version}-forge"

jarJar.register()

minecraft {
    mappings("official", sc.current.version)
}

renamer {
    enableMixinRefmaps {
        config("polyglottooltip.mixins.json")
        refMap = "polyglottooltip.refmap.json"
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
    maven("https://maven.blamejared.com/")
    maven("https://modmaven.dev/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://api.modrinth.com/maven")
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.Towdium")
        }
    }
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:${sc.current.version}-${property("loader.version")}"))
    annotationProcessor("org.spongepowered:mixin:0.8.7:processor")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.Towdium:PinIn:1.6.0") {
        exclude(group = "it.unimi.dsi", module = "fastutil")
    }

    compileOnly(renamer.dependency("appeng:appliedenergistics2-forge:${property("ae2.version")}"))
    compileOnly(renamer.dependency("mezz.jei:jei-${sc.current.version}-common-api:${property("jei.version")}"))
    compileOnly(renamer.dependency("mezz.jei:jei-${sc.current.version}-forge-api:${property("jei.version")}"))
    compileOnly(renamer.dependency("com.blamejared.searchables:Searchables-common-${sc.current.version}:${property("searchables.version")}"))
    compileOnly(renamer.dependency("maven.modrinth:jade:${property("jade.version")}"))

    val opencc4jVersion = "1.14.0"
    implementation("com.github.houbb:opencc4j:$opencc4jVersion")
    add("jarJar", "com.github.houbb:opencc4j:$opencc4jVersion")
    add("jarJar", "com.github.houbb:heaven:0.13.0")
    add("jarJar", "com.github.houbb:nlp-common:0.0.5")

    val mixinExtrasVersion = "0.5.5"
    compileOnly("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")
    annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")
    testRuntimeOnly("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")
    add("jarJar", "io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")
}

renamer.mappings.from(minecraft.dependency.toSrgFile)

java.toolchain.languageVersion = JavaLanguageVersion.of(17)
sourceSets.main {
    java.exclude(
        "com/starskyxiii/polyglottooltip/FabricConfigValue.java",
        "com/starskyxiii/polyglottooltip/FabricConfigStore.java",
        "com/starskyxiii/polyglottooltip/FabricPolyglotTooltipClient.java",
        "com/starskyxiii/polyglottooltip/integration/jei/FabricJeiFluidNameHelper.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/FabricJeiFluidHelperMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/FabricJeiListElementInfoMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/FabricAe2PatternAccessSearchMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/ExtendedAePatternTerminalSearchMixin.java"
    )
    java.exclude("com/starskyxiii/polyglottooltip/integration/rs2/**")
    java.exclude("com/starskyxiii/polyglottooltip/mixin/rs2/**")
    java.exclude("com/starskyxiii/polyglottooltip/mixin/mekanism/MekanismChemicalStackMixin.java")
    java.exclude("com/starskyxiii/polyglottooltip/mixin/jei/JeiGeneralizedSuffixTreeSearchStorageMixin.java")
    resources.srcDir("src/main/templates")
}
sourceSets.test {
    java.exclude("com/starskyxiii/polyglottooltip/FabricConfigStoreTest.java")
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 17
    }
    test {
        useJUnitPlatform()
    }
    processResources {
        exclude {
            it.name == "neoforge.mods.toml" ||
                (it.name == "polyglottooltip.mixins.json" &&
                    it.file != rootProject.file("src/forge/resources/polyglottooltip.mixins.json"))
        }
        from(rootProject.file("src/forge/resources")) {
            val values = mapOf(
                "mod_version" to modVersion,
                "minecraft_version" to sc.current.version,
                "forge_minimum" to project.property("forge.minimum")
            )
            inputs.properties(values)
            filesMatching("META-INF/mods.toml") {
                expand(values)
            }
        }
    }
    withType<Jar>().configureEach {
        manifest.attributes("MixinConfigs" to "polyglottooltip.mixins.json")
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

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifact(productionJar)
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
