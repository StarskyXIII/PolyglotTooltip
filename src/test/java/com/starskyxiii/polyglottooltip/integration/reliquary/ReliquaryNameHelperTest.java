package com.starskyxiii.polyglottooltip.integration.reliquary;
//~ minecraft_identifier minecraft_116_packages

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import com.starskyxiii.polyglottooltip.minecraft.ComponentLanguageResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ReliquaryNameHelperTest {
    private static final Function<Component, Optional<String>> ENGLISH = component -> ComponentLanguageResolver.resolve(
            component, Map.of("entity.minecraft.zombie", "Zombie", "entity.minecraft.skeleton", "Skeleton",
                    "charm", "%s Charm", "fragment", "%s Charm Fragment")::get);

    @Test
    void resolvesEntityBeforeFormattingBothNames() {
        assertEquals(Optional.of("Zombie Charm"), ReliquaryNameHelper.resolveName(
                "charm", ComponentFactory.translatable("entity.minecraft.zombie"), ENGLISH));
        assertEquals(Optional.of("Skeleton Charm Fragment"), ReliquaryNameHelper.resolveName(
                "fragment", ComponentFactory.translatable("entity.minecraft.skeleton"), ENGLISH));
    }

    @Test
    void missingTranslationsDeclineTheSpecialResolverWithoutAnEmptyEntity() {
        assertEquals(Optional.empty(), ReliquaryNameHelper.resolveName(
                "charm", ComponentFactory.translatable("entity.unknown.mob"), ENGLISH));
        assertEquals(Optional.empty(), ReliquaryNameHelper.resolveName(
                "missing", ComponentFactory.translatable("entity.minecraft.zombie"), ENGLISH));
    }

    @Test
    void onlyReleasedCharmIdsAndGetterNamesAreSelected() {
        //? if <=1.16.5 {
        /*String namespace = "xreliquary";
        *///?} else {
        String namespace = "reliquary";
        //?}
        assertTrue(ReliquaryNameHelper.isSupportedItem(namespace, "mob_charm"));
        assertTrue(ReliquaryNameHelper.isSupportedItem(namespace, "mob_charm_fragment"));
        assertFalse(ReliquaryNameHelper.isSupportedItem("other", "mob_charm"));
        assertFalse(ReliquaryNameHelper.isSupportedItem(namespace, "other"));
        assertEquals("getEntityEggRegistryName", ReliquaryNameHelper.entityMethod("mob_charm"));
        //? if <=1.20.1 {
        /*assertEquals("getEntityEggRegistryName", ReliquaryNameHelper.entityMethod("mob_charm_fragment"));
        *///?} else {
        assertEquals("getEntityRegistryName", ReliquaryNameHelper.entityMethod("mob_charm_fragment"));
        //?}
    }

    @Test
    void reflectiveFailuresAndWrongReturnTypesFallBack() {
        assertEquals(Optional.of(ResourceLocation.tryParse("minecraft:zombie")),
                ReliquaryNameHelper.readEntityId(PublicApi.class, "valid", null));
        for (String method : new String[]{"missing", "broken", "wrong", "instance"}) {
            assertTrue(ReliquaryNameHelper.readEntityId(PublicApi.class, method, null).isEmpty());
        }
    }

    @Test
    void legacyMacroMatchesNativeFormattingOnlyOnAffectedVersions() {
        String template = "{{!colors.light_green}}Zombie Charm";
        //? if <=1.18.2 {
        /*assertEquals("\u00a7aZombie Charm", ReliquaryNameHelper.expandNameFormatting(template));
        *///?} else {
        assertEquals(template, ReliquaryNameHelper.expandNameFormatting(template));
        //?}
        assertEquals("Zombie Charm", ReliquaryNameHelper.expandNameFormatting("Zombie Charm"));
    }

    public static class PublicApi {
        public static ResourceLocation valid(ItemStack stack) { return ResourceLocation.tryParse("minecraft:zombie"); }
        public static ResourceLocation broken(ItemStack stack) { throw new IllegalArgumentException("Malformed entity"); }
        public static String wrong(ItemStack stack) { return "minecraft:zombie"; }
        public ResourceLocation instance(ItemStack stack) { return ResourceLocation.tryParse("minecraft:zombie"); }
    }
}
