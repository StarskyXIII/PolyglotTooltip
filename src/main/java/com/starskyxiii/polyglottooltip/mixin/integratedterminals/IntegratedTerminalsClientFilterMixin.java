package com.starskyxiii.polyglottooltip.mixin.integratedterminals;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.search.IntegratedTerminalSearchHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(targets = "org.cyclops.integratedterminals.core.terminalstorage.TerminalStorageTabIngredientComponentClient", remap = false)
public abstract class IntegratedTerminalsClientFilterMixin {

    @Unique
    private static final Map<Class<?>, Method> POLYGLOT$INSTANCE_GETTERS = new ConcurrentHashMap<>();

    @Unique
    private static final Map<Class<?>, Method> POLYGLOT$CRAFTING_OPTION_GETTERS = new ConcurrentHashMap<>();

    @Unique
    private static final Map<Class<?>, Method> POLYGLOT$WRAPPED_CRAFTING_OPTION_GETTERS = new ConcurrentHashMap<>();

    @Unique
    private final Int2ObjectMap<List<?>> polyglot$processedViews = new Int2ObjectOpenHashMap<>();

    @Shadow
    public abstract String getInstanceFilter(int channel);

    @Shadow
    public abstract List<?> createUnfilteredIngredientsView(int channel);

    @Shadow
    public abstract Predicate<Object> getInstanceFilterMetadata();

    @Shadow
    public abstract Comparator<Object> getInstanceSorter();

    @Shadow
    protected abstract Stream<?> transformIngredientsView(Stream<?> ingredientStream);

    @Shadow
    private Int2ObjectMap<List<?>> filteredIngredientsViews;

    @Inject(method = "getFilteredIngredientsView(I)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void polyglot$filterVisibleList(int channel, CallbackInfoReturnable<List<?>> cir) {
        String query = getInstanceFilter(channel);
        if (query == null || query.isBlank()) {
            return;
        }

        List<?> current = cir.getReturnValue();
        if (current != null && polyglot$processedViews.get(channel) == current) {
            return;
        }

        List<?> rebuilt = createUnfilteredIngredientsView(channel);
        if (rebuilt == null || rebuilt.isEmpty()) {
            return;
        }

        List<Object> transformed = transformIngredientsView(rebuilt.stream())
                .collect(Collectors.toCollection(() -> new ArrayList<>(rebuilt.size())));
        boolean sawItemStack = transformed.stream()
                .map(IntegratedTerminalsClientFilterMixin::getInstanceReflective)
                .anyMatch(ItemStack.class::isInstance);
        if (!sawItemStack) {
            return;
        }

        List<Object> filtered = transformed.stream()
                .filter(element -> polyglot$matchesElement(element, query))
                .filter(getInstanceFilterMetadata())
                .collect(Collectors.toCollection(ArrayList::new));

        polyglot$sort(filtered);
        filteredIngredientsViews.put(channel, filtered);
        polyglot$processedViews.put(channel, filtered);
        cir.setReturnValue(filtered);
    }

    @Unique
    private void polyglot$sort(List<Object> ingredients) {
        Comparator<Object> sorter = getInstanceSorter();
        if (sorter == null || ingredients.isEmpty()) {
            return;
        }

        ingredients.sort((left, right) -> polyglot$compareElements(left, right, sorter));
    }

    @Unique
    private static int polyglot$compareElements(Object left, Object right, Comparator<Object> sorter) {
        try {
            Object leftInstance = getInstanceReflective(left);
            Object rightInstance = getInstanceReflective(right);
            int comp = sorter.compare(leftInstance, rightInstance);
            if (comp != 0) {
                return comp;
            }

            Comparable<?> leftCraftingOption = getCraftingOptionComparable(left);
            Comparable<?> rightCraftingOption = getCraftingOptionComparable(right);
            if (leftCraftingOption == null) {
                return rightCraftingOption == null ? 0 : -1;
            }
            if (rightCraftingOption == null) {
                return 1;
            }

            @SuppressWarnings("unchecked")
            Comparable<Object> castLeft = (Comparable<Object>) leftCraftingOption;
            return castLeft.compareTo(rightCraftingOption);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            PolyglotTooltip.LOGGER.debug("Failed to sort rebuilt Integrated Terminals result", e);
            return 0;
        }
    }

    @Unique
    private static Object getInstanceReflective(Object element) {
        if (element == null) {
            return null;
        }
        try {
            Method method = POLYGLOT$INSTANCE_GETTERS.computeIfAbsent(element.getClass(), clazz -> {
                try {
                    return clazz.getMethod("getInstance");
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            });
            if (method == null) {
                return null;
            }
            return method.invoke(element);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Unique
    private static Comparable<?> getCraftingOptionComparable(Object element) throws ReflectiveOperationException {
        if (element == null) {
            return null;
        }

        Method method = POLYGLOT$CRAFTING_OPTION_GETTERS.computeIfAbsent(element.getClass(), clazz -> {
            try {
                return clazz.getMethod("getCraftingOption");
            } catch (ReflectiveOperationException e) {
                return null;
            }
        });
        if (method == null) {
            return null;
        }

        Object wrapper = method.invoke(element);
        if (wrapper == null) {
            return null;
        }

        Method craftingOptionMethod = POLYGLOT$WRAPPED_CRAFTING_OPTION_GETTERS.computeIfAbsent(wrapper.getClass(), clazz -> {
            try {
                return clazz.getMethod("getCraftingOption");
            } catch (ReflectiveOperationException e) {
                return null;
            }
        });
        if (craftingOptionMethod == null) {
            return null;
        }

        Object craftingOption = craftingOptionMethod.invoke(wrapper);
        if (craftingOption instanceof Comparable<?> comparable) {
            return comparable;
        }
        return null;
    }

    @Unique
    private static boolean polyglot$matchesElement(Object element, String query) {
        Object instance = getInstanceReflective(element);
        if (instance instanceof ItemStack stack) {
            return IntegratedTerminalSearchHelper.matches(stack, query);
        }
        return true;
    }
}
