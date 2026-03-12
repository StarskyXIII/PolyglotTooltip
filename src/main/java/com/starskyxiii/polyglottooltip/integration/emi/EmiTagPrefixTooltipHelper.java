package com.starskyxiii.polyglottooltip.integration.emi;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection helpers for GTOLib's custom EMI wildcard stack type
 * ({@code com.gtolib.api.emi.stack.EmiTagprefixStack}).
 *
 * <p>This class cannot be used as a Mixin {@code target} because GTOLib applies
 * its own bytecode transformation to {@code EmiTagprefixStack} at runtime,
 * making the pre-transform class name unavailable for standard Mixin resolution.
 * Instead, the secondary name is injected from generic EMI slot hooks that fire
 * for every ingredient type, and the actual name is extracted here via reflection
 * after a class-name guard confirms the ingredient is an {@code EmiTagprefixStack}.
 */
public final class EmiTagPrefixTooltipHelper {

    private static final String TAG_PREFIX_STACK_CLASS = "com.gtolib.api.emi.stack.EmiTagprefixStack";
    private static volatile Method tagPrefixGetNameMethod;
    private static volatile Method slotGetStackMethod;

    private EmiTagPrefixTooltipHelper() {
    }

    public static List<ClientTooltipComponent> appendSecondaryName(Object ingredient,
                                                                   List<ClientTooltipComponent> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return tooltip;
        }

        Component sourceName = tryResolveName(ingredient);
        if (sourceName == null) {
            return tooltip;
        }

        List<Component> secondaryLines = SecondaryTooltipUtil.getSecondaryNameLines(sourceName);
        if (secondaryLines.isEmpty()) {
            return tooltip;
        }

        List<ClientTooltipComponent> copy = new ArrayList<>(tooltip);
        int insertAt = copy.isEmpty() ? 0 : 1;
        for (int i = secondaryLines.size() - 1; i >= 0; i--) {
            copy.add(insertAt, ClientTooltipComponent.create(secondaryLines.get(i).getVisualOrderText()));
        }
        return copy;
    }

    public static Object getSlotIngredient(Object slotWidget) {
        if (slotWidget == null) {
            return null;
        }
        try {
            Method method = slotGetStackMethod;
            if (method == null) {
                synchronized (EmiTagPrefixTooltipHelper.class) {
                    method = slotGetStackMethod;
                    if (method == null) {
                        method = slotWidget.getClass().getMethod("getStack");
                        slotGetStackMethod = method;
                    }
                }
            }
            return method.invoke(slotWidget);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Component tryResolveName(Object ingredient) {
        if (ingredient == null || !TAG_PREFIX_STACK_CLASS.equals(ingredient.getClass().getName())) {
            return null;
        }
        try {
            Method method = tagPrefixGetNameMethod;
            if (method == null) {
                synchronized (EmiTagPrefixTooltipHelper.class) {
                    method = tagPrefixGetNameMethod;
                    if (method == null) {
                        method = ingredient.getClass().getMethod("getName");
                        tagPrefixGetNameMethod = method;
                    }
                }
            }
            Object result = method.invoke(ingredient);
            return result instanceof Component component ? component : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
