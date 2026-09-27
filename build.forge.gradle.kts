import groovy.json.JsonSlurper
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.OutputStream
import java.util.jar.JarInputStream
import java.util.zip.ZipFile

plugins {
    id("java-library")
    id("maven-publish")
    id("net.minecraftforge.gradle") version "7.0.36"
    id("net.minecraftforge.renamer") version "1.1.7"
    id("net.minecraftforge.jarjar") version "0.2.3"
    id("com.gradleup.shadow") version "9.4.0"
    id("idea")
}

val legacyShade = configurations.create("legacyShade")

val modId = "polyglottooltip"
val modVersion = "2.0"

stonecutter {
    replacements.string(false, "minecraft_identifier") {
        replace("ResourceLocation", "Identifier")
    }
    replacements.string(sc.current.version == "1.16.5", "minecraft_util_package") {
        replace("import net.minecraft.Util;", "import net.minecraft.util.Util;")
    }
    replacements.string(false, "ae_key_rendering_package") {
        replace("appeng.api.client.AEKeyRendering", "appeng.client.api.AEKeyRendering")
    }
    replacements.string(sc.current.parsed <= "1.19.2", "ae_key_rendering_legacy") {
        replace("AEKeyRendering", "AEStackRendering")
    }
    replacements.string(sc.current.version == "1.18.2" || sc.current.version == "1.19.2", "minecraft_legacy_registries") {
        replace("import net.minecraft.core.registries.BuiltInRegistries;", "import net.minecraft.core.Registry;")
        replace("BuiltInRegistries.ITEM", "Registry.ITEM")
    }
    replacements.string(sc.current.version == "1.18.2", "minecraft_legacy_components") {
        replace("Component.empty()", "new net.minecraft.network.chat.TextComponent(\"\")")
        replace("Component.literal(", "new net.minecraft.network.chat.TextComponent(")
        replace("Component.translatable(", "new net.minecraft.network.chat.TranslatableComponent(")
        replace("Component::translatable", "net.minecraft.network.chat.TranslatableComponent::new")
    }
    replacements.string(sc.current.version == "1.18.2", "minecraft_118_factories") {
        replace("Component.empty()", "new net.minecraft.network.chat.TextComponent(\"\")")
        replace("Component.literal(", "new net.minecraft.network.chat.TextComponent(")
        replace("Component.translatable(", "new net.minecraft.network.chat.TranslatableComponent(")
        replace("Component::translatable", "net.minecraft.network.chat.TranslatableComponent::new")
    }
    replacements.string(sc.current.version == "1.16.5", "minecraft_116_packages") {
        replace("Component.empty()", "new net.minecraft.util.text.StringTextComponent(\"\")")
        replace("Component.literal(", "new net.minecraft.util.text.StringTextComponent(")
        replace("Component.translatable(", "new net.minecraft.util.text.TranslationTextComponent(")
        replace("Component::translatable", "net.minecraft.util.text.TranslationTextComponent::new")
        replace("net.minecraft.network.chat.TranslatableComponent", "net.minecraft.util.text.TranslationTextComponent")
        replace("net.minecraft.network.chat.MutableComponent", "net.minecraft.util.text.IFormattableTextComponent")
        replace("net.minecraft.network.chat.FormattedText", "net.minecraft.util.text.ITextProperties")
        replace("net.minecraft.network.chat.Component", "net.minecraft.util.text.ITextComponent")
        replace("net/minecraft/network/chat/Component", "net/minecraft/util/text/ITextComponent")
        replace("net.minecraft.network.chat.Style", "net.minecraft.util.text.Style")
        replace("net.minecraft.ChatFormatting", "net.minecraft.util.text.TextFormatting")
        replace("net.minecraft.resources.ResourceLocation", "net.minecraft.util.ResourceLocation")
        replace("net.minecraft.world.item.enchantment", "net.minecraft.enchantment")
        replace("net.minecraft.world.item.ItemStack", "net.minecraft.item.ItemStack")
        replace("net/minecraft/world/item/ItemStack", "net/minecraft/item/ItemStack")
        replace("net.minecraft.world.item.TooltipFlag", "net.minecraft.client.util.ITooltipFlag")
        replace("net.minecraft.world.item.DyeColor", "net.minecraft.item.DyeColor")
        replace("net.minecraft.world.item.Item", "net.minecraft.item.Item")
        replace("net.minecraft.world.entity.player.Player", "net.minecraft.entity.player.PlayerEntity")
        replace("net.minecraft.world.entity.item.ItemEntity", "net.minecraft.entity.item.ItemEntity")
        replace("net.minecraft.world.entity.EntityType", "net.minecraft.entity.EntityType")
        replace("net.minecraft.world.entity.Entity", "net.minecraft.entity.Entity")
        replace("net.minecraft.world.level.block.LiquidBlock", "net.minecraft.block.FlowingFluidBlock")
        replace("net.minecraft.world.level.block.Block", "net.minecraft.block.Block")
        replace("net.minecraft.world.level.material.FluidState", "net.minecraft.fluid.FluidState")
        replace("net.minecraft.world.level.material.Fluid", "net.minecraft.fluid.Fluid")
        replace("net.minecraft.world.level.Level", "net.minecraft.world.World")
        replace("net/minecraft/world/level/Level", "net/minecraft/world/World")
        replace("net/minecraft/world/level/biome/Biome", "net/minecraft/world/biome/Biome")
        replace("net.minecraft.client.gui.screens.options.controls.KeyBindsList", "net.minecraft.client.gui.widget.list.KeyBindingList")
        replace("net.minecraft.client.gui.components.EditBox", "net.minecraft.client.gui.widget.TextFieldWidget")
        replace("net.minecraft.client.gui.screens.Screen", "net.minecraft.client.gui.screen.Screen")
        replace("net.minecraft.client.gui.Font", "net.minecraft.client.gui.FontRenderer")
        replace("net.minecraft.client.KeyMapping", "net.minecraft.client.settings.KeyBinding")
        replace("net.minecraft.core.Registry", "net.minecraft.util.registry.Registry")
        replace("net.minecraft.nbt.CompoundTag", "net.minecraft.nbt.CompoundNBT")
        replace("net.minecraft.nbt.Tag", "net.minecraft.nbt.INBT")
        replace("net.minecraft.server.packs.resources.PreparableReloadListener", "net.minecraft.resources.IFutureReloadListener")
        replace("net.minecraft.server.packs.resources.ResourceManager", "net.minecraft.resources.IResourceManager")
        replace("net.minecraft.server.packs.resources.Resource", "net.minecraft.resources.IResource")
        replace("net.minecraft.server.packs.PackResources", "net.minecraft.resources.IResourcePack")
        replace("net.minecraft.server.packs.metadata.MetadataSectionSerializer", "net.minecraft.resources.data.IMetadataSectionSerializer")
        replace("net.minecraft.util.profiling.ProfilerFiller", "net.minecraft.profiler.IProfiler")
        replace("net.minecraft.world.level.biome.Biome", "net.minecraft.world.biome.Biome")
        replace("com.mojang.blaze3d.vertex.PoseStack", "com.mojang.blaze3d.matrix.MatrixStack")
    }
    replacements.regex(sc.current.version == "1.16.5", "minecraft_116_packages") {
        replace("""(?<![.\w])Component\b""", "ITextComponent", """(?<![.\w])ITextComponent\b""", "Component")
        replace("""(?<![.\w])MutableComponent\b""", "IFormattableTextComponent", """(?<![.\w])IFormattableTextComponent\b""", "MutableComponent")
        replace("""(?<![.\w])TranslatableComponent\b""", "TranslationTextComponent", """(?<![.\w])TranslationTextComponent\b""", "TranslatableComponent")
        replace("""(?<![.\w])FormattedText\b""", "ITextProperties", """(?<![.\w])ITextProperties\b""", "FormattedText")
        replace("""(?<![.\w])ChatFormatting\b""", "TextFormatting", """(?<![.\w])TextFormatting\b""", "ChatFormatting")
        replace("""(?<![.\w])KeyMapping\b""", "KeyBinding", """(?<![.\w])KeyBinding\b""", "KeyMapping")
        replace("""(?<![.\w])KeyBindsList\b""", "KeyBindingList", """(?<![.\w])KeyBindingList\b""", "KeyBindsList")
        replace("""(?<![.\w])EditBox\b""", "TextFieldWidget", """(?<![.\w])TextFieldWidget\b""", "EditBox")
        replace("""(?<![.\w])TooltipFlag\b""", "ITooltipFlag", """(?<![.\w])ITooltipFlag\b""", "TooltipFlag")
        replace("""(?<![.\w])PoseStack\b""", "MatrixStack", """(?<![.\w])MatrixStack\b""", "PoseStack")
        replace("""(?<![.\w])Level\b""", "World", """(?<![.\w])World\b""", "Level")
        replace("""(?<![.\w])PreparableReloadListener\b""", "IFutureReloadListener", """(?<![.\w])IFutureReloadListener\b""", "PreparableReloadListener")
        replace("""(?<![.\w])ResourceManager\b""", "IResourceManager", """(?<![.\w])IResourceManager\b""", "ResourceManager")
        replace("""(?<![.\w])Resource\b""", "IResource", """(?<![.\w])IResource\b""", "Resource")
        replace("""(?<![.\w])ProfilerFiller\b""", "IProfiler", """(?<![.\w])IProfiler\b""", "ProfilerFiller")
        replace("""(?<![.\w])PreparationBarrier\b""", "IStage", """(?<![.\w])IStage\b""", "PreparationBarrier")
    }
    replacements.string(sc.current.version == "1.16.5", "minecraft_116_registries") {
        replace("import net.minecraft.core.registries.BuiltInRegistries;", "import net.minecraft.util.registry.Registry;")
        replace("BuiltInRegistries.ITEM", "Registry.ITEM")
    }
    replacements.string(sc.current.version == "1.16.5", "jei_lowmem_internal_package") {
        replace("mezz.jei.gui.search.ElementSearchLowMem", "mezz.jei.search.ElementSearchLowMem")
    }
    replacements.string(sc.current.version == "1.16.5", "jei7_internal_packages") {
        replace("mezz.jei.core.search.suffixtree.GeneralizedSuffixTree", "mezz.jei.search.suffixtree.GeneralizedSuffixTree")
    }
    replacements.string(true, "minecraft_116_compound_names") {
        replace("ITextComponentLanguageResolver", "ComponentLanguageResolver")
        replace("ITooltipFlags", "TooltipFlags")
        replace("InMemoryIResourceManager", "InMemoryResourceManager")
        replace("getIResource", "getResource")
        replace("getIResources", "getResources")
        replace("hasIResource", "hasResource")
        replace("listIResources", "listResources")
        replace("TerminalStorageTabIngredientITextComponentClient", "TerminalStorageTabIngredientComponentClient")
        replace("getTextITextComponent", "getTextComponent")
    }
    replacements.regex(sc.current.version == "1.16.5", "minecraft_116_pack_resource") {
        replace("""\bPackResources\b""", "IResourcePack", """\bIResourcePack\b""", "PackResources")
    }
    replacements.string(sc.current.version == "1.16.5", "minecraft_116_language_map") {
        replace("net.minecraft.client.resources.language.ClientLanguage", "net.minecraft.client.resources.ClientLanguageMap")
    }
    replacements.regex(sc.current.version == "1.16.5", "minecraft_116_language_map") {
        replace("""(?<![.\w])ClientLanguage\b""", "ClientLanguageMap", """(?<![.\w])ClientLanguageMap\b""", "ClientLanguage")
    }
    replacements.string(sc.current.version == "1.16.5", "forge_config_set") {
        replace("Config.SPEC.acceptConfig(config);", "Config.SPEC.setConfig(config);")
    }
    replacements.string(sc.current.version == "1.18.2", "jei_lowmem_internal_package") {
        replace("mezz.jei.gui.search.ElementSearchLowMem", "mezz.jei.common.search.ElementSearchLowMem")
    }
    replacements.string(sc.current.parsed <= "1.18.2", "occultism_legacy_package") {
        replace("com.klikli_dev.occultism", "com.github.klikli_dev.occultism")
        replace("com/klikli_dev/occultism", "com/github/klikli_dev/occultism")
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

    if (sc.current.version == "1.16.5") {
        compileOnly(renamer.dependency("appeng:appliedenergistics2:${property("ae2.version")}"))
        compileOnly(renamer.dependency("mezz.jei:jei-${sc.current.version}:${property("jei.version")}"))
    } else {
        compileOnly(renamer.dependency("appeng:appliedenergistics2-forge:${property("ae2.version")}"))
        compileOnly(renamer.dependency("mezz.jei:jei-${sc.current.version}-common-api:${property("jei.version")}"))
        compileOnly(renamer.dependency("mezz.jei:jei-${sc.current.version}-forge-api:${property("jei.version")}"))
    }
    if (sc.current.version == "1.18.2") {
        compileOnly(renamer.dependency("mezz.jei:jei-${sc.current.version}-forge:${property("jei.version")}"))
    }
    if (sc.current.parsed >= "1.18.2") {
        compileOnly(renamer.dependency("com.blamejared.searchables:Searchables-common-${sc.current.version}:${property("searchables.version")}"))
    }
    compileOnly(renamer.dependency("maven.modrinth:jade:${property("jade.version")}"))

    val opencc4jVersion = "1.14.0"
    implementation("com.github.houbb:opencc4j:$opencc4jVersion")
    if (sc.current.version == "1.16.5") {
        legacyShade("com.github.houbb:opencc4j:$opencc4jVersion") { isTransitive = false }
        legacyShade("com.github.houbb:heaven:0.13.0") { isTransitive = false }
        legacyShade("com.github.houbb:nlp-common:0.0.5") { isTransitive = false }
    } else {
        add("jarJar", "com.github.houbb:opencc4j:$opencc4jVersion")
        add("jarJar", "com.github.houbb:heaven:0.13.0")
        add("jarJar", "com.github.houbb:nlp-common:0.0.5")
    }

    val mixinExtrasVersion = "0.5.5"
    compileOnly("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")
    annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")
    testRuntimeOnly("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")
    if (sc.current.version == "1.16.5") {
        legacyShade("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion") { isTransitive = false }
    } else {
        add("jarJar", "io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")
    }
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
    if (sc.current.version == "1.16.5") {
        java.exclude(
            "com/starskyxiii/polyglottooltip/integration/ae2/Ae2SearchPredicate.java",
            "com/starskyxiii/polyglottooltip/integration/ae2/Ae2TooltipSearchPredicate.java",
            "com/starskyxiii/polyglottooltip/integration/ae2/LegacyAe2RegexSearch.java",
            "com/starskyxiii/polyglottooltip/integration/ae2/PatternAccessSearchUtil.java",
            "com/starskyxiii/polyglottooltip/integration/emi/**",
            "com/starskyxiii/polyglottooltip/integration/apothicenchanting/**",
            "com/starskyxiii/polyglottooltip/integration/controlling/ControllingSearchUtil.java",
            "com/starskyxiii/polyglottooltip/integration/jade/JadeBlockNameProvider.java",
            "com/starskyxiii/polyglottooltip/integration/jade/JadeEntityNameProvider.java",
            "com/starskyxiii/polyglottooltip/integration/jade/PolyglotTooltipJadePlugin.java",
            "com/starskyxiii/polyglottooltip/integration/jei/OptionalEmiSearchRefresh.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2AssemblerMatrixMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2ExtendedPatternTerminalMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2FluidKeyRenderHandlerMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2InterfaceRecordMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2InterfaceTerminalScreenMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2RepoSearchCacheMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2SearchPredicatesMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/emi/**",
            "com/starskyxiii/polyglottooltip/mixin/apothicenchanting/**",
            "com/starskyxiii/polyglottooltip/mixin/jei/JeiBakedSubstringIndexBuilderMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei10ElementPrefixParserMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei10FluidTankRendererMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/LegacyProductiveBeesBeeIngredientRendererMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/mekae2/AmChemicalStackRendererMixin.java"
        )
    } else {
        java.exclude(
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
    }
    if (sc.current.parsed <= "1.18.2") {
        java.exclude(
            "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2PatternAccessTermScreenMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/Ae2PatternContainerRecordMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/JeiListElementInfoMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/JeiFluidHelperMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/JeiSafeIngredientUtilMixin.java",
            "com/starskyxiii/polyglottooltip/integration/jec/JecLegacySearchStoragePlugin.java",
            "com/starskyxiii/polyglottooltip/mixin/jade/JadeGtoCoreCompatMixin.java"
        )
    } else {
        java.exclude(
            "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2InterfaceTerminalScreenMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/ae2/LegacyAe2InterfaceRecordMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei10ElementPrefixParserMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/LegacyJei10FluidTankRendererMixin.java",
            "com/starskyxiii/polyglottooltip/mixin/jei/LegacyProductiveBeesBeeIngredientRendererMixin.java"
        )
    }
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
    if (sc.current.version == "1.16.5") {
        java.exclude("com/starskyxiii/polyglottooltip/integration/jei/OptionalEmiSearchRefreshTest.java")
    }
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 17
    }
    named<JavaCompile>("compileJava") {
        options.release = if (sc.current.version == "1.16.5") 8 else 17
    }
    test {
        useJUnitPlatform()
    }
    processResources {
        val mixinConfig = rootProject.file(
            if (sc.current.version == "1.16.5") {
                "src/forge1165/resources/polyglottooltip.mixins.json"
            } else if (sc.current.version == "1.18.2") {
                "src/forge1182/resources/polyglottooltip.mixins.json"
            } else if (sc.current.parsed <= "1.19.2") {
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

val legacyShadowJar = tasks.register<ShadowJar>("legacyShadowJar") {
    archiveClassifier = "shaded-dev"
    destinationDirectory = layout.buildDirectory.dir("intermediates/shadow")
    from(sourceSets.main.get().output)
    from(layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json")) {
        rename { "polyglottooltip.refmap.json" }
    }
    configurations = listOf(legacyShade)
    mergeServiceFiles()
    relocate("com.github.houbb.opencc4j", "com.starskyxiii.polyglottooltip.shadow.opencc4j")
    relocate("com.github.houbb.heaven", "com.starskyxiii.polyglottooltip.shadow.heaven")
    relocate("com.github.houbb.nlp", "com.starskyxiii.polyglottooltip.shadow.nlp")
    relocate("com.llamalad7.mixinextras", "com.starskyxiii.polyglottooltip.shadow.mixinextras")
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    manifest.attributes("MixinConfigs" to "polyglottooltip.mixins.json")
}

tasks.named<ShadowJar>("shadowJar") {
    enabled = false
    destinationDirectory = layout.buildDirectory.dir("intermediates/shadow-default")
}

val productionInput = if (sc.current.version == "1.16.5") {
    legacyShadowJar
} else {
    tasks.named<Jar>("jarJar")
}

val productionJar = renamer.classes(productionInput) {
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
            if (sc.current.version == "1.16.5") {
                val requiredEntries = listOf(
                    "com/starskyxiii/polyglottooltip/mixin/LegacyMixinExtrasBootstrapPlugin.class",
                    "com/starskyxiii/polyglottooltip/shadow/mixinextras/MixinExtrasBootstrap.class",
                    "com/starskyxiii/polyglottooltip/shadow/opencc4j/core/impl/ZhConvertBootstrap.class",
                    "LICENSE_MixinExtras",
                    "polyglottooltip.mixins.json",
                    "polyglottooltip.refmap.json"
                )
                val missingEntries = requiredEntries.filter { productionZip.getEntry(it) == null }
                if (missingEntries.isNotEmpty()) {
                    throw GradleException("Production jar is missing shaded entries: ${missingEntries.joinToString()}")
                }
                if (productionZip.getEntry("META-INF/jarjar/metadata.json") != null) {
                    throw GradleException("Legacy production jar must not depend on JarJar metadata")
                }
                val nestedJars = productionZip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".jar") }
                    .map { it.name }
                    .toList()
                if (nestedJars.isNotEmpty()) {
                    throw GradleException("Legacy production jar contains nested jars: ${nestedJars.joinToString()}")
                }
                productionZip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { entry ->
                        productionZip.getInputStream(entry).use { input ->
                            val header = ByteArray(8)
                            if (input.read(header) != header.size) {
                                throw GradleException("Truncated class file: ${entry.name}")
                            }
                            val major = ((header[6].toInt() and 0xff) shl 8) or (header[7].toInt() and 0xff)
                            if (major > 52) {
                                throw GradleException("Class ${entry.name} requires class-file version $major")
                            }
                        }
                    }
                return@use
            }
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
