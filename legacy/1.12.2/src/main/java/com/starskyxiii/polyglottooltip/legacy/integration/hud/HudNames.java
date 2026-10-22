package com.starskyxiii.polyglottooltip.legacy.integration.hud;

import java.util.Map;
import java.util.function.Supplier;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.Settings;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public final class HudNames {
    private HudNames() {}

    public static SecondaryNames block(Block block, Supplier<ItemStack> pick) {
        Fluid fluid = block == null ? null : FluidRegistry.lookupFluidForBlock(block);
        if (fluid != null) {
            FluidStack stack = new FluidStack(fluid, Fluid.BUCKET_VOLUME);
            return names(plain(stack.getLocalizedName()), FluidSecondaryNames.of(stack));
        }
        return ItemNames.of(pick.get());
    }

    @SuppressWarnings("deprecation")
    public static SecondaryNames entity(Entity entity) {
        if (entity == null || entity.func_145818_k_()) {
            return SecondaryNames.EMPTY;
        }
        String type = EntityList.func_75621_b(entity);
        if (type == null) {
            return SecondaryNames.EMPTY;
        }
        String key = "entity." + type + ".name";
        String actual = plain(entity.func_70005_c_());
        String expected = plain(I18n.func_74838_a(key));
        return names(actual, FluidSecondaryNames.resolve(key, actual, expected, SecondaryLanguages.dictionaries()));
    }

    private static SecondaryNames names(String primary, Map<String, String> namesByLanguage) {
        Settings settings = Config.settings();
        return SecondaryNames.of(primary, settings.displayLanguages(), namesByLanguage, settings.alwaysShow(),
                SecondaryLanguages.currentLanguage());
    }

    private static String plain(String text) {
        return text == null ? null : TextFormatting.func_110646_a(text).trim();
    }
}
