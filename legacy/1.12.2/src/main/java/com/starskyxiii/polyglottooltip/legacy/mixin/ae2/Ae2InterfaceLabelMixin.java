package com.starskyxiii.polyglottooltip.legacy.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.legacy.integration.ae2.Ae2Tooltips;

@Pseudo
@Mixin(targets = {
        "appeng.client.gui.implementations.GuiInterfaceTerminal",
        "appeng.client.gui.implementations.GuiInterfaceConfigurationTerminal",
        "appeng.client.gui.implementations.GuiFluidInterfaceConfigurationTerminal"
}, remap = false)
public abstract class Ae2InterfaceLabelMixin {
    @Unique private Object polyglot$entry;
    @Unique private String polyglot$label;

    @Redirect(method = "refreshList()V", require = 0,
            at = @At(value = "INVOKE", target = "Lappeng/client/me/ClientDCInternalInv;getName()Ljava/lang/String;"))
    private String polyglot$itemLabel(@Coerce Object entry) {
        return polyglot$capture(entry);
    }

    @Redirect(method = "refreshList()V", require = 0,
            at = @At(value = "INVOKE", target = "Lappeng/client/me/ClientDCInternalFluidInv;getName()Ljava/lang/String;"))
    private String polyglot$fluidLabel(@Coerce Object entry) {
        return polyglot$capture(entry);
    }

    @WrapOperation(method = "refreshList()V", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"))
    private boolean polyglot$contains(String name, CharSequence query, Operation<Boolean> original) {
        return original.call(polyglot$searchText(name), query);
    }

    @WrapOperation(method = "refreshList()V", require = 0, at = @At(value = "INVOKE",
            target = "Lme/towdium/jecharacters/util/Match;contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z"))
    private boolean polyglot$matchContains(String name, CharSequence query, Operation<Boolean> original) {
        return original.call(polyglot$searchText(name), query);
    }

    @Unique
    private String polyglot$capture(Object entry) {
        String label = ((Ae2LabelKeyAccessor) entry).polyglot$name();
        polyglot$entry = entry;
        polyglot$label = label;
        return label;
    }

    @Unique
    private String polyglot$searchText(String name) {
        Object entry = polyglot$entry;
        String label = polyglot$label;
        polyglot$entry = null;
        polyglot$label = null;
        if (entry == null || label == null || name == null || !name.equals(label.toLowerCase())) {
            return name;
        }
        String text = Ae2Tooltips.labelSearchText(entry, label);
        return text.equals(label) ? name : text.toLowerCase();
    }
}
