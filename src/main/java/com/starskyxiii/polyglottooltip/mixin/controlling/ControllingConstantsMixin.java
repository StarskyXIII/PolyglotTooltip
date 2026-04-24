package com.starskyxiii.polyglottooltip.mixin.controlling;

import com.blamejared.searchables.api.SearchableComponent;
import com.blamejared.searchables.api.SearchableType;
import com.starskyxiii.polyglottooltip.integration.controlling.ControllingSearchUtil;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.blamejared.controlling.ControllingConstants", remap = false)
public abstract class ControllingConstantsMixin {

    @Shadow @Mutable @Final
    public static SearchableType<KeyBindsList.Entry> SEARCHABLE_KEYBINDINGS;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void polyglot$expandCategorySearch(CallbackInfo ci) {
        SEARCHABLE_KEYBINDINGS = new SearchableType.Builder<KeyBindsList.Entry>()
                .component(SearchableComponent.create(
                        "category",
                        ControllingSearchUtil::resolveCategoryText,
                        ControllingSearchUtil::matchesCategoryText
                ))
                .component(SearchableComponent.create(
                        "key",
                        ControllingSearchUtil::resolveKeyText,
                        ControllingSearchUtil::matchesKeyText
                ))
                .defaultComponent(SearchableComponent.create(
                        "name",
                        ControllingSearchUtil::resolveNameText,
                        ControllingSearchUtil::matchesNameText
                ))
                .build();
    }
}
