package com.starskyxiii.polyglottooltip;

//? if neoforge && >=1.21.1 && <=1.21.1 {
import com.starskyxiii.polyglottooltip.integration.reliquary.ReliquaryNameHelper;
import com.starskyxiii.polyglottooltip.minecraft.ComponentLanguageResolver;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReliquaryItemNameBehaviorTest {
    private static CharmItem charm, fragment;
    private static final Map<String, String> TRANSLATIONS = Map.of(
            "item.reliquary.mob_charm", "%s Charm", "item.reliquary.mob_charm_fragment", "%s Charm Fragment",
            "entity.minecraft.zombie", "Zombie", "entity.minecraft.skeleton", "Skeleton", "entity.minecraft.pig", "Pig",
            "custom", "Renamed", "patched", "Patched");
    private final LanguageCache cache = LanguageCache.getInstance();

    @BeforeAll
    static void setup() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        charm = ItemNameTestRegistry.register("reliquary:mob_charm", CharmItem::new);
        fragment = ItemNameTestRegistry.register("reliquary:mob_charm_fragment", CharmItem::new);
        LanguageCache.registerSpecialNameResolver(ReliquaryNameHelper::tryResolveSpecialName);
    }

    @AfterEach
    void clear() { cache.replaceLanguages(List.of()); }

    @Test
    void realRegistryLookupResolvesBothItemsAndSeparatesCachedVariants() {
        useEnglish();
        ItemStack zombie = stack(charm, "minecraft:zombie"), skeleton = stack(charm, "minecraft:skeleton");
        ItemStack before = zombie.copy();
        Language current = Language.getInstance();
        assertEquals(List.of("Zombie Charm"), cache.resolveDisplayNamesForAll(zombie));
        assertEquals(List.of("Skeleton Charm"), cache.resolveSearchNamesForAll(skeleton));
        assertEquals(List.of("Zombie Charm Fragment"), cache.resolveDisplayNamesForAll(stack(fragment, "minecraft:zombie")));
        assertEquals(List.of("Zombie Charm"), cache.resolveSearchNamesForAll(zombie));
        assertTrue(ItemStack.isSameItemSameComponents(before, zombie));
        assertSame(current, Language.getInstance());
    }

    @Test
    void unknownAndMalformedIdentitiesDoNotResolveToTheDefaultEntity() {
        for (String entity : List.of("missing:entity", "!invalid!", "")) {
            ItemStack stack = stack(charm, entity);
            ItemStack before = stack.copy();
            assertTrue(ReliquaryNameHelper.tryResolveSpecialName(stack,
                    component -> ComponentLanguageResolver.resolve(component, TRANSLATIONS::get)).isEmpty());
            assertTrue(ItemStack.isSameItemSameComponents(before, stack));
        }
    }

    @Test
    void explicitNamesPrecedeEntityGeneratedNames() {
        useEnglish();
        ItemStack stack = stack(charm, "minecraft:zombie");
        stack.set(DataComponents.ITEM_NAME, Component.translatable("patched"));
        assertEquals(List.of("Patched"), cache.resolveDisplayNamesForAll(stack));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("custom"));
        assertEquals(List.of("Renamed"), cache.resolveDisplayNamesForAll(stack));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("My Charm"));
        assertEquals(List.of("My Charm"), cache.resolveDisplayNamesForAll(stack));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("missing"));
        assertTrue(cache.resolveDisplayNamesForAll(stack).isEmpty());
    }

    private static ItemStack stack(Item item, String entity) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString("entity", entity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private void useEnglish() {
        try {
            Constructor<ClientLanguage> constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
            constructor.setAccessible(true);
            cache.replaceLanguages(List.of(constructor.newInstance(TRANSLATIONS, false)));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    public static class CharmItem extends Item {
        public CharmItem() { super(new Item.Properties()); }
        public static ResourceLocation getEntityEggRegistryName(ItemStack stack) { return getEntityRegistryName(stack); }
        public static ResourceLocation getEntityRegistryName(ItemStack stack) {
            var data = stack.get(DataComponents.CUSTOM_DATA);
            return data == null ? null : ResourceLocation.tryParse(data.copyTag().getString("entity"));
        }
    }
}
//?}
