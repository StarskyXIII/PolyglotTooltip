package com.starskyxiii.polyglottooltip.minecraft;

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Optional;

public final class ComponentLanguageResolver {
    private ComponentLanguageResolver() {
    }

    public static Optional<String> resolve(Component component, ClientLanguage language) {
        if (language == null) {
            return Optional.empty();
        }

        Optional<String> mainPart = resolveContents(component, language);
        if (mainPart.isEmpty()) {
            return Optional.empty();
        }
        if (component.getSiblings().isEmpty()) {
            return mainPart;
        }

        StringBuilder result = new StringBuilder(mainPart.get());
        for (Component sibling : component.getSiblings()) {
            result.append(resolve(sibling, language).orElse(""));
        }
        return Optional.of(result.toString());
    }

    private static Optional<String> resolveContents(Component component, ClientLanguage language) {
        ComponentContents contents = component.getContents();
        if (!(contents instanceof TranslatableContents translatable)) {
            return Optional.of(resolvePlainContents(contents));
        }

        String template = language.getOrDefault(translatable.getKey(), null);
        if (template == null) {
            template = translatable.getFallback();
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
                resolvedArgs[index] = resolve(nested, language).orElse("");
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
