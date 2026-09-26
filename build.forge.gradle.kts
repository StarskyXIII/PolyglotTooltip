import groovy.json.JsonSlurper
import java.io.OutputStream
import java.util.jar.JarInputStream
import java.util.zip.ZipFile

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
    replacements.string(sc.current.parsed <= "1.19.2", "ae_key_rendering_legacy") {
        replace("AEKeyRendering", "AEStackRendering")
    }
    replacements.string(sc.current.parsed <= "1.19.2", "minecraft_legacy_registries") {
        replace("import net.minecraft.core.registries.BuiltInRegistries;", "import net.minecraft.core.Registry;")
        replace("BuiltInRegistries.ITEM", "Registry.ITEM")
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
    if (sc.current.parsed <= "1.19.2") {
        java.exclude(
            "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2RepoSearchMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/controlling/ControllingConstantsMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/projecte/ProjectENameQueryMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/sophisticatedcore/SophisticatedCoreStorageScreenMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/mekanism/MekanismJeiChemicalStackRendererMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/mekanism/MekanismQioNameQueryMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/IndustrialForegoingLaserLensItemMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/IndustrialForegoingRangeAddonItemMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/IndustrialForegoingSpeedAddonItemMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/IndustrialForegoingEfficiencyAddonItemMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/IndustrialForegoingProcessingAddonItemMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ldlib/LdlibTankWidgetFluidTooltipMixin.java"
        )
    } else {
        java.exclude(
            "com/starskyxiii/polyglottooltip/integration/ae2/LegacyAe2RegexSearch.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2SearchPredicatesMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2RepoSearchCacheMixin.java",
            "com/starskyxiii/polyglottooltip/integration/controlling/LegacyControllingSearch.java",
            "com/starskyxiii/polyglottooltip/mixin/controlling/LegacyControllingKeyBindsScreenMixin.java",
            "com/starskyxiii/polyglottooltip/integration/projecte/LegacyProjectESearch.java",
            "com/starskyxiii/polyglottooltip/mixin/projecte/LegacyProjectETransmutationInventoryMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/mekanism/LegacyMekanismJeiChemicalStackRendererMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/mekanism/LegacyMekanismQioNameQueryMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/LegacyIndustrialForegoing*Mixin.java"
        )
    }
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
        val mixinConfig = rootProject.file(
            if (sc.current.parsed <= "1.19.2") {
                "src/forge1192/resources/polyglottooltip.mixins.json"
            } else {
                "src/forge/resources/polyglottooltip.mixins.json"
            }
        )
        exclude {
            it.name == "neoforge.mods.toml" ||
                (it.name == "polyglottooltip.mixins.json" &&
                    it.file != mixinConfig)
        }
        from(rootProject.file("src/forge/resources")) {
            exclude("polyglottooltip.mixins.json")
            val values = mapOf(
                "mod_version" to modVersion,
                "minecraft_range" to project.property("minecraft.range"),
                "fml_major" to project.property("fml.major"),
                "forge_minimum" to project.property("forge.minimum"),
                "resource_pack_format" to project.property("resource_pack_format")
            )
            inputs.properties(values)
            filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
                expand(values)
            }
        }
        from(mixinConfig)
    }
    withType<Jar>().configureEach {
        manifest.attributes("MixinConfigs" to "polyglottooltip.mixins.json")
    }
    named<Jar>("jar") {
        archiveClassifier = "dev"
        destinationDirectory = layout.buildDirectory.dir("intermediates/jars")
    }
    named<Jar>("jarJar") {
        archiveClassifier = "bundled-dev"
        destinationDirectory = layout.buildDirectory.dir("intermediates/jarjar")
    }
    named("compileJava") {
        dependsOn("stonecutterGenerate")
    }
}

val productionJar = renamer.classes(tasks.named<Jar>("jarJar")) {
    mappings(renamer.mixin.generatedMappings)
    output.set(layout.buildDirectory.file(base.archivesName.map { "libs/$it-${project.version}.jar" }))
}

val productionArchive = productionJar.flatMap { it.output }
val requiredBundledLibraries = setOf(
    "com.github.houbb:opencc4j",
    "com.github.houbb:heaven",
    "com.github.houbb:nlp-common",
    "io.github.llamalad7:mixinextras-forge"
)

val verifyProductionJar = tasks.register("verifyProductionJar") {
    group = "verification"
    dependsOn(productionJar)
    inputs.file(productionArchive)
    doLast {
        val archive = productionArchive.get().asFile
        if (!archive.isFile) {
            throw GradleException("Production jar does not exist: $archive")
        }
        ZipFile(archive).use { productionZip ->
            val metadataEntry = productionZip.getEntry("META-INF/jarjar/metadata.json")
                ?: throw GradleException("Production jar is missing META-INF/jarjar/metadata.json")
            val metadata = productionZip.getInputStream(metadataEntry).use {
                JsonSlurper().parse(it) as? Map<*, *>
            } ?: throw GradleException("JarJar metadata root is not an object")
            val jars = metadata["jars"] as? List<*>
                ?: throw GradleException("JarJar metadata does not contain a jars array")
            val declaredLibraries = jars.map { item ->
                val jar = item as? Map<*, *>
                    ?: throw GradleException("JarJar metadata contains a non-object entry")
                val identifier = jar["identifier"] as? Map<*, *>
                    ?: throw GradleException("JarJar metadata entry has no identifier")
                val group = identifier["group"] as? String
                    ?: throw GradleException("JarJar metadata identifier has no group")
                val artifact = identifier["artifact"] as? String
                    ?: throw GradleException("JarJar metadata identifier has no artifact")
                "$group:$artifact" to jar
            }
            val missingLibraries = requiredBundledLibraries - declaredLibraries.map { it.first }.toSet()
            if (missingLibraries.isNotEmpty()) {
                throw GradleException("Production jar is missing bundled libraries: ${missingLibraries.sorted().joinToString()}")
            }
            declaredLibraries.forEach { (coordinate, jar) ->
                val path = jar["path"] as? String
                    ?: throw GradleException("Bundled library $coordinate has no path")
                if (!path.startsWith("META-INF/jarjar/") || path.contains("..") || !path.endsWith(".jar")) {
                    throw GradleException("Bundled library $coordinate has an invalid path: $path")
                }
                val nestedEntry = productionZip.getEntry(path)
                    ?: throw GradleException("Bundled library $coordinate is missing at $path")
                if (nestedEntry.isDirectory) {
                    throw GradleException("Bundled library $coordinate points to a directory: $path")
                }
                productionZip.getInputStream(nestedEntry).use { input ->
                    JarInputStream(input).use { nestedJar ->
                        var entry = nestedJar.nextJarEntry
                        if (nestedJar.manifest == null && entry == null) {
                            throw GradleException("Bundled library $coordinate is not a valid non-empty jar: $path")
                        }
                        val sink = OutputStream.nullOutputStream()
                        while (entry != null) {
                            nestedJar.transferTo(sink)
                            entry = nestedJar.nextJarEntry
                        }
                    }
                }
            }
        }
    }
}

val exportProductionJarPath = tasks.register("exportProductionJarPath") {
    dependsOn(verifyProductionJar)
    inputs.file(productionArchive)
    doLast {
        val archive = productionArchive.get().asFile.absoluteFile
        val githubOutput = System.getenv("GITHUB_OUTPUT")
        if (!githubOutput.isNullOrBlank()) {
            file(githubOutput).appendText("path=${archive.path}${System.lineSeparator()}")
        }
        logger.lifecycle(archive.path)
    }
}

tasks.check {
    dependsOn(verifyProductionJar)
}

tasks.build {
    dependsOn(verifyProductionJar)
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifact(productionJar) {
                builtBy(verifyProductionJar)
            }
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
