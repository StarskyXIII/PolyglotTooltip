package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

public final class ForestryContainerNameResolver implements SpecialItemNameResolver {
    static final String ITEM_CLASS = "forestry.core.items.ItemFluidContainerForestry";

    private final IntegrationGuard guard;

    public ForestryContainerNameResolver(IntegrationGuard guard) {
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
            String type = ForestryAccess.containerType(stack.func_77973_b());
            if (type == null) {
                return null;
            }
            FluidStack fluid = FluidUtil.getFluidContained(stack);
            if (fluid == null) {
                return name(type, null, null, null, result.primary(), NameParts.PRIMARY, dictionary::get);
            }
            return name(type, fluid.getFluid().getName(), fluid.getLocalizedName(),
                    NameParts.fluid(fluid, languageCode), result.primary(), NameParts.PRIMARY, dictionary::get);
        }, null);
    }

    static String name(String type, String fluid, String primaryFluid, String secondaryFluid, String primary,
                       Function<String, String> primaryLang, Function<String, String> secondaryLang) {
        return NameParts.checked(render(type, fluid, primaryFluid, primaryLang), primary,
                () -> render(type, fluid, secondaryFluid, secondaryLang));
    }

    static String render(String type, String fluid, String fluidName, Function<String, String> lang) {
        if (fluid == null) {
            return lang.apply("item.for." + type + ".empty.name");
        }
        String exact = lang.apply("item.for." + type + "." + fluid + ".name");
        return exact != null ? exact : NameParts.format(lang.apply("item.for." + type + ".grammar"), fluidName);
    }
}
