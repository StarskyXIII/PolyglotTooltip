package com.starskyxiii.polyglottooltip;
//~ minecraft_legacy_components

//? if >1.20.1 {
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? !fabric {
import net.neoforged.fml.loading.LoadingModList;
//?}
import org.junit.jupiter.api.AfterEach;
//? !fabric {
import org.junit.jupiter.api.AfterAll;
//?}
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemStackNameBehaviorTest {

    //? !fabric {
    private static AutoCloseable fmlLoader;
    //?}
    private final LanguageCache cache = LanguageCache.getInstance();

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        //? if >=26 && !fabric {
        /*Class<?> loaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Constructor<?> loaderConstructor = loaderClass.getDeclaredConstructor(
                ClassLoader.class,
                String[].class,
                net.neoforged.api.distmarker.Dist.class,
                boolean.class,
                java.nio.file.Path.class);
        loaderConstructor.setAccessible(true);
        fmlLoader = (AutoCloseable) loaderConstructor.newInstance(
                Thread.currentThread().getContextClassLoader(),
                new String[0],
                net.neoforged.api.distmarker.Dist.CLIENT,
                false,
                java.nio.file.Path.of("."));
        LoadingModList loadingModList = LoadingModList.of(
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
        var loadingModListField = loaderClass.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        loadingModListField.set(fmlLoader, loadingModList);
        *///?} else if !fabric {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        //?}
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        //? if >=26 {
        /*net.minecraft.core.component.DataComponentMap stoneComponents =
                net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS)
                        .set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                                Component.translatable(Items.STONE.getDescriptionId()))
                        .set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.STONE))
                        .build();
        Items.STONE.builtInRegistryHolder().bindComponents(stoneComponents);
        *///?}
    }

    //? !fabric {
    @AfterAll
    static void closeFmlLoader() throws Exception {
        if (fmlLoader != null) {
            fmlLoader.close();
        }
    }
    //?}

    @AfterEach
    void clearLanguages() {
        cache.replaceLanguages(List.of());
    }

    @Test
    void customNameTakesPriorityOverPatchedItemName() {
        cache.replaceLanguages(List.of(language(Map.of(
                "probe.custom", "Translated Custom",
                "probe.patched", "Translated Patched"
        ))));
        ItemStack stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("probe.patched"));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("probe.custom"));

        assertEquals(List.of("Translated Custom"), cache.resolveDisplayNamesForAll(stack));
    }

    @Test
    void unresolvedCustomNameDoesNotFallThroughToPatchedItemName() {
        cache.replaceLanguages(List.of(language(Map.of(
                "probe.patched", "Translated Patched"
        ))));
        ItemStack stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("probe.patched"));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("probe.missing"));

        assertTrue(cache.resolveDisplayNamesForAll(stack).isEmpty());
    }

    @Test
    void unresolvedPatchedItemNameFallsThroughToItemName() {
        cache.replaceLanguages(List.of(language(Map.of(
                "block.minecraft.stone", "Translated Stone"
        ))));
        ItemStack stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("probe.missing"));

        //? if >=26 {
        /*assertTrue(cache.resolveDisplayNamesForAll(stack).isEmpty());
        *///?} else {
        assertEquals(List.of("Translated Stone"), cache.resolveDisplayNamesForAll(stack));
        //?}
    }

    @Test
    void sameItemWithDifferentNameComponentsDoesNotShareCachedResult() {
        cache.replaceLanguages(List.of(language(Map.of(
                "probe.first", "First Name",
                "probe.second", "Second Name"
        ))));
        ItemStack first = new ItemStack(Items.STONE);
        first.set(DataComponents.ITEM_NAME, Component.translatable("probe.first"));
        ItemStack second = new ItemStack(Items.STONE);
        second.set(DataComponents.ITEM_NAME, Component.translatable("probe.second"));

        assertEquals(List.of("First Name"), cache.resolveDisplayNamesForAll(first));
        assertEquals(List.of("Second Name"), cache.resolveDisplayNamesForAll(second));

        first.set(DataComponents.CUSTOM_NAME, Component.literal("First Custom"));
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Second Custom"));

        assertEquals(List.of("First Custom"), cache.resolveDisplayNamesForAll(first));
        assertEquals(List.of("Second Custom"), cache.resolveDisplayNamesForAll(second));
    }

    private static ClientLanguage language(Map<String, String> translations) {
        try {
            Constructor<ClientLanguage> constructor;
            try {
                constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
            } catch (NoSuchMethodException exception) {
                constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class, Map.class);
            }
            constructor.setAccessible(true);
            return constructor.getParameterCount() == 2
                    ? constructor.newInstance(translations, false)
                    : constructor.newInstance(translations, false, Map.of());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("ClientLanguage could not be constructed", exception);
        }
    }
}
//?}
