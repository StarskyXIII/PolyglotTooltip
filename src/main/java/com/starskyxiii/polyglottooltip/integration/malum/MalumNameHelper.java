package com.starskyxiii.polyglottooltip.integration.malum;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import com.starskyxiii.polyglottooltip.minecraft.ItemStackNameAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class MalumNameHelper {
    private static final String GEAS_ITEM = "com.sammy.malum.common.item.GeasItem";
    private static final String EFFECT_HANDLER = "com.sammy.malum.core.handlers.GeasEffectHandler";
    private static final Map<Class<?>, Optional<Method>> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Optional<Method>>> METHODS = new ConcurrentHashMap<>();

    private MalumNameHelper() {
    }

    public static Optional<String> decorateName(ItemStack stack, String baseName,
                                                 Function<Component, Optional<String>> componentResolver) {
        Class<?> itemClass = stack.getItem().getClass();
        if (!isGeasItem(itemClass) || ItemStackNameAdapter.resolveCustomNameComponent(stack)
                .filter(ItemStackNameAdapter::isLiteralName).isPresent()) {
            return Optional.of(baseName);
        }
        Optional<String> typeKey = readEffect(itemClass, stack)
                .flatMap(value -> invoke(value, "geasEffectType"))
                .flatMap(value -> invoke(value, "getLangKey"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> !value.isEmpty());
        return typeKey.isPresent() ? appendType(baseName, typeKey.get(), componentResolver) : Optional.of(baseName);
    }

    static boolean isGeasItem(Class<?> itemClass) {
        for (Class<?> current = itemClass; current != null; current = current.getSuperclass()) {
            if (GEAS_ITEM.equals(current.getName())) return true;
        }
        return false;
    }

    private static Optional<?> readEffect(Class<?> itemClass, ItemStack stack) {
        Optional<Method> method = HANDLERS.computeIfAbsent(itemClass, key -> {
            try {
                Class<?> handler = Class.forName(EFFECT_HANDLER, false, key.getClassLoader());
                return Optional.of(handler.getMethod("getStoredGeasEffect", ItemStack.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return Optional.empty();
            }
        });
        if (!method.isPresent()) return Optional.empty();
        try {
            Object value = method.get().invoke(null, stack);
            return value instanceof Optional<?> ? (Optional<?>) value : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    static Optional<Object> invoke(Object target, String name) {
        Optional<Method> method = METHODS.computeIfAbsent(target.getClass(), key -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, key -> {
                    try {
                        return Optional.of(target.getClass().getMethod(key));
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                        return Optional.empty();
                    }
                });
        return method.flatMap(found -> {
            try {
                return Optional.ofNullable(found.invoke(target));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return Optional.empty();
            }
        });
    }

    static Optional<String> appendType(String baseName, String key,
                                        Function<Component, Optional<String>> componentResolver) {
        return componentResolver.apply(ComponentFactory.translatable(key)).map(type -> baseName + " <" + type + ">");
    }
}
