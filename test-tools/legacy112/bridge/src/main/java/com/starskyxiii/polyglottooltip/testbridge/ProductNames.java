package com.starskyxiii.polyglottooltip.testbridge;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** The minimal-core product's shown secondary names, reached by reflection because the bridge is not compiled against it. */
final class ProductNames {
    static final String PACKAGE = "com.starskyxiii.polyglottooltip.legacy.minecraft.";

    private ProductNames() { }

    static Object item(ItemStack stack) throws Exception {
        return NativeAccess.call(NativeAccess.call(NativeAccess.type(PACKAGE + "ItemNames"), "of", stack), "names");
    }

    static Object fluid(FluidStack stack) throws Exception {
        return NativeAccess.call(NativeAccess.type(PACKAGE + "FluidSecondaryNames"), "of", stack);
    }
}
