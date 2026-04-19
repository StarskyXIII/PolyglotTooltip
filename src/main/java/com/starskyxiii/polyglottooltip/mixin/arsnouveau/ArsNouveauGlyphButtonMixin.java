package com.starskyxiii.polyglottooltip.mixin.arsnouveau;

import com.starskyxiii.polyglottooltip.integration.arsnouveau.ArsNouveauGlyphSearchHelper;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton", remap = false)
public abstract class ArsNouveauGlyphButtonMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void polyglottooltip$appendSecondaryGlyphName(List<Component> tip, CallbackInfo ci) {
        ArsNouveauGlyphSearchHelper.insertSecondaryNameLine(tip, polyglottooltip$getFieldValue("abstractSpellPart"));
    }

    private Object polyglottooltip$getFieldValue(String fieldName) {
        try {
            Field field = this.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(this);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
