package com.starskyxiii.polyglottooltip.integration.productivebees;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ProductiveBeesNameHelper {

    private static final String MOD_ID = "productivebees";
    private static final String BEE_INGREDIENT = "cy.jdkdigital.productivebees.common.crafting.ingredient.BeeIngredient";
    private static final String BEE_TYPE_COMPONENT_ID = "productivebees:bee_type";
    private static final String CONFIGURABLE_HONEYCOMB = "configurable_honeycomb";
    private static final String CONFIGURABLE_COMB_BLOCK = "configurable_comb";
    private static final String CONFIGURABLE_SPAWN_EGG = "spawn_egg_configurable_bee";
    private static final String ENGLISH_BEE_SUFFIX = " Bee";
    // Thread-safe caches: Method lookups and BeeIngredient class resolution are called
    // from JEI's background indexing thread as well as the render thread.
    private static final Map<String, Optional<Method>> methodCache = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Class<?>>> classCache = new ConcurrentHashMap<>();

    private ProductiveBeesNameHelper() {
    }

    public static Optional<String> tryResolveSpecialName(
            ItemStack stack,
            Function<Component, Optional<String>> componentResolver
    ) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!MOD_ID.equals(itemId.getNamespace())) {
            return Optional.empty();
        }

        return switch (itemId.getPath()) {
            case CONFIGURABLE_HONEYCOMB -> resolveCombLikeName(
                    stack,
                    "item.productivebees.honeycomb_configurable",
                    componentResolver
            );
            case CONFIGURABLE_COMB_BLOCK -> resolveCombLikeName(
                    stack,
                    "block.productivebees.comb_configurable",
                    componentResolver
            );
            case CONFIGURABLE_SPAWN_EGG -> resolveSpawnEggName(stack, componentResolver);
            default -> Optional.empty();
        };
    }

    public static Optional<Component> tryCreateBeeIngredientName(Object ingredient) {
        if (ingredient == null || !isBeeIngredient(ingredient)) {
            return Optional.empty();
        }

        Optional<Identifier> beeType = invokeIdentifier(ingredient, "getBeeType");
        if (beeType.isPresent()) {
            return Optional.of(createBeeNameComponent(beeType.get()));
        }

        return invokeMethod(ingredient, "getBeeEntity")
                .filter(EntityType.class::isInstance)
                .map(EntityType.class::cast)
                .map(EntityType::getDescription);
    }

    public static Optional<String> tryResolveBeeIngredientName(
            Object ingredient,
            Function<Component, Optional<String>> componentResolver
    ) {
        return tryCreateBeeIngredientName(ingredient).flatMap(componentResolver);
    }

    private static Optional<String> resolveCombLikeName(
            ItemStack stack,
            String templateKey,
            Function<Component, Optional<String>> componentResolver
    ) {
        // PB's comb template (e.g. "%s Honeycomb") expects the bee adjective WITHOUT the
        // "Bee" word — i.e. "Forest", not "Forest Bee".  We resolve the bee name in the
        // secondary language first, then strip the English suffix.
        //
        // Limitation: for non-English secondary languages the suffix strip is a no-op,
        // so the full translated bee name is passed as the template arg instead (e.g.
        // "森林蜜蜂" rather than "森林").  In practice this only matters when PB ships a
        // translation for the comb template key in that language; if the key is absent,
        // resolveComponentWithLang returns empty and nothing is shown — which is correct.
        return resolveBeeName(stack, componentResolver)
                .map(ProductiveBeesNameHelper::stripEnglishBeeSuffix)
                .flatMap(beeName -> componentResolver.apply(Component.translatable(templateKey, beeName)));
    }

    private static Optional<String> resolveSpawnEggName(
            ItemStack stack,
            Function<Component, Optional<String>> componentResolver
    ) {
        return resolveBeeName(stack, componentResolver)
                .flatMap(beeName -> componentResolver.apply(
                        Component.translatable("item.productivebees.spawn_egg_configurable", beeName)
                ));
    }

    private static Optional<String> resolveBeeName(
            ItemStack stack,
            Function<Component, Optional<String>> componentResolver
    ) {
        return findBeeType(stack)
                .map(ProductiveBeesNameHelper::createBeeNameComponent)
                .flatMap(componentResolver);
    }

    private static Optional<String> findBeeType(ItemStack stack) {
        var entityData = stack.get(DataComponents.ENTITY_DATA);
        if (entityData != null) {
            // In 26.1, CompoundTag.getString() returns Optional<String>.
            Optional<String> type = entityData.getUnsafe().getString("type");
            if (type.isPresent() && !type.get().isEmpty()) {
                return type;
            }
        }

        // Look up the productivebees:bee_type DataComponent through the registry.
        // In 26.1, Registry.get(Identifier) returns Optional<Reference<T>>.
        // Using raw DataComponentType to avoid wildcard-capture issues at the call site.
        Identifier beeTypeKey = Identifier.tryParse(BEE_TYPE_COMPONENT_ID);
        if (beeTypeKey != null) {
            @SuppressWarnings("rawtypes")
            Optional<? extends DataComponentType> beeTypeComp =
                    BuiltInRegistries.DATA_COMPONENT_TYPE.get(beeTypeKey)
                            .map(ref -> ref.value());
            if (beeTypeComp.isPresent()) {
                @SuppressWarnings("unchecked")
                Object value = stack.get(beeTypeComp.get());
                if (value != null) {
                    return Optional.of(value.toString());
                }
            }
        }

        return Optional.empty();
    }

    private static Component createBeeNameComponent(String beeType) {
        Identifier beeId = Identifier.tryParse(beeType);
        // Malformed bee type string (e.g. leftover NBT from an older mod version):
        // fall back to a literal so the tooltip still shows something.
        if (beeId == null) return Component.literal(beeType);
        return createBeeNameComponent(beeId);
    }

    private static Component createBeeNameComponent(Identifier beeId) {
        String beePath = beeId.getPath();
        if (!MOD_ID.equals(beeId.getNamespace())) {
            return Component.translatable("entity." + beeId.getNamespace() + "." + beePath);
        }

        if (beePath.endsWith("_bee")) {
            beePath = beePath.substring(0, beePath.length() - 4);
        }

        return Component.translatable("entity.productivebees." + beePath + "_bee");
    }

    private static String stripEnglishBeeSuffix(String name) {
        return name.endsWith(ENGLISH_BEE_SUFFIX)
                ? name.substring(0, name.length() - ENGLISH_BEE_SUFFIX.length())
                : name;
    }

    /**
     * Returns true if {@code ingredient} is an instance of {@code BeeIngredient} or any
     * subclass of it, without requiring a compile-time dependency on Productive Bees.
     * The resolved {@link Class} is cached so reflection is only paid once.
     */
    private static boolean isBeeIngredient(Object ingredient) {
        Optional<Class<?>> beeClass = classCache.computeIfAbsent(BEE_INGREDIENT, k -> {
            try {
                return Optional.of(Class.forName(k, false, ingredient.getClass().getClassLoader()));
            } catch (ClassNotFoundException ignored) {
                return Optional.empty();
            }
        });
        return beeClass.isPresent() && beeClass.get().isAssignableFrom(ingredient.getClass());
    }

    private static Optional<Identifier> invokeIdentifier(Object target, String methodName) {
        return invokeMethod(target, methodName)
                .filter(Identifier.class::isInstance)
                .map(Identifier.class::cast);
    }

    private static Optional<Object> invokeMethod(Object target, String methodName) {
        String cacheKey = target.getClass().getName() + "#" + methodName;
        Optional<Method> method = methodCache.computeIfAbsent(cacheKey,
                k -> findMethod(target.getClass(), methodName));
        return method.flatMap(m -> {
            try {
                return Optional.ofNullable(m.invoke(target));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Method> findMethod(Class<?> startClass, String methodName) {
        try {
            Method method = startClass.getMethod(methodName);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (NoSuchMethodException ignored) {
            // Fall through to declared-method lookup for non-public members.
        } catch (SecurityException ignored) {
            return Optional.empty();
        }

        Class<?> current = startClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
