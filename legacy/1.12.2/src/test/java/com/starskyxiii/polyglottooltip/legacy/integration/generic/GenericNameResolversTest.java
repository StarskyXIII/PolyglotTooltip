package com.starskyxiii.polyglottooltip.legacy.integration.generic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import com.starskyxiii.polyglottooltip.legacy.minecraft.ResolvedName;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;

public class GenericNameResolversTest {
    private static final String[][] PRIMARY = {
            {"tile.stone.stone.name", "石头"},
            {"tile.stone.granite.name", "花岗岩"},
            {"tile.water.name", "水"},
            {"fluid.tile.water", "水"},
            {"fluid.tconstruct.iron.name", "熔融铁"},
            {"material.electrical_steel.name", "磁钢"},
            {"material.energetic_alloy.name", "充能合金"},
            {"entity.WitherSkeleton.name", "凋灵骷髅"},
            {"info.cofh.empty", "空"},
            {"item.amethyst.name", "紫水晶"},
            {"spell.ebwizardry:magic_missile", "魔法彈"},
            {"tile.compressed.cobblestone.name", "%s Cobblestone"},
            {"tile.styled.name", "§e石头 "},
            {"gui.page.first", "0"},
            {"tile.thermalfoundation.ore.copper", "铜矿石"},
            {"tile.thermalexpansion.machine.furnace", "红石炉"},
            {"info.thermalexpansion.level.basic", "基础"},
    };
    private static final String[][] SECONDARY = {
            {"tile.stone.stone.name", "Stone"},
            {"tile.stone.granite.name", "Granite"},
            {"tile.water.name", "Water"},
            {"fluid.tile.water", "Fluid Water"},
            {"fluid.tconstruct.iron.name", "Molten Iron"},
            {"material.electrical_steel.name", "Electrical Steel"},
            {"material.energetic_alloy.name", "Energetic Alloy"},
            {"entity.WitherSkeleton.name", "Wither Skeleton"},
            {"entity.Bat.name", "Bat"},
            {"info.cofh.empty", "Empty"},
            {"item.amethyst.name", "Amethyst"},
            {"spell.ebwizardry:magic_missile", "Magic Missile"},
            {"item.appliedenergistics2.facade.name", "Cable Facade"},
            {"item.tconstruct.pick_head.name", "Pickaxe Head"},
            {"item.tconstruct.crossbow.name", "Crossbow"},
            {"item.mod.chiselsandbits.block_bit.name", "Chiseled Bit"},
            {"tile.openblocks.tank.name", "Tank"},
            {"item.thermalexpansion.florb.standard.name", "Florb"},
            {"item.thermalexpansion.morb.standard.name", "Morb"},
            {"item.monsterPlacer.name", "Spawn"},
            {"item.thermaldynamics.cover.name", "%s Cover"},
            {"item.forge.bucketFilled.name", "%s Bucket"},
            {"item.jaopca.dust.name", "%s Dust"},
            {"tile.jaopca.block.name", "Block of %s"},
            {"item.ebwizardry:scroll.name", "Scroll of %1$s"},
            {"item.gendustry.gene_sample.name", "Gene Sample - %s - %s"},
            {"tile.compressed.cobblestone.name", "%s Cobblestone"},
            {"item.potion.name", "Potion"},
            {"gui.page.first", "4"},
            {"item.openblocks.glyph.name", "'%s' Glyph"},
            {"tile.thermalfoundation.ore.copper", "Copper Ore"},
            {"tile.thermalexpansion.machine.furnace", "Redstone Furnace"},
            {"info.thermalexpansion.level.basic", "Basic"},
    };

    private static final String[][] COMPOSITE_SAMPLES = {
            {"AE2 facade", "item.appliedenergistics2.facade.name", "线缆伪装板", "线缆伪装板 - 石头", "Cable Facade - Stone", ""},
            {"Tinkers ToolPart", "item.tconstruct.pick_head.name", "镐头", "磁钢 镐头", "Electrical Steel Pickaxe Head", ""},
            {"Tinkers tool", "item.tconstruct.crossbow.name", "十字弓", "充能合金 十字弓", "Energetic Alloy Crossbow", ""},
            {"Tinkers unknown material", "item.tconstruct.pick_head.name", "镐头", "Electrical 镐头", "Electrical Pickaxe Head", "Electrical"},
            {"Chisels & Bits", "item.mod.chiselsandbits.block_bit.name", "雕刻碎屑", "雕刻碎屑 - 花岗岩", "Chiseled Bit - Granite", ""},
            {"OpenBlocks tank", "tile.openblocks.tank.name", "蓄水槽", "水蓄水槽", "Water Tank", ""},
            {"TE florb", "item.thermalexpansion.florb.standard.name", "流体球", "流体球 (水)", "Florb (Water)", ""},
            {"TE empty florb", "item.thermalexpansion.florb.standard.name", "流体球", "流体球 (空)", "Florb (Empty)", ""},
            {"TE morb", "item.thermalexpansion.morb.standard.name", "生物球", "生物球 (凋灵骷髅)", "Morb (Wither Skeleton)", ""},
            {"vanilla potion keeps unknown prefix", "item.potion.name", "药水", "平凡的药水", "平凡的Potion", "平凡的"},
    };

    private static final String[][] FORMAT_SAMPLES = {
            {"Thermal Dynamics cover", "item.thermaldynamics.cover.name", "覆盖板：%s", "覆盖板：石头", "Stone Cover", ""},
            {"Forge UniversalBucket", "item.forge.bucketFilled.name", "%s Bucket", "熔融铁 Bucket", "Molten Iron Bucket", ""},
            {"JAOPCA dust", "item.jaopca.dust.name", "%s粉", "紫水晶粉", "Amethyst Dust", ""},
            {"JAOPCA block", "tile.jaopca.block.name", "%s 块", "花岗岩 块", "Block of Granite", ""},
            {"Wizardry scroll", "item.ebwizardry:scroll.name", "%1$s的卷軸", "魔法彈的卷軸", "Scroll of Magic Missile", ""},
            {"Gendustry sample keeps every unknown argument", "item.gendustry.gene_sample.name", "基因样本 - %s - %s",
                    "基因样本 - INVALID - ERROR", "Gene Sample - INVALID - ERROR", "INVALID;ERROR"},
    };

    private final SecondaryDictionary primary = dictionary("zh_cn", PRIMARY);
    private final SecondaryDictionary secondary = dictionary("en_us", SECONDARY);
    private final ReverseIndex index = ReverseIndex.of(primary);

    private static SecondaryDictionary dictionary(String code, String[][] entries) {
        return SecondaryDictionary.load(code, (language, sink) -> {
            if (language.equals(code)) {
                for (String[] entry : entries) {
                    sink.accept(entry[0], entry[1]);
                }
            }
        });
    }

    private Function<String, String> reverse() {
        return text -> index.translate(text, secondary);
    }

    private ResolvedName composite(String key, String expected, String actual, List<String> derived,
                                   Function<String, String> primaryOf) {
        return CompositeNameResolver.compose(expected, actual, secondary.get(key),
                CompositeNameResolver.translator(derived, primaryOf, secondary::get, reverse()));
    }

    private ResolvedName format(String key, String expected, String actual) {
        return FormatNameResolver.compose(expected, actual, secondary.get(key), reverse());
    }

    private ResolvedName bareKey(String bare, String actual) {
        String expected = ReverseIndex.normalize(primary.get(bare));
        String value = secondary.get(bare);
        return value == null ? null : BareKeyResolver.named(expected, actual, value,
                () -> CompositeNameResolver.compose(expected, actual, value, reverse()));
    }

    private static String name(ResolvedName resolved) {
        return resolved == null ? null : resolved.name();
    }

    private static void assertResolved(String message, String name, String untranslated, ResolvedName resolved) {
        assertEquals(message, name, name(resolved));
        List<String> parts = untranslated.isEmpty() ? Collections.<String>emptyList()
                : Arrays.asList(untranslated.split(";"));
        assertEquals(message, parts, resolved.untranslated());
        assertEquals(message, !parts.isEmpty(), resolved.partial());
    }

    @Test
    public void reverseIndexStripsStyleAndPrefersNameKeysForDuplicateValues() {
        assertEquals("tile.stone.stone.name", index.keyOf("石头"));
        assertEquals("tile.stone.stone.name", index.keyOf(" §a石头"));
        assertEquals("tile.water.name", index.keyOf("水"));
        assertEquals(2, index.ambiguous());
        assertNull(index.keyOf("%s Cobblestone"));
        assertNull(index.translate("未知", secondary));
    }

    @Test
    public void compositeSamplesReplaceTheAnchorAndDerivableParts() {
        for (String[] sample : COMPOSITE_SAMPLES) {
            assertResolved(sample[0], sample[4], sample[5], composite(sample[1], sample[2], sample[3],
                    Collections.<String>emptyList(), key -> null));
        }
    }

    @Test
    public void bareKeyEqualToTheShownNameUsesItsSecondaryValue() {
        assertResolved("CoFH ore", "Copper Ore", "", bareKey("tile.thermalfoundation.ore.copper", "铜矿石"));
    }

    @Test
    public void bareKeyContainedInTheShownNameAnchorsTheComposite() {
        assertResolved("TE machine", "Redstone Furnace (Basic)", "",
                bareKey("tile.thermalexpansion.machine.furnace", "红石炉 (基础)"));
        assertResolved("TE machine, unknown level", "Redstone Furnace (强化)", "强化",
                bareKey("tile.thermalexpansion.machine.furnace", "红石炉 (强化)"));
    }

    @Test
    public void bareKeyLeavesOtherNamesToLaterResolvers() {
        assertNull(bareKey("tile.thermalfoundation.ore.tin", "锡矿石"));
        assertNull(bareKey("tile.thermalfoundation.ore.copper", "铁矿石"));
        assertNull(BareKeyResolver.named("", "铜矿石", "Copper Ore", () -> null));
    }

    @Test
    public void spawnEggUsesTheEntityKeyDerivedFromTheStack() {
        Map<String, String> shown = new HashMap<>();
        shown.put("entity.Bat.name", "蝙蝠");
        assertResolved("derived", "Spawn Bat", "", composite("item.monsterPlacer.name", "生成", "生成 蝙蝠",
                Collections.singletonList("entity.Bat.name"), shown::get));
        assertResolved("not derived", "Spawn 蝙蝠", "蝙蝠", composite("item.monsterPlacer.name", "生成", "生成 蝙蝠",
                Collections.<String>emptyList(), key -> null));
    }

    @Test
    public void derivedKeyWinsOverTheReverseIndex() {
        Map<String, String> shown = Collections.singletonMap("fluid.tile.water", "水");
        assertEquals("Fluid Water Tank", name(composite("tile.openblocks.tank.name", "蓄水槽", "水蓄水槽",
                Arrays.asList("tile.missing.name", "fluid.tile.water"), shown::get)));
    }

    @Test
    public void compositeReturnsNullWhenItCannotHelp() {
        assertNull(CompositeNameResolver.compose("线缆伪装板", "线缆伪装板 - 未知", "线缆伪装板", reverse()));
        assertNull(CompositeNameResolver.compose("镐头", "磁钢镐", "Pickaxe Head", reverse()));
        assertNull(CompositeNameResolver.compose("", "石头", "Stone", reverse()));
        assertNull(CompositeNameResolver.compose("镐头", "磁钢 镐头", null, reverse()));
    }

    @Test
    public void formatSamplesTranslateEveryArgumentThroughTheReverseIndex() {
        for (String[] sample : FORMAT_SAMPLES) {
            assertResolved(sample[0], sample[4], sample[5], format(sample[1], sample[2], sample[3]));
        }
    }

    @Test
    public void formatKeepsPrimaryArgumentsWhenNoneReverseResolve() {
        assertResolved("cover", "Unknown Block Cover", "Unknown Block", format("item.thermaldynamics.cover.name",
                "覆盖板：%s", "覆盖板：Unknown Block"));
        assertNull(format("tile.compressed.cobblestone.name", "%s Cobblestone", "Nonuple Compressed Cobblestone"));
    }

    @Test
    public void argumentsWithoutLettersStayLiteral() {
        assertNull(index.keyOf("0"));
        assertNull(format("item.openblocks.glyph.name", "'%s' Glyph", "'0' Glyph"));
    }

    @Test
    public void formatGivesUpWithoutASecondaryFormatOrAMatchingName() {
        assertNull(format("item.missing.name", "覆盖板：%s", "覆盖板：石头"));
        assertNull(format("item.thermaldynamics.cover.name", "覆盖板：%s", "盖板：石头"));
        assertNull(FormatNameResolver.compose("%2$s", "石头", "%2$s and %1$s", reverse()));
    }

    @Test
    public void formatHandlesExplicitPositionsAndLiteralPercent() {
        assertTrue(FormatNameResolver.hasPositions("%1$s的卷軸"));
        assertTrue(FormatNameResolver.hasPositions("%s Bucket"));
        assertFalse(FormatNameResolver.hasPositions("100%% pure"));
        assertFalse(FormatNameResolver.hasPositions("Plain"));
        assertEquals(Arrays.asList("石头", "花岗岩"), FormatNameResolver.arguments("%2$s上的%1$s", "花岗岩上的石头"));
        assertEquals("Stone on Granite", name(FormatNameResolver.compose("%2$s上的%1$s", "花岗岩上的石头",
                "%1$s on %2$s", reverse())));
        assertEquals("Stone 50%", name(FormatNameResolver.compose("%s 五成", "石头 五成", "%s 50%%", reverse())));
        assertNull(FormatNameResolver.arguments("%1$s和%1$s", "石头和花岗岩"));
    }
}
