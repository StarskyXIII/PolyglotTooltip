package com.starskyxiii.polyglottooltip.mixin.ae2;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternAccessTermScreen", remap = false)
public class Ae2PatternAccessTermScreenMixin {

    @ModifyReturnValue(method = "getPatternSearchText", at = @At("RETURN"))
    private String extendPatternSearchText(String original, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        return PatternAccessSearchUtil.extendPatternSearchText(original, stack, minecraft.level);
    }
}
