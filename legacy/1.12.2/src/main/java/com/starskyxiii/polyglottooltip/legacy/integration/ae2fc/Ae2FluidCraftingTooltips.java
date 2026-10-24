package com.starskyxiii.polyglottooltip.legacy.integration.ae2fc;

import java.util.List;

import com.glodblock.github.common.item.fake.FakeItemRegister;
import com.starskyxiii.polyglottooltip.legacy.integration.IngredientTooltips;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.mekanism.MekanismGasNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class Ae2FluidCraftingTooltips {
    private static final String FLUID_PACKET = "com.glodblock.github.common.item.ItemFluidPacket";
    private static final String GAS_PACKET = "com.glodblock.github.common.item.ItemGasPacket";

    private Ae2FluidCraftingTooltips() {}

    public static List<String> drop(List<String> lines, IAEItemStack stack) {
        IntegrationGuard guard = Ae2FluidCraftingIntegration.active;
        if (guard == null || stack == null) {
            return lines;
        }
        return withNames(guard, lines, guard.call(() -> FakeItemRegister.getStack(stack), null));
    }

    public static List<String> packet(List<String> lines, ItemStack stack) {
        IntegrationGuard guard = Ae2FluidCraftingIntegration.active;
        if (guard == null || stack == null || stack.func_190926_b() || stack.func_82837_s()) {
            return lines;
        }
        String type = stack.func_77973_b().getClass().getName();
        if (!type.equals(FLUID_PACKET) && !type.equals(GAS_PACKET)) {
            return lines;
        }
        return withNames(guard, lines, guard.call(() -> FakeItemRegister.getStack(stack), null));
    }

    public static List<String> tank(List<String> lines, IAEFluidStack stack) {
        IntegrationGuard guard = Ae2FluidCraftingIntegration.active;
        return stack == null ? lines : withNames(guard, lines, stack.getFluidStack());
    }

    private static List<String> withNames(IntegrationGuard guard, List<String> lines, Object content) {
        if (content == null || lines == null || lines.isEmpty()) {
            return lines;
        }
        return IngredientTooltips.withNames(guard, lines, lines.get(0), () -> names(guard, content));
    }

    private static SecondaryNames names(IntegrationGuard guard, Object content) {
        if (content instanceof FluidStack) {
            return FluidSecondaryNames.names((FluidStack) content);
        }
        return MekanismGasNames.names(guard, content);
    }
}
