package com.starskyxiii.polyglottooltip.integration.reliquary;
//~ minecraft_identifier minecraft_116_packages

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
//? if <=1.16.5 {
/*import net.minecraft.util.registry.Registry;
*///?} else if <=1.19.2 {
/*import net.minecraft.core.Registry;
*///?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ReliquaryNameHelper {
    private static final Map<Class<?>, Map<String, Optional<Method>>> METHODS = new ConcurrentHashMap<>();

    private ReliquaryNameHelper() {
    }

    public static Optional<String> tryResolveSpecialName(ItemStack stack,
                                                         Function<Component, Optional<String>> componentResolver) {
        //? if <=1.19.2 {
        /*ResourceLocation itemId = Registry.ITEM.getKey(stack.getItem());
        *///?} else {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        //?}
        if (itemId == null || !isSupportedItem(itemId.getNamespace(), itemId.getPath())) {
            return Optional.empty();
        }
        String method = entityMethod(itemId.getPath());
        return readEntityId(stack.getItem().getClass(), method, stack)
                .flatMap(ReliquaryNameHelper::findEntity)
                .flatMap(entity -> resolveName(stack.getItem().getDescriptionId(), entity.getDescription(), componentResolver));
    }

    static boolean isSupportedItem(String namespace, String path) {
        //? if <=1.16.5 {
        /*boolean namespaceMatches = "xreliquary".equals(namespace);
        *///?} else {
        boolean namespaceMatches = "reliquary".equals(namespace);
        //?}
        return namespaceMatches && ("mob_charm".equals(path) || "mob_charm_fragment".equals(path));
    }

    static String entityMethod(String path) {
        //? if >1.20.1 {
        if ("mob_charm_fragment".equals(path)) return "getEntityRegistryName";
        //?}
        return "getEntityEggRegistryName";
    }

    static Optional<ResourceLocation> readEntityId(Class<?> itemClass, String methodName, ItemStack stack) {
        Optional<Method> method = METHODS.computeIfAbsent(itemClass, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(methodName, key -> {
                    try {
                        Method found = itemClass.getMethod(key, ItemStack.class);
                        return Modifier.isStatic(found.getModifiers()) ? Optional.of(found) : Optional.empty();
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                        return Optional.empty();
                    }
                });
        return method.flatMap(found -> {
            try {
                Object value = found.invoke(null, stack);
                return value instanceof ResourceLocation ? Optional.of((ResourceLocation) value) : Optional.empty();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<EntityType<?>> findEntity(ResourceLocation id) {
        //? if <=1.19.2 {
        /*return Registry.ENTITY_TYPE.getOptional(id);
        *///?} else if >=26 {
        /*return BuiltInRegistries.ENTITY_TYPE.get(id).map(reference -> reference.value());
        *///?} else {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        //?}
    }

    static Optional<String> resolveName(String template, Component entityName,
                                         Function<Component, Optional<String>> componentResolver) {
        return componentResolver.apply(entityName)
                .flatMap(entity -> componentResolver.apply(ComponentFactory.translatable(template, entity)))
                .map(ReliquaryNameHelper::expandNameFormatting);
    }

    static String expandNameFormatting(String name) {
        //? if <=1.18.2 {
        /*return name.replace("{{!colors.light_green}}", "\u00a7a");
        *///?} else {
        return name;
        //?}
    }
}
