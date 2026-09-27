plugins {
    id("java-library")
    id("maven-publish")
    id("dev.kikugie.loom-back-compat")
    id("idea")
}

val modId = "polyglottooltip"
val modVersion = "2.0"
val requiredJava = if (sc.current.parsed > "1.20.1") 21 else 17

stonecutter {
    for (marker in listOf(
        "minecraft_118_factories",
        "minecraft_116_compound_names",
        "minecraft_116_language_map",
        "minecraft_116_registries",
        "minecraft_116_pack_resource",
        "forge_config_set",
        "jei7_internal_packages",
        "jei_lowmem_internal_package"
    )) {
        replacements.string(true, marker) {
            replace("__polyglot_inactive_replacement__", "__polyglot_inactive_replacement_target__")
        }
    }
    replacements.string(false, "minecraft_116_packages") {
        replace("net.minecraft.network.chat.ITextComponent", "net.minecraft.util.text.ITextComponent")
    }
    replacements.string(true, "minecraft_modern_component_types") {
        replace("IFormattableTextComponent", "MutableComponent")
        replace("TranslationTextComponent", "TranslatableComponent")
        replace("ITextProperties", "FormattedText")
        replace("ITextComponent", "Component")
        replace("TextFormatting", "ChatFormatting")
    }
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
    replacements.string(sc.current.parsed <= "1.20.1", "keybinds_list_package") {
        replace("net.minecraft.client.gui.screens.options.controls.KeyBindsList", "net.minecraft.client.gui.screens.controls.KeyBindsList")
    }
    replacements.string(false, "minecraft_identifier") { replace("ResourceLocation", "Identifier") }
    replacements.string(false, "minecraft_util_package") { replace("import net.minecraft.Util;", "import net.minecraft.util.Util;") }
    replacements.string(false, "ae_key_rendering_package") { replace("appeng.api.client.AEKeyRendering", "appeng.client.api.AEKeyRendering") }
    replacements.string(false, "gui_graphics_extractor") {
        replace("GuiGraphics", "GuiGraphicsExtractor")
        replace("method = \"renderTooltip\"", "method = \"extractHoveredTooltip\"")
    }
    replacements.string(false, "rs_platform_resource") { replace("getResourceForRecipeMods()", "getAutocraftingResource()") }
    replacements.string(false, "rs_fluid_resource") { replace("getResourceForRecipeMods()", "getFluidResource()") }
    replacements.string(false, "ae_fluid_renderer") {
        replace("appeng.init.client.InitStackRenderHandlers${'$'}FluidKeyRenderHandler", "appeng.client.renderer.keytypes.FluidKeyRenderer")
    }
}

group = "com.starskyxiii.polyglottooltip"
version = modVersion
base.archivesName = "$modId-${sc.current.version}-fabric"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.blamejared.com/")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.creeperhost.net/")
    maven("https://jitpack.io") {
        content { includeGroup("com.github.Towdium") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("loader.version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api.version")}")

    modCompileOnly("mezz.jei:jei-${sc.current.version}-fabric-api:${property("jei.version")}")
    modCompileOnly("maven.modrinth:controlling:${property("controlling.version")}")
    modCompileOnly("maven.modrinth:emi:${property("emi.version")}")
    modCompileOnly("maven.modrinth:explorers-compass:${property("explorers_compass.version")}")
    modCompileOnly("maven.modrinth:figura:${property("figura.version")}")
    modCompileOnly("maven.modrinth:jade:${property("jade.version")}")
    modCompileOnly("maven.modrinth:I7k4B65h:${property("jec.version")}")
    modCompileOnly("maven.modrinth:natures-compass:${property("natures_compass.version")}")
    modCompileOnly("maven.modrinth:searchables:${property("searchables.version")}")
    modCompileOnly("maven.modrinth:guitPqEi:${property("storage_drawers.version")}")

    if (sc.current.parsed <= "1.20.1") {
        modCompileOnly("maven.modrinth:ae2:${property("ae2.version")}")
        modCompileOnly("maven.modrinth:extended-ae:${property("extended_ae.version")}")
        modCompileOnly("maven.modrinth:7tG215v7:${property("gtceu.version")}")
        modCompileOnly("maven.modrinth:ldlib:${property("ldlib.version")}")
    } else {
        modCompileOnly("maven.modrinth:refined-storage:${property("refined_storage.version")}")
    }

    compileOnly("io.github.llamalad7:mixinextras-common:0.5.5")
    modImplementation("io.github.llamalad7:mixinextras-fabric:0.5.5")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.Towdium:PinIn:1.6.0") {
        exclude(group = "it.unimi.dsi", module = "fastutil")
    }

    implementation("com.github.houbb:opencc4j:1.14.0")
    include("com.github.houbb:opencc4j:1.14.0")
    include("com.github.houbb:heaven:0.13.0")
    include("com.github.houbb:nlp-common:0.0.5")
    include("io.github.llamalad7:mixinextras-fabric:0.5.5")
}

loom {
    mixin.defaultRefmapName = "polyglottooltip.refmap.json"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(requiredJava)

sourceSets.main {
    resources.srcDir(rootProject.file("src/fabric/resources"))
    java.exclude(
        "com/starskyxiii/polyglottooltip/PolyglotTooltipClient.java",
        "com/starskyxiii/polyglottooltip/TooltipHandler.java",
        "com/starskyxiii/polyglottooltip/integration/apothicenchanting/**",
        "com/starskyxiii/polyglottooltip/integration/arsnouveau/**",
        "com/starskyxiii/polyglottooltip/integration/industrialforegoing/**",
        "com/starskyxiii/polyglottooltip/integration/mekanism/**",
        "com/starskyxiii/polyglottooltip/integration/occultism/**",
        "com/starskyxiii/polyglottooltip/integration/productivebees/**",
        "com/starskyxiii/polyglottooltip/search/IntegratedTerminalSearchHelper.java",
        "com/starskyxiii/polyglottooltip/mixin/apothicenchanting/**",
        "com/starskyxiii/polyglottooltip/mixin/arsnouveau/**",
        "com/starskyxiii/polyglottooltip/mixin/gtceu/**",
        "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/**",
        "com/starskyxiii/polyglottooltip/mixin/integratedterminals/**",
        "com/starskyxiii/polyglottooltip/mixin/mekae2/**",
        "com/starskyxiii/polyglottooltip/mixin/mekanism/**",
        "com/starskyxiii/polyglottooltip/mixin/occultism/**",
        "com/starskyxiii/polyglottooltip/mixin/projecte/**",
        "com/starskyxiii/polyglottooltip/mixin/rs/**",
        "com/starskyxiii/polyglottooltip/mixin/sophisticatedcore/**",
        "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2AssemblerMatrixMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/GtoAe2RepoSearchMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/GtoEmiSearchMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ldlib/GtoModularUiFluidTooltipMixin.java",
        "com/starskyxiii/polyglottooltip/integration/gto/**",
        "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2ExtendedPatternTerminalMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2PatternAccessTermScreenMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/JeiFluidHelperMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/JeiListElementInfoMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/JeiSafeIngredientUtilMixin.java",
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
        "com/starskyxiii/polyglottooltip/mixin/jade/JadeGtoCoreCompatMixin.java",
        "com/starskyxiii/polyglottooltip/integration/jec/JecLegacySearchStoragePlugin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/EmiScreenManagerTooltipMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/EmiSlotWidgetTooltipMixin.java",
        "com/starskyxiii/polyglottooltip/integration/ae2/LegacyAe2InterfaceSearch.java",
        "com/starskyxiii/polyglottooltip/integration/ae2/LegacyAe2RepoRefresh.java",
        "com/starskyxiii/polyglottooltip/integration/jade/LegacyJadeTooltipHelper.java",
        "com/starskyxiii/polyglottooltip/mixin/LegacyMixinExtrasBootstrapPlugin.java",
        "com/starskyxiii/polyglottooltip/mixin/LegacyLanguageManagerMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2EightInterfaceRecordMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2EightInterfaceTerminalMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2FluidRepoMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2ItemRepoMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2ItemStackCacheMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/controlling/LegacyControlling7ScreenMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jade/LegacyJadeBlockNameMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jade/LegacyJadeEntityNameMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei7BeeIngredientRendererMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei7ElementPrefixParserMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei7FluidStackRendererMixin.java"
    )
    if (sc.current.parsed <= "1.20.1") {
        java.exclude("com/starskyxiii/polyglottooltip/integration/rs2/**", "com/starskyxiii/polyglottooltip/mixin/rs2/**")
    } else {
        java.exclude("com/starskyxiii/polyglottooltip/integration/ae2/**", "com/starskyxiii/polyglottooltip/mixin/ae2/**", "com/starskyxiii/polyglottooltip/mixin/ldlib/**")
    }
}

sourceSets.test {
    java.exclude("com/starskyxiii/polyglottooltip/integration/mekanism/MekanismTooltipHelperTest.java")
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava
    }
    test { useJUnitPlatform() }
    processResources {
        val values = mapOf(
            "mod_version" to modVersion,
            "minecraft_version" to sc.current.version,
            "loader_version" to project.property("loader.min_version"),
            "fabric_api_version" to project.property("fabric_api.min_version"),
            "java_version" to requiredJava.toString(),
            "java_level" to "JAVA_$requiredJava",
            "fabric_mixins" to if (sc.current.parsed <= "1.20.1")
                "    \"ae2.Ae2RepoMixin\",\n" +
                "    \"ae2.Ae2RepoSearchMixin\",\n" +
                "    \"ae2.Ae2PatternContainerRecordMixin\",\n" +
                "    \"ae2.FabricAe2PatternAccessSearchMixin\",\n" +
                "    \"ae2.ExtendedAePatternTerminalSearchMixin\",\n" +
                "    \"ae2.Ae2FluidKeyRenderHandlerMixin\",\n" +
                "    \"jei.FabricJeiFluidHelperMixin\",\n" +
                "    \"emi.EmiFluidEmiStackMixin\",\n" +
                "    \"emi.EmiNameQueryMixin\",\n" +
                "    \"emi.EmiTooltipQueryMixin\",\n" +
                "    \"emi.EmiTagEmiIngredientMixin\",\n" +
                "    \"figura.ItemStackHoverNameContextMixin\",\n" +
                "    \"figura.FiguraEmojiTranslatableGuardMixin\",\n" +
                "    \"ldlib.LdlibTankWidgetFluidTooltipMixin\""
            else
                "    \"emi.EmiFluidEmiStackMixin\",\n" +
                "    \"emi.EmiNameQueryMixin\",\n" +
                "    \"emi.EmiTooltipQueryMixin\",\n" +
                "    \"emi.EmiTagEmiIngredientMixin\",\n" +
                "    \"figura.ItemStackHoverNameContextMixin\",\n" +
                "    \"figura.FiguraEmojiTranslatableGuardMixin\",\n" +
                "    \"jei.FabricJeiFluidHelperMixin\",\n" +
                "    \"rs2.Rs2GridQueryParserMixin\",\n" +
                "    \"rs2.Rs2AutocrafterManagerViewGroupMixin\",\n" +
                "    \"rs2.Rs2AutocrafterManagerSubViewGroupMixin\",\n" +
                "    \"rs2.Rs2ItemGridResourceMixin\",\n" +
                "    \"rs2.Rs2FluidGridResourceMixin\",\n" +
                "    \"rs2.Rs2FluidResourceRenderingMixin\",\n" +
                "    \"rs2.Rs2ItemResourceRenderingMixin\""
        )
        inputs.properties(values)
        filesMatching(listOf("fabric.mod.json", "polyglottooltip.mixins.json")) { expand(values) }
        exclude {
            it.name == "mods.toml" || it.name == "neoforge.mods.toml" || it.name == "pack.mcmeta" ||
                (it.name == "polyglottooltip.mixins.json" && it.file != rootProject.file("src/fabric/resources/polyglottooltip.mixins.json"))
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") { from(components["java"]) }
    }
}

idea { module { isDownloadSources = true; isDownloadJavadoc = true } }
