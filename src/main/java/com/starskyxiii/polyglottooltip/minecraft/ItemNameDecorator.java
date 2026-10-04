package com.starskyxiii.polyglottooltip.minecraft;
//~ minecraft_116_packages

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Function;

@FunctionalInterface
public interface ItemNameDecorator {
    Optional<String> decorate(ItemStack stack, String baseName,
                              Function<Component, Optional<String>> componentResolver);
}
