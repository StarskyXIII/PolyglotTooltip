package com.starskyxiii.polyglotjeiprobe;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Mod(JeiRuntimeProbe.MOD_ID)
@JeiPlugin
public final class JeiRuntimeProbe implements IModPlugin {
    public static final String MOD_ID = "polyglotjeiruntimeprobe";
    private static final String COMMAND = "polyglot_jei_probe";
    private static final String FLUID_COMMAND = "polyglot_fluid_probe";
    private static final String JEI_FLUID_COMMAND = "polyglot_jei_fluid_probe";
    private static final String OBSERVE_COMMAND = "polyglot_jei_observe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<RuntimeHandle> RUNTIME = new AtomicReference<>();
    private static final AtomicLong RUNTIME_GENERATION = new AtomicLong();
    private static final AtomicLong OBSERVATION_SEQUENCE = new AtomicLong();
    private static final AtomicReference<PendingObservation> PENDING_OBSERVATION = new AtomicReference<>();

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, "runtime");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        long generation = RUNTIME_GENERATION.incrementAndGet();
        RUNTIME.set(new RuntimeHandle(runtime, generation));
        LOGGER.info("[PolyglotJeiRuntimeProbe] RUNTIME_AVAILABLE generation={}", generation);
    }

    @Override
    public void onRuntimeUnavailable() {
        RuntimeHandle previous = RUNTIME.getAndSet(null);
        PENDING_OBSERVATION.set(null);
        LOGGER.info(
                "[PolyglotJeiRuntimeProbe] RUNTIME_UNAVAILABLE generation={}",
                previous == null ? 0 : previous.generation());
    }

    private static int startObservation() {
        RuntimeHandle handle = RUNTIME.get();
        if (handle == null) {
            return abort("jei_runtime", "actual=unavailable");
        }

        long observation = OBSERVATION_SEQUENCE.incrementAndGet();
        logObservation(observation, "immediate", handle);
        PENDING_OBSERVATION.set(new PendingObservation(observation, handle.runtime(), handle.generation()));
        sendFeedback("JEI observation " + observation + " captured; close chat to capture the next valid inventory tick.");
        return 1;
    }

    private static void logObservation(long observation, String phase, RuntimeHandle observedHandle) {
        Object runtime = observedHandle.runtime();
        Object currentRuntime = RUNTIME.get() == null ? null : RUNTIME.get().runtime();
        String filterText = "unknown";
        String ingredientFilterApiClass = "unknown";
        String ingredientListCached = "unknown";
        String gridActive = "unknown";
        String gridVisibleSlotCount = "unknown";
        String gridSlotCount = "unknown";
        String gridOccupiedSlotCount = "unknown";
        String screenPropertiesValid = "unknown";
        String jecKeyboard = "unknown";
        String jecMatch = "unknown";
        String injectedHandlerPresent = "unknown";
        String matchClassLoader = "unknown";
        String bridgeRefreshInvocation = "unknown";
        String bridgeClassLoader = "unknown";
        String productRuntimeIdentity = "unknown";
        String productRuntimeSameAsProbe = "unknown";

        try {
            IIngredientFilter filterApi = observedHandle.runtime().getIngredientFilter();
            ingredientFilterApiClass = filterApi.getClass().getName();
            filterText = quote(filterApi.getFilterText());
            Object ingredientFilter = readField(filterApi, "ingredientFilter");
            Object cached = readField(ingredientFilter, "ingredientListCached");
            ingredientListCached = cached == null
                    ? "null"
                    : cached instanceof Collection<?> collection
                            ? "size:" + collection.size()
                            : "type:" + cached.getClass().getName();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ingredientListCached = diagnosticFailure(exception);
        }

        try {
            Object overlay = observedHandle.runtime().getIngredientListOverlay();
            Object screenPropertiesCache = readField(overlay, "screenPropertiesCache");
            screenPropertiesValid = String.valueOf(readField(screenPropertiesCache, "guiPropertiesAreValid"));
            Object contents = readField(overlay, "contents");
            gridActive = String.valueOf(readField(contents, "active"));
            Object ingredientGrid = readField(contents, "ingredientGrid");
            gridVisibleSlotCount = String.valueOf(readField(ingredientGrid, "visibleSlotCount"));
            Object renderer = readField(ingredientGrid, "ingredientListRenderer");
            Object slotsValue = readField(renderer, "slots");
            if (slotsValue instanceof Collection<?> slots) {
                gridSlotCount = String.valueOf(slots.size());
                int occupied = 0;
                for (Object slot : slots) {
                    if (readField(slot, "element") != null) {
                        occupied++;
                    }
                }
                gridOccupiedSlotCount = String.valueOf(occupied);
            } else {
                gridSlotCount = "type:" + slotsValue.getClass().getName();
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            gridActive = diagnosticFailure(exception);
        }

        try {
            Class<?> configClass = Class.forName("me.towdium.jecharacters.JechConfig");
            jecKeyboard = String.valueOf(readStaticField(configClass, "enumKeyboard"));
            Class<?> matchClass = Class.forName("me.towdium.jecharacters.utils.Match");
            Method contains = matchClass.getDeclaredMethod("contains", String.class, CharSequence.class);
            jecMatch = String.valueOf(contains.invoke(null, "橡木", (CharSequence) "xiangmu"));
            injectedHandlerPresent = String.valueOf(Arrays.stream(matchClass.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains("refreshJeiSearchResults")));
            matchClassLoader = identity(matchClass.getClassLoader());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            jecMatch = diagnosticFailure(exception);
        }

        try {
            Class<?> bridgeClass = Class.forName(
                    "com.starskyxiii.polyglottooltip.integration.jei.JeiSearchRefreshBridge");
            Method refreshResults = bridgeClass.getMethod("refreshResults");
            refreshResults.invoke(null);
            bridgeRefreshInvocation = "success";
            bridgeClassLoader = identity(bridgeClass.getClassLoader());

            Class<?> pluginClass = Class.forName(
                    "com.starskyxiii.polyglottooltip.integration.jei.PolyglotJeiRuntimePlugin");
            Object productRuntime = readStaticField(pluginClass, "activeRuntime");
            productRuntimeIdentity = identity(productRuntime);
            productRuntimeSameAsProbe = String.valueOf(productRuntime == runtime);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            bridgeRefreshInvocation = diagnosticFailure(exception);
        }

        LOGGER.info(
                "[PolyglotJeiRuntimeProbe] OBSERVATION id={} phase={} generation={} runtimeIdentity={} currentRuntimeIdentity={} sameActiveRuntime={} screen={} filterText={} ingredientFilterApiClass={} ingredientListCached={} screenPropertiesValid={} gridActive={} gridVisibleSlotCount={} gridSlotCount={} gridOccupiedSlotCount={} jecKeyboard={} jecMatchOakXiangmu={} injectedHandlerPresent={} matchClassLoader={} bridgeRefreshInvocation={} bridgeClassLoader={} productRuntimeIdentity={} productRuntimeSameAsProbe={}",
                observation,
                phase,
                observedHandle.generation(),
                identity(runtime),
                identity(currentRuntime),
                runtime == currentRuntime,
                Minecraft.getInstance().screen == null ? "null" : Minecraft.getInstance().screen.getClass().getName(),
                filterText,
                ingredientFilterApiClass,
                ingredientListCached,
                screenPropertiesValid,
                gridActive,
                gridVisibleSlotCount,
                gridSlotCount,
                gridOccupiedSlotCount,
                jecKeyboard,
                jecMatch,
                injectedHandlerPresent,
                matchClassLoader,
                bridgeRefreshInvocation,
                bridgeClassLoader,
                productRuntimeIdentity,
                productRuntimeSameAsProbe);
    }

    private static Object readStaticField(Class<?> type, String fieldName) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static boolean hasValidInventoryScreen(IJeiRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        try {
            Object overlay = runtime.getIngredientListOverlay();
            Object screenPropertiesCache = readField(overlay, "screenPropertiesCache");
            return Boolean.TRUE.equals(readField(screenPropertiesCache, "guiPropertiesAreValid"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("[PolyglotJeiRuntimeProbe] OBSERVATION_WAIT status=reflection_failure detail={}",
                    diagnosticFailure(exception));
            PENDING_OBSERVATION.set(null);
            return false;
        }
    }

    private static String diagnosticFailure(Exception exception) {
        return "error:" + exception.getClass().getSimpleName();
    }

    private static String identity(Object value) {
        return value == null
                ? "null"
                : value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static int runAssertions(boolean expectPinyin, boolean expectTraditional) {
        RuntimeHandle handle = RUNTIME.get();
        String languageCode = Minecraft.getInstance().options.languageCode;
        if (!"en_us".equalsIgnoreCase(languageCode)) {
            return abort("primary_language", "expected=en_us actual=" + languageCode);
        }
        if (handle == null) {
            return abort("jei_runtime", "actual=unavailable");
        }

        IIngredientFilter filter = handle.runtime().getIngredientFilter();
        if (filter == null) {
            return abort("ingredient_filter", "actual=null");
        }

        LOGGER.info(
                "[PolyglotJeiRuntimeProbe] RUN_START generation={} minecraft={} forge={} jei={} jec={} polyglot={} probe={} primaryLanguage={} expectedSecondaryLanguage=zh_cn expectPinyin={} expectTraditional={}",
                handle.generation(),
                version("minecraft"),
                version("forge"),
                version("jei"),
                version("jecharacters"),
                version("polyglottooltip"),
                version(MOD_ID),
                languageCode,
                expectPinyin,
                expectTraditional);

        long runStart = System.nanoTime();
        List<String> failures = new ArrayList<>();
        String originalFilter;
        try {
            originalFilter = filter.getFilterText();
        } catch (RuntimeException exception) {
            return abort(
                    "ingredient_filter_text",
                    "exception=" + exception.getClass().getName()
                            + " message=" + String.valueOf(exception.getMessage()));
        }
        int completed = 0;
        try {
            checkItem(filter, "native_oak", "oak", Items.OAK_LOG, true, failures);
            completed++;
            checkItem(filter, "secondary_chinese_oak", "橡木", Items.OAK_LOG, true, failures);
            completed++;
            checkItem(filter, "secondary_pinyin_oak", "xiangmu", Items.OAK_LOG, expectPinyin, failures);
            completed++;
            checkItem(filter, "traditional_iron", "鐵錠", Items.IRON_INGOT, expectTraditional, failures);
            completed++;
            checkEmpty(filter, "impossible_query", "polyglot_probe_no_such_ingredient_7f3c9a", failures);
            completed++;
        } catch (RuntimeException exception) {
            String failure = "case=execution exception=" + exception.getClass().getName()
                    + " message=" + String.valueOf(exception.getMessage());
            failures.add(failure);
            LOGGER.error("[PolyglotJeiRuntimeProbe] ASSERTION_FAILED {}", failure, exception);
        } finally {
            try {
                filter.setFilterText(originalFilter);
                LOGGER.info(
                        "[PolyglotJeiRuntimeProbe] FILTER_RESTORED value={}",
                        quote(originalFilter));
            } catch (RuntimeException exception) {
                String failure = "case=restore_filter exception=" + exception.getClass().getName()
                        + " message=" + String.valueOf(exception.getMessage());
                failures.add(failure);
                LOGGER.error("[PolyglotJeiRuntimeProbe] ASSERTION_FAILED {}", failure, exception);
            }
        }

        BackendInfo backend = inspectBackend(filter);
        LOGGER.info(
                "[PolyglotJeiRuntimeProbe] BACKEND elementSearchClass={} noPrefixStorageClass={} status={}",
                backend.elementSearchClass(),
                backend.noPrefixStorageClass(),
                backend.status());
        double elapsedMs = elapsedMilliseconds(runStart);
        boolean passed = completed == 5 && failures.isEmpty();
        LOGGER.info(
                "[PolyglotJeiRuntimeProbe] RUN_RESULT status={} expectedAssertions=5 assertionsCompleted={} failed={} elapsedMs={}",
                passed ? "PASS" : "FAIL",
                completed,
                failures.size(),
                elapsedMs);
        sendFeedback(passed
                ? "JEI probe passed 5 assertions; see latest.log for structured results."
                : "JEI probe failed " + failures.size() + " assertion(s); see latest.log.");
        return passed ? 1 : 0;
    }

    private static void checkItem(IIngredientFilter filter,
                                  String caseName,
                                  String query,
                                  Item expectedItem,
                                  boolean expectedPresence,
                                  List<String> failures) {
        long start = System.nanoTime();
        filter.setFilterText(query);
        List<ItemStack> results = filter.getFilteredIngredients(VanillaTypes.ITEM_STACK);
        boolean actualPresence = results.stream().anyMatch(stack -> stack.is(expectedItem));
        boolean passed = actualPresence == expectedPresence;
        ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(expectedItem);
        LOGGER.info(
                "[PolyglotJeiRuntimeProbe] CASE name={} query={} expectedItem={} expectedPresence={} actualPresence={} count={} elapsedMs={} status={}",
                caseName,
                quote(query),
                itemId,
                expectedPresence,
                actualPresence,
                results.size(),
                elapsedMilliseconds(start),
                passed ? "PASS" : "FAIL");
        if (!passed) {
            String failure = "case=" + caseName
                    + " expectedItem=" + itemId
                    + " expectedPresence=" + expectedPresence
                    + " actualPresence=" + actualPresence
                    + " count=" + results.size();
            failures.add(failure);
            LOGGER.error("[PolyglotJeiRuntimeProbe] ASSERTION_FAILED {}", failure);
        }
    }

    private static void checkEmpty(IIngredientFilter filter,
                                   String caseName,
                                   String query,
                                   List<String> failures) {
        long start = System.nanoTime();
        filter.setFilterText(query);
        List<ItemStack> results = filter.getFilteredIngredients(VanillaTypes.ITEM_STACK);
        boolean passed = results.isEmpty();
        LOGGER.info(
                "[PolyglotJeiRuntimeProbe] CASE name={} query={} expectedItem=none expectedPresence=false actualPresence=false count={} elapsedMs={} status={}",
                caseName,
                quote(query),
                results.size(),
                elapsedMilliseconds(start),
                passed ? "PASS" : "FAIL");
        if (!passed) {
            String failure = "case=" + caseName + " expectedCount=0 actualCount=" + results.size();
            failures.add(failure);
            LOGGER.error("[PolyglotJeiRuntimeProbe] ASSERTION_FAILED {}", failure);
        }
    }

    private static int abort(String precondition, String detail) {
        LOGGER.error(
                "[PolyglotJeiRuntimeProbe] RUN_RESULT status=FAIL assertions=0 failed=1 precondition={} {}",
                precondition,
                detail);
        sendFeedback("JEI probe could not run: " + precondition + " " + detail);
        return 0;
    }

    private static BackendInfo inspectBackend(IIngredientFilter filter) {
        String elementSearchClass = "unknown";
        try {
            Object ingredientFilter = readField(filter, "ingredientFilter");
            Object elementSearch = readField(ingredientFilter, "elementSearch");
            elementSearchClass = elementSearch.getClass().getName();
            Object noPrefix = readField(elementSearch, "noPrefix");
            Object prefixedSearchables = readField(elementSearch, "prefixedSearchables");
            if (!(prefixedSearchables instanceof Map<?, ?> searchableMap)) {
                return new BackendInfo(elementSearchClass, "unknown", "unknown:not_a_map");
            }
            Object searchable = searchableMap.get(noPrefix);
            if (searchable == null) {
                return new BackendInfo(elementSearchClass, "unknown", "unknown:no_prefix_searchable");
            }
            Method getSearchStorage = searchable.getClass().getMethod("getSearchStorage");
            Object storage = getSearchStorage.invoke(searchable);
            if (storage == null) {
                return new BackendInfo(elementSearchClass, "unknown", "unknown:null_storage");
            }
            return new BackendInfo(elementSearchClass, storage.getClass().getName(), "resolved");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return new BackendInfo(
                    elementSearchClass,
                    "unknown",
                    "unknown:" + exception.getClass().getSimpleName());
        }
    }

    private static Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
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

    private static void sendFeedback(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }

    private record RuntimeHandle(IJeiRuntime runtime, long generation) {
    }

    private record BackendInfo(String elementSearchClass, String noPrefixStorageClass, String status) {
    }

    private record PendingObservation(long id, IJeiRuntime runtime, long generation) {
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientCommands {
        private ClientCommands() {
        }

        @SubscribeEvent
        public static void register(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal(COMMAND)
                    .then(Commands.argument("expectPinyin", BoolArgumentType.bool())
                            .then(Commands.argument("expectTraditional", BoolArgumentType.bool())
                                    .executes(context -> runAssertions(
                                            BoolArgumentType.getBool(context, "expectPinyin"),
                                            BoolArgumentType.getBool(context, "expectTraditional"))))));
            event.getDispatcher().register(Commands.literal(FLUID_COMMAND)
                    .then(Commands.argument("expectSecondary", BoolArgumentType.bool())
                            .executes(context -> FluidRuntimeProbe.run(
                                    BoolArgumentType.getBool(context, "expectSecondary")))));
            event.getDispatcher().register(Commands.literal(JEI_FLUID_COMMAND)
                    .then(Commands.argument("expectSecondary", BoolArgumentType.bool())
                            .executes(context -> JeiFluidRuntimeProbe.run(
                                    BoolArgumentType.getBool(context, "expectSecondary")))));
            event.getDispatcher().register(Commands.literal(OBSERVE_COMMAND)
                    .executes(context -> startObservation()));
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
                return;
            }
            PendingObservation pending = PENDING_OBSERVATION.get();
            if (pending == null || !hasValidInventoryScreen(pending.runtime())) {
                return;
            }
            if (PENDING_OBSERVATION.compareAndSet(pending, null)) {
                logObservation(
                        pending.id(),
                        "valid_inventory_tick",
                        new RuntimeHandle(pending.runtime(), pending.generation()));
                sendFeedback("JEI observation " + pending.id() + " captured the valid inventory tick.");
            }
        }
    }
}
