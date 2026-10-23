package com.starskyxiii.polyglottooltip.legacy.integration.names;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

public class NameResolversTest {
    private static final String[][] PRIMARY = {
            {"potion.effect.water", "水瓶"},
            {"splash_potion.effect.mundane", "喷溅型平凡的药水"},
            {"lingering_potion.effect.mundane", "滞留型平凡的药水"},
            {"tipped_arrow.effect.night_vision", "夜视之箭"},
            {"item.for.can.empty.name", "罐"},
            {"item.for.can.grammar", "%s罐"},
            {"item.for.refractory.grammar", "耐火%s胶囊"},
            {"item.for.capsule.ice.name", "冰胶囊"},
            {"for.log.grammar", "%TYPE原木"},
            {"for.slab.grammar", "%TYPE台阶"},
            {"for.trees.woodType.larch", "落叶树"},
            {"for.trees.woodType.teak", "柚树"},
            {"tile.for.fireproof", "抗燃%s"},
            {"for.trees.grammar.sapling", "%SPECIES%TYPE"},
            {"for.trees.grammar.sapling.type", "树苗"},
            {"for.trees.species.appleOak", "苹果橡"},
            {"for.bees.grammar.queen", "%SPECIES%TYPE"},
            {"for.bees.grammar.queen.type", "蜂后"},
            {"for.bees.species.forest", "森林"},
            {"for.butterflies.grammar.butterfly", "%SPECIES%TYPE"},
            {"for.butterflies.grammar.butterfly.type", "蝴蝶"},
            {"for.butterflies.species.geometridae.brimstone", "黄蛾"},
            {"ic2.te.itnt", "工业TNT"},
            {"ic2.crushed.copper", "粉碎铜矿石"},
            {"ic2.fluid_cell", "通用流体单元"},
            {"mcr_face.1.name", "%s 薄板"},
            {"extracells.item.fluid.pattern", "ME流體模板"},
    };
    private static final String[][] SECONDARY = {
            {"potion.effect.water", "Water Bottle"},
            {"splash_potion.effect.mundane", "Mundane Splash Potion"},
            {"lingering_potion.effect.mundane", "Mundane Lingering Potion"},
            {"tipped_arrow.effect.night_vision", "Arrow of Night Vision"},
            {"item.for.can.empty.name", "Can"},
            {"item.for.can.grammar", "%s Can"},
            {"item.for.refractory.grammar", "%s Capsule"},
            {"item.for.capsule.ice.name", "Ice Capsule"},
            {"for.log.grammar", "%TYPE Wood"},
            {"for.slab.grammar", "%TYPE Wood Slab"},
            {"for.trees.woodType.larch", "Larch"},
            {"for.trees.woodType.teak", "Teak"},
            {"tile.for.fireproof", "%s (Fireproof)"},
            {"for.trees.grammar.sapling", "%SPECIES %TYPE"},
            {"for.trees.grammar.sapling.type", "Sapling"},
            {"for.trees.species.appleOak", "Apple Oak"},
            {"for.trees.custom.sapling.appleOak", "Oak Sapling"},
            {"for.bees.grammar.queen", "%SPECIES %TYPE"},
            {"for.bees.grammar.queen.type", "Queen"},
            {"for.bees.species.forest", "Forest"},
            {"for.butterflies.grammar.butterfly", "%SPECIES %TYPE"},
            {"for.butterflies.grammar.butterfly.type", "Butterfly"},
            {"for.butterflies.species.geometridae.brimstone", "Brimstone Moth"},
            {"ic2.te.itnt", "Industrial TNT"},
            {"ic2.crushed.copper", "Crushed Copper Ore"},
            {"ic2.fluid_cell", "Universal Fluid Cell"},
            {"mcr_face.1.name", "%s Cover"},
            {"extracells.item.fluid.pattern", "ME Fluid Pattern"},
    };

    private final Function<String, String> primary = lang(PRIMARY);
    private final Function<String, String> secondary = lang(SECONDARY);

    private static Function<String, String> lang(String[][] entries) {
        Map<String, String> map = new HashMap<>();
        for (String[] entry : entries) {
            map.put(entry[0], entry[1]);
        }
        return map::get;
    }

    @Test
    public void potionKeysUseTheItemClassPrefix() {
        assertEquals("potion.effect.", PotionNameResolver.prefix("net.minecraft.item.ItemPotion"));
        assertEquals("splash_potion.effect.", PotionNameResolver.prefix("net.minecraft.item.ItemSplashPotion"));
        assertEquals("lingering_potion.effect.", PotionNameResolver.prefix("net.minecraft.item.ItemLingeringPotion"));
        assertEquals("tipped_arrow.effect.", PotionNameResolver.prefix("net.minecraft.item.ItemTippedArrow"));
        assertNull(PotionNameResolver.prefix("net.minecraft.item.ItemArrow"));
        assertEquals("Water Bottle", PotionNameResolver.name("potion.effect.water", "水瓶", primary, secondary));
        assertEquals("Mundane Splash Potion",
                PotionNameResolver.name("splash_potion.effect.mundane", "喷溅型平凡的药水", primary, secondary));
        assertEquals("Mundane Lingering Potion",
                PotionNameResolver.name("lingering_potion.effect.mundane", "滞留型平凡的药水", primary, secondary));
        assertEquals("Arrow of Night Vision",
                PotionNameResolver.name("tipped_arrow.effect.night_vision", "夜视之箭", primary, secondary));
    }

    @Test
    public void potionGivesUpWhenThePrimaryNameDiffersOrTheKeyIsMissing() {
        assertNull(PotionNameResolver.name("potion.effect.water", "滞留型水瓶", primary, secondary));
        assertNull(PotionNameResolver.name("potion.effect.unknown", "未知药水", primary, secondary));
        assertNull(PotionNameResolver.name("potion.effect.water", "水瓶", primary, key -> null));
    }

    @Test
    public void forestryContainersUseTheEmptyExactOrGrammarKey() {
        assertEquals("Can", ForestryContainerNameResolver.name("can", null, null, null, "罐", primary, secondary));
        assertEquals("Water Can",
                ForestryContainerNameResolver.name("can", "water", "水", "Water", "水罐", primary, secondary));
        assertEquals("Lava Capsule", ForestryContainerNameResolver.name("refractory", "lava", "熔岩", "Lava",
                "耐火熔岩胶囊", primary, secondary));
        assertEquals("Ice Capsule",
                ForestryContainerNameResolver.name("capsule", "ice", "冰", "Ice", "冰胶囊", primary, secondary));
        assertNull(ForestryContainerNameResolver.name("can", "water", "水", null, "水罐", primary, secondary));
        assertNull(ForestryContainerNameResolver.name("can", "water", "水", "Water", "熔岩罐", primary, secondary));
    }

    @Test
    public void forestryWoodUsesGrammarAndFireproofKeys() {
        ForestryWoodNameResolver.Wood larchLog = new ForestryWoodNameResolver.Wood("log", "larch", false);
        assertEquals("Larch Wood", ForestryWoodNameResolver.name(larchLog, "落叶树原木", primary, secondary));
        assertEquals("Teak Wood Slab", ForestryWoodNameResolver.name(
                new ForestryWoodNameResolver.Wood("slab", "teak", false), "柚树台阶", primary, secondary));
        assertEquals("Larch Wood (Fireproof)", ForestryWoodNameResolver.name(
                new ForestryWoodNameResolver.Wood("log", "larch", true), "抗燃落叶树原木", primary, secondary));
        assertNull(ForestryWoodNameResolver.name(larchLog, "柚树原木", primary, secondary));
        assertNull(ForestryWoodNameResolver.name(
                new ForestryWoodNameResolver.Wood("log", "acacia", false), "金合欢树原木", primary, secondary));
        assertTrue(ForestryWoodNameResolver.ITEM_CLASSES.contains("forestry.arboriculture.items.ItemBlockWoodSlab"));
    }

    @Test
    public void forestryGeneticItemsUseSpeciesAndTypeKeys() {
        assertEquals("trees", ForestryGeneticNameResolver.group("forestry.arboriculture.items.ItemGermlingGE"));
        assertEquals("bees", ForestryGeneticNameResolver.group("forestry.apiculture.items.ItemBeeGE"));
        assertEquals("butterflies",
                ForestryGeneticNameResolver.group("forestry.lepidopterology.items.ItemButterflyGE"));
        assertEquals("Oak Sapling", ForestryGeneticNameResolver.name("trees", "sapling",
                "for.trees.species.appleOak", "苹果橡树苗", primary, secondary));
        assertEquals("Forest Queen", ForestryGeneticNameResolver.name("bees", "queen",
                "for.bees.species.forest", "森林蜂后", primary, secondary));
        assertEquals("Brimstone Moth Butterfly", ForestryGeneticNameResolver.name("butterflies", "butterfly",
                "for.butterflies.species.geometridae.brimstone", "黄蛾蝴蝶", primary, secondary));
        assertNull(ForestryGeneticNameResolver.name("bees", "queen", "for.bees.species.forest", "草原蜂后",
                primary, secondary));
        assertNull(ForestryGeneticNameResolver.name("bees", "drone", "for.bees.species.forest", "森林雄蜂",
                primary, secondary));
    }

    @Test
    public void ic2LangFilesArePrefixedLikeIc2LoadsThem() throws IOException {
        Map<String, String> entries = new HashMap<>();
        Ic2NameResolver.parse(new StringReader("te.itnt = Industrial TNT\nitemGroup.IC2=IndustrialCraft 2\n"
                + "achievement.ic2.x=X\n# comment\n"), entries::put);
        assertEquals("Industrial TNT", entries.get("ic2.te.itnt"));
        assertEquals("IndustrialCraft 2", entries.get("itemGroup.IC2"));
        assertEquals("X", entries.get("achievement.ic2.x"));
        assertEquals(3, entries.size());
    }

    @Test
    public void ic2NamesUseTheUnlocalizedNameAsTheKey() {
        assertEquals("Industrial TNT", Ic2NameResolver.name("ic2.te.itnt", "工业TNT", primary, secondary));
        assertEquals("Crushed Copper Ore",
                Ic2NameResolver.name("ic2.crushed.copper", "粉碎铜矿石", primary, secondary));
        assertEquals("Universal Fluid Cell",
                Ic2NameResolver.name("ic2.fluid_cell", "通用流体单元", primary, secondary));
        assertNull(Ic2NameResolver.name("ic2.te.nuke", "核弹", primary, secondary));
        assertNull(Ic2NameResolver.name("ic2.te.itnt", "核弹", primary, secondary));
        assertNull(Ic2NameResolver.name(null, "核弹", primary, secondary));
    }

    @Test
    public void microblockNamesFormatTheMaterialIntoTheFactoryKey() {
        assertEquals("mcr_face.1.name", MicroblockNameResolver.key("mcr_face", 1));
        assertEquals("Stone Cover", MicroblockNameResolver.name("mcr_face.1.name", "石頭", "Stone", "石頭 薄板",
                primary, secondary));
        assertNull(MicroblockNameResolver.name("mcr_face.1.name", "石頭", null, "石頭 薄板", primary, secondary));
        assertNull(MicroblockNameResolver.name("mcr_face.1.name", "花崗岩", "Granite", "石頭 薄板",
                primary, secondary));
        assertNull(MicroblockNameResolver.name("mcr_face.2.name", "石頭", "Stone", "石頭 面板", primary, secondary));
    }

    @Test
    public void extraCellsPatternsAppendTheFluidName() {
        assertEquals("ME Fluid Pattern", ExtraCellsNameResolver.name("extracells.item.fluid.pattern", null, null,
                "ME流體模板", primary, secondary));
        assertEquals("ME Fluid Pattern: Water", ExtraCellsNameResolver.name("extracells.item.fluid.pattern",
                "水", "Water", "ME流體模板: 水", primary, secondary));
        assertNull(ExtraCellsNameResolver.name("extracells.item.fluid.pattern", "水", null, "ME流體模板: 水",
                primary, secondary));
        assertNull(ExtraCellsNameResolver.name("extracells.item.fluid.pattern", "熔岩", "Lava", "ME流體模板: 水",
                primary, secondary));
    }
}
