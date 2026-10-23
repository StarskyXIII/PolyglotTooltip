package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

final class TinkersAccess {
    private final Class<?> part;
    private final Class<?> tool;
    private final MethodHandle materialOf;
    private final MethodHandle identifier;
    private final MethodHandle baseMaterials;
    private final MethodHandle materialsOf;
    private final MethodHandle repairParts;

    TinkersAccess(ClassLoader loader) throws ReflectiveOperationException {
        part = Class.forName("slimeknights.tconstruct.library.tools.ToolPart", false, loader);
        tool = Class.forName("slimeknights.tconstruct.library.tools.ToolCore", false, loader);
        Class<?> material = Class.forName("slimeknights.tconstruct.library.materials.Material", false, loader);
        Class<?> util = Class.forName("slimeknights.tconstruct.library.utils.TinkerUtil", false, loader);
        Class<?> tags = Class.forName("slimeknights.tconstruct.library.utils.TagUtil", false, loader);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        materialOf = lookup.findStatic(util, "getMaterialFromStack", MethodType.methodType(material, ItemStack.class))
                .asType(MethodType.methodType(Object.class, ItemStack.class));
        identifier = lookup.findVirtual(material, "getIdentifier", MethodType.methodType(String.class))
                .asType(MethodType.methodType(String.class, Object.class));
        baseMaterials = lookup.findStatic(tags, "getBaseMaterialsTagList",
                MethodType.methodType(NBTTagList.class, ItemStack.class));
        materialsOf = lookup.findStatic(util, "getMaterialsFromTagList",
                MethodType.methodType(List.class, NBTTagList.class));
        repairParts = lookup.findVirtual(tool, "getRepairParts", MethodType.methodType(int[].class))
                .asType(MethodType.methodType(int[].class, Object.class));
    }

    boolean isPart(Item item) {
        return part.isInstance(item);
    }

    boolean isTool(Item item) {
        return tool.isInstance(item);
    }

    String partMaterial(ItemStack stack) throws Throwable {
        return (String) identifier.invokeExact(materialOf.invokeExact(stack));
    }

    List<String> toolMaterials(ItemStack stack) throws Throwable {
        List<?> all = (List<?>) materialsOf.invokeExact((NBTTagList) baseMaterials.invokeExact(stack));
        Set<String> ids = new LinkedHashSet<>();
        for (int index : (int[]) repairParts.invokeExact((Object) stack.func_77973_b())) {
            if (index < all.size()) {
                ids.add((String) identifier.invokeExact((Object) all.get(index)));
            }
        }
        return new ArrayList<>(ids);
    }
}
