package com.starskyxiii.polyglottooltip.minecraft;
//~ minecraft_116_packages

import net.minecraft.network.chat.MutableComponent;

public final class ComponentFactory {

    private ComponentFactory() {
    }

    public static MutableComponent empty() {
        //? if <=1.16.5 {
        /*return new net.minecraft.util.text.StringTextComponent("");
        *///?} else if <=1.18.2 {
        /*return new net.minecraft.network.chat.TextComponent("");
        *///?} else {
        return net.minecraft.network.chat.Component.empty();
        //?}
    }

    public static MutableComponent literal(String value) {
        //? if <=1.16.5 {
        /*return new net.minecraft.util.text.StringTextComponent(value);
        *///?} else if <=1.18.2 {
        /*return new net.minecraft.network.chat.TextComponent(value);
        *///?} else {
        return net.minecraft.network.chat.Component.literal(value);
        //?}
    }

    public static MutableComponent translatable(String key, Object... args) {
        //? if <=1.16.5 {
        /*return new net.minecraft.util.text.TranslationTextComponent(key, args);
        *///?} else if <=1.18.2 {
        /*return new net.minecraft.network.chat.TranslatableComponent(key, args);
        *///?} else {
        return net.minecraft.network.chat.Component.translatable(key, args);
        //?}
    }
}
