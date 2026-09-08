package com.starskyxiii.polyglotjeiprobe;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class FluidRuntimeProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CAPACITY = 4000;
    private static final int AMOUNT = 1000;
    private static final String GTM_MOD_ID = "gtceu";
    private static final String LDLIB_MOD_ID = "ldlib";
    private static final String GTM_WIDGET = "com.gregtechceu.gtceu.api.gui.widget.TankWidget";
    private static final String LDLIB_WIDGET = "com.lowdragmc.lowdraglib.gui.widget.TankWidget";
    private static final String LDLIB_STORAGE = "com.lowdragmc.lowdraglib.misc.FluidStorage";
    private static final String LDLIB_STORAGE_INTERFACE = "com.lowdragmc.lowdraglib.side.fluid.IFluidStorage";
    private static final String LDLIB_FLUID_STACK = "com.lowdragmc.lowdraglib.side.fluid.FluidStack";

    private FluidRuntimeProbe() {
    }

    static int run(boolean expectSecondary) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return abort("client_player", "actual=absent");
        }
        String languageCode = minecraft.options.languageCode;
        if (!"zh_tw".equalsIgnoreCase(languageCode)) {
            return abort("primary_language", "expected=zh_tw actual=" + languageCode);
        }
        if (!ModList.get().isLoaded(GTM_MOD_ID) || !ModList.get().isLoaded(LDLIB_MOD_ID)) {
            return abort("host_mods", "gtceu=" + version(GTM_MOD_ID) + " ldlib=" + version(LDLIB_MOD_ID));
        }

        LOGGER.info(
                "[PolyglotFluidRuntimeProbe] RUN_START minecraft={} forge={} gtceu={} ldlib={} polyglot={} probe={} primaryLanguage={} expectedSecondaryLanguage=en_us expectSecondary={}",
                version("minecraft"), version("forge"), version(GTM_MOD_ID), version(LDLIB_MOD_ID),
                version("polyglottooltip"), version(JeiRuntimeProbe.MOD_ID), languageCode, expectSecondary);

        long started = System.nanoTime();
        List<String> failures = new ArrayList<>();
        int completed = 0;
        completed += runCase("gtm_water", failures,
                () -> verifyFilled("gtm_water", gtmTooltip(Fluids.WATER),
                        new FluidStack(Fluids.WATER, AMOUNT).getDisplayName(), "Water",
                        "gtceu.fluid.amount", new Object[]{"1,000", "4,000"}, expectSecondary, failures));
        completed += runCase("gtm_lava", failures,
                () -> verifyFilled("gtm_lava", gtmTooltip(Fluids.LAVA),
                        new FluidStack(Fluids.LAVA, AMOUNT).getDisplayName(), "Lava",
                        "gtceu.fluid.amount", new Object[]{"1,000", "4,000"}, expectSecondary, failures));
        completed += runCase("gtm_empty", failures,
                () -> verifyEmpty("gtm_empty", gtmTooltip(null),
                        "gtceu.fluid.empty", "gtceu.fluid.amount", new Object[]{0, "4,000"}, failures));
        completed += runCase("ldlib_water", failures,
                () -> verifyFilled("ldlib_water", ldlibTooltip(Fluids.WATER),
                        ldlibDisplayName(Fluids.WATER), "Water",
                        "ldlib.fluid.amount", new Object[]{1000L, 4000L}, expectSecondary, failures));
        completed += runCase("ldlib_lava", failures,
                () -> verifyFilled("ldlib_lava", ldlibTooltip(Fluids.LAVA),
                        ldlibDisplayName(Fluids.LAVA), "Lava",
                        "ldlib.fluid.amount", new Object[]{1000L, 4000L}, expectSecondary, failures));
        completed += runCase("ldlib_empty", failures,
                () -> verifyEmpty("ldlib_empty", ldlibTooltip(null),
                        "ldlib.fluid.empty", "ldlib.fluid.amount", new Object[]{0, 4000L}, failures));

        boolean passed = completed == 6 && failures.isEmpty();
        LOGGER.info(
                "[PolyglotFluidRuntimeProbe] RUN_RESULT status={} expectedCases=6 casesCompleted={} failed={} elapsedMs={}",
                passed ? "PASS" : "FAIL", completed, failures.size(), elapsedMilliseconds(started));
        minecraft.player.sendSystemMessage(Component.literal(passed
                ? "Fluid probe passed 6 cases; see latest.log for structured results."
                : "Fluid probe failed " + failures.size() + " assertion(s); see latest.log."));
        return passed ? 1 : 0;
    }

    private static int runCase(String name, List<String> failures, ThrowingRunnable assertion) {
        try {
            assertion.run();
            return 1;
        } catch (Throwable exception) {
            String failure = "case=" + name + " exception=" + exception.getClass().getName()
                    + " message=" + String.valueOf(exception.getMessage());
            failures.add(failure);
            LOGGER.error("[PolyglotFluidRuntimeProbe] CASE name={} status=FAIL {}", name, failure, exception);
            return 0;
        }
    }

    private static void verifyFilled(String caseName,
                                     List<Component> lines,
                                     Component expectedPrimary,
                                     String expectedSecondary,
                                     String amountKey,
                                     Object[] amountArgs,
                                     boolean expectSecondary,
                                     List<String> failures) {
        List<String> texts = lines.stream().map(Component::getString).toList();
        int expectedAmountIndex = expectSecondary ? 2 : 1;
        boolean primaryFirst = !texts.isEmpty() && texts.get(0).equals(expectedPrimary.getString());
        long secondaryCount = texts.stream().filter(expectedSecondary::equals).count();
        boolean secondaryCorrect = expectSecondary
                ? texts.size() > 1 && texts.get(1).equals(expectedSecondary) && secondaryCount == 1
                : secondaryCount == 0;
        boolean amountPreserved = lines.size() > expectedAmountIndex
                && hasTranslation(lines.get(expectedAmountIndex), amountKey, amountArgs);
        boolean passed = primaryFirst && secondaryCorrect && amountPreserved;
        logCase(caseName, lines, expectedPrimary.getString(), expectedSecondary, expectSecondary,
                amountKey, amountArgs, passed);
        if (!passed) {
            String failure = "case=" + caseName + " primaryFirst=" + primaryFirst
                    + " secondaryCorrect=" + secondaryCorrect + " secondaryCount=" + secondaryCount
                    + " amountPreserved=" + amountPreserved + " lines=" + describe(lines);
            failures.add(failure);
            LOGGER.error("[PolyglotFluidRuntimeProbe] ASSERTION_FAILED {}", failure);
        }
    }

    private static void verifyEmpty(String caseName,
                                    List<Component> lines,
                                    String emptyKey,
                                    String amountKey,
                                    Object[] amountArgs,
                                    List<String> failures) {
        boolean emptyFirst = !lines.isEmpty() && hasTranslation(lines.get(0), emptyKey, new Object[0]);
        boolean noFluidName = lines.stream().map(Component::getString)
                .noneMatch(text -> text.equals("Water") || text.equals("Lava"));
        boolean amountPreserved = lines.size() > 1 && hasTranslation(lines.get(1), amountKey, amountArgs);
        boolean passed = emptyFirst && noFluidName && amountPreserved;
        logCase(caseName, lines, emptyKey, "none", false, amountKey, amountArgs, passed);
        if (!passed) {
            String failure = "case=" + caseName + " emptyFirst=" + emptyFirst
                    + " noFluidName=" + noFluidName + " amountPreserved=" + amountPreserved
                    + " lines=" + describe(lines);
            failures.add(failure);
            LOGGER.error("[PolyglotFluidRuntimeProbe] ASSERTION_FAILED {}", failure);
        }
    }

    private static List<Component> gtmTooltip(Fluid fluid) throws ReflectiveOperationException {
        FluidTank tank = new FluidTank(CAPACITY);
        if (fluid != null) {
            tank.setFluid(new FluidStack(fluid, AMOUNT));
        }
        Class<?> widgetClass = Class.forName(GTM_WIDGET);
        Constructor<?> constructor = widgetClass.getConstructor(
                IFluidHandler.class, int.class, int.class, int.class, int.class, boolean.class, boolean.class);
        Object widget = constructor.newInstance(tank, 0, 0, 18, 18, false, false);
        return tooltip(widgetClass, widget);
    }

    private static List<Component> ldlibTooltip(Fluid fluid) throws ReflectiveOperationException {
        Class<?> storageClass = Class.forName(LDLIB_STORAGE);
        Class<?> storageInterface = Class.forName(LDLIB_STORAGE_INTERFACE);
        Class<?> stackClass = Class.forName(LDLIB_FLUID_STACK);
        Object storage = storageClass.getConstructor(long.class).newInstance((long) CAPACITY);
        Object stack = fluid == null
                ? stackClass.getMethod("empty").invoke(null)
                : stackClass.getMethod("create", Fluid.class, long.class).invoke(null, fluid, (long) AMOUNT);
        storageClass.getMethod("setFluid", stackClass).invoke(storage, stack);

        Class<?> widgetClass = Class.forName(LDLIB_WIDGET);
        Constructor<?> constructor = widgetClass.getConstructor(
                storageInterface, int.class, int.class, int.class, int.class, boolean.class, boolean.class);
        Object widget = constructor.newInstance(storage, 0, 0, 18, 18, false, false);
        return tooltip(widgetClass, widget);
    }

    private static Component ldlibDisplayName(Fluid fluid) throws ReflectiveOperationException {
        Class<?> stackClass = Class.forName(LDLIB_FLUID_STACK);
        Object stack = stackClass.getMethod("create", Fluid.class, long.class)
                .invoke(null, fluid, (long) AMOUNT);
        return (Component) stackClass.getMethod("getDisplayName").invoke(stack);
    }

    @SuppressWarnings("unchecked")
    private static List<Component> tooltip(Class<?> widgetClass, Object widget) throws ReflectiveOperationException {
        widgetClass.getMethod("setClientSideWidget").invoke(widget);
        return List.copyOf((List<Component>) widgetClass.getMethod("getFullTooltipTexts").invoke(widget));
    }

    private static boolean hasTranslation(Component component, String key, Object[] expectedArgs) {
        if (!(component.getContents() instanceof TranslatableContents contents)
                || !contents.getKey().equals(key)) {
            return false;
        }
        return Arrays.deepEquals(contents.getArgs(), expectedArgs);
    }

    private static void logCase(String name,
                                List<Component> lines,
                                String expectedPrimary,
                                String expectedSecondary,
                                boolean expectSecondary,
                                String amountKey,
                                Object[] amountArgs,
                                boolean passed) {
        LOGGER.info(
                "[PolyglotFluidRuntimeProbe] CASE name={} expectedPrimary={} expectedSecondary={} expectSecondary={} expectedAmountKey={} expectedAmountArgs={} actualLines={} status={}",
                name, quote(expectedPrimary), quote(expectedSecondary), expectSecondary, amountKey,
                Arrays.deepToString(amountArgs), describe(lines), passed ? "PASS" : "FAIL");
    }

    private static String describe(List<Component> lines) {
        List<String> description = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            String key = "literal";
            String args = "[]";
            if (line.getContents() instanceof TranslatableContents translatable) {
                key = translatable.getKey();
                args = Arrays.deepToString(translatable.getArgs());
            }
            description.add(index + ":{text=" + quote(line.getString()) + ",key=" + key + ",args=" + args + "}");
        }
        return description.toString();
    }

    private static int abort(String precondition, String detail) {
        LOGGER.error("[PolyglotFluidRuntimeProbe] RUN_RESULT status=FAIL casesCompleted=0 failed=1 precondition={} {}",
                precondition, detail);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("Fluid probe could not run: " + precondition + " " + detail));
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
