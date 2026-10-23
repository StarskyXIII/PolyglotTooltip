package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;

import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ResolvedName;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public final class ChiselsAndBitsNameResolver implements SpecialItemNameResolver {
    static final String ITEM_CLASS = "mod.chiselsandbits.items.ItemChiseledBit";
    static final String SEPARATOR = " - ";

    private final IntegrationGuard guard;
    private final BiConsumer<String, Throwable> warn;
    private final MethodHandle stackState;
    private final MethodHandle stateById;
    private final MethodHandle fluidOf;
    private volatile boolean broken;

    public ChiselsAndBitsNameResolver(IntegrationGuard guard) throws ReflectiveOperationException {
        this.guard = guard;
        this.warn = LogManager.getLogger(PolyglotTooltipMod.MOD_ID)::warn;
        ClassLoader loader = getClass().getClassLoader();
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        stackState = lookup.findStatic(Class.forName(ITEM_CLASS, false, loader), "getStackState",
                MethodType.methodType(int.class, ItemStack.class));
        stateById = lookup.findStatic(Class.forName("mod.chiselsandbits.helpers.ModUtil", false, loader),
                "getStateById", MethodType.methodType(IBlockState.class, int.class));
        fluidOf = lookup.findStatic(Class.forName("mod.chiselsandbits.chiseledblock.BlockBitInfo", false, loader),
                "getFluidFromBlock", MethodType.methodType(Fluid.class, Block.class));
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return !broken && guard.active() && ITEM_CLASS.equals(stack.func_77973_b().getClass().getName());
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        ResolvedName name = resolveName(stack, result, languageCode, dictionary);
        return name == null ? null : name.name();
    }

    @Override
    public ResolvedName resolveName(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                                    SecondaryDictionary dictionary) {
        if (broken || result.key() == null) {
            return null;
        }
        IBlockState state;
        Fluid fluid;
        try {
            state = (IBlockState) stateById.invokeExact((int) stackState.invokeExact(stack));
            fluid = state == null ? null : (Fluid) fluidOf.invokeExact(state.func_177230_c());
        } catch (Throwable error) {
            broken = true;
            warn.accept("Disabled the Chisels & Bits name resolver because a Chisels & Bits call failed;"
                    + " it stays off until the game restarts.", error);
            return null;
        }
        if (state == null) {
            return null;
        }
        return guard.call(() -> {
            Function<String, String> reverse = NameParts.reverse(dictionary);
            NameParts.Joined joined = new NameParts.Joined(reverse)
                    .part(result.expected(), ResolvedName.of(dictionary.get(result.key()))).text(SEPARATOR);
            Block block = state.func_177230_c();
            if (Item.func_150898_a(block) == Items.field_190931_a) {
                if (fluid == null) {
                    return null;
                }
                FluidStack fluidStack = new FluidStack(fluid, 10);
                return joined.part(fluid.getLocalizedName(fluidStack),
                        ResolvedName.of(NameParts.fluid(fluidStack, languageCode))).checked(result.primary());
            }
            ItemStack target = new ItemStack(block, 1, block.func_180651_a(state));
            joined.part(target.func_82833_r(), NameParts.itemName(target, languageCode, dictionary));
            return extras(joined, prettyKeys(state), NameParts.PRIMARY, dictionary::get).checked(result.primary());
        }, null);
    }

    private static Set<String> prettyKeys(IBlockState state) {
        Set<String> keys = new HashSet<>();
        for (IProperty<?> property : state.func_177227_a()) {
            String name = property.func_177701_a();
            if (name.equals("axis") || name.equals("facing")) {
                keys.add("mod.chiselsandbits.pretty." + name + "-" + state.func_177229_b(property));
            }
        }
        return keys;
    }

    static NameParts.Joined extras(NameParts.Joined joined, Set<String> keys, Function<String, String> primaryLang,
                                   Function<String, String> secondaryLang) {
        Map<String, String> byText = new LinkedHashMap<>();
        for (String key : keys) {
            String text = primaryLang.apply(key);
            byText.putIfAbsent(text == null ? key : text, key);
        }
        for (String text : new HashSet<>(byText.keySet())) {
            joined.text(" ").part(text, ResolvedName.of(secondaryLang.apply(byText.get(text))));
        }
        return joined;
    }
}
