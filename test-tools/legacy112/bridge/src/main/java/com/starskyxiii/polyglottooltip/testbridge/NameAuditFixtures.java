package com.starskyxiii.polyglottooltip.testbridge;

import mezz.jei.Internal;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

final class NameAuditFixtures {
    static final class Case {
        final ItemStack seed;
        final ItemStack stack;
        Case(ItemStack seed, ItemStack stack) { this.seed = seed; this.stack = stack; }
    }

    private NameAuditFixtures() {}

    private static void add(Map<String, Case> result, String id, ItemStack seed, Consumer<ItemStack> edit) {
        ItemStack before = seed.func_77946_l(), stack = before.func_77946_l();
        stack.func_190920_e(1);
        edit.accept(stack);
        if (result.put(id, new Case(before, stack)) != null) throw new IllegalArgumentException("Duplicate fixture case " + id);
    }

    static Map<String, Case> create() throws Exception {
        Map<String, Case> result = new LinkedHashMap<>();
        for (String key : new String[]{"tile.stone.stone.name", "item.ironIngot.name", "missing.audit.key", " item.ironIngot.name "}) {
            ItemStack seed = new ItemStack(Blocks.field_150348_b);
            add(result, "locname:" + key, seed, stack -> stack.func_190925_c("display").func_74778_a("LocName", key));
            add(result, "literal-over-locname:" + key, seed, stack -> {
                stack.func_190925_c("display").func_74778_a("LocName", key);
                stack.func_151001_c("Audit literal override 42");
            });
        }
        Map<String, ItemStack> prototypes = new LinkedHashMap<>();
        for (ItemStack stack : Internal.getIngredientRegistry().getAllIngredients(ItemStack.class)) {
            if (stack.func_190926_b()) continue;
            String id = String.valueOf(stack.func_77973_b().getRegistryName());
            if ((id.startsWith("tconstruct:") || id.startsWith("plustic:")) && !prototypes.containsKey(id)) prototypes.put(id, stack);
        }
        for (String id : new String[]{"tconstruct:pickaxe", "tconstruct:hammer", "tconstruct:cleaver", "tconstruct:bolt_core", "plustic:katana"}) {
            ItemStack prototype = prototypes.get(id);
            if (prototype == null) throw new IllegalStateException("Missing required fixture prototype " + id);
            for (String[] materials : new String[][]{{"iron", "cobalt", "wood", "iron"}, {"iron", "iron", "iron", "iron"},
                    {"unknown", "iron", "unknown", "iron"}, {"enderium", "nothingness", "wood", "enderium"}}) {
                add(result, "mixed:" + id + ":" + String.join(",", materials), prototype, stack -> {
                    NBTTagList list = new NBTTagList();
                    for (String material : materials) list.func_74742_a(new NBTTagString(material));
                    stack.func_190925_c("TinkerData").func_74782_a("Materials", list);
                });
            }
        }
        ItemStack pickaxe = prototypes.get("tconstruct:pickaxe");
        NBTTagCompound display = pickaxe.func_179543_a("display"), data = pickaxe.func_179543_a("TinkerData");
        if ((display != null && (display.func_74764_b("Name") || display.func_74764_b("LocName")))
                || data == null || data.func_150295_c("Materials", 8).func_74745_c() != 3) {
            throw new IllegalStateException("Invalid callback fixture pickaxe seed");
        }
        Class<?> materialType = NativeAccess.type("slimeknights.tconstruct.library.materials.Material");
        Class<?> cotType = NativeAccess.type("com.teamacronymcoders.contenttweaker.modules.tinkers.materials.CoTTConMaterial");
        java.lang.reflect.Field identifier = NativeAccess.field(materialType, "identifier");
        java.lang.reflect.Field localizer = NativeAccess.field(cotType, "itemLocalizer");
        Collection<?> materials = new ArrayList<>((Collection<?>) NativeAccess.call(
                NativeAccess.type("slimeknights.tconstruct.library.TinkerRegistry"), "getAllMaterials"));
        for (Object material : materials) {
            if (!cotType.isInstance(material) || localizer.get(material) == null) continue;
            String id = (String) identifier.get(material);
            if (material.getClass() != cotType || id == null || id.isEmpty() || id.length() > 256) {
                throw new IllegalStateException("Unsupported callback fixture material " + id + " " + material.getClass().getName());
            }
            add(result, "cot-callback:" + id, pickaxe, stack -> {
                NBTTagList list = new NBTTagList();
                for (int slot = 0; slot < 3; slot++) list.func_74742_a(new NBTTagString(id));
                stack.func_190925_c("TinkerData").func_74782_a("Materials", list);
            });
        }
        int models = 0, souls = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            String type = item.getClass().getName(), id = String.valueOf(item.getRegistryName());
            if (type.equals("mustapelto.deepmoblearning.common.items.ItemDataModel")) {
                models++;
                for (int tier : new int[]{Integer.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, 6, Integer.MAX_VALUE}) {
                    add(result, "tier:" + id + ":" + tier, new ItemStack(item), stack -> {
                        NBTTagCompound tag = new NBTTagCompound(); tag.func_74768_a("tier", tier); stack.func_77982_d(tag);
                    });
                }
                add(result, "tier-missing:" + id, new ItemStack(item), stack -> {});
                add(result, "tier-string:" + id, new ItemStack(item), stack -> {
                    NBTTagCompound tag = new NBTTagCompound(); tag.func_74778_a("tier", "3"); stack.func_77982_d(tag);
                });
            }
            if (type.equals("com.brandon3055.draconicevolution.items.MobSoul")) {
                souls++;
                for (String entity : new String[]{"minecraft:pig", "minecraft:zombie", "[Random-Display]", "minecraft:cow"}) {
                    add(result, "mob-soul:" + entity, new ItemStack(item), stack -> {
                        NBTTagCompound tag = new NBTTagCompound(); tag.func_74778_a("EntityName", entity); stack.func_77982_d(tag);
                    });
                }
            }
        }
        if (models == 0 || souls != 1) throw new IllegalStateException("Missing model or mob-soul fixture items");
        for (Map.Entry<String, Case> entry : new LinkedHashMap<>(result).entrySet()) {
            if (!entry.getKey().startsWith("mixed:") && !entry.getKey().startsWith("mob-soul:")
                    && !entry.getKey().startsWith("tier-missing:") && !entry.getKey().startsWith("cot-callback:")) continue;
            add(result, "literal:" + entry.getKey(), entry.getValue().stack, stack -> stack.func_151001_c("Audit literal boundary 42"));
        }
        return result;
    }
}
