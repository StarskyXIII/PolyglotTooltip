package com.starskyxiii.polyglottooltip;

//? if <=1.20.1 {
/*import net.minecraft.SharedConstants;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeItemStackNameBehaviorTest {

    private final LanguageCache cache = LanguageCache.getInstance();

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
    }

    @AfterEach
    void clearLanguages() {
        cache.replaceLanguages(List.of());
    }

    @Test
    void translatedCustomNameTakesPriorityOverGeneratedName() {
        cache.replaceLanguages(List.of(language(Map.of(
                "probe.custom", "Translated Custom",
                "block.minecraft.stone", "Translated Stone"
        ))));
        ItemStack stack = new ItemStack(Items.STONE);
        setRawCustomName(stack, Component.Serializer.toJson(Component.translatable("probe.custom")));

        assertEquals(List.of("Translated Custom"), cache.resolveDisplayNamesForAll(stack));
    }

    @Test
    void literalCustomNameTakesPriorityOverGeneratedName() {
        cache.replaceLanguages(List.of(language(Map.of(
                "block.minecraft.stone", "Translated Stone"
        ))));
        ItemStack stack = new ItemStack(Items.STONE);
        setRawCustomName(stack, Component.Serializer.toJson(Component.literal("Literal Custom")));

        assertEquals(List.of("Literal Custom"), cache.resolveDisplayNamesForAll(stack));
    }

    @Test
    void unresolvedCustomNameDoesNotFallThroughToGeneratedName() {
        cache.replaceLanguages(List.of(language(Map.of(
                "block.minecraft.stone", "Translated Stone"
        ))));
        ItemStack stack = new ItemStack(Items.STONE);
        setRawCustomName(stack, Component.Serializer.toJson(Component.translatable("probe.missing")));

        assertTrue(cache.resolveDisplayNamesForAll(stack).isEmpty());
    }

    @Test
    void malformedCustomNameFallsBackWithoutRewritingNbt() {
        assertInvalidCustomNameFallsBackWithoutRewriting("{definitely-not-json");
    }

    @Test
    void nullJsonCustomNameFallsBackWithoutRewritingNbt() {
        assertInvalidCustomNameFallsBackWithoutRewriting("null");
    }

    @Test
    void ordinaryEnchantmentContributesItsTranslatedFullName() {
        cache.replaceLanguages(List.of(enchantmentLanguage()));
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        EnchantmentHelper.setEnchantments(Map.of(Enchantments.SHARPNESS, 3), stack);

        assertTrue(cache.resolveSearchNamesForAll(stack).contains("Translated Sharpness Translated III"));
    }

    @Test
    void storedEnchantmentContributesItsTranslatedFullName() {
        cache.replaceLanguages(List.of(enchantmentLanguage()));
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(Enchantments.SHARPNESS, 3));

        assertTrue(cache.resolveSearchNamesForAll(stack).contains("Translated Sharpness Translated III"));
    }

    private void assertInvalidCustomNameFallsBackWithoutRewriting(String rawJson) {
        cache.replaceLanguages(List.of(language(Map.of(
                "block.minecraft.stone", "Translated Stone"
        ))));
        ItemStack stack = new ItemStack(Items.STONE);
        setRawCustomName(stack, rawJson);

        assertEquals(List.of("Translated Stone"), cache.resolveDisplayNamesForAll(stack));
        assertEquals(rawJson, rawCustomName(stack));
    }

    private static ClientLanguage enchantmentLanguage() {
        return language(Map.of(
                "item.minecraft.diamond_sword", "Translated Sword",
                "item.minecraft.enchanted_book", "Translated Enchanted Book",
                "enchantment.minecraft.sharpness", "Translated Sharpness",
                "enchantment.level.3", "Translated III"
        ));
    }

    private static void setRawCustomName(ItemStack stack, String json) {
        stack.getOrCreateTagElement(ItemStack.TAG_DISPLAY).putString(ItemStack.TAG_DISPLAY_NAME, json);
    }

    private static String rawCustomName(ItemStack stack) {
        return stack.getTagElement(ItemStack.TAG_DISPLAY).getString(ItemStack.TAG_DISPLAY_NAME);
    }

    private static ClientLanguage language(Map<String, String> translations) {
        try {
            Constructor<ClientLanguage> constructor = ClientLanguage.class.getDeclaredConstructor(
                    Map.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(translations, false);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("ClientLanguage could not be constructed", exception);
        }
    }
}
*///?}
