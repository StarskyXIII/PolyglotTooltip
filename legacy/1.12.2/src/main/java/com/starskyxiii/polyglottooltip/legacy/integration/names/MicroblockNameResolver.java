package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;

import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

public final class MicroblockNameResolver implements SpecialItemNameResolver {
    static final String ITEM_CLASS = "codechicken.microblock.ItemMicroPart";

    private final IntegrationGuard guard;
    private final BiConsumer<String, Throwable> warn;
    private final MethodHandle material;
    private final MethodHandle factory;
    private final MethodHandle size;
    private final MethodHandle materialItem;
    private final MethodHandle factoryName;
    private volatile boolean broken;

    public MicroblockNameResolver(IntegrationGuard guard) throws ReflectiveOperationException {
        this(guard, LogManager.getLogger(PolyglotTooltipMod.MOD_ID)::warn);
    }

    MicroblockNameResolver(IntegrationGuard guard, BiConsumer<String, Throwable> warn)
            throws ReflectiveOperationException {
        this.guard = guard;
        this.warn = warn;
        ClassLoader loader = getClass().getClassLoader();
        Class<?> part = Class.forName(ITEM_CLASS, false, loader);
        Class<?> materialType = Class.forName("codechicken.microblock.IMicroMaterial", false, loader);
        Class<?> factoryType = Class.forName("codechicken.microblock.MicroblockFactory", false, loader);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        material = lookup.findStatic(part, "getMaterial", MethodType.methodType(materialType, ItemStack.class))
                .asType(MethodType.methodType(Object.class, ItemStack.class));
        factory = lookup.findStatic(part, "getFactory", MethodType.methodType(
                Class.forName("codechicken.microblock.CommonMicroFactory", false, loader), ItemStack.class))
                .asType(MethodType.methodType(Object.class, ItemStack.class));
        size = lookup.findStatic(part, "getSize", MethodType.methodType(int.class, ItemStack.class));
        materialItem = lookup.findVirtual(materialType, "getItem", MethodType.methodType(ItemStack.class))
                .asType(MethodType.methodType(ItemStack.class, Object.class));
        factoryName = lookup.findVirtual(factoryType, "getName", MethodType.methodType(ResourceLocation.class))
                .asType(MethodType.methodType(ResourceLocation.class, Object.class));
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return !broken && guard.active() && ITEM_CLASS.equals(stack.func_77973_b().getClass().getName());
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        if (broken) {
            return null;
        }
        Object microMaterial;
        Object microFactory;
        int microSize;
        ItemStack materialStack;
        ResourceLocation name;
        try {
            microMaterial = material.invokeExact(stack);
            microFactory = factory.invokeExact(stack);
            microSize = (int) size.invokeExact(stack);
            materialStack = microMaterial == null ? null : (ItemStack) materialItem.invokeExact(microMaterial);
            name = microFactory == null ? null : (ResourceLocation) factoryName.invokeExact(microFactory);
        } catch (Throwable error) {
            broken = true;
            warn.accept("Disabled the microblock name resolver because a ForgeMultipart call failed;"
                    + " it stays off until the game restarts.", error);
            return null;
        }
        if (materialStack == null || name == null) {
            return null;
        }
        return guard.call(() -> name(key(name.func_110623_a(), microSize), shown(materialStack),
                NameParts.item(materialStack, languageCode, dictionary), result.primary(),
                NameParts.PRIMARY, dictionary::get), null);
    }

    @SuppressWarnings("deprecation")
    private static String shown(ItemStack stack) {
        return TextFormatting.func_110646_a(stack.func_82833_r());
    }

    static String key(String factory, int size) {
        return factory + "." + size + ".name";
    }

    static String name(String key, String primaryMaterial, String secondaryMaterial, String primary,
                       Function<String, String> primaryLang, Function<String, String> secondaryLang) {
        return NameParts.checked(NameParts.format(primaryLang.apply(key), primaryMaterial), primary,
                () -> NameParts.format(secondaryLang.apply(key), secondaryMaterial));
    }
}
