plugins {
    id("java-library")
    id("maven-publish")
    id("dev.kikugie.loom-back-compat")
    id("idea")
}

val modId = "polyglottooltip"
val modVersion = "1.6.0"

stonecutter {
    replacements.string(false, "forge_neoforge_package") {
        replace("net.neoforged.neoforge", "net.minecraftforge")
        replace("net.neoforged.bus.api", "net.minecraftforge.eventbus.api")
        replace("net.neoforged.fml", "net.minecraftforge.fml")
        replace("net.neoforged.api", "net.minecraftforge.api")
        replace("ModConfigSpec", "ForgeConfigSpec")
    }
    replacements.string(false, "keybinds_list_package") {
        replace("net.minecraft.client.gui.screens.options.controls.KeyBindsList", "net.minecraft.client.gui.screens.controls.KeyBindsList")
    }
    replacements.string(true, "minecraft_identifier") { replace("ResourceLocation", "Identifier") }
    replacements.string(true, "minecraft_util_package") { replace("import net.minecraft.Util;", "import net.minecraft.util.Util;") }
    replacements.string(true, "ae_key_rendering_package") { replace("appeng.api.client.AEKeyRendering", "appeng.client.api.AEKeyRendering") }
    replacements.string(true, "gui_graphics_extractor") {
        replace("GuiGraphics", "GuiGraphicsExtractor")
        replace("method = \"renderTooltip\"", "method = \"extractHoveredTooltip\"")
    }
    replacements.string(true, "rs_platform_resource") { replace("getResourceForRecipeMods()", "getAutocraftingResource()") }
    replacements.string(true, "rs_fluid_resource") { replace("getResourceForRecipeMods()", "getFluidResource()") }
    replacements.string(true, "ae_fluid_renderer") {
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
    maven("https://jitpack.io") { content { includeGroup("com.github.Towdium") } }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("loader.version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api.version")}")

    compileOnly("mezz.jei:jei-${sc.current.version}-fabric-api:${property("jei.version")}")
    compileOnly("maven.modrinth:controlling:${property("controlling.version")}")
    compileOnly("maven.modrinth:explorers-compass:${property("explorers_compass.version")}")
    compileOnly("maven.modrinth:jade:${property("jade.version")}")
    compileOnly("maven.modrinth:I7k4B65h:${property("jec.version")}")
    compileOnly("maven.modrinth:natures-compass:${property("natures_compass.version")}")
    compileOnly("maven.modrinth:refined-storage:${property("refined_storage.version")}")
    compileOnly("maven.modrinth:searchables:${property("searchables.version")}")
    compileOnly("maven.modrinth:guitPqEi:${property("storage_drawers.version")}")
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.5")
    implementation("io.github.llamalad7:mixinextras-fabric:0.5.5")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.Towdium:PinIn:1.6.0") { exclude(group = "it.unimi.dsi", module = "fastutil") }

    implementation("com.github.houbb:opencc4j:1.14.0")
    include("com.github.houbb:opencc4j:1.14.0")
    include("com.github.houbb:heaven:0.13.0")
    include("com.github.houbb:nlp-common:0.0.5")
    include("io.github.llamalad7:mixinextras-fabric:0.5.5")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

sourceSets.main {
    resources.srcDir(rootProject.file("src/fabric/resources"))
    java.exclude(
        "com/starskyxiii/polyglottooltip/PolyglotTooltipClient.java",
        "com/starskyxiii/polyglottooltip/TooltipHandler.java",
        "com/starskyxiii/polyglottooltip/integration/ae2/**",
        "com/starskyxiii/polyglottooltip/integration/apothicenchanting/**",
        "com/starskyxiii/polyglottooltip/integration/arsnouveau/**",
        "com/starskyxiii/polyglottooltip/integration/emi/**",
        "com/starskyxiii/polyglottooltip/integration/industrialforegoing/**",
        "com/starskyxiii/polyglottooltip/integration/mekanism/**",
        "com/starskyxiii/polyglottooltip/integration/occultism/**",
        "com/starskyxiii/polyglottooltip/integration/productivebees/**",
        "com/starskyxiii/polyglottooltip/search/IntegratedTerminalSearchHelper.java",
        "com/starskyxiii/polyglottooltip/integration/jec/JecLegacySearchStoragePlugin.java",
        "com/starskyxiii/polyglottooltip/mixin/ae2/**",
        "com/starskyxiii/polyglottooltip/mixin/apothicenchanting/**",
        "com/starskyxiii/polyglottooltip/mixin/arsnouveau/**",
        "com/starskyxiii/polyglottooltip/mixin/emi/**",
        "com/starskyxiii/polyglottooltip/mixin/figura/**",
        "com/starskyxiii/polyglottooltip/mixin/gtceu/**",
        "com/starskyxiii/polyglottooltip/mixin/industrialforegoing/**",
        "com/starskyxiii/polyglottooltip/mixin/integratedterminals/**",
        "com/starskyxiii/polyglottooltip/mixin/ldlib/**",
        "com/starskyxiii/polyglottooltip/mixin/mekae2/**",
        "com/starskyxiii/polyglottooltip/mixin/mekanism/**",
        "com/starskyxiii/polyglottooltip/mixin/occultism/**",
        "com/starskyxiii/polyglottooltip/mixin/projecte/**",
        "com/starskyxiii/polyglottooltip/mixin/rs/**",
        "com/starskyxiii/polyglottooltip/mixin/sophisticatedcore/**",
        "com/starskyxiii/polyglottooltip/mixin/jei/JeiFluidHelperMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/JeiListElementInfoMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jei/JeiSafeIngredientUtilMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/jade/JadeGtoCoreCompatMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/EmiScreenManagerTooltipMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/EmiSlotWidgetTooltipMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/emi/EmiTagEmiIngredientMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/rs2/Rs2MekanismChemicalGridResourceMixin.java",
        "com/starskyxiii/polyglottooltip/mixin/rs2/Rs2MekanismChemicalResourceRenderingMixin.java"
    )
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

tasks {
    withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release = 25 }
    test { useJUnitPlatform() }
    processResources {
        val values = mapOf(
            "mod_version" to modVersion,
            "minecraft_version" to sc.current.version,
            "loader_version" to project.property("loader.version"),
            "fabric_api_version" to project.property("fabric_api.version"),
            "java_version" to "25",
            "java_level" to "JAVA_25",
            "fabric_mixins" to
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

publishing { publications { register<MavenPublication>("mavenJava") { from(components["java"]) } } }
idea { module { isDownloadSources = true; isDownloadJavadoc = true } }
