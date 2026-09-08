package com.starskyxiii.polyglotjeiprobe;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class JeiFluidRuntimeProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HELPER_CLASS = "mezz.jei.forge.platform.FluidHelper";
    private static final String TOOLTIP_BUILDER = "mezz.jei.api.gui.builder.ITooltipBuilder";
    private static final String JEI_TOOLTIP = "mezz.jei.common.gui.JeiTooltip";

    private JeiFluidRuntimeProbe() {
    }

    static int run(boolean expectSecondary) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return abort("client_player", "actual=absent");
        }
        String languageCode = minecraft.options.languageCode;
        if (!"en_us".equalsIgnoreCase(languageCode)) {
            return abort("primary_language", "expected=en_us actual=" + languageCode);
        }
        if (!ModList.get().isLoaded("jei") || !ModList.get().isLoaded("polyglottooltip")) {
            return abort("required_mods", "jei=" + version("jei")
                    + " polyglot=" + version("polyglottooltip"));
        }

        TooltipInvoker invoker;
        try {
            invoker = TooltipInvoker.create();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.error("[PolyglotJeiFluidRuntimeProbe] ENTRYPOINT_RESOLUTION_FAILED", exception);
            return abort("jei_fluid_entrypoint", exception.getClass().getName()
                    + ":" + String.valueOf(exception.getMessage()));
        }

        LOGGER.info(
                "[PolyglotJeiFluidRuntimeProbe] RUN_START minecraft={} forge={} jei={} polyglot={} probe={} primaryLanguage={} expectedSecondaryLanguage=zh_cn expectSecondary={} typedEntrypoint={} bridgeEntrypoint={}",
                version("minecraft"), version("forge"), version("jei"), version("polyglottooltip"),
                version(JeiRuntimeProbe.MOD_ID), languageCode, expectSecondary,
                invoker.typedEntrypoint(), invoker.bridgeEntrypoint());

        long started = System.nanoTime();
        List<String> failures = new ArrayList<>();
        int completed = 0;
        completed += runCase("water_normal", failures,
                () -> verifyFilled(invoker, "water_normal", Fluids.WATER, TooltipFlag.NORMAL,
                        "Water", "水", "minecraft:water", expectSecondary, failures));
        completed += runCase("water_advanced", failures,
                () -> verifyFilled(invoker, "water_advanced", Fluids.WATER, TooltipFlag.ADVANCED,
                        "Water", "水", "minecraft:water", expectSecondary, failures));
        completed += runCase("lava_normal", failures,
                () -> verifyFilled(invoker, "lava_normal", Fluids.LAVA, TooltipFlag.NORMAL,
                        "Lava", "熔岩", "minecraft:lava", expectSecondary, failures));
        completed += runCase("lava_advanced", failures,
                () -> verifyFilled(invoker, "lava_advanced", Fluids.LAVA, TooltipFlag.ADVANCED,
                        "Lava", "熔岩", "minecraft:lava", expectSecondary, failures));
        completed += runCase("empty_normal", failures,
                () -> verifyEmpty(invoker, "empty_normal", TooltipFlag.NORMAL, failures));
        completed += runCase("empty_advanced", failures,
                () -> verifyEmpty(invoker, "empty_advanced", TooltipFlag.ADVANCED, failures));

        boolean passed = completed == 6 && failures.isEmpty();
        LOGGER.info(
                "[PolyglotJeiFluidRuntimeProbe] RUN_RESULT status={} expectedCases=6 casesCompleted={} failed={} elapsedMs={} typedEntrypoint={} bridgeEntrypoint={}",
                passed ? "PASS" : "FAIL", completed, failures.size(), elapsedMilliseconds(started),
                invoker.typedEntrypoint(), invoker.bridgeEntrypoint());
        minecraft.player.sendSystemMessage(Component.literal(passed
                ? "JEI fluid probe passed 6 cases; see latest.log for structured results."
                : "JEI fluid probe failed " + failures.size() + " assertion(s); see latest.log."));
        return passed ? 1 : 0;
    }

    private static void verifyFilled(TooltipInvoker invoker,
                                     String caseName,
                                     Fluid fluid,
                                     TooltipFlag flag,
                                     String expectedPrimary,
                                     String expectedSecondary,
                                     String expectedRegistryId,
                                     boolean expectSecondary,
                                     List<String> failures) throws ReflectiveOperationException {
        FluidStack stack = new FluidStack(fluid, 1000);
        Component actualDisplayName = invoker.displayName(stack);
        TooltipResult result = invoker.tooltipBoth(stack, flag);
        List<Component> lines = result.typedLines();
        List<Component> bridgeLines = result.bridgeLines();
        List<String> texts = lines.stream().map(Component::getString).toList();
        boolean primaryFirst = actualDisplayName.getString().equals(expectedPrimary)
                && !texts.isEmpty() && texts.get(0).equals(expectedPrimary);
        long secondaryCount = texts.stream().filter(expectedSecondary::equals).count();
        boolean secondaryCorrect = expectSecondary
                ? texts.size() > 1 && texts.get(1).equals(expectedSecondary) && secondaryCount == 1
                : secondaryCount == 0;
        int registryIndex = expectSecondary ? 2 : 1;
        boolean advancedCorrect = flag.isAdvanced()
                ? lines.size() > registryIndex
                    && lines.get(registryIndex).getString().equals(expectedRegistryId)
                    && lines.get(registryIndex).getStyle().getColor() != null
                    && lines.get(registryIndex).getStyle().getColor().getValue()
                        == ChatFormatting.DARK_GRAY.getColor()
                : texts.stream().noneMatch(expectedRegistryId::equals);
        long bridgeSecondaryCount = bridgeLines.stream().map(Component::getString)
                .filter(expectedSecondary::equals).count();
        boolean bridgeEquivalent = lines.equals(bridgeLines)
                && (!expectSecondary || bridgeSecondaryCount == 1);
        boolean passed = primaryFirst && secondaryCorrect && advancedCorrect && bridgeEquivalent;
        logCase(caseName, flag, lines, bridgeLines, expectedPrimary, expectedSecondary, expectSecondary,
                expectedRegistryId, passed);
        if (!passed) {
            String failure = "case=" + caseName + " primaryFirst=" + primaryFirst
                    + " secondaryCorrect=" + secondaryCorrect + " secondaryCount=" + secondaryCount
                    + " advancedCorrect=" + advancedCorrect
                    + " bridgeEquivalent=" + bridgeEquivalent
                    + " bridgeSecondaryCount=" + bridgeSecondaryCount
                    + " typedLines=" + describe(lines) + " bridgeLines=" + describe(bridgeLines);
            failures.add(failure);
            LOGGER.error("[PolyglotJeiFluidRuntimeProbe] ASSERTION_FAILED {}", failure);
        }
    }

    private static void verifyEmpty(TooltipInvoker invoker,
                                    String caseName,
                                    TooltipFlag flag,
                                    List<String> failures) throws ReflectiveOperationException {
        TooltipResult result = invoker.tooltipBoth(FluidStack.EMPTY, flag);
        List<Component> lines = result.typedLines();
        List<Component> bridgeLines = result.bridgeLines();
        boolean passed = lines.isEmpty() && bridgeLines.isEmpty() && lines.equals(bridgeLines);
        logCase(caseName, flag, lines, bridgeLines, "none", "none", false, "none", passed);
        if (!passed) {
            String failure = "case=" + caseName + " expectedLines=[] typedLines=" + describe(lines)
                    + " bridgeLines=" + describe(bridgeLines);
            failures.add(failure);
            LOGGER.error("[PolyglotJeiFluidRuntimeProbe] ASSERTION_FAILED {}", failure);
        }
    }

    private static int runCase(String name, List<String> failures, ThrowingRunnable assertion) {
        try {
            assertion.run();
            return 1;
        } catch (Throwable exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : exception;
            String failure = "case=" + name + " exception=" + cause.getClass().getName()
                    + " message=" + String.valueOf(cause.getMessage());
            failures.add(failure);
            LOGGER.error("[PolyglotJeiFluidRuntimeProbe] CASE name={} status=FAIL {}", name, failure, cause);
            return 0;
        }
    }

    private static void logCase(String name,
                                TooltipFlag flag,
                                List<Component> lines,
                                List<Component> bridgeLines,
                                String expectedPrimary,
                                String expectedSecondary,
                                boolean expectSecondary,
                                String expectedRegistryId,
                                boolean passed) {
        LOGGER.info(
                "[PolyglotJeiFluidRuntimeProbe] CASE name={} flag={} expectedPrimary={} expectedSecondary={} expectSecondary={} expectedRegistryId={} typedLines={} bridgeLines={} status={}",
                name, flag.isAdvanced() ? "ADVANCED" : "NORMAL", quote(expectedPrimary),
                quote(expectedSecondary), expectSecondary, expectedRegistryId, describe(lines), describe(bridgeLines),
                passed ? "PASS" : "FAIL");
    }

    private static String describe(List<Component> lines) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            result.add(index + ":{text=" + quote(line.getString())
                    + ",style=" + line.getStyle() + ",contents=" + line.getContents() + "}");
        }
        return result.toString();
    }

    private static int abort(String precondition, String detail) {
        LOGGER.error("[PolyglotJeiFluidRuntimeProbe] RUN_RESULT status=FAIL casesCompleted=0 failed=1 precondition={} {}",
                precondition, detail);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("JEI fluid probe could not run: " + precondition + " " + detail));
        }
        return 0;
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(ModContainer::getModInfo)
                .map(info -> info.getVersion().toString())
                .orElse("absent");
    }

    private static double elapsedMilliseconds(long start) {
        return Math.round((System.nanoTime() - start) / 100_000.0) / 10.0;
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private record TooltipInvoker(Object helper,
                                  Method tooltipMethod,
                                  Method bridgeMethod,
                                  Method displayNameMethod,
                                  Constructor<?> builderConstructor,
                                  Method builderComponentsMethod,
                                  String typedEntrypoint,
                                  String bridgeEntrypoint) {
        private static TooltipInvoker create() throws ReflectiveOperationException {
            Class<?> helperClass = Class.forName(HELPER_CLASS);
            Object helper = helperClass.getConstructor().newInstance();
            List<Method> typedMethods = Arrays.stream(helperClass.getMethods())
                    .filter(method -> method.getName().equals("getTooltip"))
                    .filter(method -> method.getParameterCount() == 3)
                    .filter(method -> method.getParameterTypes()[1] == FluidStack.class)
                    .filter(method -> method.getParameterTypes()[2] == TooltipFlag.class)
                    .sorted(Comparator.comparing(Method::toGenericString))
                    .toList();
            if (typedMethods.size() != 1) {
                throw new NoSuchMethodException("expected one typed getTooltip, found " + typedMethods);
            }
            Method tooltipMethod = typedMethods.get(0);
            if (tooltipMethod.isBridge() || tooltipMethod.isSynthetic()) {
                throw new NoSuchMethodException("typed getTooltip unexpectedly bridge/synthetic: " + tooltipMethod);
            }
            List<Method> bridges = Arrays.stream(helperClass.getMethods())
                    .filter(method -> method.getName().equals("getTooltip"))
                    .filter(Method::isBridge)
                    .filter(method -> method.getParameterCount() == 3)
                    .filter(method -> method.getParameterTypes()[0] == tooltipMethod.getParameterTypes()[0])
                    .filter(method -> method.getParameterTypes()[1] == Object.class)
                    .filter(method -> method.getParameterTypes()[2] == TooltipFlag.class)
                    .toList();
            if (bridges.size() != 1) {
                throw new NoSuchMethodException("expected one erased getTooltip bridge, found " + bridges);
            }
            Method bridgeMethod = bridges.get(0);
            Method displayName = helperClass.getMethod("getDisplayName", FluidStack.class);

            Class<?> firstParameter = tooltipMethod.getParameterTypes()[0];
            if (firstParameter == List.class) {
                return new TooltipInvoker(helper, tooltipMethod, bridgeMethod, displayName, null, null,
                        descriptor(tooltipMethod), descriptor(bridgeMethod));
            }
            if (!firstParameter.getName().equals(TOOLTIP_BUILDER)) {
                throw new NoSuchMethodException("unsupported typed getTooltip parameter: " + firstParameter.getName());
            }
            Class<?> builderClass = Class.forName(JEI_TOOLTIP);
            Constructor<?> constructor = builderClass.getConstructor();
            Method components = builderClass.getMethod("toLegacyToComponents");
            return new TooltipInvoker(helper, tooltipMethod, bridgeMethod, displayName, constructor, components,
                    descriptor(tooltipMethod), descriptor(bridgeMethod));
        }

        private TooltipResult tooltipBoth(FluidStack stack, TooltipFlag flag) throws ReflectiveOperationException {
            return new TooltipResult(invokeTooltip(tooltipMethod, stack, flag),
                    invokeTooltip(bridgeMethod, stack, flag));
        }

        private List<Component> invokeTooltip(Method method,
                                              FluidStack stack,
                                              TooltipFlag flag) throws ReflectiveOperationException {
            if (builderConstructor == null) {
                List<Component> lines = new ArrayList<>();
                method.invoke(helper, lines, stack, flag);
                return List.copyOf(lines);
            }
            Object builder = builderConstructor.newInstance();
            method.invoke(helper, builder, stack, flag);
            @SuppressWarnings("unchecked")
            List<Component> lines = (List<Component>) builderComponentsMethod.invoke(builder);
            return List.copyOf(lines);
        }

        private Component displayName(FluidStack stack) throws ReflectiveOperationException {
            return (Component) displayNameMethod.invoke(helper, stack);
        }

        private static String descriptor(Method method) {
            return method.getDeclaringClass().getName() + "#" + method.getName()
                    + Arrays.toString(Arrays.stream(method.getParameterTypes())
                    .map(Class::getName).toArray(String[]::new));
        }
    }

    private record TooltipResult(List<Component> typedLines, List<Component> bridgeLines) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
