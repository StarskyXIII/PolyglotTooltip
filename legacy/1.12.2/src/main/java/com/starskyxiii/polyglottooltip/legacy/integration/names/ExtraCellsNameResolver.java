package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

public final class ExtraCellsNameResolver implements SpecialItemNameResolver {
    static final String ITEM_CLASS = "extracells.item.ItemFluidPattern";

    private final IntegrationGuard guard;

    public ExtraCellsNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active() && ITEM_CLASS.equals(stack.func_77973_b().getClass().getName());
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        return guard.call(() -> {
            String key = stack.func_77973_b().func_77667_c(stack);
            FluidStack contained = FluidUtil.getFluidContained(stack);
            if (contained == null) {
                return name(key, null, null, result.primary(), NameParts.PRIMARY, dictionary::get);
            }
            FluidStack fluid = new FluidStack(contained.getFluid(), 1);
            return name(key, fluid.getLocalizedName(), NameParts.fluid(fluid, languageCode), result.primary(),
                    NameParts.PRIMARY, dictionary::get);
        }, null);
    }

    static String name(String key, String primaryFluid, String secondaryFluid, String primary,
                       Function<String, String> primaryLang, Function<String, String> secondaryLang) {
        boolean empty = primaryFluid == null;
        return NameParts.checked(render(primaryLang.apply(key), primaryFluid, empty), primary,
                () -> render(secondaryLang.apply(key), secondaryFluid, empty));
    }

    private static String render(String base, String fluid, boolean empty) {
        if (base == null) {
            return null;
        }
        if (empty) {
            return base;
        }
        return fluid == null ? null : base + ": " + fluid;
    }
}
