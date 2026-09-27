package com.starskyxiii.polyglottooltip.mixin.jade;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.integration.jade.LegacyJadeTooltipHelper;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IPluginConfig;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "mcp.mobius.waila.addons.core.HUDHandlerEntities", remap = false)
public class LegacyJadeEntityNameMixin {
    @Inject(
            method = "appendHead(Ljava/util/List;Lmcp/mobius/waila/api/IEntityAccessor;Lmcp/mobius/waila/api/IPluginConfig;)V",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private void polyglot$appendSecondaryName(List<Component> tooltip, IEntityAccessor accessor,
                                             IPluginConfig config, CallbackInfo ci) {
        if (!(accessor.getEntity() instanceof ItemEntity)) {
            LegacyJadeTooltipHelper.appendName(tooltip, accessor.getEntity().getDisplayName());
        }
    }
}
