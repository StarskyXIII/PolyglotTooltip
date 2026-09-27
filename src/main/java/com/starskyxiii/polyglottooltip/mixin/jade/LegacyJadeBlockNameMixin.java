package com.starskyxiii.polyglottooltip.mixin.jade;
//~ minecraft_116_packages

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.starskyxiii.polyglottooltip.integration.jade.LegacyJadeTooltipHelper;
import mcp.mobius.waila.api.IDataAccessor;
import mcp.mobius.waila.api.IPluginConfig;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "mcp.mobius.waila.addons.core.HUDHandlerBlocks", remap = false)
public class LegacyJadeBlockNameMixin {
    @WrapOperation(
            method = "appendHead(Ljava/util/List;Lmcp/mobius/waila/api/IDataAccessor;Lmcp/mobius/waila/api/IPluginConfig;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/text/ITextComponent;getString()Ljava/lang/String;", remap = false),
            remap = false,
            require = 1
    )
    private String polyglot$captureName(Component name, Operation<String> original,
                                        @Share("polyglotBlockName") LocalRef<Component> source) {
        source.set(name);
        return original.call(name);
    }

    @Inject(
            method = "appendHead(Ljava/util/List;Lmcp/mobius/waila/api/IDataAccessor;Lmcp/mobius/waila/api/IPluginConfig;)V",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private void polyglot$appendSecondaryName(List<Component> tooltip, IDataAccessor accessor,
                                             IPluginConfig config, CallbackInfo ci,
                                             @Share("polyglotBlockName") LocalRef<Component> source) {
        LegacyJadeTooltipHelper.appendName(tooltip, source.get());
    }
}
