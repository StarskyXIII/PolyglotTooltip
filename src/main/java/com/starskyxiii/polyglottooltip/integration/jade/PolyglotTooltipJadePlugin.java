package com.starskyxiii.polyglottooltip.integration.jade;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
//? if <=1.18.2 {
/*import mcp.mobius.waila.api.IWailaClientRegistration;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import mcp.mobius.waila.api.WailaPlugin;
*///?} else {
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
//?}

@WailaPlugin
public class PolyglotTooltipJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        //? if <=1.18.2 {
        /*registration.registerComponentProvider(JadeBlockNameProvider.INSTANCE, TooltipPosition.HEAD, Block.class);
        registration.registerComponentProvider(JadeEntityNameProvider.INSTANCE, TooltipPosition.HEAD, Entity.class);
        *///?} else {
        registration.registerBlockComponent(JadeBlockNameProvider.INSTANCE, Block.class);
        registration.registerEntityComponent(JadeEntityNameProvider.INSTANCE, Entity.class);
        //?}
    }
}
