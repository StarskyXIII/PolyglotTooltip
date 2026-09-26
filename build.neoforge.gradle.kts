plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.141"
    id("idea")
}

val modId = "polyglottooltip"
val modVersion = "2.0"
val requiredJava = if (sc.current.parsed >= "26") 25 else 21

stonecutter {
    replacements.string(false, "minecraft_legacy_components") {
        replace("Component.empty()", "new net.minecraft.network.chat.TextComponent(\"\")")
        replace("Component.literal(", "new net.minecraft.network.chat.TextComponent(")
        replace("Component.translatable(", "new net.minecraft.network.chat.TranslatableComponent(")
        replace("Component::translatable", "net.minecraft.network.chat.TranslatableComponent::new")
    }
    replacements.string(false, "jei10_internal_packages") {
        replace("mezz.jei.gui.search.ElementSearchLowMem", "mezz.jei.common.search.ElementSearchLowMem")
    }
    replacements.string(false, "occultism_legacy_package") {
        replace("com.klikli_dev.occultism", "com.github.klikli_dev.occultism")
    }
    replacements.string(false, "ae_key_rendering_legacy") {
        replace("AEKeyRendering", "AEStackRendering")
    }
    replacements.string(false, "minecraft_legacy_registries") {
        replace("import net.minecraft.core.registries.BuiltInRegistries;", "import net.minecraft.core.Registry;")
        replace("BuiltInRegistries.ITEM", "Registry.ITEM")
    }
    replacements.string(false, "forge_neoforge_package") {
        replace("net.neoforged.neoforge", "net.minecraftforge")
        replace("net.neoforged.bus.api", "net.minecraftforge.eventbus.api")
        replace("net.neoforged.fml", "net.minecraftforge.fml")
        replace("net.neoforged.api", "net.minecraftforge.api")
        replace("ModConfigSpec", "ForgeConfigSpec")
    }
    replacements.string(false, "keybinds_list_package") {
        replace(
            "net.minecraft.client.gui.screens.options.controls.KeyBindsList",
            "net.minecraft.client.gui.screens.controls.KeyBindsList"
        )
    }
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
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.Towdium")
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("io.github.llamalad7:mixinextras-common:0.5.5")
    testImplementation("com.github.Towdium:PinIn:1.6.0") {
        exclude(group = "it.unimi.dsi", module = "fastutil")
    }

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
    java.exclude(
        "com/starskyxiii/polyglottooltip/FabricConfigValue.java",
        "com/starskyxiii/polyglottooltip/FabricConfigStore.java",
        "com/starskyxiii/polyglottooltip/FabricPolyglotTooltipClient.java",
        "com/starskyxiii/polyglottooltip/integration/jei/FabricJeiFluidNameHelper.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/FabricJeiFluidHelperMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/FabricJeiListElementInfoMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/FabricAe2PatternAccessSearchMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/ExtendedAePatternTerminalSearchMixin.java",
        "com/starskyxiii/polyglottooltip/integration/FluidTooltipHelper.java",
        "com/starskyxiii/polyglottooltip/integration/ReflectionHelper.java",
        "com/starskyxiii/polyglottooltip/integration/emi/EmiTagNameHelper.java",
        "com/starskyxiii/polyglottooltip/integration/emi/EmiTagPrefixTooltipHelper.java",
        "com/starskyxiii/polyglottooltip/integration/jec/JecLegacySearchStoragePlugin.java",
        "com/starskyxiii/polyglottooltip/integration/rs/**",
        "com/starskyxiii/polyglottooltip/mixin/gtceu/**",
        "com/starskyxiii/polyglottooltip/mixin/ldlib/**",
        "com/starskyxiii/polyglottooltip/mixin/rs/**",
        "com/starskyxiii/polyglottooltip/mixin/jade/JadeGtoCoreCompatMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/EmiScreenManagerTooltipMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/EmiSlotWidgetTooltipMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/EmiTagEmiIngredientMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/mekanism/MekanismGuiChemicalBarMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/mekanism/MekanismGuiChemicalGaugeMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/mekanism/MekanismStorageUtilsMixin.java",
        "com/starskyxiii/polyglottooltip/integration/ae2/LegacyAe2RegexSearch.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2SearchPredicatesMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2RepoSearchCacheMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2InterfaceTerminalScreenMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2InterfaceRecordMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei10ElementPrefixParserMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei10FluidTankRendererMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/LegacyProductiveBeesBeeIngredientRendererMixin.java",
        "com/starskyxiii/polyglottooltip/integration/controlling/LegacyControllingSearch.java",
        "com/starskyxiii/polyglottooltip/mixin/controlling/LegacyControllingKeyBindsScreenMixin.java",
        "com/starskyxiii/polyglottooltip/integration/projecte/LegacyProjectESearch.java",
        "com/starskyxiii/polyglottooltip/mixin/projecte/LegacyProjectETransmutationInventoryMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/mekanism/LegacyMekanismJeiChemicalStackRendererMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/mekanism/LegacyMekanismQioNameQueryMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/LegacyIndustrialForegoing*Mixin.java"
    )
}
sourceSets.test {
    java.exclude("com/starskyxiii/polyglottooltip/FabricConfigStoreTest.java")
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
