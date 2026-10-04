package com.starskyxiii.polyglottooltip;

//? if neoforge && >=1.21.1 && <=1.21.1 {
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.function.Supplier;

final class ItemNameTestRegistry {
    static <T extends Item> T register(String id, Supplier<T> factory) {
        try {
            Field frozen = MappedRegistry.class.getDeclaredField("frozen");
            Field holders = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
            frozen.setAccessible(true);
            holders.setAccessible(true);
            frozen.setBoolean(BuiltInRegistries.ITEM, false);
            holders.set(BuiltInRegistries.ITEM, new IdentityHashMap<>());
            try {
                return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.parse(id), factory.get());
            } finally {
                BuiltInRegistries.ITEM.freeze();
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
//?}
