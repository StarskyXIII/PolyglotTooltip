package com.sammy.malum.core.handlers;

//? if neoforge && >=1.21.1 && <=1.21.1 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class GeasEffectHandler {
    public static Optional<GeasData> getStoredGeasEffect(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        String key = data.copyTag().getString("geas");
        if ("broken".equals(key)) throw new IllegalArgumentException("Invalid effect");
        return key.isEmpty() ? Optional.empty() : Optional.of(new GeasData(new GeasType(key)));
    }

    public record GeasData(GeasType geasEffectType) {}
    public record GeasType(String key) {
        public String getLangKey() { return key; }
    }
}
//?}
