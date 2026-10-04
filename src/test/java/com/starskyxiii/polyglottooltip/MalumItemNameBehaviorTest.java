package com.starskyxiii.polyglottooltip;

//? if neoforge && >=1.21.1 && <=1.21.1 {
import com.sammy.malum.common.item.GeasItem;
import com.starskyxiii.polyglottooltip.integration.malum.MalumNameHelper;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MalumItemNameBehaviorTest {
    private static GeasItem geasItem;
    private final LanguageCache cache = LanguageCache.getInstance();

    @BeforeAll
    static void setup() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        geasItem = ItemNameTestRegistry.register("malum:geas", GeasItem::new);
        LanguageCache.registerNameDecorator(MalumNameHelper::decorateName);
    }

    @AfterEach
    void clear() { cache.replaceLanguages(List.of()); }

    @Test
    void completeVariantNamesAreSharedByDisplayAndSearchWithoutMutation() {
        useEnglish();
        ItemStack first = geas("geas.first"), second = geas("geas.second");
        ItemStack before = first.copy();
        Language current = Language.getInstance();
        assertEquals(List.of("Geas <First Pact>"), cache.resolveDisplayNamesForAll(first));
        assertEquals(List.of("Geas <Second Pact>"), cache.resolveSearchNamesForAll(second));
        assertEquals(List.of("Geas <First Pact>"), cache.resolveSearchNamesForAll(first));
        assertTrue(ItemStack.isSameItemSameComponents(before, first));
        assertSame(current, Language.getInstance());
    }

    @Test
    void literalAndTranslatedCustomNamesRetainTheirPolicies() {
        useEnglish();
        ItemStack stack = geas("geas.first");
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("My Geas"));
        assertEquals(List.of("My Geas"), cache.resolveDisplayNamesForAll(stack));
        assertTrue(SecondaryTooltipUtil.getSecondaryNames(stack).isEmpty());
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("custom"));
        assertEquals(List.of("Renamed <First Pact>"), cache.resolveDisplayNamesForAll(stack));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("missing"));
        assertTrue(cache.resolveDisplayNamesForAll(stack).isEmpty());
    }

    @Test
    void customNamePrecedesPatchedNameAndBothCanReceiveTheSuffix() {
        useEnglish();
        ItemStack stack = geas("geas.first");
        stack.set(DataComponents.ITEM_NAME, Component.translatable("patched"));
        assertEquals(List.of("Patched <First Pact>"), cache.resolveDisplayNamesForAll(stack));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("custom"));
        assertEquals(List.of("Renamed <First Pact>"), cache.resolveDisplayNamesForAll(stack));
    }

    @Test
    void currentLanguageComparisonIncludesTheWholeTitle() {
        ClientLanguage primary = language(Map.of("item.malum.geas", "誓令", "geas.first", "第一契約"));
        ClientLanguage secondary = language(Map.of("item.malum.geas", "Geas", "geas.first", "First Pact"));
        Language previous = Language.getInstance();
        try {
            Language.inject(primary);
            cache.replaceLanguages(List.of(primary, secondary));
            ItemStack stack = geas("geas.first");
            assertEquals("誓令 <第一契約>", cache.resolveCurrentDisplayName(stack));
            assertEquals(List.of("Geas <First Pact>"), SecondaryTooltipUtil.getSecondaryNames(stack));
        } finally {
            Language.inject(previous);
        }
    }

    @Test
    void missingTypeTranslationSuppressesIncompleteNames() {
        useEnglish();
        assertTrue(cache.resolveDisplayNamesForAll(geas("missing")).isEmpty());
    }

    @Test
    void absentOrBrokenDataAndNonGeasItemsKeepTheirBaseNames() {
        useEnglish();
        assertEquals(List.of("Geas"), cache.resolveDisplayNamesForAll(new ItemStack(geasItem)));
        assertEquals(List.of("Geas"), cache.resolveDisplayNamesForAll(geas("broken")));
        ItemStack stone = new ItemStack(Items.STONE);
        stone.set(DataComponents.CUSTOM_DATA, geas("geas.first").get(DataComponents.CUSTOM_DATA));
        assertEquals(List.of("Stone"), cache.resolveDisplayNamesForAll(stone));
    }

    private static ItemStack geas(String key) {
        ItemStack stack = new ItemStack(geasItem);
        CompoundTag tag = new CompoundTag();
        tag.putString("geas", key);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private void useEnglish() {
        cache.replaceLanguages(List.of(language(Map.of(
                "item.malum.geas", "Geas", "geas.first", "First Pact", "geas.second", "Second Pact",
                "custom", "Renamed", "patched", "Patched", "block.minecraft.stone", "Stone"))));
    }

    private static ClientLanguage language(Map<String, String> translations) {
        try {
            Constructor<ClientLanguage> constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(translations, false);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
//?}
