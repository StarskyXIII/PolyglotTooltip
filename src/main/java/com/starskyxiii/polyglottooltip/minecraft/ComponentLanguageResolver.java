package com.starskyxiii.polyglottooltip.minecraft;

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Optional;
import java.util.function.Function;

public final class ComponentLanguageResolver {
    private ComponentLanguageResolver() {
    }

    public static Optional<String> resolve(Component component, ClientLanguage language) {
        if (language == null) {
            return Optional.empty();
        }

        return resolve(component, key -> {
            //? if <=1.19.2 {
            /*return language.has(key) ? language.getOrDefault(key) : null;
            *///?} else {
            return language.getOrDefault(key, null);
            //?}
        });
    }

    public static Optional<String> resolve(Component component, Function<String, String> translationLookup) {
        if (translationLookup == null) {
            return Optional.empty();
        }

        Optional<String> mainPart = resolveContents(component, translationLookup);
        if (mainPart.isEmpty()) {
            return Optional.empty();
        }
        if (component.getSiblings().isEmpty()) {
            return mainPart;
        }

        StringBuilder result = new StringBuilder(mainPart.get());
        for (Component sibling : component.getSiblings()) {
            result.append(resolve(sibling, translationLookup).orElse(""));
        }
        return Optional.of(result.toString());
    }

    private static Optional<String> resolveContents(Component component,
                                                    Function<String, String> translationLookup) {
        ComponentContents contents = component.getContents();
        if (!(contents instanceof TranslatableContents translatable)) {
            return Optional.of(resolvePlainContents(contents));
        }

        String template = translationLookup.apply(translatable.getKey());
        if (template == null) {
            //? if >1.19.2 {
            template = translatable.getFallback();
            //?}
        }
        if (template == null) {
            return Optional.empty();
        }

        Object[] args = translatable.getArgs();
        if (args.length == 0) {
            return Optional.of(template);
        }

        Object[] resolvedArgs = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            Object argument = args[index];
            if (argument instanceof Component nested) {
                resolvedArgs[index] = resolve(nested, translationLookup).orElse("");
            } else {
                resolvedArgs[index] = String.valueOf(argument);
            }
        }

        try {
            return Optional.of(String.format(template, resolvedArgs));
        } catch (RuntimeException ignored) {
            return Optional.of(template);
        }
    }

    private static String resolvePlainContents(ComponentContents contents) {
        StringBuilder result = new StringBuilder();
        contents.visit(text -> {
            result.append(text);
            return Optional.empty();
        });
        return result.toString();
    }
}
