package com.starskyxiii.polyglottooltip.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Function;

@FunctionalInterface
public interface SpecialItemNameResolver {
    Optional<String> resolve(ItemStack stack, Function<Component, Optional<String>> componentResolver);
}
