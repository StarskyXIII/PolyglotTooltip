package com.sammy.malum.common.item;

//? if neoforge && >=1.21.1 && <=1.21.1 {
import net.minecraft.world.item.Item;

public class GeasItem extends Item {
    public GeasItem() { super(new Item.Properties()); }

    @Override
    public String getDescriptionId() { return "item.malum.geas"; }
}
//?}
