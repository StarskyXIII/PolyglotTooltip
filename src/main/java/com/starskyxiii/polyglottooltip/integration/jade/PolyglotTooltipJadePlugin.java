package com.starskyxiii.polyglottooltip.integration.jade;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class PolyglotTooltipJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.markAsClientFeature(JadeBlockNameProvider.INSTANCE.getUid());
        registration.registerBlockComponent(JadeBlockNameProvider.INSTANCE, Block.class);
        PolyglotTooltip.LOGGER.info("[PolyglotTooltip] Registered Jade provider {}", JadeBlockNameProvider.INSTANCE.getUid());
    }
}
