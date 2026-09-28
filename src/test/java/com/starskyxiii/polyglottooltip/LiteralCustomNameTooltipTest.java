package com.starskyxiii.polyglottooltip;
//~ minecraft_116_packages minecraft_legacy_components minecraft_118_factories

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiteralCustomNameTooltipTest {
    @Test
    void transformedLiteralDoesNotBecomeASecondaryName() {
        String alias = ":pt_fixture_emoji_927:";
        assertEquals(List.of("Other"), SecondaryTooltipUtil.getSecondaryNames(
                List.of("★", alias, "Other"), "★", ComponentFactory.literal(alias)));
    }

    @Test
    void literalSiblingsUseTheWholeOriginalName() {
        Component custom = ComponentFactory.empty().append(ComponentFactory.literal(":emoji:"))
                .append(ComponentFactory.literal(" suffix"));
        assertEquals(List.of(), SecondaryTooltipUtil.getSecondaryNames(
                List.of(":emoji: suffix"), "★ suffix", custom));
    }

    @Test
    void absentCustomNameKeepsOtherResolvedNames() {
        assertEquals(List.of(":emoji:"), SecondaryTooltipUtil.getSecondaryNames(
                List.of("★", ":emoji:"), "★", null));
    }

    @Test
    void unknownLiteralStillUsesOrdinaryPrimaryDeduplication() {
        String unknown = ":pt_unknown_emoji:";
        assertEquals(List.of(), SecondaryTooltipUtil.getSecondaryNames(
                List.of(unknown), unknown, ComponentFactory.literal(unknown)));
    }

    @Test
    void translatableSiblingIsNotAPlainLiteralName() {
        Component custom = ComponentFactory.literal(":emoji:")
                .append(ComponentFactory.translatable("probe.name"));
        assertEquals(List.of(custom.getString(), "Translated"), SecondaryTooltipUtil.getSecondaryNames(
                List.of(custom.getString(), "Translated"), "Native", custom));
    }

    @Test
    void nestedTranslatableArgumentsKeepTheirResolvedNames() {
        Component custom = ComponentFactory.translatable("probe.outer",
                ComponentFactory.translatable("probe.inner"));
        assertEquals(List.of(custom.getString(), "Translated"), SecondaryTooltipUtil.getSecondaryNames(
                List.of(custom.getString(), "Translated"), "Native", custom));
    }

    @Test
    void keybindContentsAreNotTreatedAsLiterals() {
        //? if <=1.16.5 {
        /*Component custom = new net.minecraft.util.text.KeybindTextComponent("key.jump");
        *///?} else if <=1.18.2 {
        /*Component custom = new net.minecraft.network.chat.KeybindComponent("key.jump");
        *///?} else {
        Component custom = Component.keybind("key.jump");
        //?}
        assertEquals(List.of(custom.getString()), SecondaryTooltipUtil.getSecondaryNames(
                List.of(custom.getString()), "Native", custom));
    }
}
