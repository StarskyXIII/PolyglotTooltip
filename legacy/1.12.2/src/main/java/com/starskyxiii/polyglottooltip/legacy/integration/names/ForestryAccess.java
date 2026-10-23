package com.starskyxiii.polyglottooltip.legacy.integration.names;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.arboriculture.EnumForestryWoodType;
import forestry.api.arboriculture.EnumGermlingType;
import forestry.api.arboriculture.IWoodType;
import forestry.api.arboriculture.TreeManager;
import forestry.api.genetics.IIndividual;
import forestry.api.lepidopterology.ButterflyManager;
import forestry.api.lepidopterology.EnumFlutterType;
import forestry.arboriculture.IWoodTyped;
import forestry.core.items.ItemFluidContainerForestry;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

final class ForestryAccess {
    private ForestryAccess() {}

    static String containerType(Item item) {
        return item instanceof ItemFluidContainerForestry ? ((ItemFluidContainerForestry) item).getType().getName() : null;
    }

    static ForestryWoodNameResolver.Wood wood(ItemStack stack) {
        Item item = stack.func_77973_b();
        Block block = item instanceof ItemBlock ? ((ItemBlock) item).func_179223_d() : null;
        if (!(block instanceof IWoodTyped)) {
            return null;
        }
        IWoodTyped wood = (IWoodTyped) block;
        IWoodType type = wood.getWoodType(stack.func_77960_j());
        if (!(type instanceof EnumForestryWoodType)) {
            return null;
        }
        return new ForestryWoodNameResolver.Wood(wood.getBlockKind().toString(), type.toString(), wood.isFireproof());
    }

    static String[] genetic(ItemStack stack, String group) {
        if (!stack.func_77942_o()) {
            return null;
        }
        switch (group) {
            case "trees": {
                EnumGermlingType type = TreeManager.treeRoot.getType(stack);
                return genetic(type == null ? null : type.getName(), TreeManager.treeRoot.getMember(stack));
            }
            case "butterflies": {
                EnumFlutterType type = ButterflyManager.butterflyRoot.getType(stack);
                return genetic(type == null ? null : type.getName(), ButterflyManager.butterflyRoot.getMember(stack));
            }
            case "bees": {
                EnumBeeType type = BeeManager.beeRoot.getType(stack);
                return genetic(type == null ? null : type.getName(), BeeManager.beeRoot.getMember(stack));
            }
            default:
                return null;
        }
    }

    private static String[] genetic(String type, IIndividual individual) {
        if (type == null || individual == null) {
            return null;
        }
        return new String[]{type, individual.getGenome().getPrimary().getUnlocalizedName()};
    }
}
