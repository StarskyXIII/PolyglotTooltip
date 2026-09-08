package com.starskyxiii.polyglottooltip.integration.mekanism;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MekanismTooltipHelper {

    private MekanismTooltipHelper() {
    }

    public static void addSecondaryName(List<Component> tooltip, Component sourceName) {
        SecondaryTooltipUtil.insertSecondaryName(tooltip, sourceName);
    }

    public static List<Component> withSecondaryName(List<Component> tooltip, Component sourceName) {
        List<Component> copy = new ArrayList<>(tooltip);
        addSecondaryName(copy, sourceName);
        return copy;
    }

    public static Optional<Component> getChemicalNameFromStack(Object stack) {
        return getChemicalNameFromTypeHolder(stack);
    }

    public static Optional<Component> getChemicalName(Object chemical) {
        return toComponent(chemical);
    }

    public static Optional<Component> getChemicalNameFromTank(Object tank) {
        return getChemicalNameFromTypeHolder(tank);
    }

    public static Optional<Component> getChemicalNameFromGauge(Object gauge) {
        return invokeNoArg(gauge, "getTank").flatMap(MekanismTooltipHelper::getChemicalNameFromTank);
    }

    private static Optional<Component> getChemicalNameFromTypeHolder(Object holder) {
        return invokeNoArg(holder, "getType").flatMap(MekanismTooltipHelper::toComponent);
    }

    private static Optional<Component> toComponent(Object chemical) {
        return invokeNoArg(chemical, "getTextComponent")
                .filter(Component.class::isInstance)
                .map(Component.class::cast);
    }

    private static Optional<Object> invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }
}
